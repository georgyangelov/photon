#![feature(test)]

extern crate photon;
extern crate test;

use photon::testing::*;
use test::Bencher;

#[bench]
fn bench_simple_lex(b: &mut Bencher) {
    b.iter(|| lex("
        def fib(n: Int)
          if n <= 2
            n
          else
            fib(n - 2) + fib(n - 1)
          end
        end

        def main
          puts \"Hello world!\"
          puts \"Fib(42) is \", fib(42)
        end
    "));
}

#[bench]
fn bench_simple_parse(b: &mut Bencher) {
    b.iter(|| parse_all("
        def fib(n: Int)
          if n <= 2
            n
          else
            fib(n - 2) + fib(n - 1)
          end
        end

        def main
          puts \"Hello world!\"
          puts \"Fib(42) is \", fib(42)
        end
    "));
}
