package photon.compiler

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.RootNode
import photon.compiler.core.*
import photon.core.EvalError

class PhotonFunctionRootNode(
  language: TruffleLanguage<*>,

  private val isMainModuleFunction: Boolean,
  val unevaluatedArgumentTypes: List<Value>,

  @CompilerDirectives.CompilationFinal
  private var body: Value,

  frameDescriptor: FrameDescriptor,
  val captures: List<NameCapture>,

  // The main function of a module will not have a parent
  private val parentPartialFrame: MaterializedFrame?
): RootNode(language, frameDescriptor) {
  private var evaluatedArgumentTypes = mutableListOf<Type>()

  fun executePartial(module: PhotonModule) {
    CompilerDirectives.transferToInterpreter()

    if (parentPartialFrame != null) {
      for (typeValue in unevaluatedArgumentTypes) {
        // TODO: Guard against using values which are not yet executed
        val argumentType = typeValue.executeCompileTimeOnly(parentPartialFrame) as Type

        evaluatedArgumentTypes.add(argumentType)
      }
    } else if (unevaluatedArgumentTypes.isNotEmpty()) {
      CompilerDirectives.shouldNotReachHere("Main function of module cannot have argument types")
    }

    // TODO: This should know the correct eval mode. Probably.
    val evalMode = EvalMode.Partial

    val language = PhotonContext.currentFor(this).language
    val context = PartialContext(module, evalMode, evaluatedArgumentTypes)

//    for ((index, global) in PhotonContext.currentFor(this).globals.withIndex()) {
//      context.localTypes[index] = global.second.type
//    }

    // The auxiliary slots are used to store operations where needed
    val partialFrameDescriptor = frameDescriptor.copy()
    for (slot in 0 until (frameDescriptor.numberOfSlots + captures.size)) {
      partialFrameDescriptor.findOrAddAuxiliarySlot(slot)
    }

    val capturedValues = arrayOfNulls<Pair<Any, Value>>(captures.size)
    if (captures.isNotEmpty()) {
      assert(parentPartialFrame != null)

      for (i in captures.indices) {
        // TODO: We shouldn't be getting arguments from the parent frame because there are no
        //       auxiliary slots we can use.
        val actualValue =
          if (captures[i].from.type == Name.Type.Local)
            parentPartialFrame!!.getObject(captures[i].from.slotOrArgumentIndex)
          else
            throw EvalError("Cannot capture parent argument in partial evaluation (yet)", null)

        val partialValue =
          if (captures[i].from.type == Name.Type.Local)
            parentPartialFrame!!.getAuxiliarySlot(captures[i].from.slotOrArgumentIndex)
          else
            throw EvalError("Cannot capture parent argument in partial evaluation (yet)", null)

        capturedValues[i] = Pair(actualValue, partialValue as Value)
      }
    }

    // TODO: This can have arguments
    val partialRootNode = PhotonFunctionRootNodePartial(
      language,
      body,
      partialFrameDescriptor,
      context,
      captures,
      isMainModuleFunction
    )

    // TODO: Add captures here
    body = partialRootNode.callTarget.call(capturedValues) as Value
  }

  @ExplodeLoop
  override fun execute(frame: VirtualFrame): Any {
    if (isMainModuleFunction) {
      PhotonContext.currentFor(this).setGlobalsToFrame(frame)
    }

    val capturedValues = frame.arguments[0] as Array<*>

    CompilerAsserts.compilationConstant<Int>(capturedValues.size)
    CompilerAsserts.compilationConstant<Int>(captures.size)

    assert(capturedValues.size == captures.size)

    for (i in capturedValues.indices) {
      frame.setObject(captures[i].toSlot, capturedValues[i])
    }

    return body.executeCompileTimeOnly(frame)
  }
}

private class PhotonFunctionRootNodePartial(
  language: TruffleLanguage<*>,
  val value: Value,
  partialFrameDescriptor: FrameDescriptor,
  val context: PartialContext,
  val captures: List<NameCapture>,
  val isMainModuleFunction: Boolean
): RootNode(language, partialFrameDescriptor) {
  override fun execute(frame: VirtualFrame): Any {
    if (isMainModuleFunction) {
      PhotonContext.currentFor(this).setGlobalsToPartialFrame(frame)
    }

    val capturedValues = frame.arguments[0] as Array<Pair<Any, Value>>

    CompilerAsserts.compilationConstant<Int>(capturedValues.size)
    CompilerAsserts.compilationConstant<Int>(captures.size)

    assert(capturedValues.size == captures.size)

    for (i in capturedValues.indices) {
      frame.setObject(captures[i].toSlot, capturedValues[i].first)
      frame.setAuxiliarySlot(captures[i].toSlot, capturedValues[i].second)
    }

    return value.executePartial(frame, context)
  }
}