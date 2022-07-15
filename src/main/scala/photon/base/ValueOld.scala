//package photon.base
//
//import photon.lib.Lazy
//
//sealed trait UValue {
//  val location: Option[Location]
//
//  override def toString: String = ???
//}
//
//object UValue {
////  case class Object(ref: java.lang.Object, typ: Value, location: Option[Location]) extends UValue
//  case class Bool(value: scala.Boolean, location: Option[Location]) extends UValue
//  case class Int(value: scala.Int, location: Option[Location]) extends UValue
//  case class Float(value: scala.Float, location: Option[Location]) extends UValue
//  case class String(value: java.lang.String, location: Option[Location]) extends UValue
//
//  case class Unknown(errorMessageIfEvaluated: String, location: Option[Location]) extends UValue
//
//  case class Lazy(value: UValue, scope: Scope, location: Option[Location]) extends UValue
//
//  case class Block(values: Seq[UValue], location: Option[Location]) extends UValue
//  case class Let(name: VarName, value: UValue, body: UValue, location: Option[Location]) extends UValue
//  case class Reference(name: VarName, location: Option[Location]) extends UValue
//  case class Function(fn: photon.base.Function, location: Option[Location]) extends UValue
//  case class Call(name: String, arguments: Arguments[UValue], location: Option[Location]) extends UValue
//}
//
//class VarName(val originalName: String)
//
//trait Function
//
//sealed trait Value {
//  val location: Option[Location]
//  def typ: Value
//
//  override def toString: String = ???
//}
//
//object Value {
//  case class Object(ref: java.lang.Object, typ: Value, location: Option[Location]) extends Value
//
//  case class Unknown(errorMessageIfEvaluated: String, typ: Value, location: Option[Location]) extends Value
//
//  case class Lazy(value: UValue, typ: Value, scope: Scope, location: Option[Location]) extends Value
//
//  case class Block(values: Seq[Value], location: Option[Location]) extends Value {
//    override def typ = values.last.typ
//  }
//
//  case class Let(name: VarName, value: Value, body: Value, location: Option[Location]) extends Value {
//    override def typ = body.typ
//  }
//
//  case class Reference(name: VarName, typ: Value, location: Option[Location]) extends Value
//
//  case class Function(fn: photon.base.Function, location: Option[Location]) extends Value {
//    override def typ = ???
//  }
//  case class Call(name: String, arguments: Arguments[Value], typ: Value, location: Option[Location]) extends Value
//}
//
//
//case class EValue(value: Value, typ: Value)