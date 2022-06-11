package photon

import photon.base._
import photon.core._
import photon.frontend.{ASTValue, Parser, StaticScope, UOperation, UValue}

class Core extends RealEValue {
  override val location = None
  override def unboundNames = Set.empty

  val meta = new Type {
    override def typ: Type = $Type
    override val methods = Map(
      "typeCheck" -> new CompileTimeOnlyMethod {
        override val signature: MethodSignature = ???
        override def apply(args: CallSpec, location: Option[Location]): EValue = ???
      }
    )

    override def toUValue(core: Core): UValue = inconvertible
  }
  override def typ: Type = meta
  override def toUValue(core: Core): UValue = this.referenceTo(this, location)

  lazy val globals = Globals.of(
    "Core" -> this,
    "AnyStatic" -> $AnyStatic,
    "Bool" -> $Bool,
    "Float" -> $Float,
    "Int" -> $Int,
    "Optional" -> $Optional,
    "Patten" -> $Pattern,
    "String" -> $String,
    "Type" -> $Type,
    "Unknown" -> $Unknown
  )

  val macros = Map[String, (Parser, Location) => ASTValue](

  )

  lazy val rootScope = Scope.newRoot(globals.names)
  lazy val staticRootScope = StaticScope.fromRootScope(rootScope)

  def applyMacro(name: String, parser: Parser, location: Location): Option[ASTValue] =
    macros.get(name).map(_.apply(parser, location))

  def referenceTo(value: EValue, location: Option[Location]) =
    globals.referenceTo(value, location)

  def typeCheck(value: EValue, typ: Type, location: Option[Location]): EValue = {
    val valueTyp = value.realType

    if (typ == $AnyStatic) {
      return value
    }

    if (valueTyp != typ) {
      throw EvalError(s"Invalid value ${value.inspect}: ${valueTyp.inspect} for type ${typ.inspect}", location)
    }

    value
  }
}

object Globals {
  def of(vars: (String, EValue)*) = {
    new Globals(vars.map { case (name, value) => Variable(new VariableName(name), value) })
  }
}

class Globals(val names: Seq[Variable]) {
  def referenceTo(value: EValue, location: Option[Location]) = {
    val name = names
      .find { case Variable(_, evalue) => evalue == value }
      .getOrElse { throw EvalError(s"Cannot find a name for $value", location) }
      .name

    UOperation.Reference(name, location)
  }
}