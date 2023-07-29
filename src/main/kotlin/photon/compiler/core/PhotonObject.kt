package photon.compiler.core

import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import photon.compiler.libraries.PhotonValueLibrary

@ExportLibrary(PhotonValueLibrary::class)
@ExportLibrary(InteropLibrary::class)
abstract class PhotonObject(private val type: Type?): TruffleObject {
  @ExportMessage
  fun isPhotonValue() = true

  @ExportMessage
  open fun type(): Type = type ?: RootType

  @ExportMessage
  fun hasMembers() = true

  @ExportMessage
  fun getMembers(includeInternal: Boolean): Any {
    return type().methods.keys.sorted()
  }

  @ExportMessage
  fun isMemberInvocable(member: String): Boolean {
    val method = type().getMethod(member, null)

    return method != null
  }

  @ExportMessage
  fun invokeMember(member: String, vararg arguments: Any): Any {
    // TODO: Correct argTypes
    val method = type().getMethod(member, null)
    // TODO: Correct error for `invokeMember`
      ?: throw RuntimeException("Cannot invoke member $member as it's missing")

    return method.call(this, *arguments)
  }

  // TODO: Support template functions by specifying the argTypes
  @ExportMessage
  fun isExecutable() = type().getMethod("call", null) != null

  @ExportMessage
  fun execute(vararg arguments: Any): Any {
    // TODO: Support template functions by specifying the argTypes
    return type().getMethod("call", null)!!.call(this, *arguments)
  }
}