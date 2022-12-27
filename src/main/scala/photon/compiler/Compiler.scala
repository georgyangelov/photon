package photon.compiler

import photon.base._
import photon.core._
import photon.core.operations._

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable

class Compiler(input: Value) {
  var functions = Map.newBuilder[VMGlobalName, VMFunction]

  def compile: VMProgram = {
    val instructions = Array.newBuilder[VMInstruction]
    compileBlock(asBlock(input), Array.empty, instructions, Some("__mainReturn"))
    instructions.addOne(VMInstruction.Return(VMRef.Local("__mainReturn")))

    val mainFunction = VMFunction("main", VMBasicBlock(instructions.result))

    val mainName = new VMGlobalName
    functions.addOne(mainName -> mainFunction)

    VMProgram(functions.result, mainName)
  }

  private def compileBlock(
    block: $Block,
    argIndexes: Array[VarName],
    instructions: mutable.ArrayBuilder[VMInstruction],
    resultLocal: Option[String]
  ): Unit = {
    val length = block.values.length
    var i = 0

    while (i < length) {
      val value = block.values(i)

      val result = value match {
        case $Object(obj, _, _) => Some(VMValue.Object(obj.asInstanceOf[Object], FunctionTable(Map.empty)))

        case $Reference(name, _) =>
          val argumentIndex = argIndexes.indexOf(name)

          if (argumentIndex >= 0)
            Some(VMRef.Argument(argumentIndex))
          else
            Some(VMRef.Local(nameOf(name)))

        case value: $Block =>
          compileBlock(value, argIndexes, instructions, resultLocal)

          None

        case $Let(name, letValue, letBody, _) =>
          compileBlock(asBlock(letValue), argIndexes, instructions, Some(nameOf(name)))
          compileBlock(asBlock(letBody), argIndexes, instructions, resultLocal)

          None

        case $FunctionDef(params, body, _, _) =>
          val globalName = new VMGlobalName

          val bodyInstructions = Array.newBuilder[VMInstruction]
          compileBlock(asBlock(body), params.map(_.inName).toArray, bodyInstructions, Some("returns"))
          bodyInstructions.addOne(VMInstruction.Return(VMRef.Local("returns")))

          val function = VMFunction(s"fn_${uniqueName()}", VMBasicBlock(bodyInstructions.result))

          functions.addOne(globalName -> function)

          Some(VMValue.Object(null, FunctionTable(Map("call" -> globalName))))

        case $Call(name, args, _) =>
          val selfName = uniqueName()
          val argNames = args.positional.indices.map { _ => uniqueName() }

          compileBlock(asBlock(args.self), argIndexes, instructions, Some(selfName))

          args.positional.zip(argNames)
            .foreach { case value -> name => compileBlock(asBlock(value), argIndexes, instructions, Some(name)) }

          val resultName = resultLocal.getOrElse(uniqueName())
          val callInstruction = VMInstruction.Call(
            name,
            (VMRef.Local(selfName) +: argNames.map(VMRef.Local)).toArray,
            resultName
          )

          instructions.addOne(callInstruction)

          Some(VMRef.Local(resultName))
      }

      if (i == length - 1 && resultLocal.isDefined && result.isDefined) {
        instructions.addOne(VMInstruction.Assign(resultLocal.get, result.get))
      }

      i += 1
    }
  }

  private val nameMap = mutable.Map[VarName, String]()

  private val nextUniqueId = new AtomicInteger(1)
  private def uniqueName(): String = s"_${nextUniqueId.getAndIncrement()}"
  private def nameOf(varName: VarName): String =
    nameMap.getOrElseUpdate(varName, s"${varName.originalName}_${nextUniqueId.getAndIncrement()}")

  private def asBlock(value: Value): $Block = value match {
    case block: $Block => block
    case notABlock => $Block(Seq(notABlock), notABlock.location)
  }
}
