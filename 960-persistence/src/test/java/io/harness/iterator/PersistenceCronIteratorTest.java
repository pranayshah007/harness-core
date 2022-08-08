/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iterator;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.iterator.PersistenceIterator.ProcessMode.LOOP;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.IRREGULAR_SKIP_MISSED;
import static io.harness.rule.OwnerRule.GEORGE;

import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.joor.Reflect.on;

import io.harness.PersistenceTestBase;
import io.harness.category.element.StressTests;
import io.harness.category.element.UnitTests;
import io.harness.iterator.TestCronIterableEntity.CronIterableEntityKeys;
import io.harness.maintenance.MaintenanceGuard;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.persistence.HPersistence;
import io.harness.queue.QueueController;
import io.harness.rule.Owner;
import io.harness.threading.Morpheus;
import io.harness.threading.ThreadPool;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class PersistenceCronIteratorTest extends PersistenceTestBase {
  @Inject private HPersistence persistence;
  @Inject private QueueController queueController;
  @Inject private MorphiaPersistenceProvider<TestCronIterableEntity> persistenceProvider;
  private ExecutorService executorService = ThreadPool.create(4, 15, 1, TimeUnit.SECONDS);

  class TestHandler implements Handler<TestCronIterableEntity> {
    @Override
    public void handle(TestCronIterableEntity entity) {
      Morpheus.sleep(ofSeconds(1));
      log.info("Handle {}", entity.getUuid());
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testExpandNextIterationsAllStay() {
    long now = System.currentTimeMillis();
    TestCronIterableEntity cronIterableEntity = TestCronIterableEntity.builder()
                                                    .nextIterations(new ArrayList<>(asList(now + 1000, now + 2000)))
                                                    .expression("* * * * * ?")
                                                    .build();

    List<Long> longs = cronIterableEntity.recalculateNextIterations("", true, now);

    assertThat(longs).hasSize(10);
    assertThat(longs.get(0)).isEqualTo(now + 1000);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testExpandNextIterationsAllOld() {
    long now = System.currentTimeMillis();
    TestCronIterableEntity cronIterableEntity = TestCronIterableEntity.builder()
                                                    .nextIterations(new ArrayList<>(asList(now - 2000, now - 1000)))
                                                    .expression("* * * * * ?")
                                                    .build();

    List<Long> longs = cronIterableEntity.recalculateNextIterations("", true, now);

    assertThat(longs).hasSize(10);
    assertThat(longs.get(0)).isNotEqualTo(now - 2000).isNotEqualTo(now - 1000);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testExpandNextIterationsTruncatedMatch() {
    long now = System.currentTimeMillis();
    TestCronIterableEntity cronIterableEntity =
        TestCronIterableEntity.builder()
            .nextIterations(new ArrayList<>(asList(now - 2000, now - 1000, now, now + 1000, now + 2000)))
            .expression("* * * * * ?")
            .build();

    List<Long> longs = cronIterableEntity.recalculateNextIterations("", true, now);

    assertThat(longs).hasSize(10);
    assertThat(longs.get(0)).isEqualTo(now + 1000);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testExpandNextIterationsTruncatedNoMatch() {
    long now = System.currentTimeMillis();
    TestCronIterableEntity cronIterableEntity =
        TestCronIterableEntity.builder()
            .nextIterations(new ArrayList<>(asList(now - 2000, now - 1000, now, now + 1000, now + 2000)))
            .expression("* * * * * ?")
            .build();

    List<Long> longs = cronIterableEntity.recalculateNextIterations("", true, now + 1);

    assertThat(longs).hasSize(10);
    assertThat(longs.get(0)).isEqualTo(now + 1000);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(StressTests.class)
  @Ignore("This is a stress test, we ignore it to not mess with the unit tests")
  public void testNextReturnsJustAdded() {
    PersistenceIterator<TestCronIterableEntity> iterator =
        MongoPersistenceIterator.<TestCronIterableEntity, MorphiaFilterExpander<TestCronIterableEntity>>builder()
            .mode(LOOP)
            .iteratorName(this.getClass().getName())
            .clazz(TestCronIterableEntity.class)
            .fieldName(CronIterableEntityKeys.nextIterations)
            .targetInterval(ofSeconds(10))
            .acceptableNoAlertDelay(ofSeconds(1))
            .maximumDelayForCheck(ofSeconds(1))
            .executorService(executorService)
            .semaphore(new Semaphore(10))
            .handler(new TestHandler())
            .schedulingType(IRREGULAR_SKIP_MISSED)
            .redistribute(true)
            .persistenceProvider(persistenceProvider)

            .build();
    on(iterator).set("queueController", queueController);

    assertThatCode(() -> {
      try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
        for (int i = 1; i <= 10; i++) {
          TestCronIterableEntity iterableEntity = TestCronIterableEntity.builder()
                                                      .uuid(generateUuid())
                                                      .expression(String.format("*/%d * * * * ? *", i))
                                                      .build();
          persistence.save(iterableEntity);
        }

        Future<?> future1 = executorService.submit(() -> iterator.process());

        Morpheus.sleep(ofSeconds(300));
        future1.cancel(true);
      }
    }).doesNotThrowAnyException();
  }
}
