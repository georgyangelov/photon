package photon.core

import photon.frontend.{ASTValue, Parser, StaticScope}
import photon.frontend.macros.ClassMacros
import photon.interpreter.EvalError
import photon.{Arguments, EValue, Location, Scope, UOperation, Variable, VariableName}

object CoreType extends StandardType {
  override val typ = TypeRoot
  override def unboundNames = Set.empty
  override val location = None
  override val methods = Map(
    "typeCheck" -> new Method {
      override val runMode = MethodRunMode.CompileTimeOnly
      override def typeCheck(args: Arguments[EValue]) = args.positional(1).evalAssert[Type]
      override def call(args: Arguments[EValue], location: Option[Location]) = {
        val value = args.positional.head
        val typ = args.positional(1).evalAssert[Type]
        val valueTyp = value.evalType.getOrElse(value.typ)

        if (valueTyp != typ) {
          throw EvalError(s"Invalid value ${value.inspect}: ${valueTyp.inspect} for type ${typ.inspect}", location)
        }

        value.evaluated
      }
    }
  )
  override def toUValue(core: Core) = inconvertible
}

class Core extends EValue {
  override val typ = CoreType
  override def unboundNames = Set.empty
  override val location = None
  override def evalMayHaveSideEffects = false
  override def evalType = None
  override protected def evaluate: EValue = this
  override def finalEval = this
  override def toUValue(core: Core) = this.referenceTo(this, location)

  lazy val globals = Globals.of(
    "Core" -> this,
    "Type" -> TypeRoot,
    "Bool" -> photon.core.Bool,
    "Int" -> photon.core.Int,
    "Float" -> photon.core.Float,
    "String" -> photon.core.String,
    "List" -> photon.core.List,
    "Function" -> photon.core.operations.FunctionRoot,
    "Class" -> photon.core.ClassRoot,
    "ClassBuilder" -> photon.core.ClassBuilderRoot,
    "Optional" -> photon.core.OptionalRoot
  )

  lazy val rootScope = Scope.newRoot(globals.names)
  lazy val staticRootScope = StaticScope.fromScope(rootScope)

  val macros = Map[String, (Parser, Location) => ASTValue](
    "class" -> ClassMacros.classMacro,
    "def" -> ClassMacros.defMacro
  )

  def applyMacro(name: String, parser: Parser, location: Location): Option[ASTValue] =
    macros.get(name).map(_.apply(parser, location))

  def referenceTo(value: EValue, location: Option[Location]) =
    globals.referenceTo(value, location)
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