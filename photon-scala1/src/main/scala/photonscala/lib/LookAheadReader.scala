package photonscala.lib

import scala.collection.mutable

class LookAheadReaderBuffer[T](
  private val producer: () => T,
  private val buffer: mutable.ArrayDeque[T]
) {
  var bufferPointer = 0
  var readingFromBuffer = true

  def nextToken(): T = {
    if (readingFromBuffer && bufferPointer < buffer.length) {
      val token = buffer(bufferPointer)

      bufferPointer += 1

      token
    } else {
      readingFromBuffer = false

      val token = producer()

      buffer.append(token)

      token
    }
  }
}

class LookAheadReader[T](private val producer: () => T) {
  private val lookAheadBuffer = mutable.ArrayDeque[T]()

  def next(): T = {
    if (lookAheadBuffer.nonEmpty) {
      lookAheadBuffer.removeHead()
    } else {
      producer.apply()
    }
  }

  def lookAhead() = new LookAheadReaderBuffer(producer, lookAheadBuffer)
}