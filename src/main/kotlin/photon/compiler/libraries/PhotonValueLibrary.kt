package photon.compiler.libraries

import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.library.GenerateLibrary
import com.oracle.truffle.api.library.GenerateLibrary.Abstract
import com.oracle.truffle.api.library.Library
import photon.compiler.core.Type
import photon.compiler.types.IntTypeDefaultLibraryExports
import photon.compiler.types.StringTypeDefaultLibraryExports

@GenerateLibrary
@GenerateLibrary.DefaultExport(IntTypeDefaultLibraryExports::class)
@GenerateLibrary.DefaultExport(StringTypeDefaultLibraryExports::class)
abstract class PhotonValueLibrary: Library() {
  // TODO: Do I need this at all?
  open fun isPhotonValue(receiver: Any) = false

  /**
   * This is only supposed to be called from partial evaluation context
   * as we know the type otherwise.
   */
  @Abstract(ifExported = ["isPhotonValue"])
  open fun type(receiver: Any): Type = throw UnsupportedMessageException.create()
}