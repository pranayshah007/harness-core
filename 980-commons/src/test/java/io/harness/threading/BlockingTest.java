package io.harness.threading;

import static io.harness.rule.OwnerRule.GEORGE;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class BlockingTest extends CategoryTest {
  private static class SleepQueuePolicy implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
      try {
        log.info("Put is blocking size is {}", executor.getQueue().size());
        executor.getQueue().put(r);
        log.info("Item has been added in the queue");
      } catch (InterruptedException ex) {
        // should never happen since we never wait
        throw new RejectedExecutionException(ex);
      }
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testBlocking() throws ExecutionException, InterruptedException {
    ThreadPoolExecutor executor = ThreadPool.create(1, 1, 20L, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("blocker-test-%d").build(), 1, new SleepQueuePolicy());
    CompletableFuture<Object> f1 = CompletableFuture.supplyAsync(() -> {
      Morpheus.sleep(Duration.ofSeconds(10));
      return null;
    }, executor);

    CompletableFuture<Object> f2 = CompletableFuture.supplyAsync(() -> {
      Morpheus.sleep(Duration.ofSeconds(10));
      return null;
    }, executor);

    CompletableFuture<Object> f3 = CompletableFuture.supplyAsync(() -> {
      Morpheus.sleep(Duration.ofSeconds(10));
      return null;
    }, executor);

    log.info("code reached here");
    CompletableFuture<Void> f4 = CompletableFuture.allOf(f1, f2);
    f3.get();
  }
}
