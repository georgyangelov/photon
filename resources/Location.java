package photon;case class Location(
        fileName: String,
        startLine: Int,
        startCol: Int,
        endLine: Int,
        endCol: Int
        ) {
        def extendWith(other: Location): Location = {
                if (fileName != other.fileName) {
                        throw new IllegalArgumentException("Locations are not of the same file")
                        }

                copy(endLine = other.endLine, endCol = other.endCol)
                }

        override def toString: String = {
                if (startLine == endLine && startCol == endCol)
                        s"$fileName:$startLine:$startCol"
                        else
                        s"$fileName:(from $startLine:$startCol to $endLine:$endCol)"
                }
        }
