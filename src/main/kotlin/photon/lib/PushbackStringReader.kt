package photon.lib

import java.io.FilterReader
import java.io.Reader
import java.nio.CharBuffer

class PushbackStringReader(val reader: Reader) : FilterReader(reader) {
  var pushbackValue: Int? = null

  override fun read(): Int {
    synchronized(lock) {
      val c = pushbackValue

      if (c != null) {
        pushbackValue = null
        return c
      } else {
        return super.read()
      }
    }
  }

  fun unread(c: Int) {
    pushbackValue = c
  }

  override fun read(cbuf: CharArray): Int = throw UnsupportedOperationException()
  override fun read(cbuf: CharArray, off: Int, len: Int): Int = throw UnsupportedOperationException()
  override fun read(target: CharBuffer): Int = throw UnsupportedOperationException()
}