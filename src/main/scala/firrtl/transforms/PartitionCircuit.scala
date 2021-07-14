
package firrtl.transforms

import firrtl._
import firrtl.ir._
import firrtl.annotations._
import firrtl.graph._
import firrtl.traversals.Foreachers._
import firrtl.options.{RegisteredTransform, ShellOption}
import firrtl.stage.RunFirrtlTransformAnnotation

import collection.mutable

case class PartitionCircuitAnnotation(target: ModuleTarget) extends SingleTargetAnnotation[ModuleTarget] {
  def duplicate(n: ModuleTarget): PartitionCircuitAnnotation = this.copy(n)
}

/** Partition a Circuit by keeping only marked modules
  *
  * This transform enables minimization of test-cases by removing all Modules but the marked ones
  * (and blackboxing all submodules of the marked modules)
  *
  * @note It does **not** do renaming
  */
class PartitionCircuit extends Transform with RegisteredTransform {
  def inputForm = ChirrtlForm
  def outputForm = ChirrtlForm

  val options = Seq(
    new ShellOption[Seq[String]](
      longOption = "partition-circuit",
      toAnnotationSeq = (a: Seq[String]) => a.map { mod =>
        PartitionCircuitAnnotation(ModuleTarget("DontCare", mod))
      } :+ RunFirrtlTransformAnnotation(new PartitionCircuit),
      helpText = "Partition the circuit keeping the marked modules",
      helpValueName = Some("<module name>[,...]")
    )
  )

  // Return DiGraph where edges are from Module to child Modules
  private def buildModuleGraph(circuit: Circuit): DiGraph[String] = {
    val graph = new MutableDiGraph[String]
    def onMod(mod: DefModule): Unit = {
      val children = mutable.ArrayBuffer.empty[String]
      def onStmt(stmt: Statement): Unit = {
        stmt.foreach(onStmt)
        stmt match {
          case DefInstance(_, _, mod) => children += mod
          case _ =>
        }
      }
      graph.addVertex(mod.name)
      mod.foreach(onStmt)
      for (c <- children) {
        graph.addPairWithEdge(mod.name, c)
      }
    }
    circuit.modules.foreach(onMod)
    DiGraph(graph)
  }

  // Returns (Modules to keep, Modules to BlackBox)
  private def classifyModules(keep: Set[String], graph: DiGraph[String]): (Set[String], Set[String]) = {
    val revGraph = graph.reverse
    val allKeep: Set[String] = keep.flatMap(revGraph.reachableFrom) ++ keep
    val blackbox: Set[String] = allKeep.flatMap(graph.getEdges) -- allKeep
    (allKeep, blackbox)
  }

  private def blackboxify(mod: DefModule): ExtModule = mod match {
    case ext: ExtModule => ext
    case Module(info, name, ports, _) => ExtModule(info, name, ports, name, Nil)
  }

  def execute(state: CircuitState): CircuitState = {

    val marked = state.annotations.collect {
      case PartitionCircuitAnnotation(ModuleTarget(_, mod)) => mod
    }

    if (marked.nonEmpty) {
      val graph = buildModuleGraph(state.circuit)
      val (keep, blackbox) = classifyModules(marked.toSet, graph)
      val modulesx = state.circuit.modules.flatMap {
        case mod if keep(mod.name)     => Some(mod)
        case mod if blackbox(mod.name) => Some(blackboxify(mod))
        case other                     => None
      }
      state.copy(circuit = state.circuit.copy(modules = modulesx))
    } else {
      logger.warn("PartitionCircuit run but no PartitionCircuitAnnotation found! Skipping...")
      state
    }
  }
}
