// See LICENSE for license details.

package firrtl.graph

import firrtl.analyses.ConnectionGraph
import firrtl.annotations.{ReferenceTarget, Target}

import scala.collection.Set

trait GraphAttribute {
  val id: String
  val attrFor: String
  val attrType: String
  val attrName: String
  val value: String

  def toGraphMLHeader =
    s"""<key id="$id" for="$attrFor" attr.name="$attrName" attr.type="$attrType"/>""".stripMargin

  // @todo: in Scala 2.13 it will be cleaner with Type tricks...
  def toGraphML =
    s"""<data key="$id">${
      attrType match {
        case "string" => s""""$value""""
        case _ => value
      }
    }</data>""".stripMargin
}

trait GraphMLEdge {
  val gmId: Option[String] = None
  val gmSource: GraphMLVertex
  val gmTarget: GraphMLVertex
  val gmAttributes: Set[GraphAttribute]

  def toGraphML =
    s"""<edge${
      gmId match {
        case Some(id) => s" $id "
        case None => " "
      }
    }source="${gmSource.gmId}" target="${gmTarget.gmId}">${gmAttributes.map(_.toGraphML).mkString("\n  ")}</edge>"""
}

trait GraphMLVertex {
  def gmId: String

  def gmAttributes: Set[GraphAttribute]

  final def gmSource = this

  def gmEdge(target: GraphMLVertex): GraphMLEdge

  def toGraphML =
    s"""<node id="${gmId}">${gmAttributes.map(_.toGraphML).mkString("\n  ")}</node>""".stripMargin
}


object GraphML {
  def connectionGraph(connectionGraph: ConnectionGraph): String = apply(connectionGraph, referenceTargetHelper)


  def apply[T <: GraphMLVertex](diGraph: DiGraph[T]): String = gml(
    diGraph.getVertices,
    diGraph.getEdgeMap.flatMap { case (source, targets) => targets.map(t => source.gmEdge(t)) }.toSet
  )

  def apply[T, G <: DiGraph[T]](diGraph: G, helper: (G, T) => GraphMLVertex): String = gml(
    diGraph.getVertices.map(helper(diGraph, _)),
    diGraph.getEdgeMap.flatMap { case (source, targets) => targets.map(t => helper(diGraph, source).gmEdge(helper(diGraph, t))) }.toSet
  )

  private def referenceTargetHelper(digraph: ConnectionGraph, rt: ReferenceTarget): GraphMLVertex = new GraphMLVertex {
    gmlv =>
    override def gmId: String = rt.toString

    override def gmAttributes: Set[GraphAttribute] = Set.empty

    override def gmEdge(target: GraphMLVertex): GraphMLEdge = new GraphMLEdge {
      val gmSource: GraphMLVertex = gmlv
      val gmTarget: GraphMLVertex = target
      val gmAttributes: Set[GraphAttribute] = Set.empty
    }
  }

  private def gml[V <: GraphMLVertex, E <: GraphMLEdge](graphMLVertices: Set[V], graphMLEdges: Set[E]): String = {
    require(graphMLVertices.groupBy(_.gmId).values.forall(_.size == 1))
    s"""<?xml version="1.0" encoding="UTF-8"?>
       |<graphml xmlns="http://graphml.graphdrawing.org/xmlns">
       |${(graphMLVertices.flatMap(_.gmAttributes) ++ graphMLEdges.flatMap(_.gmAttributes)).map(_.toGraphMLHeader).mkString("\n")}
       |<graph id="G" edgedefault="directed">
       |  ${graphMLVertices.map(_.toGraphML).mkString("\n  ")}
       |  ${graphMLEdges.map(_.toGraphML).mkString("\n  ")}
       |</graph>
       |</graphml>
      """.stripMargin
  }
}

