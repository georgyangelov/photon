package photon.transforms

import photon._

import scala.collection.View
import scala.util.control.Breaks

object AssignmentTransform {
  def transform(value: Value): Value = value match {
    case Value.Operation(Operation.Block(values), location) =>
      val resultValues = Vector.newBuilder[Value]

      Breaks.breakable {
        for (value <- values) {
          var done = false

          resultValues += (value match {
            case Value.Operation(Operation.Assignment(name, expression), location) =>
              done = true
              transformAssignment(
                name,
                expression,
                values.view.dropWhile(_ != value).drop(1),
                location
              )

            case _ => value
          })

          if (done) {
            Breaks.break
          }
        }
      }

      Value.Operation(Operation.Block(resultValues.result), location)

    case _ => value
  }

  private def transformAssignment(
    name: String,
    value: Value,
    scope: View[Value],
    location: Option[Location]
  ): Value =
    Value.Operation(Operation.Call(
      target = Value.Lambda(Lambda(
        params = Vector(name),
        body = Operation.Block(scope.toSeq),
        scope = None
      ), location),
      name = "$call",
      arguments = Vector(value),
      mayBeVarCall = false
    ), location)
}
