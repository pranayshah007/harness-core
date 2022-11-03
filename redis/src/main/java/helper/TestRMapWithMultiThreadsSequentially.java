package helper;

import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RedissonClient;
import util.DecrementCountThread;
import util.IncrementCountThread;
import util.ReadFromRMapThread;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TestRMapWithMultiThreadsSequentially {
    RLocalCachedMap<String, Integer> localCachedMap;

    public TestRMapWithMultiThreadsSequentially(RedissonClient redissonClient) {
        localCachedMap = redissonClient.getLocalCachedMap("task", LocalCachedMapOptions.defaults());
        System.out.println("======  Test 1 (10 iteration, 2 updates and one read, 1 keys)===================");
        test1_withOneKey(localCachedMap, "del4");
        test1_withOneKey(localCachedMap, "del5");


        System.out.println("======  Test 2 (200 iteration, 2 updates and one read, 1 keys)===================");
        test2_WithOneKey(localCachedMap, "del6");
        test2_WithOneKey(localCachedMap, "del7");


        System.out.println("======  Test 3 (6000 iteration, 1 update and 1 read, 1 keys)===================");
        test3_withOneKey(localCachedMap, "del11");
        test3_withOneKey(localCachedMap, "del12");



        System.out.println("======  Test 4 (6000 iteration, 2 updates and 1 read, 1 keys)===================");
        test4_withOneKey(localCachedMap, "del21");
        test4_withOneKey(localCachedMap, "del22");


        System.out.println("======  Test 5 (6000 iteration, 2 updates and 2 reads, 1 keys)===================");
        test5_withOneKey(localCachedMap, "del21");
        test5_withOneKey(localCachedMap, "del22");

        System.out.println("======  Test 6 (10 iteration, 2 updates and one read, 5 keys)===================");
        test1_withFiveKeys(localCachedMap, Arrays.asList("d51", "d52","d53","d54","d55"));
        test1_withFiveKeys(localCachedMap, Arrays.asList("d56", "d57","d58","d59","d50"));


        System.out.println("======  Test 7 (200 iteration, 2 updates and one read,5 keys)===================");
        test2_WithFiveKeys(localCachedMap, Arrays.asList("d61", "d62","d63","d64","d65"));
        test2_WithFiveKeys(localCachedMap, Arrays.asList("d66", "d67","d68","d69","d60"));



        System.out.println("======  Test 8 (6000 iteration, 2 updates and 1 read, 5 keys)===================");
        test4_withFiveKeys(localCachedMap, Arrays.asList("d71", "d72","d73","d74","d75"));
        test4_withFiveKeys(localCachedMap, Arrays.asList("d76", "d77","d78","d79","d80"));


        System.out.println("======  Test 9 (6000 iteration, 2 updates and 2 reads, 5 keys)===================");
        test5_withFiveKeys(localCachedMap, Arrays.asList("d71", "d72","d73","d74","d75"));
        test5_withFiveKeys(localCachedMap, Arrays.asList("d76", "d77","d78","d79","d80"));



        //test1_withFiveKeys
        System.out.println("=========================================");
        //System.out.println("Map values " + localCachedMap.entrySet());
    }

    public Set<Map.Entry<String, Integer>> getMapEntry(){
        return localCachedMap.entrySet();
    }


    private void test1_withOneKey(RLocalCachedMap<String, Integer> localCachedMap, String key){
        long startTime = System.currentTimeMillis();
        int i=1;
        while (i<10){
            new IncrementCountThread(localCachedMap,key).run();
            new ReadFromRMapThread(localCachedMap, key).run();
            i++;
        }

        int j=1;
        while (j<10){
            new DecrementCountThread(localCachedMap,key).run();
            j++;
        }
        long timeDiff = System.currentTimeMillis()- startTime;
        System.out.println("Total time taken in ms : " + timeDiff);
        //System.out.println("After decrementing " + localCachedMap.get("del2"));
    }
    private void test2_WithOneKey(RLocalCachedMap<String, Integer> localCachedMap, String key){
        long startTime = System.currentTimeMillis();
        int i=1;
        while (i<15000){
            new IncrementCountThread(localCachedMap,key).run();
            new ReadFromRMapThread(localCachedMap, key).run();
            i++;
        }

        int j=1;
        while (j<10000){
            new DecrementCountThread(localCachedMap,key).run();
            j++;
        }
        long timeDiff = System.currentTimeMillis()- startTime;
        System.out.println("Total time taken in ms : " + timeDiff);
    }
    private void test3_withOneKey(RLocalCachedMap<String, Integer> localCachedMap, String key) {
        long startTime = System.currentTimeMillis();
        int i = 1;
        while (i < 6000) {
            new IncrementCountThread(localCachedMap, key).run();
            new ReadFromRMapThread(localCachedMap, key).run();
            i++;
        }
        long timeDiff = System.currentTimeMillis()- startTime;
        System.out.println("Total time taken in ms : " + timeDiff);
    }
    private void test4_withOneKey(RLocalCachedMap<String, Integer> localCachedMap, String key) {
        long startTime = System.currentTimeMillis();
        int i = 1;
        while (i < 6000) {
            new IncrementCountThread(localCachedMap, key).run();
            new ReadFromRMapThread(localCachedMap, key).run();
            i++;
        }
        int j=1;
        while (j<6000){
            new DecrementCountThread(localCachedMap,key).run();
            j++;
        }
        long timeDiff = System.currentTimeMillis()- startTime;
        System.out.println("Total time taken in ms : " + timeDiff);
    }
    private void test5_withOneKey(RLocalCachedMap<String, Integer> localCachedMap, String key) {
        long startTime = System.currentTimeMillis();
        int i = 1;
        while (i < 6000) {
            new IncrementCountThread(localCachedMap, key).run();
            new ReadFromRMapThread(localCachedMap, key).run();
            i++;
        }
        int j=1;
        while (j<6000){
            new DecrementCountThread(localCachedMap,key).run();
            new ReadFromRMapThread(localCachedMap, key).run();
            j++;
        }
        long timeDiff = System.currentTimeMillis()- startTime;
        System.out.println("Total time taken in ms : " + timeDiff);
    }



    //2 keys


    private void test1_withFiveKeys(RLocalCachedMap<String, Integer> localCachedMap, List<String> keys){
        long startTime = System.currentTimeMillis();
        int i=1;
        while (i<10){
            keys.forEach(key -> new IncrementCountThread(localCachedMap,key).run());
            keys.forEach(key -> new ReadFromRMapThread(localCachedMap,key).run());
            i++;
        }

        int j=1;
        while (j<9){
            keys.forEach(key -> new DecrementCountThread(localCachedMap,key).run());
            j++;
        }
        long timeDiff = System.currentTimeMillis()- startTime;
        System.out.println("Total time taken in ms : " + timeDiff);
    }
    private void test2_WithFiveKeys(RLocalCachedMap<String, Integer> localCachedMap, List<String> keys){
        long startTime = System.currentTimeMillis();
        int i=1;
        while (i<200){
            keys.forEach(key -> new IncrementCountThread(localCachedMap,key).run());
            keys.forEach(key -> new ReadFromRMapThread(localCachedMap,key).run());
            i++;
        }

        int j=1;
        while (j<150){
            keys.forEach(key -> new DecrementCountThread(localCachedMap,key).run());
            j++;
        }
        long timeDiff = System.currentTimeMillis()- startTime;
        System.out.println("Total time taken in ms : " + timeDiff);
    }
    private void test3_withFiveKeys(RLocalCachedMap<String, Integer> localCachedMap, List<String> keys) {
        long startTime = System.currentTimeMillis();
        int i = 1;
        while (i < 6000) {
            keys.forEach(key -> new IncrementCountThread(localCachedMap,key).run());
            keys.forEach(key -> new ReadFromRMapThread(localCachedMap,key).run());
            i++;
        }
        long timeDiff = System.currentTimeMillis()- startTime;
        System.out.println("Total time taken in ms : " + timeDiff);
    }
    private void test4_withFiveKeys(RLocalCachedMap<String, Integer> localCachedMap, List<String> keys) {
        long startTime = System.currentTimeMillis();
        int i = 1;
        while (i < 6000) {
            keys.forEach(key -> new IncrementCountThread(localCachedMap,key).run());
            keys.forEach(key -> new ReadFromRMapThread(localCachedMap,key).run());
            i++;
        }
        int j=1;
        while (j<6000){
            keys.forEach(key -> new DecrementCountThread(localCachedMap,key).run());
            j++;
        }
        long timeDiff = System.currentTimeMillis()- startTime;
        System.out.println("Total time taken in ms : " + timeDiff);
    }
    private void test5_withFiveKeys(RLocalCachedMap<String, Integer> localCachedMap, List<String> keys) {
        long startTime = System.currentTimeMillis();
        int i = 1;
        while (i < 6000) {
            keys.forEach(key -> new IncrementCountThread(localCachedMap,key).run());
            keys.forEach(key -> new ReadFromRMapThread(localCachedMap,key).run());
            i++;
        }
        int j=1;
        while (j<6000){
            keys.forEach(key -> new DecrementCountThread(localCachedMap,key).run());
            keys.forEach(key -> new ReadFromRMapThread(localCachedMap,key).run());
            j++;
        }
        long timeDiff = System.currentTimeMillis()- startTime;
        System.out.println("Total time taken in ms : " + timeDiff);
    }

}
