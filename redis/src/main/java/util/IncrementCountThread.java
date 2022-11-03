package util;

import org.redisson.api.RLocalCachedMap;

public class IncrementCountThread implements Runnable {
  RLocalCachedMap<String, Integer> rLocalCachedMap;
  String key;

  public IncrementCountThread(RLocalCachedMap rLocalCachedMap, String key) {
    this.rLocalCachedMap = rLocalCachedMap;
    this.key = key;
  }

  @Override
  public void run() {
    rLocalCachedMap.put(key,rLocalCachedMap.getOrDefault(key, 0) + 1);
  }
}
