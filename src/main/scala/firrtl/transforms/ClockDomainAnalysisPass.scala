// SPDX-License-Identifier: Apache-2.0

package firrtl.transforms

import firrtl._
import firrtl.analyses.InstanceKeyGraph
import firrtl.annotations._
import firrtl.options.Dependency

import scala.collection.mutable

/** marks a module where all signals are under a single clock domain */
case class SingleClockModule(target: ModuleTarget) extends SingleTargetAnnotation[ModuleTarget] {
  override def duplicate(n: ModuleTarget) = copy(target = n)
}

/** marks a module where signals are under different clock domains
  *  @note the signal names in this annotation will not be renamed!
  */
case class MultiClockModule(target: ModuleTarget, domains: Seq[ClockDomain])
    extends SingleTargetAnnotation[ModuleTarget] {
  override def duplicate(n: ModuleTarget) = copy(target = n)
}
case class ClockDomain(localSource: String, signals: Seq[String])

/** Analyses the Clock Domains of all Signals in the Circuit. */
object ClockDomainAnalysisPass extends Transform with DependencyAPIMigration {
  override def prerequisites =
    Seq(
      Dependency[passes.ExpandWhensAndCheck],
      Dependency(passes.LowerTypes),
      Dependency(ClockAndResetTreeAnalysisPass)
    )

  // we do not change the circuit, only annotate the results, or throw errors
  override def invalidates(a: Transform) = false

  override def execute(state: CircuitState): CircuitState = {
    // analyze each module in isolation
    val allClockAnnos = state.annotations.collect { case a: ClockAnnotation => a }
    val local = state.circuit.modules.flatMap { m => ModuleDomainScanner.scan(m, allClockAnnos) }

    // combine local information into a global list of clocks and resets
    val iGraph = InstanceKeyGraph(state.circuit)
    //val annos = mergeInfo(iGraph, local.map(i => i.name -> i).toMap)

    state
  }

  import ModuleDomainScanner.ModuleInfo
  private def mergeInfo(iGraph: InstanceKeyGraph, local: Map[String, ModuleInfo]): AnnotationSeq = {
    ???
  }

}

private object ModuleDomainScanner {
  def scan(m: ir.DefModule, annos: Seq[ClockAnnotation]): Option[ModuleInfo] = m match {
    case e:   ir.ExtModule => None
    case mod: ir.Module =>
      val localClocks = filterClocks(mod.name, annos)
      val clocks = localClocks.map(c => c.name -> Clock(c.name)).toMap
      val scan = new ModuleDomainScanner(clocks)
      scan.onModule(mod)
      Some(analyzeResults(mod.name, scan, clocks.contains))
  }

  private case class Source(name: String, inverted: Boolean)
  private case class LocalClock(name: String, source: Option[Source], sources: Map[String, Source]) {
    require(source.isEmpty || sources.isEmpty)
  }

  /** find all clocks that (may) be visible in the current module */
  private def filterClocks(module: String, annos: Seq[ClockAnnotation]): Seq[LocalClock] = {
    val local = annos.filter(_.target.leafModule == module)
    // we also want to include port annotations of direct submodules
    val subPorts = annos
      .filter(a => a.target.component.isEmpty && a.target.path.nonEmpty)
      .filter(a => a.target.targetParent.asInstanceOf[IsModule].targetParent.asInstanceOf[IsModule].module == module)
    // group annotations by local names and turn them into clocks
    val byLocalName =
      local.groupBy(_.target.ref) ++ subPorts.groupBy(a => a.target.path.last._1.value + "." + a.target.ref)
    val clocks = byLocalName.toSeq.map {
      case (name, as) =>
        val infos = as.map(a => (a.source, a.inverted)).distinct
        if (infos.length == 1) {
          LocalClock(name, Some(Source(infos.head._1, infos.head._2)), Map())
        } else {
          println(s"TODO: $name, $as")
          val sources = ???
          LocalClock(name, None, sources)
        }
    }
    clocks
  }

  private def analyzeResults(name: String, m: ModuleDomainScanner, isClock: String => Boolean): ModuleInfo = {
    // filter clocks out of inputs/outputs
    val inputs = m.inputs.filterNot(isClock)
    val isInput = inputs.toSet
    val outputs = m.outputs.filterNot(isClock)

    // determine which clock domains are driven by inputs
    val stateInputToDomain = m.stateUpdates.toSeq.flatMap {
      case (state, inputs) =>
        val domain = m.stateDomains(state)
        inputs.map(i => i -> domain)
    }.groupBy(_._1).map { case (k, v) => k -> v.map(_._2) }

    val inputInfo = inputs.map { i => InputInfo(i, stateInputToDomain.getOrElse(i, List())) }

    // for outputs we want to know what domains and inputs they are affected by
    val outputInfo = outputs.map { o =>
      val (deps, maybeStates) = m.dependsOn(o).toSeq.partition(isInput)
      val domains = maybeStates.flatMap(m.stateDomains.get)
      OutputInfo(o, DriverInfo(domains, deps))
    }

    ModuleInfo(name, outputInfo.toSeq, inputInfo.toSeq)
  }

  case class Clock(name: String, inverted: Boolean = false) {
    override def toString = if (inverted) { "@negedge " + name }
    else { "@posedge " + name }
  }
  case class ModuleInfo(name: String, outputs: Seq[OutputInfo], inputs: Seq[InputInfo])
  case class OutputInfo(name: String, driver: DriverInfo)
  case class InputInfo(name: String, domains: Seq[Clock])
  case class DriverInfo(domains: Seq[Clock], dependencies: Seq[String])
}

/** Performs a (combinatorial) connectivity check similar to what we would do to check for combinatorial loops
  * - inputs are: module inputs, registers (RHS), submodule/memory outputs
  * - outputs are: module outputs, registers (LHS), submodule/memory inputs
  */
private class ModuleDomainScanner(clocks: Map[String, ModuleDomainScanner.Clock]) {

  /** keep track of inputs and outputs */
  val inputs = mutable.ArrayBuffer[String]()
  val outputs = mutable.ArrayBuffer[String]()

  def onModule(m: ir.Module): Unit = {
    m.foreachPort(onPort)
    m.foreachStmt(onStmt)
  }

  import ModuleDomainScanner._

  private def onPort(p: ir.Port): Unit = p.direction match {
    case ir.Input  => inputs.append(p.name)
    case ir.Output => outputs.append(p.name)
  }

  private def onStmt(s: ir.Statement): Unit = s match {
    case ir.DefNode(_, name, rhs) => dependsOn(name) = getDependencies(rhs)
    case c: ir.Connect     => onConnect(c)
    case r: ir.DefRegister => stateDomains(r.name) = getDomain(r.clock).get
    case _: ir.DefMemory   => // TODO: do we need to do anything special here?
    case ir.IsInvalid(_, ref) => dependsOn(ref.serialize) = Set()
    case ir.Block(stmts)      => stmts.foreach(onStmt)
    case s: ir.Stop =>
      stateUpdates(s.name) = getDependencies(s.en)
      stateDomains(s.name) = getDomain(s.clk).get
    case s: ir.Print =>
      stateUpdates(s.name) = (Seq(s.en) ++ s.args).map(getDependencies).reduce(_ | _)
      stateDomains(s.name) = getDomain(s.clk).get
    case s: ir.Verification =>
      stateUpdates(s.name) = Seq(s.en, s.pred).map(getDependencies).reduce(_ | _)
      stateDomains(s.name) = getDomain(s.clk).get
    case ir.DefInstance(_, name, _, tpe) =>
      val ports = tpe.asInstanceOf[ir.BundleType].fields
      // for fields, Default means Output, Flip means Input
      ports.foreach {
        // we treat the outputs of the submodule as inputs to our module
        case ir.Field(p, ir.Default, _) => inputs.append(name + "." + p)
        case ir.Field(p, ir.Flip, _)    => outputs.append(name + "." + p)
      }
    case _ =>
  }

  private def onConnect(c: ir.Connect): Unit = c.loc match {
    // we need to note down state update dependencies separately, since they are not combinatorial
    case ir.Reference(name, _, RegKind, _) =>
      stateUpdates(name) = getDependencies(c.expr)
    case ir.SubField(ir.SubField(ir.Reference(name, _, _, _), port, _, _), field, _, _) =>
      val key = name + "." + port
      if (field == "clk") {
        stateDomains(key) = getDomain(c.expr).get
      } else {
        // TODO: read ports with latency = 0 are actually combinatorial...
        stateUpdates(key) = getDependencies(c.expr)
      }
    case ref => dependsOn(ref.serialize) = getDependencies(c.expr)
  }

  /** Track the domains of regs/mems/statements */
  private val stateDomains = mutable.HashMap[String, Clock]()

  /** Track the signals feeding into regs/mems/statements */
  private val stateUpdates = mutable.HashMap[String, Set[String]]()

  /** Track the dependencies of all signals on our inputs/registers/memories */
  private val dependsOn = mutable.HashMap[String, Set[String]]()

  /** returns a set of inputs/registers that the expression depends */
  private def getDependencies(e: ir.Expression): Set[String] = e match {
    case ref: ir.RefLikeExpression =>
      val name = ref.serialize
      dependsOn.getOrElse(name, Set(name))
    case ir.Mux(cond, tval, fval, _) =>
      Seq(cond, tval, fval).map(getDependencies).reduce(_ | _)
    case ir.ValidIf(cond, value, _) =>
      Seq(cond, value).map(getDependencies).reduce(_ | _)
    case ir.DoPrim(_, args, _, _) =>
      args.map(getDependencies).reduce(_ | _)
    case _ => Set()
  }

  private def getDomain(clock: ir.Expression): Option[Clock] = clock match {
    case ref: ir.RefLikeExpression => clocks.get(ref.serialize)
    case _:   ir.Mux               => None // for now we do not analyze muxes
    case ir.DoPrim(op, Seq(arg), consts, _) =>
      op match {
        case PrimOps.Not                                          => getDomain(arg).map { case Clock(name, inverted) => Clock(name, !inverted) }
        case PrimOps.AsAsyncReset                                 => getDomain(arg)
        case PrimOps.AsClock                                      => getDomain(arg)
        case PrimOps.AsUInt                                       => getDomain(arg)
        case PrimOps.AsSInt                                       => getDomain(arg)
        case PrimOps.Bits if consts == List(BigInt(0), BigInt(0)) => getDomain(arg)
        case _                                                    => None
      }
    // TODO: can we always safely ignore that a clock or reset signal might be invalid?
    case ir.ValidIf(_, value, _) => getDomain(value)
    case _                       => None
  }
}
