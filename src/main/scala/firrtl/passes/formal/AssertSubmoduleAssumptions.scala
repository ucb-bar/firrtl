
package firrtl.passes.formal

import firrtl.passes.{Pass, PullMuxes, ReplaceAccesses, ResolveFlows}
import firrtl.ir.{Assert, Assume, Circuit, Statement}
import firrtl.options.Dependency
import firrtl.stage.TransformManager.TransformDependency
import firrtl.{CircuitState, FirrtlUserException, Transform}


// TODO How do I actually make it so that this pass gets executed? Add it to a
//      transform?
object AssertSubmoduleAssumptions extends Pass {
  override def prerequisites: Seq[TransformDependency] =
    firrtl.stage.Forms.Deduped

  override def invalidates(a: Transform): Boolean = a match {
    case _ => false
  }

  def assertAssumption(s: Statement): Statement = s match {
    case Assume(info, clk, cond, en, msg) => Assert(info, clk, cond, en, msg)
    case t => t.mapStmt(assertAssumption)
  }

  def run(c: Circuit): Circuit = {
    c.mapModule(mod => {
      // TODO is this right?
      if (mod.name != c.main) {
        mod.mapStmt(assertAssumption)
      } else {
        mod
      }
    })
  }
}
