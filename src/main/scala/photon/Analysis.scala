package photon

import scala.collection.immutable._
import scala.collection.mutable

case class AnalysisError(message: String, override val location: Option[Location])
  extends PhotonError(message, location) {}

case class AnalyzedLambda(
  closedVariables: Map[String, Value]
) {
  def isFree: Boolean = closedVariables.isEmpty
}

object AnalyzedLambda {
  class Builder {
    final val closedVariables = HashMap.newBuilder[String, Value]

    def build: AnalyzedLambda = AnalyzedLambda(closedVariables.result)
  }

  def newBuilder = new Builder
}

class ObjectIdCache[K <: WithObjectId, V]() {
  val map = new mutable.HashMap[ObjectId, V]

  def resolve(key: K, resolver: => V): V = map.getOrElseUpdate(key.id, resolver)
}

class Analysis {
  val lambdaCache = new ObjectIdCache[Value.Lambda, AnalyzedLambda]

  def analyze(lambdaValue: Value.Lambda): AnalyzedLambda = {
    lambdaCache.resolve(lambdaValue, {
      val lambda = lambdaValue.value
      val builder = AnalyzedLambda.newBuilder

      lambda.body.values.foreach(analyzeLambdaValue(builder, lambda, _))

      builder.build
    })
  }

  private def analyzeLambdaValue(builder: AnalyzedLambda.Builder, lambda: Lambda, value: Value): Unit = {
    value match {
      case Value.Unknown(_) |
           Value.Nothing(_) |
           Value.Boolean(_, _) |
           Value.Int(_, _) |
           Value.Float(_, _) |
           Value.String(_, _) |
           Value.Native(_, _) =>

      case Value.Struct(Struct(props), _) =>
        props.values.foreach(analyzeLambdaValue(builder, lambda, _))

      case lambdaValue @ Value.Lambda(_, _) =>
        // TODO: We should set the lambda scope somehow here.
        //       Not sure if this will be able to be set correctly by the Interpreter.
        //       ...
        //       Actually, it should be able to set it
        val analysis = analyze(lambdaValue)

        analysis.closedVariables.foreach { case (name, resolvedValue) =>
          // TODO: Explicit assert with meaningful message?
          if (!lambda.params.contains(name)) {
            builder.closedVariables.addOne(name, resolvedValue)
          }
        }

      case Value.Operation(Operation.Assignment(_, _), location) =>
        throw AnalysisError("Found Assignment operation. This should have been transformed to a lambda call", location)

      case Value.Operation(Operation.Block(values), _) =>
        values.foreach(analyzeLambdaValue(builder, lambda, _))

      case Value.Operation(Operation.Call(target, name, arguments, mayBeVarCall), _) =>
        analyzeLambdaValue(builder, lambda, target)

        arguments.foreach(analyzeLambdaValue(builder, lambda, _))

        if (mayBeVarCall) {
          val hasParameterWithTheSameName = lambda.params.contains(name)

          // TODO: Explicit assert with meaningful message?
          val valueInParentScope = lambda.scope.get.find(name)

          if (!hasParameterWithTheSameName && valueInParentScope.isDefined) {
            builder.closedVariables.addOne((name, valueInParentScope.get))
          }
        }

      case Value.Operation(Operation.NameReference(name), location) =>
        val hasParameterWithTheSameName = lambda.params.contains(name)

        if (!hasParameterWithTheSameName) {
          // TODO: Explicit assert with meaningful message?
          val valueInParentScope = lambda.scope.get
            .find(name)
            .getOrElse { throw AnalysisError(s"Cannot find name '${name}'", location) }

          builder.closedVariables.addOne((name, valueInParentScope))
        }
    }
  }
}
