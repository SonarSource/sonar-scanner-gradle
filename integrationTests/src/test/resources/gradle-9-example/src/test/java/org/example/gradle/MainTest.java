package org.example.gradle;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MainTest {

  @Test
  void test_main() {
    Assertions.assertEquals("Hello World!", new Main().getGreeting());
  }
}
