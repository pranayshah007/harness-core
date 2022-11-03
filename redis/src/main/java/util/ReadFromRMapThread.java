package util;

import org.redisson.api.RLocalCachedMap;

public class ReadFromRMapThread implements Runnable {
  RLocalCachedMap rLocalCachedMap;
  String key;


  public ReadFromRMapThread(RLocalCachedMap rLocalCachedMap, String key) {
    this.rLocalCachedMap = rLocalCachedMap;
    this.key = key;
  }

  @Override
  public void run() {
    rLocalCachedMap.get(key);
    //System.out.println("Key value is " + rLocalCachedMap.get(key));
  }
}
