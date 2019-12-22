package photon.transforms

import photon._

import scala.collection.View
import scala.util.control.Breaks

object AssignmentTransform extends Transform {
  override def transform(value: Value): Value = next(value) match {
    case Value.Operation(block @ Operation.Block(_), location) =>
      Value.Operation(transformBlock(block), location)

    case Value.Lambda(Lambda(params, scope, body), location) =>
      Value.Lambda(Lambda(params, scope, transformBlock(body)), location)

    case value @ _ => value
  }

  private def transformBlock(block: Operation.Block): Operation.Block = {
    val resultValues = Vector.newBuilder[Value]

    Breaks.breakable {
      for (value <- block.values) {
        var done = false

        resultValues += (value match {
          case Value.Operation(Operation.Assignment(name, expression), location) =>
            done = true
            transformAssignment(
              name,
              expression,
              block.values.dropWhile(_ != value).drop(1),
              location
            )

          case _ => value
        })

        if (done) {
          Breaks.break
        }
      }
    }

    Operation.Block(resultValues.result)
  }

  private def transformAssignment(
    name: String,
    value: Value,
    scope: Seq[Value],
    location: Option[Location]
  ): Value =
    Value.Operation(Operation.Call(
      target = Value.Lambda(Lambda(
        params = Vector(name),
        body = transformBlock(Operation.Block(scope.toSeq)),
        scope = None
      ), location),
      name = "call",
      arguments = Vector(value),
      mayBeVarCall = false
    ), location)
}
