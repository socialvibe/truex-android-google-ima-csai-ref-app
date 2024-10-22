package com.truex.googlereferenceapp;

import java.util.ArrayList;
import java.util.List;

class AdBreak {
  long contentPosition;
  boolean wasStarted;

  static public List<AdBreak> createAdBreaks(List<Float> cuePoints) {
    List<AdBreak> adBreaks = new ArrayList<>();
    for(Float adBreakPosition : cuePoints) {
      AdBreak adBreak = new AdBreak();
      adBreak.contentPosition = (long) (adBreakPosition * 1000);
      adBreak.wasStarted = false;
      adBreaks.add(adBreak);
    }
    return adBreaks;
  }
}
