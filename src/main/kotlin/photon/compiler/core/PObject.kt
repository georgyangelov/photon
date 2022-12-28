package photon.compiler.core

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.*
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import java.lang.reflect.InvocationTargetException

@ExportLibrary(value = InteropLibrary::class, delegateTo = "object")
class PObject<T>(
  @JvmField val `object`: T,
  val typeObject: Type
): Value(), TruffleObject {
  override fun typeOf(frame: VirtualFrame): Type {
    return typeObject
  }

  override fun executeGeneric(frame: VirtualFrame): Any {
    return this
  }

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

  @ExportMessage
  @Throws(UnknownIdentifierException::class)
  fun invokeMember(member: String?, vararg arguments: Any?): Any {
    val method = typeObject.methods[member]

    if (method != null) {
      return try {
        method.invoke(null, *arguments)
      } catch (e: IllegalAccessException) {
        throw RuntimeException(e)
      } catch (e: InvocationTargetException) {
        throw RuntimeException(e)
      }
    }

    throw UnknownIdentifierException.create(member)
  }
}