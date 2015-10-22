package org.hello;

import org.joda.time.LocalTime;

public class Util {

  public static String time() {
    LocalTime currentTime = new LocalTime();
    return "" + currentTime;
  }

}