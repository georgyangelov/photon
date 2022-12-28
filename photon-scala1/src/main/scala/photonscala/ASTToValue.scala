package photonscala

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.{InteropLibrary, TruffleObject}
import com.oracle.truffle.api.library.{ExportLibrary, ExportMessage, LibraryFactory}
import com.oracle.truffle.api.nodes.Node.{Child, Children}
import com.oracle.truffle.api.nodes.{BlockNode, ExplodeLoop, Node}
import photonscala.frontend.ASTValue

import java.lang.reflect.Method
import java.util

abstract class Value extends Node {
  def typeOf(frame: VirtualFrame): Type
  def executeGeneric(frame: VirtualFrame): AnyRef
}

abstract class Type extends Value {
  def methods: java.util.Map[String, Method]
}

object RootType extends Type {
  override def typeOf(frame: VirtualFrame): Type = this
  override def executeGeneric(frame: VirtualFrame): AnyRef = this

  override val methods: util.Map[String, Method] = util.Map.of()
}

case class $Literal(obj: Object) extends Value {
  override def typeOf(frame: VirtualFrame): Type = RootType
  override def executeGeneric(frame: VirtualFrame): AnyRef = obj
}

case class $Block(nodes: Seq[Value]) extends Value with BlockNode.ElementExecutor[Value] {
  @Child
  val bodyBlock: BlockNode[Value] =
    if (nodes.isEmpty) null
    else BlockNode.create(nodes.toArray, this)

  override def typeOf(frame: VirtualFrame): Type = RootType

  override def executeGeneric(frame: VirtualFrame): AnyRef =
    if (bodyBlock != null)
      bodyBlock.executeGeneric(frame, BlockNode.NO_ARGUMENT)
    else
      null

  override def executeVoid(frame: VirtualFrame, node: Value, index: Int, argument: Int): Unit =
    node.executeGeneric(frame)

  override def executeGeneric(frame: VirtualFrame, node: Value, index: Int, argument: Int): AnyRef =
    node.executeGeneric(frame)
}

@ExportLibrary(value = classOf[InteropLibrary], receiverType = classOf[Integer])
object IntegerMethods extends TruffleObject {
  @ExportMessage
  def hasMembers(receiver: AnyRef) = true

  @ExportMessage
  def isMemberInvokable(receiver: AnyRef, member: String): Boolean = {
    ???
    true
  }

  @ExportMessage
  def invokeMember(receiver: AnyRef, member: String, arguments: AnyRef*): AnyRef = ???
}

case class $Call(
  @Child val target: Value,
  name: String,
  @Children arguments: Array[Value]
) extends Value {
  @Child
//  val interop = LibraryFactory.resolve(classOf[PhotonLibrary]).createDispatched(3)
  val interop = InteropLibrary.getFactory.createDispatched(3)

  @ExplodeLoop
  override def executeGeneric(frame: VirtualFrame): AnyRef = {
    CompilerAsserts.compilationConstant(arguments.length)

    val evaluatedTarget = target.executeGeneric(frame)
//    val target = TestObject

    val evaluatedArguments = new Array[AnyRef](arguments.length + 1)
    evaluatedArguments(0) = evaluatedTarget

    var i = 0
    while (i < arguments.length) {
      evaluatedArguments(i + 1) = arguments(i).executeGeneric(frame)

      i += 1
    }

    interop.invokeMember(evaluatedTarget, name, evaluatedArguments: _*)
  }

  override def typeOf(frame: VirtualFrame): Type = ???
}

//case class $Let(
//  name: String,
//  value: photon.Value
//)

object ASTToTruffleNode {
  def transform(ast: ASTValue): Value = ast match {
    case ASTValue.Boolean(value, location) => $Literal(value)
    case ASTValue.Int(value, location) => ??? // new PObject(value, IntType.instance)
    case ASTValue.Float(value, location) => $Literal(value)
    case ASTValue.String(value, location) => $Literal(value)

    case ASTValue.Block(values, location) => $Block(values.map(transform))

    case ASTValue.Let(name, value, block, location) => ???
    case ASTValue.NameReference(name, location) => ???

    case ASTValue.Function(params, body, returnType, location) => ???
    case ASTValue.Call(target, name, arguments, mayBeVarCall, location) =>
      $Call(transform(target), name, arguments.positional.map(transform).toArray)


    case ASTValue.FunctionType(params, returnType, location) => ???
  }
}
