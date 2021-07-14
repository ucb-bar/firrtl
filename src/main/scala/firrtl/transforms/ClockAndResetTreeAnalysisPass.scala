// SPDX-License-Identifier: Apache-2.0

package firrtl.transforms

import firrtl._
import firrtl.analyses.InstanceKeyGraph
import firrtl.analyses.InstanceKeyGraph.InstanceKey
import firrtl.annotations.{Annotation, CircuitTarget, IsModule, ModuleTarget, ReferenceTarget, SingleTargetAnnotation}
import firrtl.options.Dependency

import scala.collection.mutable

// TODO: distinguish sync vs async reset
case class ResetAnnotation(target: ReferenceTarget, source: String, inverted: Boolean = false)
    extends SingleTargetAnnotation[ReferenceTarget] {
  override def duplicate(n: ReferenceTarget) = copy(target = n)
}
case class ClockAnnotation(target: ReferenceTarget, source: String, inverted: Boolean = false)
    extends SingleTargetAnnotation[ReferenceTarget] {
  override def duplicate(n: ReferenceTarget) = copy(target = n)
}
case class ClockSourceAnnotation(target: ReferenceTarget, sinkCount: Int)
    extends SingleTargetAnnotation[ReferenceTarget] {
  override def duplicate(n: ReferenceTarget) = copy(target = n)
}
case class ResetSourceAnnotation(target: ReferenceTarget, sinkCount: Int)
    extends SingleTargetAnnotation[ReferenceTarget] {
  override def duplicate(n: ReferenceTarget) = copy(target = n)
}

/** Finds all clocks and reset signals in the design and annotates them with a global name.
  * @author Kevin Laeufer <laeufer@berkeley.edu>
  */
object ClockAndResetTreeAnalysisPass extends Transform with DependencyAPIMigration {
  // we want every wire to only have a single assignment
  override def prerequisites = Seq(Dependency[passes.ExpandWhensAndCheck], Dependency(passes.LowerTypes))
  // we want to be able to identify resets (including synchronous resets)
  override def optionalPrerequisiteOf = Seq(Dependency(firrtl.transforms.RemoveReset))
  // we do not change the circuit, only annotate the results
  override def invalidates(a: Transform) = false

  override def execute(state: CircuitState): CircuitState = {
    // analyze each module in isolation
    val local = state.circuit.modules.map(m => m.name -> ModuleTreeScanner.scan(m)).toMap

    // combine local information into a global list of clocks and resets
    val iGraph = InstanceKeyGraph(state.circuit)
    val merged = mergeInfo(iGraph, local)
    val annos = makeAnnos(iGraph, merged)

    state.copy(annotations = annos ++ state.annotations)
  }

  import ModuleTreeScanner.{ModuleInfo, Sink, Tree}

  private def mergeInfo(iGraph: InstanceKeyGraph, local: Map[String, ModuleInfo]): ModuleInfo = {
    // compute global results
    val moduleOrderBottomUp = iGraph.moduleOrder.reverseIterator
    val childInstances = iGraph.getChildInstances.toMap
    val merged = mutable.HashMap[String, ModuleInfo]()

    moduleOrderBottomUp.foreach {
      case m: ir.Module =>
        val info = mergeWithSubmodules(local(m.name), merged, childInstances(m.name))
        merged(m.name) = info
      case e: ir.ExtModule =>
        merged(e.name) = local(e.name)
    }

    merged(iGraph.top.module)
  }

  private def mergeWithSubmodules(
    local:     ModuleInfo,
    getMerged: String => ModuleInfo,
    instances: Seq[InstanceKey]
  ): ModuleInfo = {
    val submoduleInfo = instances.map { case InstanceKey(name, module) => name -> getMerged(module) }
    // resolve all new tree connections
    val resolved = resolveTrees(local, submoduleInfo)
    ModuleInfo(resolved)
  }

  private def resolveTrees(local: ModuleInfo, submoduleInfo: Seq[(String, ModuleInfo)]): Seq[Tree] = {
    // we consider local connections and any connections from our submodule
    val trees = local.trees ++ submoduleInfo.flatMap { case (n, i) => i.trees.map(addPrefix(n, _)) }

    // If the source is local to our module (i.e., it does not contain a '.') we consider it as a local starting point.
    // If the source is "final", i.e. it can not be expanded, it also serves as a starting point.
    def isStart(t: Tree) = !t.source.contains('.') || t.sourceIsFinal
    val (startSrc, intSrc) = trees.partition(isStart)

    // create an index for internal sources to allow us to trace connections
    val intTrees = intSrc.groupBy(_.source) // since it is a clock/reset TREE, a single source can have multiple sinks

    // the connections that start at an "external" port are our starting points
    var todo = startSrc

    // collect all connections that start at an external source
    val done = mutable.ArrayBuffer[Tree]()

    // this is a fixed point computation, since connections could "snake" through submodules
    while (todo.nonEmpty) {
      todo = todo.flatMap { tree =>
        val (didExpand, expandedTree) = expandTree(tree, intTrees)
        if (didExpand) {
          Some(expandedTree)
        } else {
          done.append(expandedTree)
          None
        }
      }
    }

    done.toSeq
  }

  private def expandTree(tree: Tree, sources: Map[String, Seq[Tree]]): (Boolean, Tree) = {
    // expand leaves when possible
    val leavesAndInternal = tree.leaves.map { s =>
      sources.get(s.fullName) match {
        case Some(value) =>
          // if there is an expansion, the leaf becomes and inner node
          val leaves = value.flatMap(v => connectSinks(tree.source, v.leaves, s.inverted))
          val internal = s +: value.flatMap(v => connectSinks(tree.source, v.internal, s.inverted))
          (leaves, internal)
        case None =>
          // if there is no expansion, the leaf stays a leaf
          (List(s), List())
      }
    }

    val leaves = leavesAndInternal.flatMap(_._1)
    val newInternal = leavesAndInternal.flatMap(_._2)
    val didExpand = newInternal.nonEmpty

    (didExpand, tree.copy(leaves = leaves, internal = tree.internal ++ newInternal))
  }

  private def connectSinks(source: String, sinks: Seq[Sink], inverted: Boolean): Seq[Sink] =
    sinks.map(s => s.copy(inverted = s.inverted ^ inverted))

  private def addPrefix(name: String, t: Tree): Tree = {
    val leaves = t.leaves.map(s => s.addPrefix(name))
    val internal = t.internal.map(s => s.addPrefix(name))
    Tree(name + "." + t.source, t.sourceIsFinal, leaves, internal = internal)
  }

  private def makeAnnos(iGraph: InstanceKeyGraph, merged: ModuleInfo): AnnotationSeq = {
    val top = CircuitTarget(iGraph.top.module).module(iGraph.top.module)
    val childInstances = iGraph.getChildInstances.toMap

    // analyze trees and annotate sources
    val sourceAnnosAndSinks = merged.trees.flatMap { t =>
      val info = ModuleTreeScanner.analyzeTree(t)
      def target = pathToTarget(childInstances, top, t.source.split('.').toList)

      if (info.clockSinks > 0) {
        assert(!(info.resetSinks > 0), s"Tree starting at ${t.source} is used both as a reset and a clock!")
        assert(!(info.resetPortSink > 0), s"Tree starting at ${t.source} is used both as a reset and a clock!")
        val sinks = t.sinks.map(s => SinkAnnoInfo(s.prefix, s.name, t.source, s.inverted, isClockNotReset = true))
        Some((ClockSourceAnnotation(target, info.clockSinks), sinks))
      } else if (info.resetSinks > 0) {
        assert(!(info.clockPortSinks > 0), s"Tree starting at ${t.source} is used both as a reset and a clock!")
        val sinks = t.sinks.map(s => SinkAnnoInfo(s.prefix, s.name, t.source, s.inverted, isClockNotReset = false))
        Some((ResetSourceAnnotation(target, info.resetSinks), sinks))
      } else { None }
    }

    val sourceAnnos = sourceAnnosAndSinks.map(_._1)
    val sinkAnnos = annotateSinks(CircuitTarget(iGraph.top.module), childInstances, sourceAnnosAndSinks.flatMap(_._2))

    sourceAnnos ++ sinkAnnos
  }

  private case class SinkAnnoInfo(
    prefix:          String,
    name:            String,
    source:          String,
    inverted:        Boolean,
    isClockNotReset: Boolean)

  private def annotateSinks(
    c:              CircuitTarget,
    childInstances: Map[String, Seq[InstanceKey]],
    sinks:          Seq[SinkAnnoInfo]
  ): Seq[Annotation] = {
    val topInstance = InstanceKey("", c.name)
    val topTarget = c.module(c.name)
    val topChildren = childInstances(topInstance.module)
    val instances = topInstance +: topChildren.flatMap(onInstance("", _, childInstances))

    val moduleToInstances = instances.groupBy(_.module).toSeq.sortBy(_._1)
    val instanceToSinks = sinks.groupBy(_.prefix)
    moduleToInstances.flatMap {
      case (module, insts) =>
        val instSinks = insts.map(i => instanceToSinks.getOrElse(i.name, List()))
        // if the sinks are the same in all instances, we can just do module level annos
        val (same, different) = sameSinks(instSinks)
        val m = c.module(module)
        val moduleAnnos = same.map(makeSinkAnno(childInstances, m, _))

        // for sinks that are different in different instances, we need to annotate on an instance level
        val instAnnos = different.map { info =>
          val m = pathToModuleTarget(childInstances, topTarget, info.prefix.split('.').toList)
          makeSinkAnno(childInstances, m, info)
        }

        moduleAnnos ++ instAnnos
    }
  }

  private def makeSinkAnno(
    childInstances: Map[String, Seq[InstanceKey]],
    top:            IsModule,
    info:           SinkAnnoInfo
  ): Annotation = {
    val target = pathToTarget(childInstances, top, info.name.split('.').toList)
    if (info.isClockNotReset) {
      ClockAnnotation(target, source = info.source, inverted = info.inverted)
    } else {
      ResetAnnotation(target, source = info.source, inverted = info.inverted)
    }
  }

  // partitions sinks into sinks that are the same in all instances and the ones that are different across instances
  private def sameSinks(instSinks: Seq[Seq[SinkAnnoInfo]]): (Seq[SinkAnnoInfo], Seq[SinkAnnoInfo]) = {
    require(instSinks.nonEmpty)
    if (instSinks.length == 1) return (removePrefix(instSinks.head), List())
    val byPrefixFreeInfo = instSinks.flatten.groupBy(i => i.copy(prefix = "")).toSeq
    val completeSize = instSinks.length
    val (all, notAll) = byPrefixFreeInfo.partition(_._2.size == completeSize)
    (all.map(_._1), notAll.flatMap(_._2))
  }
  private def removePrefix(infos: Seq[SinkAnnoInfo]): Seq[SinkAnnoInfo] = infos.map(i => i.copy(prefix = ""))

  private def pathToTarget(
    childInstances: Map[String, Seq[InstanceKey]],
    top:            IsModule,
    path:           List[String]
  ): ReferenceTarget = path match {
    case List(name) => top.ref(name)
    case inst :: tail =>
      val instances = childInstances(top.leafModule)
      val module = instances
        .find(_.name == inst)
        .getOrElse(
          throw new RuntimeException(
            s"Failed to find instance $inst in $top for path $path.\nAvailable: " + instances.mkString(", ")
          )
        )
        .module
      pathToTarget(childInstances, top.instOf(inst, module), tail)
  }
  private def pathToModuleTarget(
    childInstances: Map[String, Seq[InstanceKey]],
    top:            IsModule,
    path:           List[String]
  ): IsModule = path match {
    case Nil => top
    case inst :: tail =>
      val instances = childInstances(top.leafModule)
      val module = instances
        .find(_.name == inst)
        .getOrElse(
          throw new RuntimeException(
            s"Failed to find instance $inst in $top for path $path.\nAvailable: " + instances.mkString(", ")
          )
        )
        .module
      pathToModuleTarget(childInstances, top.instOf(inst, module), tail)
  }

  private def onInstance(
    prefix:   String,
    inst:     InstanceKey,
    children: Map[String, Seq[InstanceKey]]
  ): Seq[InstanceKey] = {
    val ii = InstanceKey(prefix + inst.name, inst.module)
    val cc = children(ii.module).flatMap(onInstance(ii.name + ".", _, children))
    ii +: cc
  }

}

private object ModuleTreeScanner {
  private case class Con(lhs: String, rhs: String, inverted: Boolean, info: ir.Info)

  def scan(m: ir.DefModule): ModuleInfo = m match {
    case e: ir.ExtModule =>
      val outPorts = e.ports.filter(_.direction == ir.Output).filter(p => couldBeResetOrClock(p.tpe))
      val outputs =
        outPorts.map(p => Tree(p.name, sourceIsFinal = true, List(Sink(p.name, "", false, List(PortSink(p))))))
      ModuleInfo(outputs)
    case mod: ir.Module =>
      val scan = new ModuleTreeScanner()
      scan.onModule(mod)
      analyzeResults(scan)
  }

  private def analyzeResults(m: ModuleTreeScanner): ModuleInfo = {
    // our analysis goes backwards, from output, clock or reset use to the source
    val cons = m.connections.map(c => c.lhs -> c).toMap
    val isInput = m.inputs.toSet

    // determine the source of all sinks and merge them together by source
    val sourceToSink = m.sinks.toSeq.map {
      case (name, infos) =>
        val (src, inverted) = findSource(cons, name)
        src -> Sink(name, "", inverted, infos)
    }
    val trees = sourceToSink.groupBy(_._1).map {
      case (source, sinks) =>
        val (leaves, internal) = sinks.map(_._2).partition(s => isPort(s))
        val sourceIsFinal = !isInput(source) // if the source is not an input, we know its final origin
        Tree(source, sourceIsFinal, leaves, internal)
    }

    // is there any reason we should filter out trees?
    val inputTrees = trees.toSeq

    ModuleInfo(inputTrees)
  }

  private def isPort(sink: Sink): Boolean = sink.infos.exists(_.isPort)

  private def findSource(cons: Map[String, Con], name: String): (String, Boolean) = cons.get(name) match {
    case Some(value) =>
      val (name, inv) = findSource(cons, value.rhs)
      (name, inv ^ value.inverted)
    case None => (name, false)
  }

  sealed trait SinkInfo {
    def info: ir.Info; def isReset: Boolean = false; def isClock: Boolean = false; def isPort: Boolean = false
  }
  case class MemClockSink(m: ir.DefMemory, port: String) extends SinkInfo {
    override def isClock = true
    override def info = m.info
  }
  case class RegClockSink(r: ir.DefRegister) extends SinkInfo {
    override def isClock = true
    override def info = r.info
  }
  case class StmtClockSink(s: ir.Statement with ir.HasInfo) extends SinkInfo {
    override def isClock = true
    override def info = s.info
  }
  case class RegResetSink(r: ir.DefRegister) extends SinkInfo {
    override def isReset = true
    override def info = r.info
  }
  case class RegNextSink(r: ir.DefRegister) extends SinkInfo { override def info = r.info }
  case class PortSink(p: ir.Port) extends SinkInfo { override def info = p.info; override def isPort = true }
  case class InstSink(i: ir.DefInstance, port: String) extends SinkInfo {
    override def info = i.info; override def isPort = true
  }
  case class Sink(name: String, prefix: String, inverted: Boolean, infos: Seq[SinkInfo]) {
    def addPrefix(p: String): Sink = if (prefix.isEmpty) { copy(prefix = p) }
    else { copy(prefix = p + "." + prefix) }
    def fullName: String = if (prefix.isEmpty) { name }
    else { prefix + "." + name }
  }
  case class Tree(source: String, sourceIsFinal: Boolean, leaves: Seq[Sink], internal: Seq[Sink] = List()) {
    def sinks: Iterable[Sink] = leaves ++ internal
    override def toString = s"Tree($source -> " + sinks.map(_.fullName).mkString(", ") + ")"
  }
  case class ModuleInfo(trees: Seq[Tree])

  case class TreeInfo(resetSinks: Int, clockSinks: Int, clockPortSinks: Int, resetPortSink: Int)
  def analyzeTree(tree: Tree): TreeInfo = {
    var resetSinks = 0
    var clockSinks = 0
    var clockPortSinks = 0
    var resetPortSinks = 0
    (tree.leaves ++ tree.internal).foreach { s =>
      s.infos.foreach { i =>
        if (i.isReset) resetSinks += 1
        if (i.isClock) clockSinks += 1
        if (i.isPort) {
          i match {
            case PortSink(ir.Port(_, _, _, ir.ResetType))      => resetPortSinks += 1
            case PortSink(ir.Port(_, _, _, ir.AsyncResetType)) => resetPortSinks += 1
            case PortSink(ir.Port(_, _, _, ir.ClockType))      => clockPortSinks += 1
            case _                                             =>
          }
        }
      }
    }
    TreeInfo(resetSinks, clockSinks, clockPortSinks, resetPortSinks)
  }

  private def couldBeResetOrClock(tpe: ir.Type): Boolean = tpe match {
    case ir.ClockType                          => true
    case ir.ResetType                          => true
    case ir.AsyncResetType                     => true
    case ir.UIntType(ir.IntWidth(w)) if w == 1 => true
    case ir.SIntType(ir.IntWidth(w)) if w == 1 => true
    case _                                     => false
  }
}

/** Analyses all potential potential clock/reset signals in the module
  *  - the goal here is to deal with some casts
  *  - we treat SyncReset, Reset, UInt<1> and SInt<1> as potential reset signals
  *  - we treat Clock, UInt<1> and SInt<1> as potential clock signals
  *  - the only operation that we allow on reset/clock signals is inversion (not(...))
  *    this will turn a posedge clock into a negedge clock and a high-active reset into
  *    a low-active reset
  *  - we also try our best to filter out no-ops, like a bits(..., 0, 0) extraction
  */
private class ModuleTreeScanner {
  import ModuleTreeScanner._

  // we keep track of the usage and connections of signals that could be reset or clocks
  private val sinks = mutable.HashMap[String, List[SinkInfo]]()
  private val inputs = mutable.ArrayBuffer[String]()
  private val regs = mutable.HashMap[String, ir.DefRegister]()
  private val mems = mutable.HashMap[String, ir.DefMemory]()
  private val connections = mutable.ArrayBuffer[Con]()

  def onModule(m: ir.Module): Unit = {
    m.ports.foreach(onPort)
    onStmt(m.body)
  }

  /** called for module inputs and submodule outputs */
  private def addInput(ref: ir.RefLikeExpression): Unit = {
    if (!couldBeResetOrClock(ref.tpe)) return
    inputs.append(ref.serialize)
  }

  private def addSinkInfo(name: String, i: SinkInfo): Unit = {
    sinks(name) = i +: sinks.getOrElse(name, List())
  }

  /** called for module outputs and submodule inputs */
  private def addOutput(ref: ir.RefLikeExpression, info: SinkInfo): Unit = {
    if (!couldBeResetOrClock(ref.tpe)) return
    addSinkInfo(ref.serialize, info)
  }

  private def onPort(p: ir.Port): Unit = p.direction match {
    case ir.Input  => addInput(ir.Reference(p))
    case ir.Output => addOutput(ir.Reference(p), PortSink(p))
  }

  private def onInstance(i: ir.DefInstance): Unit = {
    val ports = i.tpe.asInstanceOf[ir.BundleType].fields
    val ref = ir.Reference(i)
    // for fields, Default means Output, Flip means Input
    ports.foreach {
      // we treat the outputs of the submodule as inputs to our module
      case ir.Field(name, ir.Default, tpe) => addInput(ir.SubField(ref, name, tpe))
      case ir.Field(name, ir.Flip, tpe)    => addOutput(ir.SubField(ref, name, tpe), InstSink(i, name))
      case ir.Field(_, other, _)           => throw new RuntimeException(s"Unexpected field direction: $other")
    }
  }

  private def onConnectSignal(lhs: String, rhs: ir.Expression, info: ir.Info): Unit = {
    analyzeClockOrReset(rhs) match {
      case Some((name, inv)) => connections.append(Con(lhs, name, inv, info))
      case None              =>
    }
  }

  private def onConnect(c: ir.Connect): Unit = {
    val loc = c.loc.asInstanceOf[ir.RefLikeExpression]
    Utils.kind(loc) match {
      case RegKind =>
        addSinkInfo(loc.serialize, RegNextSink(regs(loc.serialize)))
      case PortKind     => onConnectSignal(loc.serialize, c.expr, c.info)
      case InstanceKind => onConnectSignal(loc.serialize, c.expr, c.info)
      case MemKind if loc.serialize.endsWith(".clk") =>
        loc match {
          case ir.SubField(ir.SubField(ir.Reference(name, _, _, _), port, _, _), "clk", _, _) =>
            useClock(c.expr, MemClockSink(mems(name), port))
          case other => throw new RuntimeException(s"unexpected pattern: $other (${other.serialize})")
        }
      case WireKind => onConnectSignal(loc.serialize, c.expr, c.info)
      case _        => case other => throw new RuntimeException(s"Unexpected connect of kind: ${other} (${c.serialize})")
    }
  }

  private def onStmt(s: ir.Statement): Unit = s match {
    case i: ir.DefInstance => onInstance(i)
    case r: ir.DefRegister =>
      if (couldBeResetOrClock(r.tpe)) { regs(r.name) = r }
      useClock(r.clock, RegClockSink(r))
      useReset(r.reset, r)
    case ir.DefNode(info, name, value) =>
      // we ignore any connects that cannot involve resets or clocks because of the type
      if (couldBeResetOrClock(value.tpe)) { onConnectSignal(name, value, info) }
    case c @ ir.Connect(_, lhs, _) =>
      // we ignore any connects that cannot involve resets or clocks because of the type
      if (couldBeResetOrClock(lhs.tpe)) { onConnect(c) }
    case ir.Block(stmts) => stmts.foreach(onStmt)
    case p: ir.Print        => useClock(p.clk, StmtClockSink(p))
    case s: ir.Stop         => useClock(s.clk, StmtClockSink(s))
    case v: ir.Verification => useClock(v.clk, StmtClockSink(v))
    case _: ir.DefWire      => // nothing to do
    case m: ir.DefMemory    => mems(m.name) = m
    case _: ir.IsInvalid    =>
    case ir.EmptyStmt =>
    case other        => throw new RuntimeException(s"Unexpected statement type: ${other.serialize}")
  }

  /** called when a clock is used for a register or memory */
  private def useClock(e: ir.Expression, info: SinkInfo): Unit = {
    analyzeClockOrReset(e) match {
      case Some((name, false)) => addSinkInfo(name, info)
      case Some((_, true))     => throw new NotImplementedError("TODO: deal with inversions")
      case None                =>
    }
  }

  /** called when a reset is used for a register */
  private def useReset(e: ir.Expression, r: ir.DefRegister): Unit = {
    analyzeClockOrReset(e) match {
      case Some((name, false)) => addSinkInfo(name, RegResetSink(r))
      case Some((_, true))     => throw new NotImplementedError("TODO: deal with inversions")
      case None                =>
    }
  }

  /** analyzes the expression as a (potential) clock signal */
  private def analyzeClockOrReset(e: ir.Expression): Option[(String, Boolean)] = e match {
    case ref: ir.RefLikeExpression => Some((ref.serialize, false))
    case _:   ir.Mux               => None // for now we do not analyze muxes
    case ir.DoPrim(op, Seq(arg), consts, _) =>
      op match {
        case PrimOps.Not                                          => analyzeClockOrReset(arg).map { case (n, inv) => n -> !inv }
        case PrimOps.AsAsyncReset                                 => analyzeClockOrReset(arg)
        case PrimOps.AsClock                                      => analyzeClockOrReset(arg)
        case PrimOps.AsUInt                                       => analyzeClockOrReset(arg)
        case PrimOps.AsSInt                                       => analyzeClockOrReset(arg)
        case PrimOps.Bits if consts == List(BigInt(0), BigInt(0)) => analyzeClockOrReset(arg)
        case _                                                    => None
      }
    // TODO: can we always safely ignore that a clock or reset signal might be invalid?
    case ir.ValidIf(_, value, _) => analyzeClockOrReset(value)
    case _                       => None
  }
}
