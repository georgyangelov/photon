package photon.core

data class Location(
  val fileName: String,
  val startLine: Int,
  val startCol: Int,
  val endLine: Int,
  val endCol: Int
) {
  companion object {
    fun beginningOfFile(fileName: String): Location {
      return Location(fileName, 0, 0, 0, 0)
    }

    fun at(fileName: String, line: Int, column: Int): Location {
      return Location(fileName, line, column, line, column)
    }
  }

  fun extendWith(other: Location): Location {
    if (fileName != other.fileName) {
      throw IllegalArgumentException("Locations are not of the same file")
    }

    return copy(endLine = other.endLine, endCol = other.endCol)
  }

  override fun toString(): String {
    if (startLine == endLine && startCol == endCol)
      return "$fileName:$startLine:$startCol"
    else
      return "$fileName:(from $startLine:$startCol to $endLine:$endCol)"
  }
}