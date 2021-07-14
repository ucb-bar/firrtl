package firrtl.passes

import firrtl._
import firrtl.ir._
import firrtl.Mappers._
import firrtl.Utils.{isTemp, NodeMap, inline}

import scala.collection.mutable

class InlineTemporaries extends Pass {
  def run(c: Circuit): Circuit = c map onModule
  private def onModule(m: DefModule): DefModule = m map inlineTemps


  def inlineTemps(s: Statement): Statement = {
    val count = mutable.HashMap[String, Int]()
    val nodeMap = new NodeMap()

    def analyzeE(e: Expression): Expression = e map analyzeE match {
      case x@WRef(name, _, _, MALE) if isTemp(name) =>
        count(name) = count(name) + 1
        x
      case other => other map analyzeE
    }
    def analyze(s: Statement): Statement = s map analyzeE match {
      case x@DefNode(info, name, value) if isTemp(name) =>
        nodeMap(name) = value
        count(name) = 0
        x
      case other => other map analyze
    }


    /** Populate count and nodeMap */
    analyze(s)

    def fixup(s: Statement): Statement = s map inline(nodeMap, {x: String => isTemp(x) && count(x) != 1 }) match {
      case x@DefNode(info, name, value) if isTemp(name) && count(name) == 1 => EmptyStmt
      case other => other map fixup
    }

    Utils.squashEmpty(fixup(s))
  }
}
