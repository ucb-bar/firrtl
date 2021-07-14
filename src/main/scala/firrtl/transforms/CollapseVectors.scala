// See LICENSE For license details.

package firrtl.transforms

import firrtl._
import firrtl.annotations.{CircuitTarget, ModuleTarget, ReferenceTarget, Target, TargetToken}
import firrtl.Mappers._

import scala.collection.mutable

/** Collapse vectors of single-bit UInts into one UInt. Ports of the top module will *not* be collapsed.
  *
  * Concretely this is doing conversions of:
  * {{{
  * circuit Foo:
  *   module Foo:
  *     output out: UInt<1>[1]
  *     wire w: UInt<1>[1]
  *     w[0] <= UInt<1>(0)
  *     out[0] <= w[0]
  * }}}
  *
  * Into:
  * {{{
  * circuit Foo:
  *   module Foo:
  *     output out: UInt<1>[1]
  *     wire w: UInt<1>
  *     out[0] <= bits(w, 0, 0)
  *     w <= UInt<1>("h0")
  * }}}
  */
class CollapseVectors extends Transform {

  override val inputForm = UnknownForm
  override val outputForm = UnknownForm

  override val prerequisites = firrtl.stage.Forms.MidForm

  override val optionalPrerequisites = Seq.empty

  override val dependents = Seq.empty

  override def invalidates(a: Transform): Boolean = a match {
    case passes.InferTypes => true
    case _                 => false
  }

  private type BitAssignments = mutable.HashMap[BigInt, ir.Expression]

  /** Convert a ReferenceTarget with some associate Type to a WRef. This is useful for situations where you have a target,
    * but need to pass it to something that expects an expression, e.g., create_exps.
    */
  private def referenceTargetToWRef(target: ReferenceTarget, tpe: ir.Type): WRef =
    WRef(n =
           target
             .tokens
             .map {
               case TargetToken.Ref(r) => r
               case TargetToken.Field(f) => s".$f"
               case TargetToken.Index(v) => s"[$v]" }
             .mkString(""),
         t = tpe )

  private def onExpression(a: ir.Expression)
                          (implicit renames: RenameMap,
                           target: ModuleTarget,
                           instances: mutable.Map[ReferenceTarget, ModuleTarget]): ir.Expression = a match {
    case rhs@ WSubIndex(_, _, ir.UIntType(ir.IntWidth(w)), SourceFlow) if w == 1 => {
      /* Type will be wrong for rhs.expr, but is cleaned up by InferTypes */
      lazy val tmp = ir.DoPrim(PrimOps.Bits, Seq(rhs.expr), Seq(rhs.value, rhs.value), ir.UIntType(ir.IntWidth(1)))
      Utils.kind(a) match {
        case _@ InstanceKind => Utils.splitRef(a) match {
          case (car, cdr) if renames.get(Target.asTarget(instances(target.ref(car.name)))(cdr)).nonEmpty =>
            logger.debug(s"  - Replacing RHS usage of '${a.serialize}' with primop '${tmp.serialize}'")
            tmp
          case _ => a
        }
        case _ if renames.get(Target.asTarget(target)(a)).nonEmpty =>
          logger.debug(s"  - Replacing RHS usage of '${a.serialize}' with primop '${tmp.serialize}'")
          tmp
        case _ => a
      }
    }
    case _ => a.map(onExpression)
  }

  private def emitCat(lhs: ir.Expression,
                      rhs: Seq[ir.Expression])
                     (implicit namespace: Namespace): ir.Statement = {
    logger.debug(s"  - Replacing connections to '${lhs.serialize}' with concatenations of temporaries:")
    rhs.foreach(a => logger.debug(s"    - ${a.serialize}"))
    /* The type will have an unknown width due to seqCat implementation */
    ir.Connect(ir.NoInfo, lhs, seqCat(rhs))
  }

  /** For a LHS expression and a set of individual bit assignments, emit a statement that connects the individual bits to
    * the LHS.
    * @param lhs the left-hand side expression
    * @param bits a mapping of indices to expressions representing the assignments to each bit
    * @param namespace a namespace that will be used to generate temporary names
    * @param renames a list of circuit components that have been renamed
    * @param target the current module
    * @param instances a mapping of instance names to module names
    * @return a statement representing the connection (this may be multiple statements in a block)
    */
  private def emitCollapsed(lhs: ir.Expression,
                            bits: BitAssignments)
                           (implicit namespace: Namespace,
                            renames: RenameMap,
                            target: ModuleTarget,
                            instances: mutable.Map[ReferenceTarget, ModuleTarget]): ir.Statement = {

    /* Get the bit assignments in order by their index */
    val orderedBits = bits.toSeq.sortBy(_._1)

    /* Examine each of the bit assignments (in order) and try to determine if certain special cases are possible. These
     * cases are:
     *
     *   1. Every bit is invalidated
     *   2. Every bit is assigned to a literal
     *   3. Every bit is assigned to the same bit in another 1-bit vector that was collapsed
     */
    val (isInv, isLit, isVec) = {
      var isInvalid, isLiteral, isOrderedSubIndex = true
      orderedBits.foreach {
        case (_, WInvalid) =>
          isLiteral = false
          isOrderedSubIndex = false
        case (_, _: ir.UIntLiteral) =>
          isInvalid = false
          isOrderedSubIndex = false
        case (i, rhs @ WSubIndex(expr, j, ir.UIntType(ir.IntWidth(w)), _)) => expr.tpe match {
          case ir.VectorType(_, s) if i == j && w == 1 && s == bits.size => Utils.kind(rhs) match {
            case InstanceKind => Utils.splitRef(rhs) match {
              case (car, cdr) if renames.get(Target.asTarget(instances(target.ref(car.name)))(cdr)).nonEmpty =>
                isLiteral = false
                isInvalid = false
              case _ =>
                isLiteral = false
                isInvalid = false
                isOrderedSubIndex = false
            }
            case _ if renames.get(Target.asTarget(target)(rhs)).nonEmpty =>
              isLiteral = false
              isInvalid = false
            case _ =>
              isLiteral = false
              isInvalid = false
              isOrderedSubIndex = false
          }
          case _ =>
            isInvalid = false
            isLiteral = false
            isOrderedSubIndex = false
        }
        case _ =>
          isInvalid = false
          isLiteral = false
          isOrderedSubIndex = false
      }
      (isInvalid, isLiteral, isOrderedSubIndex)
    }

    /* For the special cases above, handle the connections in the following way:
     *
     *   1. If every bit in invalidated, invalidate the entire UInt.
     *   2. If every bit is connected to a literal, collapse the individual literals to one literal and connect to that.
     *   3. If both the LHS and RHS were collapsed, directly connect the two.
     *
     * If none of these are true, then crack the LHS into node/wire temporaries, do the individual connections, and
     * concatenate all the temporaries together to do the assignment.
     */
    (isInv, isLit, isVec) match {
      case (true, _, _) =>
        logger.debug(s"  - Invalidating '${lhs.serialize}' as every bit is invalid")
        ir.IsInvalid(ir.NoInfo, lhs)
      case (_, true, _) =>
        val lit = {
          val binaryValue = orderedBits.map{ case (_, ir.UIntLiteral(value, _)) => value }.mkString("")
          ir.UIntLiteral(BigInt(binaryValue, 2))
        }
        logger.debug(s"  - Connecting '${lhs.serialize}' to '${lit.serialize}' as every bit is connected to a literal")
        ir.Connect(ir.NoInfo, lhs, lit)
      case (_, _, true) =>
        val ref = bits.head._2 match { case WSubIndex(expr, _, _, _) => expr }
        logger.debug(s"  - Directly connecting '${lhs.serialize}' to '${ref.serialize}'")
        ir.Connect(ir.NoInfo, lhs, ref)
      case _ =>
        logger.debug(s"  - '${lhs.serialize}' contains sub-word assignments, will crack into nodes/wires...")
        bits.toSeq.sortBy(_._1).foldLeft(Seq.empty[ir.Statement], Seq.empty[ir.Expression]) {
          case ((s, e), (i, WInvalid)) =>
            val tmpName = namespace.newName(Utils.niceName(ir.SubIndex(lhs, i.intValue, ir.UnknownType)))
            val tmp = ir.DefWire(ir.NoInfo, tmpName, ir.UIntType(ir.IntWidth(BigInt(1))))
            val invalid = ir.IsInvalid(ir.NoInfo, WRef(tmp))
            logger.debug(s"  - '${lhs.serialize}[$i]' will become node '$tmpName'")
            (s :+ tmp :+ invalid, WRef(tmp) +: e)
          case ((s, e), (i, rhs)) =>
            val tmpName = namespace.newName(Utils.niceName(ir.SubIndex(lhs, i.intValue, ir.UnknownType)))
            logger.debug(s"  - '${lhs.serialize}[$i]' will become wire '$tmpName'")
            val tmp = ir.DefNode(ir.NoInfo, tmpName, onExpression(rhs))
            (s :+ tmp, WRef(tmp) +: e)
        } match { case (s, e) => ir.Block(s :+ emitCat(lhs, e)) }
    }
  }

  private def onStatement(a: ir.Statement)
                         (implicit renames: RenameMap,
                          target: ModuleTarget,
                          namespace: Namespace,
                          bitAssignments: mutable.Map[ReferenceTarget, BitAssignments],
                          instances: mutable.Map[ReferenceTarget, ModuleTarget]): ir.Statement = a match {
    /* As instances are found, update a mapping of instance references to modules. This is needed to disambiguate references
     * pointing at internal components or submodule ports.
     */
    case WDefInstance(_, name, module, _) =>
      instances(target.ref(name)) = target.targetParent.module(module)
      a
    /* Connections that involve a LHS with a subindex with a width of one may have been collapsed. If these have, then the
     * subindex connection needs to be converted to an assignment to a temporary node. The final connection to the
     * collapsed LHS is delayed until all temporary nodes have been assigned. This final connection is then handled with
     * a concatenation.
     */
    case a@ ir.Connect(_, lhs@ WSubIndex(_, _, ir.UIntType(ir.IntWidth(w)), _), rhs) if w == 1 => Utils.kind(lhs) match {
      case InstanceKind => Utils.splitRef(lhs) match {
        case (car, cdr) if renames.get(Target.asTarget(instances(target.ref(car.name)))(cdr)).nonEmpty =>
          val bits = bitAssignments.getOrElseUpdate(Target.asTarget(target)(lhs.expr), new BitAssignments)
          bits(lhs.value) = onExpression(rhs)
          lhs.expr.tpe match {
            case ir.VectorType(_, s) if bits.size == s => emitCollapsed(lhs.expr, bits)
            case _: ir.VectorType => ir.EmptyStmt
          }
        case _ => a.copy(expr = onExpression(rhs))
      }
      case _ if renames.get(Target.asTarget(target)(lhs)).nonEmpty =>
        val bits = bitAssignments.getOrElseUpdate(Target.asTarget(target)(lhs.expr), new BitAssignments)
        bits(lhs.value) = rhs
        lhs.expr.tpe match {
          case ir.VectorType(_, s) if bits.size == s => emitCollapsed(lhs.expr, bits)
          case _: ir.VectorType => ir.EmptyStmt
        }
      case _ => a.copy(expr = onExpression(rhs))
    }
    /* Invalidations are handled in the same was as connections, except that the bit invalidation is handled via a temporary
     * wire. The final connection can either happen in the connect case above or in the invalid case here.
     * @todo There should be a way to DRY this out with the connect case above.
     */
    case a@ ir.IsInvalid(_, lhs@ WSubIndex(_, _, ir.UIntType(ir.IntWidth(w)), _)) if w == 1 => Utils.kind(lhs) match {
      case InstanceKind => Utils.splitRef(lhs) match {
        case (car, cdr) if renames.get(Target.asTarget(instances(target.ref(car.name)))(cdr)).nonEmpty =>
          val bits = bitAssignments.getOrElseUpdate(Target.asTarget(target)(lhs.expr), new BitAssignments)
          bits(lhs.value) = WInvalid
          lhs.expr.tpe match {
            case ir.VectorType(_, s) if bits.size == s => emitCollapsed(lhs.expr, bits)
            case _: ir.VectorType => ir.EmptyStmt
          }
        case _ => a
      }
      case _ if renames.get(Target.asTarget(target)(lhs)).nonEmpty =>
        val bits = bitAssignments.getOrElseUpdate(Target.asTarget(target)(lhs.expr), new BitAssignments)
        bits(lhs.value) = WInvalid
        /* Only emit once the whole thing is full! */
        lhs.expr.tpe match {
          case ir.VectorType(_, s) if bits.size == s => emitCollapsed(lhs.expr, bits)
          case _: ir.VectorType => ir.EmptyStmt
        }
      case _ => a
    }
    /* The expression of a node is run through onType to possibly add it to the rename map. */
    case n@ ir.DefNode(_, _, expr) =>
      expr.map(onType(target.ref(n.name)))
      n.map(onExpression)
    /* Memories have extra fields that need to be renamed in addition to their type which should be separately updated. */
    case m: ir.DefMemory =>
      (m.readers.map(r => target.ref(m.name).field(r).field("data")) ++
         m.writers.flatMap(w => Seq(target.ref(m.name).field(w).field("data"),
                                    target.ref(m.name).field(w).field("mask"))) ++
         m.readwriters.flatMap(rw => Seq(target.ref(m.name).field(rw).field("rdata"),
                                         target.ref(m.name).field(rw).field("wdata"),
                                         target.ref(m.name).field(rw).field("wmask"))))
        .map(x => m.map(onType(x)))
      m.map(onType(target.ref(m.name)))
    /* This covers the case of Registers and Wires which do not require special casing like nodes and memories */
    case d: ir.IsDeclaration => d.map(onType(target.ref(d.name))).map(onExpression)
    case a => a.map(onStatement).map(onExpression)
  }

  private def onType(target: ReferenceTarget)
                    (t: ir.Type)
                    (implicit renames: RenameMap): ir.Type = t match {
    case tx@ ir.VectorType(ir.UIntType(ir.IntWidth(w)), s) if w == 1 =>
      logger.debug(s"  - Found vector of UInt<1> to collapse: ${target.serialize}")
      Some(referenceTargetToWRef(target, tx))      // 1. Convert to WRef, then wrap in Some so we can map
        .map(Utils.create_exps)                    // 2. Calculate all possible expressions.
        .get                                       // 3. Option[Seq[Expression]] => Seq[Expression]
        .map(Target.asTarget(target.moduleTarget)) // 4. Convert the expressions back to targets.
        .foreach(renames.delete)                   // 5. Update the renames, deleting subindex
      ir.UIntType(ir.IntWidth(s))                  // 6. Return the flattened type
    case tx: ir.VectorType =>
      /* TODO: Improve the performance of this so it doesn't walk every element of the vector */
      Seq
        .tabulate(tx.size)(target.index)           // 1. Populate one target for every index in the vector.
        .map(targetx => tx.map(onType(targetx)))   // 2. Map over this type (redundant work, but populates renames).
        .headOption                                // 3. Only one type matters so drop the rest.
        .getOrElse(tx)
    case tx: ir.BundleType =>
      /* This is a copy-paste of BundleType.mapType, but updating the target with the bundle field */
      ir.BundleType(tx.fields.map(x => x.copy(tpe = onType(target.field(x.name))(x.tpe))))
    case tx =>
      tx
  }

  /** Collapse ports that are vectors of single-bits skipping ports of the top-module
    * @param p a port
    * @param renames a rename map
    * @param target the current module
    */
  private def onPort(p: ir.Port)
                    (implicit renames: RenameMap,
                     target: ModuleTarget): ir.Port =
    if (target.isTop) {
      p
    } else {
      p.map(onType(target.ref(p.name)))
    }

  private def onModule(m: ir.DefModule)
                      (implicit renames: RenameMap,
                       target: CircuitTarget): ir.DefModule = m match {
    case _: ir.ExtModule => m
    case _: ir.Module  => {
      implicit val targetx = target.module(m.name)
      implicit val namespace = Namespace(m)
      implicit val bitAssignments = new mutable.HashMap[ReferenceTarget, BitAssignments]
      implicit val instances = new mutable.HashMap[ReferenceTarget, ModuleTarget]
      logger.debug(s"- Entering module '${m.name}':")
      m
        .map(onPort)
        .map(onStatement)
        .map(Utils.squashEmpty)
    }
  }

  private def run(c: ir.Circuit)
                 (implicit renames: RenameMap): ir.Circuit = {
    implicit val target = CircuitTarget(c.main)

    /* A mapping of module targets to modified modules */
    val modulesx: Map[ModuleTarget, ir.DefModule] =
      new analyses.InstanceGraph(c)          // 1. Generate an instance graph
        .moduleOrder                         // 2. Topologically sort the modules (root to leaves)
        .reverse                             // 3. Change to leaves to roots
        .map(onModule)                       // 4. Collapse vectors (from leaves to roots)
        .groupBy(m => target.module(m.name)) // 5. Group these by module target so we can find them later
        .map{ case (k, v) => (k -> v.head) } // 6. Map[ModuleTarget, Seq[DefModule]] => Map[ModuleTarget, DefModule]

    c.copy(modules = c.modules.map(m => modulesx(target.module(m.name))))
  }

  override protected def execute(state: CircuitState): CircuitState = {
    implicit val renames = RenameMap()
    state.copy(circuit = run(state.circuit), renames = Some(renames))
  }

}
