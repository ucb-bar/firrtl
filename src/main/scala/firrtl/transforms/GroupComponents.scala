package firrtl.transforms

import firrtl._
import firrtl.Mappers._
import firrtl.ir._
import firrtl.annotations.{Annotation, ComponentName}
import firrtl.passes.{LowerTypes, MemPortUtils}
import firrtl.Utils.{kind, throwInternalError}
import firrtl.graph.{DiGraph, MutableDiGraph}

import scala.collection.mutable


case class GroupAnnotation(components: Set[ComponentName], newModule: String, newInstance: String, outputSuffix: Option[String] = None, inputSuffix: Option[String]) extends Annotation {
  if(components.nonEmpty) {
    require(components.forall(_.module == components.head.module), "All components must be in the same module.")
    require(components.forall(!_.name.contains('.')), "No components can be a subcomponent.")
  }
  def currentModule: String = components.head.module.name

  /* Only keeps components renamed to components */
  def update(renames: RenameMap): Seq[Annotation] = {
    val newComponents = components.flatMap{c => renames.get(c).getOrElse(Seq(c))}.collect {
      case c: ComponentName => c
    }
    Seq(GroupAnnotation(newComponents, newModule, newInstance, outputSuffix, inputSuffix))
  }
}

class GroupComponents extends firrtl.Transform {
  type MSet[T] = mutable.Set[T]

  def inputForm: CircuitForm = LowForm
  def outputForm: CircuitForm = LowForm

  override def execute(state: CircuitState): CircuitState = {
    println(state.circuit.serialize)
    val groups = state.annotations.collect {case g: GroupAnnotation => g}
    val byModule = groups.groupBy{_.currentModule}
    val mnamespace = Namespace(state.circuit)
    val newModules = state.circuit.modules.flatMap {
      case m: Module if byModule.contains(m.name) =>
        // do stuff
        groupModule(m, byModule(m.name).filter(_.components.nonEmpty), mnamespace)
      case other => Seq(other)
    }
    val cs = state.copy(circuit = state.circuit.copy(modules = newModules))
    println(cs.circuit.serialize)
    cs
  }

  def groupModule(m: Module, groups: Seq[GroupAnnotation], mnamespace: Namespace): Seq[Module] = {
    val namespace = Namespace(m)
    val groupRoots = groups.map(_.components.map(_.name))
    val totalSum = groupRoots.foldLeft(0){(total, set) => total + set.size}
    val union = groupRoots.foldLeft(Set.empty[String]){(all, set) => all.union(set)}

    require(groupRoots.forall{_.forall{namespace.contains}}, "All names should be in this module")
    require(totalSum == union.size, "No name can be in more than one group")
    require(groupRoots.forall(_.nonEmpty), "All groupRoots must by non-empty")



    // Name groupRoots by first element
    //val byGroup: Map[String, MSet[String]] = groupRoots.collect{case set => set.head -> mutable.Set(set.toSeq:_*)}.toMap
    val annos = groups.collect{case g:GroupAnnotation => g.components.head.name -> g}.toMap
    val byGroup: Map[String, MSet[String]] = groups.collect{case GroupAnnotation(set, module, instance, _, _) => set.head.name -> mutable.Set(set.map(_.name).toSeq:_*)}.toMap
    val groupModule: Map[String, String] = groups.map(a => a.components.head.name -> mnamespace.newName(a.newModule)).toMap
    val groupInstance: Map[String, String] = groups.map(a => a.components.head.name -> namespace.newName(a.newInstance)).toMap

    // Build set of components not in set
    val notSet = byGroup.map { case (key, value) => key -> union.diff(value) }

    // Get all dependencies between components
    val (deps, drives) = getBiDirectionalComponentConnectivity(m)
    println(deps.getEdgeMap)
    println(drives.getEdgeMap)

    // For each node not in the set, which group can reach it
    val reachableNodes = new mutable.HashMap[String, MSet[String]]()

    // For each group, add connectivity between nodes in set
    // Populate reachableNodes with reachability, where blacklist is their notSet
    byGroup.foreach { case (head, set) =>
      set.foreach { x =>
        deps.addPairWithEdge(head, x)
      }
      deps.reachableFrom(head, notSet(head)) foreach { node =>
        reachableNodes.getOrElseUpdate(node, mutable.Set.empty[String]) += head
      }
    }

    // Add nodes who are reached by a single group, to that group
    reachableNodes.foreach { case (node, membership) =>
      if(membership.size == 1) {
        byGroup(membership.head) += node
      }
    }

    // Per group, find edges that cross group border
    val crossings = new mutable.HashMap[String, MSet[(String, String)]]
    byGroup.foreach { case (head, set) =>
      val edges = set.flatMap { node =>
        deps.getEdges(node).collect{case you: String if !set.contains(you) => (node, you)}
      }
      crossings(head) = edges
    }

    applyGrouping(m, namespace, byGroup, crossings, groupModule, groupInstance, annos).toSeq
  }

  def applyGrouping( m: Module,
                     namespace: Namespace,
                     byGroup: Map[String, MSet[String]],
                     crossings: mutable.Map[String, MSet[(String, String)]],
                     groupModule: Map[String, String],
                     groupInstance: Map[String, String],
                     annos: Map[String, GroupAnnotation]
                   ): Set[Module] = {
    // Maps node to group
    val byNode = mutable.HashMap[String, String]()
    byGroup.foreach { case (group, nodes) =>
      nodes.foreach { node =>
        byNode(node) = group
      }
    }
    val groupNamespace = byGroup.map { case (head, set) => head -> Namespace(set.toSeq) }

    val groupStatements = mutable.HashMap[String, mutable.ArrayBuffer[Statement]]()
    val groupPorts = mutable.HashMap[String, mutable.ArrayBuffer[Port]]()
    val groupPortNames = mutable.HashMap[String, mutable.HashMap[String, String]]()
    byGroup.keys.foreach { group =>
      groupStatements(group) = new mutable.ArrayBuffer[Statement]()
      groupPorts(group) = new mutable.ArrayBuffer[Port]()
      groupPortNames(group) = new mutable.HashMap[String, String]()
    }

    def fixEdgesOfGroupedSink(group: String, added: mutable.ArrayBuffer[Statement])(e: Expression): Expression = e match {
      case w@WRef(source, tpe, kind, gender) =>
        byNode.get(source) match {
          // case 1: source in the same group as sink
          case Some(`group`) => w //do nothing
          // case 2: source in different group
          case Some(other) =>
            // Add port to other's Module
            val otherPortName = groupPortNames(other).getOrElseUpdate(source, groupNamespace(other).newName(source + annos(other).outputSuffix.getOrElse("")))
            groupPorts(other) += Port(NoInfo, otherPortName, Output, tpe)

            // Add connection in other's Module from source to its port
            groupStatements(other) += Connect(NoInfo, WRef(otherPortName), w)

            // Add port to group's Module
            val groupPortName = groupPortNames(group).getOrElseUpdate(source, groupNamespace(group).newName(source + annos(group).inputSuffix.getOrElse("")))
            groupPorts(other) += Port(NoInfo, groupPortName, Output, tpe)

            // Add connection in Top from other's port to group's port
            val groupInst = groupInstance(group)
            val otherInst = groupInstance(other)
            added += Connect(NoInfo, WSubField(WRef(groupInst), groupPortName), WSubField(WRef(otherInst), otherPortName))

            // Return WRef with new kind (its inside the group Module now)
            WRef(groupPortName, tpe, PortKind, MALE)

          // case 3: source in top
          case None =>
            // Add port to group's Module
            val groupPortName = groupPortNames(group).getOrElseUpdate(source, groupNamespace(group).newName(source + annos(group).inputSuffix.getOrElse("")))
            groupPorts(group) += Port(NoInfo, groupPortName, Input, tpe)

            // Add connection in Top to group's Module port
            added += Connect(NoInfo, WSubField(WRef(groupInstance(group)), groupPortName), WRef(source))

            // Return WRef with new kind (its inside the group Module now)
            WRef(groupPortName, tpe, PortKind, MALE)

        }
      case other => other map fixEdgesOfGroupedSink(group, added)
    }

    def fixEdgesOfTopSink(e: Expression): Expression = e match {
      // case 1: source is in a group
      case w@WRef(source, tpe, kind, gender) if byNode.contains(source) =>
        // Get the name of source's group
        val other = byNode(source)

        // Add port to other's Module
        val otherPortName = groupPortNames(other).getOrElseUpdate(source, groupNamespace(other).newName(source + annos(other).outputSuffix.getOrElse("")))
        groupPorts(other) += Port(NoInfo, otherPortName, Output, tpe)

        // Add connection in other's Module from source to its port
        groupStatements(other) += Connect(NoInfo, WRef(otherPortName), w)

        // Return WSubField (its inside the top Module still)
        WSubField(WRef(groupInstance(other)), otherPortName)
      case other => other map fixEdgesOfTopSink
    }

    def onStmt(s: Statement): Statement = {
      s match {
        // Sink is in a group
        case r: IsDeclaration if byNode.contains(r.name) =>
          //
          val topStmts = mutable.ArrayBuffer[Statement]()
          val group = byNode(r.name)
          groupStatements(group) += r mapExpr fixEdgesOfGroupedSink(group, topStmts)
          Block(topStmts)
        case c: Connect if byNode.contains(getWRef(c.loc).name) =>
          // Sink is in a group
          val topStmts = mutable.ArrayBuffer[Statement]()
          val group = byNode(getWRef(c.loc).name)
          groupStatements(group) += Connect(c.info, c.loc, fixEdgesOfGroupedSink(group, topStmts)(c.expr))
          Block(topStmts)
        case _: IsDeclaration|_: Connect|_: Attach =>
          // Sink is in Top
          val ret = s mapExpr fixEdgesOfTopSink
          ret
        case other => other map onStmt
      }
    }


    // Build datastructures
    val newTopBody = Block(groupModule.map{case (g, m) => WDefInstance(NoInfo, groupInstance(g), m, UnknownType)}.toSeq ++ Seq(onStmt(m.body)))
    val finalTopBody = Block(Utils.squashEmpty(newTopBody).asInstanceOf[Block].stmts.distinct)

    val newModules = byGroup.keys.map { group =>
      Module(NoInfo, groupModule(group), groupPorts(group).distinct, Block(groupStatements(group).distinct))
    }
    Set(m.copy(body = finalTopBody)) ++ newModules
  }

  def getWRef(e: Expression): WRef = e match {
    case w: WRef => w
    case other =>
      var w = WRef("")
      other mapExpr { e => w = getWRef(e); e}
      w
  }

  def getBiDirectionalComponentConnectivity(m: Module): (MutableDiGraph[String], MutableDiGraph[String]) = {
    val blacklist = m.ports.map(_.name).toSet
    val colorGraph = new MutableDiGraph[String]
    val driveGraph = new MutableDiGraph[String]
    val simNamespace = Namespace()
    val simulations = new mutable.HashMap[String, Statement]
    def onExpr(sink: WRef)(e: Expression): Expression = e match {
      case w@WRef(name, _, _, _) if !blacklist.contains(name) && !blacklist.contains(sink.name) =>
        println(blacklist)
        println(name)
        colorGraph.addPairWithEdge(sink.name, name)
        colorGraph.addPairWithEdge(name, sink.name)
        driveGraph.addPairWithEdge(name, sink.name)
        w
      case other => other map onExpr(sink)
    }
    def onStmt(stmt: Statement): Unit = stmt match {
      case w: WDefInstance =>
      case h: IsDeclaration => h map onExpr(WRef(h.name))
      case Attach(_, exprs) => // Add edge between each expression
        exprs.tail map onExpr(getWRef(exprs.head))
      case Connect(_, loc, expr) =>
        onExpr(getWRef(loc))(expr)
      case q@Stop(_,_, clk, en) =>
        val simName = simNamespace.newTemp
        simulations(simName) = q
        Seq(clk, en) map onExpr(WRef(simName))
      case q@Print(_, _, args, clk, en) =>
        val simName = simNamespace.newTemp
        simulations(simName) = q
        (args :+ clk :+ en) map onExpr(WRef(simName))
      case Block(stmts) => stmts.foreach(onStmt)
      case ignore @ (_: IsInvalid | EmptyStmt) => // do nothing
      case other => throw new Exception(s"Unexpected Statement $other")
    }

    onStmt(m.body)
    (colorGraph, driveGraph)
  }
}
