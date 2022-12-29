package photon.compiler

import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind

data class Name(val type: Type, val slotOrArgumentIndex: Int) {
  enum class Type {
    /**
     * Local variable, in the function's call frame
     */
    Local,

    /**
     * An argument
     */
    Argument
  }
}

data class NameCapture(val from: Name, val toSlot: Int)

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

    override fun accessName(name: String): Name? {
      val localSlot = names[name]
      if (localSlot != null) {
        return Name(type = Name.Type.Local, slotOrArgumentIndex = localSlot)
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
    fun captures(): List<NameCapture> = captures

    override fun accessName(name: String): Name? {
      val slot = names[name]
      if (slot != null) {
        return Name(type = Name.Type.Local, slotOrArgumentIndex = slot)
      }

      val argumentIndex = argumentNames.indexOf(name)
      if (argumentIndex >= 0) {
        return Name(type = Name.Type.Argument, slotOrArgumentIndex = argumentIndex)
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

    private fun capture(name: String, nameFromParent: Name): Name {
      val localSlot = defineName(name)

      captures.add(NameCapture(nameFromParent, localSlot))

      return Name(type = Name.Type.Local, slotOrArgumentIndex = localSlot)
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
  abstract fun accessName(name: String): Name?

  fun newChildBlock() = BlockScope(frameBuilder, this, mutableMapOf())
  fun newChildBlockWithName(name: String): Pair<LexicalScope, Int> {
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
