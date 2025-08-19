package org.example.gradle;

import org.jspecify.annotations.NonNull;

public class Main {
  public static void main(String[] args) {
    System.out.println(new Main().getGreeting());
  }

  @NonNull
  public String getGreeting() {
    return "Hello World!";
  }
}
