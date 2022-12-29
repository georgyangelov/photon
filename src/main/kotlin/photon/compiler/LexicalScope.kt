package photon.compiler

import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlotKind

internal class LexicalScope private constructor(
  private val frameBuilder: FrameDescriptor.Builder,
  private val parent: LexicalScope?,
  private val names: MutableMap<String, Name>
) {
  data class Name(val isArgument: Boolean, val slotOrArgumentIndex: Int)

  companion object {
    fun newRoot(params: List<String>, frameBuilder: FrameDescriptor.Builder): LexicalScope {
      val names = mutableMapOf<String, Name>()

      for (param in params.withIndex()) {
        names[param.value] = Name(isArgument = true, slotOrArgumentIndex = param.index)
      }

      return LexicalScope(frameBuilder, null, names)
    }
  }

  fun newChild() = LexicalScope(frameBuilder, this, mutableMapOf())

  fun newChildWithName(name: String): Pair<LexicalScope, Int> {
    val scope = LexicalScope(frameBuilder, this, mutableMapOf())
    val slot = scope.defineNew(name)

    return Pair(scope, slot)
  }

  fun defineNew(name: String): Int {
    val slot = frameBuilder.addSlot(FrameSlotKind.Object, name, null)

    names[name] = Name(isArgument = false, slot)

    return slot
  }

  fun find(name: String): Name? = names[name] ?: parent?.find(name)

  fun frameDescriptor(): FrameDescriptor = frameBuilder.build()
}