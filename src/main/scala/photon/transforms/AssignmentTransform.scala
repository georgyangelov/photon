package photon.transforms

import photon._

import scala.util.control.Breaks

object AssignmentTransform extends Transform[Unit] {
  override def transform(value: Value, context: Unit): Value = next(value, context) match {
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
        // TODO: Figure out how this will affect compilation. Should this be a typed argument or not?
        params = Seq(Parameter(name, None)),
        body = transformBlock(Operation.Block(scope)),
        scope = None
      ), location),
      name = "call",
      arguments = Arguments(Seq(value), Map.empty),
      mayBeVarCall = false
    ), location)
}
