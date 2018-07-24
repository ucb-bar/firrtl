// See LICENSE for license details.

package firrtlTests

import firrtl._
import firrtl.testutils._

import java.io.File

class GCDExecutionTest extends ExecutionTest("GCDTester", "/integration")
class RightShiftExecutionTest extends ExecutionTest("RightShiftTester", "/integration")
class MemExecutionTest extends ExecutionTest("MemTester", "/integration")
class PipeExecutionTest extends ExecutionTest("PipeTester", "/integration")

// This is a bit custom some kind of one off
class GCDSplitEmissionExecutionTest extends FirrtlFlatSpec {
  "GCDTester" should "work even when the modules are emitted to different files" in {
    val top = "GCDTester"
    val testDir = createTestDirectory("GCDTesterSplitEmission")
    val sourceFile = new File(testDir, s"$top.fir")
    copyResourceToFile(s"/integration/$top.fir", sourceFile)

    val optionsManager = new ExecutionOptionsManager("GCDTesterSplitEmission") with HasFirrtlOptions {
      commonOptions = CommonOptions(topName = top, targetDirName = testDir.getPath)
      firrtlOptions = FirrtlExecutionOptions(
                        inputFileNameOverride = sourceFile.getPath,
                        compilerName = "verilog",
                        infoModeName = "ignore",
                        emitOneFilePerModule = true)
    }
    firrtl.Driver.execute(optionsManager)

    // expected filenames
    val dutFile = new File(testDir, "DecoupledGCD.v")
    val topFile = new File(testDir, s"$top.v")
    dutFile should exist
    topFile should exist

    // Copy harness over
    val harness = new File(testDir, s"testTop.cpp")
    copyResourceToFile(cppHarnessResourceName, harness)

    // topFile will be compiled by Verilator command by default but we need to also include dutFile
    verilogToCpp(top, testDir, Seq(dutFile), harness) #&&
    cppToExe(top, testDir) ! loggingProcessLogger
    assert(executeExpectingSuccess(top, testDir))
  }
}

class RobCompilationTest extends CompilationTest("Rob", "/regress")
class RocketCoreCompilationTest extends CompilationTest("RocketCore", "/regress")
class ICacheCompilationTest extends CompilationTest("ICache", "/regress")
class FPUCompilationTest extends CompilationTest("FPU", "/regress")
class HwachaSequencerCompilationTest extends CompilationTest("HwachaSequencer", "/regress")

abstract class CommonSubexprEliminationEquivTest(name: String, dir: String) extends
  EquivalenceTest(Seq(new firrtl.transforms.CommonSubexpressionElimination), name, dir)
abstract class DeadCodeEliminationEquivTest(name: String, dir: String) extends
  EquivalenceTest(Seq(new firrtl.transforms.DeadCodeElimination), name, dir)
abstract class ConstantPropagationEquivTest(name: String, dir: String) extends
  EquivalenceTest(Seq(new firrtl.transforms.ConstantPropagation), name, dir)
abstract class LowFirrtlOptimizationEquivTest(name: String, dir: String) extends
  EquivalenceTest(Seq(new LowFirrtlOptimization), name, dir)

class RocketCommonSubexprEliminationTest extends CommonSubexprEliminationEquivTest("RocketCore", "/regress")
class RocketDeadCodeEliminationTest extends DeadCodeEliminationEquivTest("RocketCore", "/regress")
class RocketConstantPropagationTest extends ConstantPropagationEquivTest("RocketCore", "/regress")
class RocketLowFirrtlOptimizationTest extends LowFirrtlOptimizationEquivTest("RocketCore", "/regress")

class FPUCommonSubexprEliminationTest extends CommonSubexprEliminationEquivTest("FPU", "/regress")
class FPUDeadCodeEliminationTest extends DeadCodeEliminationEquivTest("FPU", "/regress")
class FPUConstantPropagationTest extends ConstantPropagationEquivTest("FPU", "/regress")
class FPULowFirrtlOptimizationTest extends LowFirrtlOptimizationEquivTest("FPU", "/regress")

class RobCommonSubexprEliminationTest extends CommonSubexprEliminationEquivTest("Rob", "/regress")
class RobDeadCodeEliminationTest extends DeadCodeEliminationEquivTest("Rob", "/regress")
class RobConstantPropagationTest extends ConstantPropagationEquivTest("Rob", "/regress")
class RobLowFirrtlOptimizationTest extends LowFirrtlOptimizationEquivTest("Rob", "/regress")
