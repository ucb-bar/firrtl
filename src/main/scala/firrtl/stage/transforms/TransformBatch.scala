package firrtl.stage.transforms

import firrtl._
import firrtl.options._
import firrtl.stage.TransformManager

trait TransformBatch extends Transform {
  def inputForm: CircuitForm = UnknownForm

  def outputForm: CircuitForm = UnknownForm

  def transforms: Seq[Transform]

  final override def prerequisites: Seq[Dependency[Transform]] = transforms.flatMap(_.recursivePrerequisites).distinct

  final def allTransforms: Seq[Dependency[Transform]] = (transforms.map(t => Dependency.fromTransform(t)) ++
    prerequisites.filter { prerequisite =>
      transforms.map {
        transform =>
          transform.invalidates(prerequisite.getObject())
      }.reduce(_ | _)
    } ++
    transforms.flatMap {
      _.dependents.flatMap(_.getObject().recursivePrerequisites)
    }
    ).distinct

  val transformManager = new TransformManager(allTransforms, prerequisites)

  protected def runTransforms(state: CircuitState): CircuitState = transformManager.flattenedTransformOrder.foldLeft(state) { (in, xform) => xform.runTransform(in) }

  def execute(state: CircuitState): CircuitState = {
    val ret = runTransforms(state)
    CircuitState(ret.circuit, outputForm, ret.annotations, ret.renames)
  }
}