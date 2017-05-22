// See LICENSE for license details.

package firrtlTests

import firrtl.ir.Circuit
import firrtl.transforms.core.passes._

class CheckInitializationSpec extends FirrtlFlatSpec {
  private val passes = Seq(
     ToWorkingIR,
     CheckHighForm,
     ResolveKinds,
     InferTypes,
     CheckTypes,
     ResolveGenders,
     CheckGenders,
     InferWidths,
     CheckWidths,
     PullMuxes,
     ExpandConnects,
     RemoveAccesses,
     ExpandWhens,
     CheckInitialization)
  "Missing assignment in consequence branch" should "trigger a PassException" in {
    val input =
      """circuit Test :
        |  module Test :
        |    input p : UInt<1>
        |    wire x : UInt<32>
        |    when p :
        |      x <= UInt(1)""".stripMargin
    intercept[CheckInitialization.RefNotInitializedException] {
      passes.foldLeft(parse(input)) {
        (c: Circuit, p: Pass) => p.run(c)
      }
    }
  }
  "Missing assignment in alternative branch" should "trigger a PassException" in {
    val input =
      """circuit Test :
        |  module Test :
        |    input p : UInt<1>
        |    wire x : UInt<32>
        |    when p :
        |    else :
        |      x <= UInt(1)""".stripMargin
    intercept[CheckInitialization.RefNotInitializedException] {
      passes.foldLeft(parse(input)) {
        (c: Circuit, p: Pass) => p.run(c)
      }
    }
  }
}
