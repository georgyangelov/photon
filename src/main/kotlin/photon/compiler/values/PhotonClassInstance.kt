package photon.compiler.values

import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import photon.compiler.core.EvalMode

@ExportLibrary(InteropLibrary::class)
abstract class PhotonClassInstance(
  private val type: PhotonClass
): TruffleObject {
  @ExportMessage
  fun hasMembers() = true

  @ExportMessage
  fun getMembers(includeInternal: Boolean): Any {
    return type.methods.keys.sorted()
  }

  @ExportMessage
  fun isMemberInvocable(member: String): Boolean {
    val method = type.getMethod(member, null)

    return method != null
  }

  @ExportMessage
  fun invokeMember(member: String, vararg arguments: Any): Any {
    val method = type.getMethod(member, null)
      ?: throw RuntimeException("Cannot invoke member $member as it's missing")

    // TODO: Pass correct EvalMode
    return method.call(EvalMode.CompileTimeOnly, this, arguments)
  }
}

interface PhotonStaticObjectFactory {
  fun create(type: PhotonClass): Any
}