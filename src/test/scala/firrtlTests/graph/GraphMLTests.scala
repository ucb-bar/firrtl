// See LICENSE for license details.

package firrtlTests.graph

import firrtl.{CircuitState, FileUtils, UnknownForm}
import firrtl.analyses.ConnectionGraph
import firrtl.graph._
import firrtl.options.Dependency
import firrtl.passes.ExpandWhensAndCheck
import firrtl.testutils._


class GraphMLTests extends FirrtlFlatSpec {

  case class SimpleNode(name: String, gmAttributes: Set[GraphAttribute] = Set.empty) extends GraphMLVertex {
    override def gmId: String = name

    override def gmEdge(target: GraphMLVertex): GraphMLEdge = SimpleEdge(this, target, {
      if (gmSource.gmId == "n1" & target.gmId == "n3") Set(WeightAttr("2.0"))
      else if (gmSource.gmId == "n5" & target.gmId == "n4") Set(WeightAttr("2.0"))
      else Set.empty
    })
  }

  case class SimpleEdge(gmSource: GraphMLVertex, gmTarget: GraphMLVertex, gmAttributes: collection.Set[GraphAttribute]) extends GraphMLEdge

  case class ColorAttr(val value: String) extends GraphAttribute {
    override val id: String = "d0"
    override val attrFor: String = "node"
    override val attrName: String = "color"
    override val attrType: String = "string"
  }

  case class WeightAttr(val value: String) extends GraphAttribute {
    override val id: String = "d1"
    override val attrFor: String = "edge"
    override val attrName: String = "weight"
    override val attrType: String = "double"
  }

  val n0 = SimpleNode("n0", Set(ColorAttr("green")))
  val n1 = SimpleNode("n1")
  val n2 = SimpleNode("n2", Set(ColorAttr("blue")))
  val n3 = SimpleNode("n3", Set(ColorAttr("red")))
  val n4 = SimpleNode("n4", Set(ColorAttr("green")))
  val n5 = SimpleNode("n5", Set(ColorAttr("turquoise")))

  val simpleGraph: DiGraph[SimpleNode] = DiGraph(Map(
    n0 -> Set(n1),
    n1 -> Set(n3),
    n2 -> Set(n4),
    n3 -> Set(n2, n5),
    n4 -> Set[SimpleNode](),
    n5 -> Set(n4)
  ))

  val rocketGraph = ConnectionGraph(
    new firrtl.stage.transforms.Compiler(Seq(Dependency[ExpandWhensAndCheck]))
      .runTransform(
        CircuitState(parse(FileUtils.getTextResource("/regress/RocketCore.fir")), UnknownForm)
      )
      .circuit
  )

  "A simple graph" should "emit graphml" in {
    val graphMl = GraphML(simpleGraph)
    graphMl should include("""<node id="n0"><data key="d0">"green"</data></node>""")
    graphMl should include("""<node id="n1"></node>""")
    graphMl should include("""<node id="n2"><data key="d0">"blue"</data></node>""")
    graphMl should include("""<node id="n3"><data key="d0">"red"</data></node>""")
    graphMl should include("""<node id="n4"><data key="d0">"green"</data></node>""")
    graphMl should include("""<node id="n5"><data key="d0">"turquoise"</data></node>""")
    graphMl should include("""<edge source="n0" target="n1"></edge>""")
    graphMl should include("""<edge source="n1" target="n3"><data key="d1">2.0</data></edge>""")
    graphMl should include("""<edge source="n2" target="n4"></edge>""")
    graphMl should include("""<edge source="n3" target="n2"></edge>""")
    graphMl should include("""<edge source="n3" target="n5"></edge>""")
    graphMl should include("""<edge source="n5" target="n4"><data key="d1">2.0</data></edge>""")
  }

  "rocket connection graph" should "built" in {
    val graphMl = GraphML.connectionGraph(rocketGraph)
    println(graphMl)
  }
}
