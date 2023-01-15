package photon.compiler

import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind

data class NameCapture(val fromSlot: Int, val toSlot: Int)
data class ArgumentCapture(val argumentIndex: Int, val toSlot: Int)

internal sealed class LexicalScope private constructor(
  protected val frameBuilder: FrameDescriptor.Builder,
) {
  internal class BlockScope(
    frameBuilder: FrameDescriptor.Builder,
    private val parent: LexicalScope?,
    private val names: MutableMap<String, Int>
  ): LexicalScope(frameBuilder) {
    override fun defineName(name: String): Int {
      val slot = frameBuilder.addSlot(FrameSlotKind.Object, name, null)

      names[name] = slot

      return slot
    }

    override fun accessName(name: String): Int? {
      val localSlot = names[name]
      if (localSlot != null) {
        return localSlot
      }

      if (parent != null) {
        return parent.accessName(name)
      }

      return null
    }
  }

  internal class FunctionScope(
    frameBuilder: FrameDescriptor.Builder,
    private val parent: LexicalScope?,
    private val argumentNames: List<String>,
    private val names: MutableMap<String, Int>,
    private val captures: MutableList<NameCapture>
  ): LexicalScope(frameBuilder) {
    private val argumentCaptures = mutableListOf<ArgumentCapture>()

    fun captures(): Array<NameCapture> = captures.toTypedArray()
    fun argumentCaptures(): Array<ArgumentCapture> = argumentCaptures.toTypedArray()

    override fun accessName(name: String): Int? {
      val slot = names[name]
      if (slot != null) {
        return slot
      }

      val argumentIndex = argumentNames.indexOf(name)
      if (argumentIndex >= 0) {
        val localSlotForArgument = defineName(name)

        argumentCaptures.add(ArgumentCapture(argumentIndex, toSlot = localSlotForArgument))

        return localSlotForArgument
      }

      if (parent != null) {
        val nameFromParentScope = parent.accessName(name)
        if (nameFromParentScope != null) {
          return capture(name, nameFromParentScope)
        }
      }

      return null
    }

    override fun defineName(name: String): Int {
      val slot = frameBuilder.addSlot(FrameSlotKind.Object, name, null)

      names[name] = slot

      return slot
    }

    private fun capture(name: String, slotInParent: Int): Int {
      val localSlot = defineName(name)

      captures.add(NameCapture(fromSlot = slotInParent, toSlot = localSlot))

      return localSlot
    }

    fun frameDescriptor(): FrameDescriptor = frameBuilder.build()
  }

  companion object {
    fun newFunction(argumentNames: List<String>) = FunctionScope(
      frameBuilder = FrameDescriptor.newBuilder(),
      parent = null,
      argumentNames,
      names = mutableMapOf(),
      captures = mutableListOf()
    )
  }

  abstract fun defineName(name: String): Int
  abstract fun accessName(name: String): Int?

  fun newChildBlock() = BlockScope(frameBuilder, this, mutableMapOf())
  fun newChildBlockWithName(name: String): Pair<LexicalScope.BlockScope, Int> {
    val scope = BlockScope(frameBuilder, this, mutableMapOf())
    val slot = scope.defineName(name)

    return Pair(scope, slot)
  }

  fun newChildFunction(argumentNames: List<String>) = FunctionScope(
    frameBuilder = FrameDescriptor.newBuilder(),
    parent = this,
    argumentNames,
    names = mutableMapOf(),
    captures = mutableListOf()
  )
}
