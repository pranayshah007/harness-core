/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration.common;

import static io.harness.eraro.mongo.MongoError.DUPLICATE_KEY;
import static io.harness.rule.OwnerRule.GEORGE;

import static software.wings.beans.Idempotent.SUCCEEDED;
import static software.wings.beans.Idempotent.TENTATIVE;

import static java.time.Duration.ofHours;
import static java.time.Duration.ofMillis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.category.element.UnitTests;
import io.harness.distribution.idempotence.IdempotentId;
import io.harness.distribution.idempotence.IdempotentLock;
import io.harness.distribution.idempotence.IdempotentRegistry;
import io.harness.distribution.idempotence.IdempotentResult;
import io.harness.distribution.idempotence.UnableToRegisterIdempotentOperationException;
import io.harness.rule.Owner;
import io.harness.threading.Concurrent;

import software.wings.WingsBaseTest;
import software.wings.beans.Idempotent;
import software.wings.common.MongoIdempotentRegistry;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.mongodb.MongoCommandException;
import java.security.SecureRandom;
import java.util.ArrayList;
import lombok.Builder;
import lombok.Value;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MongoIdempotentRegistryTest extends WingsBaseTest {
  @Inject WingsPersistence wingsPersistence;

  @Value
  @Builder
  private static class TestIdempotentResult implements IdempotentResult {
    private String value;
  }

  @Inject MongoIdempotentRegistry<TestIdempotentResult> idempotentRegistry;

  IdempotentId id = new IdempotentId("foo");
  IdempotentId dataId = new IdempotentId("data");

  public void concurrencyTest(IdempotentRegistry idempotentRegistry) {
    final ArrayList<Integer> integers = new ArrayList<>();
    SecureRandom random = new SecureRandom();

    Concurrent.test(1, i -> {
      // We need at least one thread to execute positive scenario, else the test will fail
      if (i == 0 || random.nextBoolean()) {
        try (IdempotentLock idempotent = IdempotentLock.create(id, idempotentRegistry)) {
          if (idempotent.alreadyExecuted()) {
            return;
          }

          integers.add(1);
          idempotent.succeeded(TestIdempotentResult.builder().value("foo").build());
        } catch (UnableToRegisterIdempotentOperationException e) {
          // do nothing
        }
      } else {
        try (IdempotentLock idempotent = IdempotentLock.create(id, idempotentRegistry)) {
        } catch (UnableToRegisterIdempotentOperationException e) {
          // do nothing
        }
      }
    });

    assertThat(integers).hasSize(1);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testMongoRegisterNewAssumptions() {
    wingsPersistence.delete(Idempotent.class, id.getValue());

    Idempotent previousIdempotent = wingsPersistence.findAndModify(idempotentRegistry.query(id),
        idempotentRegistry.registerUpdateOperation(IdempotentLock.defaultTTL), MongoIdempotentRegistry.registerOptions);

    assertThat(previousIdempotent).isNull();
    assertThat(wingsPersistence.get(Idempotent.class, id.getValue()).getState()).isEqualTo(TENTATIVE);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testMongoRegisterTentativeAssumptions() {
    Idempotent tentativeIdempotent = Idempotent.builder().uuid(id.getValue()).state(TENTATIVE).build();
    wingsPersistence.save(tentativeIdempotent);

    Idempotent previousIdempotent = wingsPersistence.findAndModify(idempotentRegistry.query(id),
        idempotentRegistry.registerUpdateOperation(IdempotentLock.defaultTTL), MongoIdempotentRegistry.registerOptions);

    assertThat(previousIdempotent.getState()).isEqualTo(TENTATIVE);
    assertThat(wingsPersistence.get(Idempotent.class, id.getValue()).getState()).isEqualTo(TENTATIVE);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testMongoRegisterSucceededAssumptions() {
    Idempotent doneIdempotent = Idempotent.builder().uuid(id.getValue()).state(SUCCEEDED).build();
    wingsPersistence.save(doneIdempotent);

    assertThatThrownBy(()
                           -> wingsPersistence.findAndModify(idempotentRegistry.query(id),
                               idempotentRegistry.registerUpdateOperation(IdempotentLock.defaultTTL),
                               MongoIdempotentRegistry.registerOptions))
        .isInstanceOf(MongoCommandException.class)
        .hasMessageContaining("E" + DUPLICATE_KEY.getErrorCode() + " ");

    assertThat(wingsPersistence.get(Idempotent.class, id.getValue()).getState()).isEqualTo(SUCCEEDED);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testMongoUnregisterMissingAssumptions() {
    wingsPersistence.delete(Idempotent.class, id.getValue());
    wingsPersistence.findAndModify(idempotentRegistry.query(id), idempotentRegistry.unregisterUpdateOperation(),
        MongoIdempotentRegistry.unregisterOptions);
    assertThat(wingsPersistence.get(Idempotent.class, id.getValue())).isNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testMongoUnregisterTentativeAssumptions() {
    Idempotent tentativeIdempotent = Idempotent.builder().uuid(id.getValue()).state(TENTATIVE).build();
    wingsPersistence.save(tentativeIdempotent);

    wingsPersistence.findAndModify(idempotentRegistry.query(id), idempotentRegistry.unregisterUpdateOperation(),
        MongoIdempotentRegistry.unregisterOptions);
    assertThat(wingsPersistence.get(Idempotent.class, id.getValue())).isNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testMongoUnregisterSucceededAssumptions() {
    Idempotent doneIdempotent = Idempotent.builder().uuid(id.getValue()).state(SUCCEEDED).build();
    wingsPersistence.save(doneIdempotent);

    wingsPersistence.findAndModify(idempotentRegistry.query(id), idempotentRegistry.unregisterUpdateOperation(),
        MongoIdempotentRegistry.unregisterOptions);

    assertThat(wingsPersistence.get(Idempotent.class, id.getValue()).getState()).isEqualTo(SUCCEEDED);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testConcurrency() throws InterruptedException {
    wingsPersistence.delete(Idempotent.class, id.getValue());
    concurrencyTest(idempotentRegistry);
  }

  int index;

  public String operation(IdempotentId id) {
    try (IdempotentLock<TestIdempotentResult> idempotent = IdempotentLock.create(id, idempotentRegistry)) {
      if (idempotent.alreadyExecuted()) {
        return idempotent.getResult().getValue();
      }

      String result = id.getValue() + ": result " + ++index;
      idempotent.succeeded(TestIdempotentResult.builder().value(result).build());
      return result;
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testResult() throws InterruptedException, UnableToRegisterIdempotentOperationException {
    wingsPersistence.delete(Idempotent.class, dataId.getValue());
    assertThat(operation(dataId)).isEqualTo("data: result 1");
    assertThat(operation(dataId)).isEqualTo("data: result 1");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testTimeout() throws InterruptedException, UnableToRegisterIdempotentOperationException {
    wingsPersistence.delete(Idempotent.class, id.getValue());
    IdempotentLock<TestIdempotentResult> idempotentLock = IdempotentLock.create(id, idempotentRegistry);
    assertThatThrownBy(() -> IdempotentLock.create(id, idempotentRegistry, ofMillis(200), ofMillis(100), ofHours(1)));
    idempotentLock.close();
  }
}
