package firrtl.passes

import firrtl._
import firrtl.options.{Dependency, RegisteredTransform, ShellOption}
import firrtl.transforms.DedupModules
import firrtl.annotations.NoTargetAnnotation
import firrtl.stage.RunFirrtlTransformAnnotation

/** Specifies a global prefix for all module names. */
case class ModuleNamePrefixAnnotation(prefix: String = "") extends NoTargetAnnotation

class ModuleNamePrefixTransform extends Transform with DependencyAPIMigration with RegisteredTransform {

  override def prerequisites = Seq(Dependency[DedupModules])
  override def optionalPrerequisiteOf = Seq(Dependency[VerilogEmitter])

  override def invalidates(a: Transform) = false

  val options = Seq(
    new ShellOption[String](
      longOption = "module-name-prefix",
      toAnnotationSeq =
        (a: String) => Seq(ModuleNamePrefixAnnotation(a), RunFirrtlTransformAnnotation(new ModuleNamePrefixTransform)),
      helpText = "Add global prefix to every verilog module (default: \"\")",
      shortOption = Some("prefix"),
      helpValueName = Some("<prefix>")
    )
  )

  private def onStmt(s: ir.Statement, prefix: String): ir.Statement = s match {
    case i: ir.DefInstance => i.copy(module = prefix + i.module)
    case other => other.mapStmt(onStmt(_, prefix))
  }

  private def onModule(m: ir.DefModule, prefix: String): ir.DefModule = m match {
    case e:   ir.ExtModule => e.copy(name = prefix + e.name)
    case mod: ir.Module =>
      val name = prefix + mod.name
      val body = onStmt(mod.body, prefix)
      mod.copy(name = name, body = body)
  }

  override protected def execute(state: CircuitState): CircuitState = {
    val annos = state.annotations.collect { case a: ModuleNamePrefixAnnotation => a.prefix }.distinct
    annos match {
      case Seq() =>
        logger.info("[ModuleNamePrefixAnnotation] No ModuleNamePrefixAnnotation annotation found.")
        state
      case Seq("") => state
      case Seq(prefix) =>
        val c = state.circuit.mapModule(onModule(_, prefix))
        state.copy(circuit = c.copy(main = prefix + c.main))
      case other =>
        throw new PassException(s"[ModuleNamePrefixAnnotation] found more than one prefix annotation: $other")
    }
  }
}
