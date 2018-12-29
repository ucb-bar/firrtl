package firrtlTests.analyses

import firrtl.PrimOps.AsUInt
import firrtl.analyses.IRLookup
import firrtl.annotations.{ ModuleTarget, ReferenceTarget}
import firrtl._
import firrtl.ir._
import firrtlTests.FirrtlFlatSpec
import firrtlTests.transforms.MemStuff

class IRLookupSpec extends FirrtlFlatSpec with MemStuff {

  "IRLookup" should "return declarations" in {
    val input =
      """circuit Test:
        |  module Test :
        |    input in: UInt<8>
        |    input clk: Clock
        |    input reset: UInt<1>
        |    output out: {a: UInt<8>, b: UInt<8>[2]}
        |    input ana1: Analog<8>
        |    output ana2: Analog<8>
        |    out is invalid
        |    reg r: UInt<8>, clk with:
        |      (reset => (reset, UInt(0)))
        |    node x = r
        |    wire y: UInt<8>
        |    y <= x
        |    out.b[0] <= and(y, asUInt(SInt(-1)))
        |    attach(ana1, ana2)
        |    inst child of Child
        |    out.a <= child.out
        |  module Child:
        |    output out: UInt<8>
        |    out <= UInt(1)
        |""".stripMargin

    val circuit = toMiddleFIRRTL(parse(input))
    val irLookup = IRLookup(circuit)
    val Test = ModuleTarget("Test", "Test")
    val uint8 = UIntType(IntWidth(8))

    irLookup.declaration(Test.ref("in")) shouldBe Port(NoInfo, "in", Input, uint8)
    irLookup.declaration(Test.ref("clk")) shouldBe Port(NoInfo, "clk", Input, ClockType)
    irLookup.declaration(Test.ref("reset")) shouldBe Port(NoInfo, "reset", Input, UIntType(IntWidth(1)))

    val out = Port(NoInfo, "out", Output,
      BundleType(Seq(Field("a", Default, uint8), Field("b", Default, VectorType(uint8, 2))))
    )
    irLookup.declaration(Test.ref("out")) shouldBe out
    irLookup.declaration(Test.ref("out").field("a")) shouldBe out
    irLookup.declaration(Test.ref("out").field("b").index(0)) shouldBe out
    irLookup.declaration(Test.ref("out").field("b").index(1)) shouldBe out

    irLookup.declaration(Test.ref("ana1")) shouldBe Port(NoInfo, "ana1", Input, AnalogType(IntWidth(8)))
    irLookup.declaration(Test.ref("ana2")) shouldBe Port(NoInfo, "ana2", Output, AnalogType(IntWidth(8)))

    val clk = WRef("clk", ClockType, PortKind, MALE)
    val reset = WRef("reset", UIntType(IntWidth(1)), PortKind, MALE)
    val init = UIntLiteral(0)
    val reg = DefRegister(NoInfo, "r", uint8, clk, reset, init)
    irLookup.declaration(Test.ref("r")) shouldBe reg
    irLookup.declaration(Test.ref("r").clock) shouldBe reg
    irLookup.declaration(Test.ref("r").reset) shouldBe reg
    irLookup.declaration(Test.ref("r").init) shouldBe reg

    irLookup.declaration(Test.ref("x")) shouldBe
      DefNode(NoInfo, "x", WRef("r", uint8, RegKind, MALE))

    irLookup.declaration(Test.ref("y")) shouldBe
      DefWire(NoInfo, "y", uint8)

    irLookup.declaration(Test.ref("@and#0")) shouldBe
      DoPrim(PrimOps.And,
        Seq(WRef("y", uint8, WireKind, MALE), DoPrim(AsUInt, Seq(SIntLiteral(-1)), Nil, UIntType(IntWidth(1)))),
        Nil,
        uint8
      )

    val inst = WDefInstance(NoInfo, "child", "Child", BundleType(Seq(Field("out", Default, uint8))))
    irLookup.declaration(Test.ref("child")) shouldBe inst
    irLookup.declaration(Test.ref("child").field("out")) shouldBe inst
  }

  "IRLookup" should "return mem declarations" in {
    val input = makeInput(0)
    val circuit = toMiddleFIRRTL(parse(input))
    val irLookup = IRLookup(circuit)
    val Test = ModuleTarget("Test", "Test")
    val uint8 = UIntType(IntWidth(8))
    val mem = DefMemory(NoInfo, "m", uint8, 2, 1, 0, Seq("r"), Seq("w"), Seq("rw"))
    allSignals.foreach { at =>
      irLookup.declaration(at) shouldBe mem
    }
  }

  "IRLookup" should "return expressions, types, kinds, and genders" in {
    val input =
      """circuit Test:
        |  module Test :
        |    input in: UInt<8>
        |    input clk: Clock
        |    input reset: UInt<1>
        |    output out: {a: UInt<8>, b: UInt<8>[2]}
        |    input ana1: Analog<8>
        |    output ana2: Analog<8>
        |    out is invalid
        |    reg r: UInt<8>, clk with:
        |      (reset => (reset, UInt(0)))
        |    node x = r
        |    wire y: UInt<8>
        |    y <= x
        |    out.b[0] <= and(y, asUInt(SInt(-1)))
        |    attach(ana1, ana2)
        |    inst child of Child
        |    out.a <= child.out
        |  module Child:
        |    output out: UInt<8>
        |    out <= UInt(1)
        |""".stripMargin

    val circuit = toMiddleFIRRTL(parse(input))
    val irLookup = IRLookup(circuit)
    val Test = ModuleTarget("Test", "Test")
    val uint8 = UIntType(IntWidth(8))
    val uint1 = UIntType(IntWidth(1))

    def check(rt: ReferenceTarget, e: Expression): Unit = {
      irLookup.expr(rt) shouldBe e
      irLookup.tpe(rt) shouldBe e.tpe
      irLookup.kind(rt) shouldBe Utils.kind(e)
      irLookup.gender(rt) shouldBe Utils.gender(e)
    }

    check(Test.ref("in"), WRef("in", uint8, PortKind, MALE))
    check(Test.ref("clk"), WRef("clk", ClockType, PortKind, MALE))
    check(Test.ref("reset"), WRef("reset", uint1, PortKind, MALE))

    val out = Test.ref("out")
    val outExpr =
      WRef("out",
        BundleType(Seq(Field("a", Default, uint8), Field("b", Default, VectorType(uint8, 2)))),
        PortKind,
        FEMALE
      )
    check(out, outExpr)
    check(out.field("a"), WSubField(outExpr, "a", uint8, FEMALE))
    val outB = out.field("b")
    val outBExpr = WSubField(outExpr, "b", VectorType(uint8, 2), FEMALE)
    check(outB, outBExpr)
    check(outB.index(0), WSubIndex(outBExpr, 0, uint8, FEMALE))
    check(outB.index(1), WSubIndex(outBExpr, 1, uint8, FEMALE))

    check(Test.ref("ana1"), WRef("ana1", AnalogType(IntWidth(8)), PortKind, MALE))
    check(Test.ref("ana2"), WRef("ana2", AnalogType(IntWidth(8)), PortKind, FEMALE))

    val clk = WRef("clk", ClockType, PortKind, MALE)
    val reset = WRef("reset", UIntType(IntWidth(1)), PortKind, MALE)
    val init = UIntLiteral(0)
    val reg = DefRegister(NoInfo, "r", uint8, clk, reset, init)
    check(Test.ref("r"), WRef("r", uint8, RegKind, BIGENDER))
    check(Test.ref("r").clock, clk)
    check(Test.ref("r").reset, reset)
    check(Test.ref("r").init, init)

    check(Test.ref("x"), WRef("x", uint8, ExpKind, MALE))

    check(Test.ref("y"), WRef("y", uint8, WireKind, BIGENDER))

    check(Test.ref("@and#0"),
      DoPrim(PrimOps.And,
        Seq(WRef("y", uint8, WireKind, MALE), DoPrim(AsUInt, Seq(SIntLiteral(-1)), Nil, UIntType(IntWidth(1)))),
        Nil,
        uint8
      )
    )

    val child = WRef("child", BundleType(Seq(Field("out", Default, uint8))), InstanceKind, MALE)
    check(Test.ref("child"), child)
    check(Test.ref("child").field("out"),
      WSubField(child, "out", uint8, MALE)
    )
  }

  "IRLookup" should "cache expressions" in {
    def mkType(i: Int): String = {
      if(i == 0) "UInt<8>" else s"{x: ${mkType(i - 1)}}"
    }

    val depth = 500

    val input =
      s"""circuit Test:
        |  module Test :
        |    input in: ${mkType(depth)}
        |    output out: ${mkType(depth)}
        |    out <= in
        |""".stripMargin
    println(input)

    val circuit = toMiddleFIRRTL(parse(input))
    val Test = ModuleTarget("Test", "Test")
    val irLookup = IRLookup(circuit)
    def mkReferences(parent: ReferenceTarget, i: Int): Seq[ReferenceTarget] = {
      if(i == 0) Seq(parent) else {
        val newParent = parent.field("x")
        newParent +: mkReferences(newParent, i - 1)
      }
    }

    // Check caching from root to leaf
    val inRefs = mkReferences(Test.ref("in"), depth)
    val (inStartTime, _) = Utils.time(irLookup.expr(inRefs.head))
    inRefs.tail.foreach { r =>
      val (ms, _) = Utils.time(irLookup.expr(r))
      require(inStartTime > ms)
    }
    val outRefs = mkReferences(Test.ref("out"), depth).reverse
    val (outStartTime, _) = Utils.time(irLookup.expr(outRefs.head))
    outRefs.tail.foreach { r =>
      val (ms, _) = Utils.time(irLookup.expr(r))
      require(outStartTime > ms)
    }
  }
}
