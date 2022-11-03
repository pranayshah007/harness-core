import helper.TestRMapWithMultiThreadsSequentially;
import org.redisson.Redisson;
import org.redisson.api.LocalCachedMapOptions;
import org.redisson.api.RLocalCachedMap;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import util.DecrementCountThread;
import util.IncrementCountThread;
import util.ReadFromRMapThread;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class RedisBenchmarkingApplication {

    public static void main(String... args) throws IOException {
        try {
            System.out.println("hello world");
            Config config = new Config();
            config.useSingleServer().setAddress("redis://localhost:6379");
            RedissonClient redisson = Redisson.create(config);

            TestRMapWithMultiThreadsSequentially test1 = new TestRMapWithMultiThreadsSequentially(redisson);

            Future future = (Future) test1.getMapEntry();
            System.out.println("Test 1: " + future.get());


            //TestRMapWithMultiThreadsSequentially test2 = new TestRMapWithMultiThreadsSequentially(redisson);
            //Future future1 = (Future) test2.getMapEntry();
            //System.out.println("Test 2: " + future1.get());


            redisson.shutdown();
        } catch (Exception e){

        }
    }








    private void test_def(RedissonClient redisson){
        RMap<String, Integer> map =  redisson.getMap("myMap");
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);

        boolean contains = map.containsKey("a");

        Integer value = map.get("c");
        Integer updatedValue = map.addAndGet("a", 32);

        Integer valueSize = map.valueSize("c");

        Set keys = new HashSet();
        keys.add("a");
        keys.add("b");
        keys.add("c");
        Map mapSlice = map.getAll(keys);

        // use read* methods to fetch all objects
        Set allKeys = map.readAllKeySet();
        Collection allValues = map.readAllValues();
        Set allEntries = map.readAllEntrySet();

        // use fast* methods when previous value is not required
        boolean isNewKey = map.fastPut("a", 100);
        boolean isNewKeyPut = map.fastPutIfAbsent("d", 33);
        long removedAmount = map.fastRemove("b");


        System.out.println(allEntries);
    }
}
