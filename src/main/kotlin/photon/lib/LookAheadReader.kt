package photon.lib

data class LookAheadReaderBuffer<T>(
  private val producer: () -> T,
  private val buffer: ArrayDeque<T>
) {
  var bufferPointer = 0
  var readingFromBuffer = true

  fun nextToken(): T {
    if (readingFromBuffer && bufferPointer < buffer.size) {
      val token = buffer[bufferPointer]

      bufferPointer += 1

      return token
    } else {
      readingFromBuffer = false

      val token = producer()

      buffer.add(token)

      return token
    }
  }
}

class LookAheadReader<T>(
  private val producer: () -> T
) {
  private val lookAheadBuffer = ArrayDeque<T>()

  fun next(): T {
    if (lookAheadBuffer.isNotEmpty()) {
      return lookAheadBuffer.removeFirst()
    } else {
      return producer()
    }
  }

  fun lookAhead() = LookAheadReaderBuffer<T>(producer, lookAheadBuffer)
}