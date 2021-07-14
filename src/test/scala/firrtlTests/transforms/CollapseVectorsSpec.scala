// See LICENSE for license details.

package firrtlTests.transforms

import firrtl._
import firrtl.annotations.{ReferenceTarget, Target}
import firrtl.testutils.{FirrtlFlatSpec, FirrtlMatchers}
import firrtl.testutils.FirrtlCheckers._
import firrtl.transforms.DontTouchAnnotation

import logger.{ClassLogLevelAnnotation, Logger, LogLevel}

class CollapseVectorsSpec extends FirrtlFlatSpec {

  private class Canonicalize extends Transform with FirrtlMatchers {
    override val inputForm = UnknownForm
    override val outputForm = UnknownForm
    override def execute(state: CircuitState) = {
      state.copy(circuit = canonicalize(state.circuit))
    }
  }

  private def test(
    input: String,
    expectedLines: Seq[String] = Seq.empty,
    expectedTrees: Seq[PartialFunction[Any, Boolean]] = Seq.empty,
    annotations: AnnotationSeq = Seq.empty) = {

    val state = CircuitState(
      Parser.parse(input),
      UnknownForm,
      EmitCircuitAnnotation(classOf[LowFirrtlEmitter]) +: annotations)
    val output = Logger.makeScope(Seq(ClassLogLevelAnnotation("firrtl.transforms.CollapseVectors", LogLevel.Debug))) {
      Seq( new IRToWorkingIR,
           new ResolveAndCheck,
           new HighFirrtlToMiddleFirrtl,
           new transforms.CollapseVectors,
           passes.InferTypes,
           passes.CheckTypes,
           passes.CheckHighForm,
           new Canonicalize,
           new LowFirrtlEmitter )
        .foldLeft(state){ (a, b) => b.transform(a) }
    }

    // info(s"\n${output.circuit.serialize}")

    expectedLines.foreach(output should containLine(_))
    expectedTrees.foreach(output should containTree(_))
  }

  behavior of "CollapseVectors"

  it should "flatten ports and wires on LHS and RHS" in {
    val input =
      """|circuit Foo:
         |  module Bar:
         |    input in: {a: UInt<1>[1]}
         |    output out: {a: UInt<1>[2]}[2]
         |    out[0].a[0] <= UInt<1>(0)
         |    out[0].a[1] <= UInt<1>(1)
         |    out[1].a[0] <= not(out[0].a[0])
         |    out[1].a[1] <= not(out[0].a[1])
         |  module Foo:
         |    input clock: Clock
         |    output out: UInt<4>
         |    inst bar of Bar
         |    bar.in.a[0] <= UInt<1>(0)
         |    wire tmp: UInt<1>[4]
         |    reg tmp_d: UInt<1>[2], clock
         |    tmp[0] <= bar.out[0].a[0]
         |    tmp[1] <= bar.out[0].a[1]
         |    tmp[2] <= bar.out[1].a[0]
         |    tmp[3] <= bar.out[1].a[1]
         |    node out_msbs = cat(tmp[3], tmp[2])
         |    node out_lsbs = cat(tmp[1], tmp[0])
         |    out <= cat(out_msbs, out_lsbs)
         |""".stripMargin
    val expectedLines = Seq(
      "node _tmp_0 = bits(bar.out[0].a, 0, 0)",
      "node _tmp_1 = bits(bar.out[0].a, 1, 1)",
      "node _tmp_2 = bits(bar.out[1].a, 0, 0)",
      "node _tmp_3 = bits(bar.out[1].a, 1, 1)",
      "tmp <= cat(cat(_tmp_3, _tmp_2), cat(_tmp_1, _tmp_0))",
      "node out_msbs = cat(bits(tmp, 3, 3), bits(tmp, 2, 2))",
      "node out_lsbs = cat(bits(tmp, 1, 1), bits(tmp, 0, 0))" )
    val expectedTrees: Seq[PartialFunction[Any, Boolean]] = Seq(
      { case ir.DefWire(_, "tmp", ir.UIntType(ir.IntWidth(w))) => w == 4 }
    )

    test(input, expectedLines, expectedTrees)
  }

  it should "propagate type changes" in {
    val input =
      """|circuit Foo:
         |  module Foo:
         |    input sel: UInt<1>
         |    wire a: UInt<1>[1]
         |    wire b: UInt<1>[1]
         |    wire c: UInt<1>[1]
         |    a[0] <= UInt<1>(0)
         |    b[0] <= UInt<1>(1)
         |    node d = mux(sel, a, b)
         |    c[0] <= d[0]
         |""".stripMargin
    val expectedLines = Seq(
      "node d = mux(sel, a, b)",
      "c <= d"
    )
    val expectedTrees: Seq[PartialFunction[Any, Boolean]] = Seq(
      { case ir.DefWire(_, "a", ir.UIntType(ir.IntWidth(w))) => w == 1 },
      { case ir.DefWire(_, "b", ir.UIntType(ir.IntWidth(w))) => w == 1 },
      { case ir.DefWire(_, "c", ir.UIntType(ir.IntWidth(w))) => w == 1 },
      { case ir.Connect(_, WRef("a", _, _, _), ir.UIntLiteral(v, ir.IntWidth(w))) => v == 0 && w == 1 },
      { case ir.Connect(_, WRef("b", _, _, _), ir.UIntLiteral(v, ir.IntWidth(w))) => v == 1 && w == 1 }
    )

    test(input, expectedLines, expectedTrees)
  }

  it should "flatten registers" in {
    val input =
      """|circuit Foo:
         |  module Foo:
         |    input clock: Clock
         |    output out: UInt<1>[2]
         |    reg r: UInt<1>[2], clock
         |    r[0] <= UInt<1>(1)
         |    r[1] is invalid
         |    out[0] <= r[0]
         |    out[1] <= r[1]
         |""".stripMargin
    val expectedLines = Seq(
      "out[0] <= bits(r, 0, 0)",
      "out[1] <= bits(r, 1, 1)",
    )
    val expectedTrees: Seq[PartialFunction[Any, Boolean]] = Seq(
      { case ir.DefRegister(_, "r", ir.UIntType(_), _, _, _) => true },
    )

    test(input, expectedLines, expectedTrees)
  }

  it should "flatten wires" in {
    val input =
      """|circuit Foo:
         |  module Foo:
         |    output out: UInt<1>[1]
         |    wire w: UInt<1>[1]
         |    w[0] <= UInt<1>(0)
         |    out[0] <= w[0]
         |""".stripMargin

    val expectedLines = Seq(
      "out[0] <= bits(w, 0, 0)"
    )
    val expectedTrees: Seq[PartialFunction[Any, Boolean]] = Seq(
      { case ir.DefWire(_, "w", ir.UIntType(_)) => true }
    )

    test(input, expectedLines, expectedTrees)
  }

  it should "flatten memories" in {
    val input =
      """|circuit Foo:
         |  module Foo:
         |    input clock: Clock
         |    input read: {flip data: UInt<1>[1]}
         |    input write: {data: UInt<1>[1]}
         |    input readwrite: {flip rdata: UInt<1>[1], wdata: UInt<1>[1]}
         |    mem m:
         |      data-type => UInt<1>[1]
         |      depth => 4
         |      reader => r
         |      writer => w
         |      readwriter => rw
         |      read-latency => 1
         |      write-latency => 1
         |      read-under-write => undefined
         |    m.r.clk <= clock
         |    m.r.en is invalid
         |    m.r.addr is invalid
         |    read.data <= m.r.data
         |    m.w.clk <= clock
         |    m.w.en is invalid
         |    m.w.addr is invalid
         |    m.w.data <= write.data
         |    m.w.mask is invalid
         |    m.rw.clk <= clock
         |    m.rw.en is invalid
         |    m.rw.wmode is invalid
         |    m.rw.addr is invalid
         |    m.rw.wdata <= readwrite.wdata
         |    readwrite.rdata <= m.rw.rdata
         |    m.rw.wmask is invalid
         |""".stripMargin

    val expectedLines = Seq(
      "read.data[0] <= bits(m.r.data, 0, 0)",
      "node _m_w_data_0 = write.data[0]",
      "m.w.data <= _m_w_data_0",
      "readwrite.rdata[0] <= bits(m.rw.rdata, 0, 0)",
      "node _m_rw_wdata_0 = readwrite.wdata[0]",
      "m.rw.wdata <= _m_rw_wdata_0"
    )

    val expectedTrees: Seq[PartialFunction[Any, Boolean]] = Seq(
      { case ir.DefMemory(_, "m", ir.UIntType(_), _, _, _, _, _, _, _) => true }
    )

    test(input, expectedLines, expectedTrees)
  }

  it should "handle bulk connects" in {
    val input =
      """|circuit Foo:
         |  module Foo:
         |    input in: {a: UInt<1>[1]}
         |    output out: {a: UInt<1>[1]}
         |    wire bar: {a: UInt<1>[1]}
         |    bar <= in
         |    out <= bar
         |""".stripMargin

    test(input)
  }

  it should "handle submodule connections" in {
    val input =
      """|circuit Foo:
         |  module Bar:
         |    input a: UInt<1>[1]
         |    output b: UInt<1>[1]
         |    b[0] <= UInt<1>(0)
         |  module Foo:
         |    output out: UInt<1>[1]
         |    inst bar of Bar
         |    node baz = bar.b[0]
         |    bar.a[0] <= baz
         |    out[0] <= bar.b[0]
         |""".stripMargin
    val expectedLines = Seq(
      """b <= UInt<1>("h0")""",
      "node baz = bits(bar.b, 0, 0)",
      "out[0] <= bits(bar.b, 0, 0)",
      "node _bar_a_0 = baz",
      "bar.a <= _bar_a_0"
    )

    test(input, expectedLines)
  }

  it should "not collapse top-level ports" in {
    val input =
      """|circuit Foo:
         |  module Foo:
         |    output out: UInt<1>[2]
         |    out[0] <= UInt<1>(0)
         |    out[1] <= UInt<1>(1)
         |""".stripMargin

    val expectedTrees: Seq[PartialFunction[Any, Boolean]] = Seq(
      { case ir.Module(_, _, Seq(ir.Port(_, "out", _, ir.VectorType(ir.UIntType(ir.IntWidth(w)), s))), _) => w == 1 && s == 2 }
    )

    test(input, Seq.empty, expectedTrees)
  }

  it should "handle zero-sized vectors" in {
    val input =
      """|circuit Top:
         |  module Foo:
         |    output out: {a: UInt<1>}[0]
         |    out is invalid
         |  module Top:
         |    inst foo of Foo
         |""".stripMargin

    val expectedTrees: Seq[PartialFunction[Any, Boolean]] = Seq(
      { case ir.Module(_, _, Seq(ir.Port(_, "out", _, ir.VectorType(_, s))), _) => s == 0 }
    )

    test(input, Seq.empty, expectedTrees)
  }

  it should "handle invalids" in {
    val input =
      """|circuit Foo:
         |  module Bar:
         |    input in: UInt<1>[1]
         |  module Foo:
         |    output out: UInt<1>[1]
         |    wire a: UInt<1>[1]
         |    a[0] is invalid
         |    inst bar of Bar
         |    bar.in[0] is invalid
         |    out[0] is invalid
         |""".stripMargin
    val expectedLines = Seq(
      "a is invalid",
      "bar.in is invalid",
      "out[0] is invalid"
    )

    test(input, expectedLines)
  }

  it should "handle connects with LHS and RHS collapsed" in {
    val input =
      """|circuit Foo:
         |  module Bar:
         |    input a: UInt<1>[2]
         |    output b: UInt<1>[2]
         |    b <= a
         |  module Foo:
         |    inst bar of Bar
         |    bar.a is invalid
         |""".stripMargin
    val expectedLines = Seq(
      "b <= a"
    )
    val expectedTrees: Seq[PartialFunction[Any, Boolean]] = Seq(
      { case ir.Module(_, "Bar", Seq(ir.Port(_, "a", _, ir.UIntType(ir.IntWidth(aw))),
                                     ir.Port(_, "b", _, ir.UIntType(ir.IntWidth(bw)))), _) => aw == 2 && bw == 2 }
    )

    test(input, expectedLines, expectedTrees)
  }

  it should "handle connections where a RHS was *NOT* collapsed" in {
    val input =
      """|circuit Foo:
         |  module Foo:
         |    input a: UInt<1>[2]
         |    wire b: UInt<1>[2]
         |    b <= a
         |""".stripMargin
    val expectedLines = Seq(
      "node _b_0 = a[0]",
      "node _b_1 = a[1]",
      "b <= cat(_b_1, _b_0)"
    )

    test(input, expectedLines)
  }

  ignore should "respect targets with DontTouchAnnotations" in {
    val input =
      """|circuit Foo:
         |  module Bar:
         |    input in: UInt<1>[1]
         |  module Foo:
         |    input in: UInt<1>[1]
         |    wire a: UInt<1>[1]
         |    a[0] <= in[0]
         |    inst bar1 of Bar
         |    bar1.in[0] <= a[0]
         |    inst bar2 of Bar
         |    bar2.in[0] <= a[0]
         |""".stripMargin
    val annotations = Seq(
      "~Foo|Foo>a",
      "~Foo|Foo/bar1:Bar>in[0]"
    ).map(Target.deserialize(_) match {
            case r: ReferenceTarget => println(r); DontTouchAnnotation(r)
            case _ => ???
          })
    val expectedTrees: Seq[PartialFunction[Any, Boolean]] = Seq(
      { case ir.DefWire(_, "a", ir.VectorType(ir.UIntType(ir.IntWidth(w)), s)) => w == 1 && s == 1 },
      { case ir.Module(_, "Bar", Seq(ir.Port(_, "in", _, ir.VectorType(ir.UIntType(ir.IntWidth(w)), s))), _) => w == 1 && s == 1 },
      { case ir.Module(_, "Foo", Seq(ir.Port(_, "in", _, ir.VectorType(ir.UIntType(ir.IntWidth(w)), s))), _) => w == 1 && s == 1 }
    )
    test(input, Seq.empty, expectedTrees, annotations)
  }

}
