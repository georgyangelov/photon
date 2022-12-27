package photon.compiler

import scala.collection.mutable

case class VMProgram(
  functions: Map[VMGlobalName, VMFunction],
  mainName: VMGlobalName
)

class VMGlobalName
case class VMFunction(name: String, block: VMBasicBlock)

case class VMBasicBlock(
  instructions: Array[VMInstruction]
)

sealed abstract class VMInstruction
object VMInstruction {
  case class Return(value: VMValueOrRef) extends VMInstruction
  case class Assign(name: String, value: VMValueOrRef) extends VMInstruction
  case class Call(name: String, args: Array[VMValueOrRef], resultName: String) extends VMInstruction
}

sealed trait VMValueOrRef
sealed trait VMValue extends VMValueOrRef
sealed trait VMRef extends VMValueOrRef

object VMValue {
  case class Object(obj: java.lang.Object, fns: FunctionTable) extends VMValue
}

object VMRef {
  case class Argument(index: Int) extends VMRef
  case class Local(name: String) extends VMRef
}

case class FunctionTable(
  functions: Map[String, VMGlobalName]
)

class VM(program: VMProgram) {
  def executeMain(): VMValue.Object =
    execute(
      block = program.functions(program.mainName).block,
      Array.empty
    )

  private def execute(block: VMBasicBlock, args: Array[VMValue.Object]): VMValue.Object = {
    val locals = mutable.Map[String, VMValue.Object]()

    block.instructions.foreach {
      case VMInstruction.Return(value) =>
        return resolveRefs(value, args, locals)

      case VMInstruction.Assign(name, value) =>
        locals.put(name, resolveRefs(value, args, locals))

      case VMInstruction.Call(fnName, callArgs, resultName) =>
        val argObjects = callArgs.map(resolveRefs(_, args, locals))
        val target = argObjects.head
        val actualArguments = argObjects.tail

        val fnId = resolveRefs(target, args, locals)
          .fns
          .functions
          .getOrElse(fnName, throw new RuntimeException("Call must target a function, not an object"))

        val function = program.functions
          .getOrElse(fnId, throw new RuntimeException("Undefined function"))

        val result = execute(function.block, actualArguments)

        locals.put(resultName, result)
    }

    throw new RuntimeException("Missing return instruction")
  }

//  private def callNativeMethod(
//    name: String,
//    args: Array[VMValue.Object]
//  ): VMValue.Object = {
//    val target = args.head
//    val nativeMethod = target.obj.getClass.getMethod(name, args.tail.map(_.obj.getClass): _*)
//    val result = nativeMethod.invoke(target, args.tail)
//
//    VMValue.Object(result)
//  }

  private def resolveRefs(
    value: VMValueOrRef,
    args: Array[VMValue.Object],
    locals: scala.collection.Map[String, VMValue.Object],
  ): VMValue.Object =
    value match {
      case obj: VMValue.Object => obj
      case VMRef.Argument(index) => args(index)
      case VMRef.Local(name) => locals.getOrElse(name, throw new RuntimeException(s"No local named $name"))
    }
}
