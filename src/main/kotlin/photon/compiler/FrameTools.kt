package photon.compiler

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import photon.compiler.core.PhotonNode
import photon.compiler.core.Type
import photon.compiler.nodes.UnknownNode

// TODO: May need to inline these methods at some point because of loop unrolling
class FrameTools {
  companion object {
    @ExplodeLoop
    fun applyGlobalsToFrame(frame: VirtualFrame, context: PhotonContext) {
      for (i in context.globals.indices) {
        val literal = context.globals[i].second

        frame.setObject(i, literal.value)
      }
    }

    @ExplodeLoop
    fun applyGlobalsToFramePartial(frame: VirtualFrame, context: PhotonContext) {
      for (i in context.globals.indices) {
        val literal = context.globals[i].second

        frame.setObject(i, literal.value)
        frame.setAuxiliarySlot(i, literal)
      }
    }

    @Suppress("UNCHECKED_CAST")
    @ExplodeLoop
    fun captureValues(
      fromFrame: VirtualFrame,
      captures: Array<NameCapture>
    ): Array<Any> {
      CompilerAsserts.compilationConstant<Int>(captures.size)

      val capturedValues = arrayOfNulls<Any>(captures.size)
      for (i in capturedValues.indices) {
        capturedValues[i] = fromFrame.getObject(captures[i].fromSlot)
      }

      return capturedValues as Array<Any>
    }

    @ExplodeLoop
    fun applyCapturedValuesFromFirstArgument(
      frame: VirtualFrame,
      captures: Array<NameCapture>
    ) {
      val capturedValues = frame.arguments[0] as Array<*>

      // CompilerAsserts.compilationConstant<Int>(capturedValues.size)
      // CompilerAsserts.compilationConstant<Int>(captures.size)

      assert(capturedValues.size == captures.size)

      for (i in capturedValues.indices) {
        frame.setObject(captures[i].toSlot, capturedValues[i])
      }
    }

    @Suppress("UNCHECKED_CAST")
    @ExplodeLoop
    fun captureValuesPartial(
      fromFrame: MaterializedFrame,
      captures: Array<NameCapture>
    ): Array<Any> {
      val capturedValues = arrayOfNulls<Pair<Any, PhotonNode>>(captures.size)

      for (i in captures.indices) {
        val actualValue = fromFrame.getObject(captures[i].fromSlot)
        val partialValue = fromFrame.getAuxiliarySlot(captures[i].fromSlot)

        capturedValues[i] = Pair(actualValue, partialValue as PhotonNode)
      }

      return capturedValues as Array<Any>
    }

    @Suppress("UNCHECKED_CAST")
    @ExplodeLoop
    fun applyCapturedValuesFromFirstArgumentPartial(
      frame: VirtualFrame,
      captures: Array<NameCapture>
    ) {
      val capturedValues = frame.arguments[0] as Array<*>

      // CompilerAsserts.compilationConstant<Int>(capturedValues.size)
      // CompilerAsserts.compilationConstant<Int>(captures.size)

      assert(capturedValues.size == captures.size)

      for (i in capturedValues.indices) {
        val slot = captures[i].toSlot
        val pair = capturedValues[i] as Pair<Any, PhotonNode>

        frame.setObject(slot, pair.first)
        frame.setAuxiliarySlot(slot, pair.second)
      }
    }

    fun copyOfFrameDescriptorForPartialExecution(
      frameDescriptor: FrameDescriptor
    ): FrameDescriptor {
      val result = frameDescriptor.copy()

      for (slot in 0 until frameDescriptor.numberOfSlots) {
        // The auxiliary slots are used to store the source values after partial evaluation
        result.findOrAddAuxiliarySlot(slot)
      }

      return result
    }

    @ExplodeLoop
    fun applyArguments(frame: VirtualFrame, argumentCaptures: Array<ArgumentCapture>) {
      for (i in argumentCaptures.indices) {
        val capture = argumentCaptures[i]

        // First argument should be the closure captures array
        frame.setObject(capture.toSlot, frame.arguments[capture.argumentIndex + 1])
      }
    }

    @ExplodeLoop
    fun applyArgumentsForPartialExecution(
      frame: VirtualFrame,
      argumentCaptures: Array<ArgumentCapture>,
      argumentTypes: Array<Type>,
      captureActualValues: Boolean
    ) {
      for (i in argumentCaptures.indices) {
        val capture = argumentCaptures[i]

        // TODO: Make it possible to pass some of the arguments maybe
        // First argument should be the closure captures array

//        TODO("need to pass actual arguments when partially evaluating")
//        frame.setObject(capture.toSlot, frame.arguments[capture.argumentIndex + 1])

        if (captureActualValues) {
          frame.setObject(capture.toSlot, frame.arguments[capture.argumentIndex + 1])
        }

        frame.setAuxiliarySlot(capture.toSlot, UnknownNode(argumentTypes[capture.argumentIndex]))
      }
    }
  }
}