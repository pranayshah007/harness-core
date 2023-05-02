/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iterator;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.iterator.PersistenceIterator.ProcessMode.LOOP;
import static io.harness.iterator.PersistenceIterator.ProcessMode.PUMP;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.IRREGULAR_SKIP_MISSED;
import static io.harness.rule.OwnerRule.GEORGE;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.joor.Reflect.on;

import io.harness.PersistenceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.iterator.PersistenceIterator.ProcessMode;
import io.harness.iterator.TestIrregularIterableEntity.TestIrregularIterableEntityKeys;
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
import com.mongodb.BasicDBObject;
import dev.morphia.query.FilterOperator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class PersistenceIrregularIteratorTest extends PersistenceTestBase {
  @Inject private HPersistence persistence;
  @Inject private MorphiaPersistenceProvider<TestIrregularIterableEntity> persistenceProvider;
  @Inject private QueueController queueController;
  private ExecutorService executorService = ThreadPool.create(4, 15, 1, TimeUnit.SECONDS);

  class TestHandler implements Handler<TestIrregularIterableEntity> {
    @Override
    public void handle(TestIrregularIterableEntity entity) {
      Morpheus.sleep(ofSeconds(1));
      log.info("Handle {}", entity.getUuid());
    }
  }

  public PersistenceIterator<TestIrregularIterableEntity> iterator(
      ProcessMode mode, MorphiaFilterExpander<TestIrregularIterableEntity> filterExpander) {
    PersistenceIterator<TestIrregularIterableEntity> iterator =
        MongoPersistenceIterator
            .<TestIrregularIterableEntity, MorphiaFilterExpander<TestIrregularIterableEntity>>builder()
            .mode(mode)
            .iteratorName(this.getClass().getName())
            .clazz(TestIrregularIterableEntity.class)
            .fieldName(TestIrregularIterableEntityKeys.nextIterations)
            .targetInterval(ofSeconds(10))
            .acceptableNoAlertDelay(ofSeconds(1))
            .maximumDelayForCheck(ofSeconds(1))
            .executorService(executorService)
            .semaphore(new Semaphore(10))
            .handler(new TestHandler())
            .schedulingType(IRREGULAR_SKIP_MISSED)
            .persistenceProvider(persistenceProvider)
            .redistribute(true)
            .build();
    on(iterator).set("queueController", queueController);
    return iterator;
  }

  public PersistenceIterator<TestIrregularIterableEntity> iterator(ProcessMode mode) {
    return iterator(mode, null);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testPumpWithEmptyCollection() {
    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      PersistenceIterator<TestIrregularIterableEntity> pumpIterator = iterator(PUMP);
      assertThatCode(() -> { pumpIterator.process(); }).doesNotThrowAnyException();
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testLoopWithEmptyCollection() {
    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      PersistenceIterator<TestIrregularIterableEntity> iterator = iterator(LOOP);
      assertThatCode(() -> {
        Future<?> future1 = executorService.submit(() -> iterator.process());
        Morpheus.sleep(ofMillis(300));
        future1.cancel(true);
      }).doesNotThrowAnyException();
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testPumpNewItem() {
    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      PersistenceIterator<TestIrregularIterableEntity> pumpIterator = iterator(PUMP);
      TestIrregularIterableEntity iterableEntity = TestIrregularIterableEntity.builder().uuid(generateUuid()).build();
      persistence.save(iterableEntity);
      pumpIterator.process();

      TestIrregularIterableEntity testIrregularIterableEntity =
          persistence.get(TestIrregularIterableEntity.class, iterableEntity.getUuid());
      assertThat(testIrregularIterableEntity.getNextIterations()).hasSize(4);
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testPumpNewItemWithFilter() {
    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      PersistenceIterator<TestIrregularIterableEntity> pumpIterator =
          iterator(PUMP, query -> query.filter(TestIrregularIterableEntityKeys.name, "foo"));
      TestIrregularIterableEntity iterableEntity =
          TestIrregularIterableEntity.builder().name("foo").uuid(generateUuid()).build();
      persistence.save(iterableEntity);
      pumpIterator.process();

      TestIrregularIterableEntity testIrregularIterableEntity =
          persistence.get(TestIrregularIterableEntity.class, iterableEntity.getUuid());
      assertThat(testIrregularIterableEntity.getNextIterations()).hasSize(4);
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testPumpAfterSetToEmpty() {
    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      PersistenceIterator<TestIrregularIterableEntity> pumpIterator = iterator(PUMP);
      TestIrregularIterableEntity iterableEntity = TestIrregularIterableEntity.builder().uuid(generateUuid()).build();
      persistence.save(iterableEntity);

      persistence.update(iterableEntity,
          persistence.createUpdateOperations(TestIrregularIterableEntity.class)
              .set(TestIrregularIterableEntityKeys.nextIterations, emptyList()));

      pumpIterator.recoverAfterPause();
      pumpIterator.process();

      TestIrregularIterableEntity testIrregularIterableEntity =
          persistence.get(TestIrregularIterableEntity.class, iterableEntity.getUuid());
      assertThat(testIrregularIterableEntity.getNextIterations()).hasSize(4);
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testPumpAfterSetToEmptyWithFilter() {
    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      PersistenceIterator<TestIrregularIterableEntity> pumpIterator =
          iterator(PUMP, query -> query.filter(TestIrregularIterableEntityKeys.name, "foo"));
      TestIrregularIterableEntity iterableEntity =
          TestIrregularIterableEntity.builder().name("foo").uuid(generateUuid()).build();
      persistence.save(iterableEntity);

      persistence.update(iterableEntity,
          persistence.createUpdateOperations(TestIrregularIterableEntity.class)
              .set(TestIrregularIterableEntityKeys.nextIterations, emptyList()));

      pumpIterator.recoverAfterPause();
      pumpIterator.process();

      TestIrregularIterableEntity testIrregularIterableEntity =
          persistence.get(TestIrregularIterableEntity.class, iterableEntity.getUuid());
      assertThat(testIrregularIterableEntity.getNextIterations()).hasSize(4);
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testPumpAfterUnset() {
    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      PersistenceIterator<TestIrregularIterableEntity> pumpIterator = iterator(PUMP);
      TestIrregularIterableEntity iterableEntity = TestIrregularIterableEntity.builder().uuid(generateUuid()).build();
      persistence.save(iterableEntity);

      persistence.update(iterableEntity,
          persistence.createUpdateOperations(TestIrregularIterableEntity.class)
              .unset(TestIrregularIterableEntityKeys.nextIterations));

      pumpIterator.process();

      TestIrregularIterableEntity testIrregularIterableEntity =
          persistence.get(TestIrregularIterableEntity.class, iterableEntity.getUuid());
      assertThat(testIrregularIterableEntity.getNextIterations()).hasSize(4);
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testPumpAfterUnsetWithFilter() {
    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      PersistenceIterator<TestIrregularIterableEntity> pumpIterator =
          iterator(PUMP, query -> query.filter(TestIrregularIterableEntityKeys.name, "foo"));
      TestIrregularIterableEntity iterableEntity =
          TestIrregularIterableEntity.builder().name("foo").uuid(generateUuid()).build();
      persistence.save(iterableEntity);

      persistence.update(iterableEntity,
          persistence.createUpdateOperations(TestIrregularIterableEntity.class)
              .unset(TestIrregularIterableEntityKeys.nextIterations));

      pumpIterator.process();

      TestIrregularIterableEntity testIrregularIterableEntity =
          persistence.get(TestIrregularIterableEntity.class, iterableEntity.getUuid());
      assertThat(testIrregularIterableEntity.getNextIterations()).hasSize(4);
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testPumpAfterTakingOutAll() {
    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      PersistenceIterator<TestIrregularIterableEntity> pumpIterator = iterator(PUMP);
      TestIrregularIterableEntity iterableEntity =
          TestIrregularIterableEntity.builder().uuid(generateUuid()).nextIterations(asList(1L, 2L, 3L, 4L)).build();
      persistence.save(iterableEntity);

      do {
        TestIrregularIterableEntity testIrregularIterableEntity = persistence.findAndModifySystemData(
            persistence.createQuery(TestIrregularIterableEntity.class)
                .filter(TestIrregularIterableEntityKeys.uuid, iterableEntity.getUuid()),
            persistence.createUpdateOperations(TestIrregularIterableEntity.class)
                .removeFirst(TestIrregularIterableEntityKeys.nextIterations),
            HPersistence.returnNewOptions);
        if (isEmpty(testIrregularIterableEntity.getNextIterations())) {
          break;
        }
      } while (true);

      pumpIterator.recoverAfterPause();
      pumpIterator.process();

      TestIrregularIterableEntity testIrregularIterableEntity =
          persistence.get(TestIrregularIterableEntity.class, iterableEntity.getUuid());
      assertThat(testIrregularIterableEntity.getNextIterations()).hasSize(4);
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testPumpAfterSkipped() {
    try (MaintenanceGuard guard = new MaintenanceGuard(false)) {
      PersistenceIterator<TestIrregularIterableEntity> pumpIterator = iterator(PUMP);
      TestIrregularIterableEntity iterableEntity =
          TestIrregularIterableEntity.builder().uuid(generateUuid()).nextIterations(asList(1L, 2L, 3L, 4L)).build();
      persistence.save(iterableEntity);

      persistence.update(iterableEntity,
          persistence.createUpdateOperations(TestIrregularIterableEntity.class)
              .removeAll(TestIrregularIterableEntityKeys.nextIterations,
                  new BasicDBObject(FilterOperator.LESS_THAN_OR_EQUAL.val(), 5)));

      pumpIterator.recoverAfterPause();
      pumpIterator.process();

      TestIrregularIterableEntity testIrregularIterableEntity =
          persistence.get(TestIrregularIterableEntity.class, iterableEntity.getUuid());
      assertThat(testIrregularIterableEntity.getNextIterations()).hasSize(4);
    }
  }
}
