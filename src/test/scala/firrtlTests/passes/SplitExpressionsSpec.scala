// SPDX-License-Identifier: Apache-2.0

package firrtlTests.passes

import firrtl.{ir, CircuitState, Parser, PrimOps}
import firrtl.passes.SplitExpressions
import firrtl.testutils.FirrtlCheckers._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SplitExpressionsSpec extends AnyFlatSpec {

  behavior.of("SplitExpressions")

  it should "be idempotent" in {

    val input =
      """|circuit Foo:
         |  module Foo:
         |    input a: UInt<1>
         |
         |    node b = not(a)
         |""".stripMargin

    val state = CircuitState(Parser.parse(input), Seq.empty)

    val output = Seq.fill(2)(SplitExpressions).foldLeft(state) { case (acc, a) => a.transform(acc) }

    output should be(state)

  }

  it should "fix issue #2037" in {

    val input =
      """|circuit Foo:
         |  module Foo:
         |    input cond: UInt<1>
         |    input a: UInt<1>
         |    input b: SInt<1>
         |    output c: SInt<1>
         |
         |    c <= mux(cond, asSInt(a), b)
         |""".stripMargin

    val state = CircuitState(Parser.parse(input), Seq.empty)

    SplitExpressions.transform(state) should containTree {
      case ir.DefNode(_, _, ir.DoPrim(PrimOps.AsSInt, Seq(ir.Reference("a", _, _, _)), _, _)) => true
    }

  }

}
