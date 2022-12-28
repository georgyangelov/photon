package photon.compiler.core

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.*
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import photon.compiler.libraries.PhotonLibrary

@ExportLibrary(value = InteropLibrary::class, delegateTo = "value")
@ExportLibrary(value = PhotonLibrary::class)
class PObject<T>(
  @JvmField val value: T,
  private val typeObject: Type
): Value(), TruffleObject {
  override fun isOperation(): Boolean = false
  override fun typeOf(frame: VirtualFrame): Type = typeObject
  override fun executeGeneric(frame: VirtualFrame, evalMode: EvalMode): Any = this

  @ExportMessage
  fun hasMembers(): Boolean {
    return true
  }

  @ExportMessage
  @Throws(UnsupportedMessageException::class)
  fun getMembers(includeInternal: Boolean): Any? {
    return null
  }

  @ExportMessage
  fun isMemberInvocable(member: String?): Boolean {
    return typeObject.methods.containsKey(member)
  }

  @ExportMessage(library = InteropLibrary::class)
  @Throws(UnknownIdentifierException::class)
  fun invokeMember(member: String, vararg arguments: Any): Any {
    return invokeMemberWithEvalMode(EvalMode.RunTime, member, *arguments)
  }

  @ExportMessage(name = "invokeMember", library = PhotonLibrary::class)
  @Throws(UnknownIdentifierException::class)
  fun invokeMemberWithEvalMode(evalMode: EvalMode, member: String, vararg arguments: Any): Any {
    val method = typeObject.methods[member] ?: throw UnknownIdentifierException.create(member)

    return method.callMethod(arguments, evalMode)
  }
}