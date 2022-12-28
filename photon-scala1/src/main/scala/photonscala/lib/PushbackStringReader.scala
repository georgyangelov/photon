package photonscala.lib

import java.io.{FilterReader, Reader}
import java.nio.CharBuffer

class PushbackStringReader(reader: Reader) extends FilterReader(reader) {
  var pushbackValue: Option[Int] = None

  override def read(): Int = {
    lock.synchronized {
      pushbackValue match {
        case Some(c) =>
          pushbackValue = None
          c
        case None => super.read()
      }
    }
  }

  def unread(c: Int): Unit = {
    pushbackValue = Some(c)
  }

  override def read(cbuf: Array[Char]): Int = throw new UnsupportedOperationException
  override def read(cbuf: Array[Char], off: Int, len: Int): Int = throw new UnsupportedOperationException
  override def read(target: CharBuffer): Int = throw new UnsupportedOperationException
}
