package photon

import org.scalatest._

import photon.TestHelpers._

class CompilerTest extends FunSuite {
  test("compiles simple constants") {
    expectCompiled(
      "42",
      """
        #include <stdio.h>

        int main() {
          42;
          return 0;
        }
      """
    )
  }

  test("compiles simple lambda calls") {
    expectCompiled(
      """
         a = { 41 }.runTimeOnly.call

         a + 1
       """,
      """
        #include <stdio.h>

        int anon$a$1() {
          return 41;
        }

        int main() {
          int a = anon$a$1();

          a + 1;

          return 0;
        }
      """
    )
  }

  test("compiles lambdas into variables") {
    expectCompiled(
      """
         a = { 41 }.runTimeOnly

         a() + a() + 1
       """,
      """
        #include <stdio.h>

        int anon$a$1() {
          return 41;
        }

        int main() {
          int (*a)();
          a = &anon$a$1;

          a() + a() + 1;

          return 0;
        }
      """
    )
  }
}
