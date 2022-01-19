//#include <iostream>
#include <unistd.h>

inline int add(int a, int b) {
  return a + b;
}

int main() {
  //  std::cout << "Hello world!\n";
  const char output[] = "Hello world!\n";

  write(0, &output[0], sizeof(output));

  return 0;
}