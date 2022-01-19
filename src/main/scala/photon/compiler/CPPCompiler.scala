package photon.compiler

import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import scala.sys.process.Process

class CPPCompiler(
  private[this] val code: String
) {
  def format: String = {
//    val codeStream = new ByteArrayInputStream(Charset.forName("UTF-8").encode(code).array)
    val codeStream = new ByteArrayInputStream(code.getBytes)

    val processBuilder = Process("wsl clang-format") #< codeStream

    processBuilder.!!
  }
}
