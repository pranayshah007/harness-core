package util;

import org.redisson.api.RLocalCachedMap;

public class DecrementCountThread implements Runnable{
    RLocalCachedMap<String, Integer> rLocalCachedMap;
    String key;
    public DecrementCountThread(RLocalCachedMap rLocalCachedMap, String key) {
        this.rLocalCachedMap = rLocalCachedMap;
        this.key = key;
    }

    @Override
    public void run() {
        rLocalCachedMap.put(key,rLocalCachedMap.getOrDefault(key, 0) - 1);
    }
}
