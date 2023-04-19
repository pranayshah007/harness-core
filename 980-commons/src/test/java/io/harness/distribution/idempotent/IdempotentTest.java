/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.distribution.idempotent;

import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofMillis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.distribution.idempotence.IdempotentId;
import io.harness.distribution.idempotence.IdempotentLock;
import io.harness.distribution.idempotence.IdempotentRegistry;
import io.harness.distribution.idempotence.IdempotentRegistry.State;
import io.harness.distribution.idempotence.IdempotentResult;
import io.harness.distribution.idempotence.InprocIdempotentRegistry;
import io.harness.distribution.idempotence.UnableToRegisterIdempotentOperationException;
import io.harness.rule.Owner;
import io.harness.threading.Concurrent;

import java.security.SecureRandom;
import java.util.ArrayList;
import lombok.Builder;
import lombok.Value;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class IdempotentTest extends CategoryTest {
  IdempotentId id = new IdempotentId("foo");

  @Value
  @Builder
  private static class BooleanIdempotentResult implements IdempotentResult {
    private Boolean value;
  }

  private static BooleanIdempotentResult TRUE = BooleanIdempotentResult.builder().value(Boolean.TRUE).build();

  private static IdempotentRegistry.Response<BooleanIdempotentResult> newResponse =
      IdempotentRegistry.Response.<BooleanIdempotentResult>builder().state(State.NEW).build();
  private static IdempotentRegistry.Response<BooleanIdempotentResult> doneResponse =
      IdempotentRegistry.Response.<BooleanIdempotentResult>builder().state(State.DONE).result(TRUE).build();

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testNewIdempotentFailed() {
    final IdempotentRegistry mockIdempotentRegistry = mock(IdempotentRegistry.class);

    when(mockIdempotentRegistry.register(any(), any())).thenReturn(newResponse);

    try (IdempotentLock idempotent = IdempotentLock.create(id, mockIdempotentRegistry)) {
      assertThat(idempotent).isNotNull();
    }

    verify(mockIdempotentRegistry).unregister(id);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testNewIdempotentSucceeded() {
    final IdempotentRegistry mockIdempotentRegistry = mock(IdempotentRegistry.class);

    when(mockIdempotentRegistry.register(any(), any())).thenReturn(newResponse);

    try (IdempotentLock idempotent = IdempotentLock.create(id, mockIdempotentRegistry)) {
      assertThat(idempotent).isNotNull();
      idempotent.succeeded(TRUE);
    }

    verify(mockIdempotentRegistry).finish(id, TRUE);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testFinishedIdempotent() {
    final IdempotentRegistry mockIdempotentRegistry = mock(IdempotentRegistry.class);

    when(mockIdempotentRegistry.register(any(), any())).thenReturn(doneResponse);

    try (IdempotentLock<BooleanIdempotentResult> idempotent = IdempotentLock.create(id, mockIdempotentRegistry)) {
      assertThat(idempotent).isNotNull();
      assertThat(idempotent.alreadyExecuted()).isTrue();
      assertThat(idempotent.getResult().getValue()).isTrue();
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testIdempotentAfterTtl() {
    final IdempotentRegistry<BooleanIdempotentResult> idempotentRegistry = new InprocIdempotentRegistry<>();
    try (IdempotentLock<BooleanIdempotentResult> idempotent =
             idempotentRegistry.create(id, ofMillis(1000), ofMillis(1), ofMillis(1500))) {
      assertThat(idempotent).isNotNull();
      assertThat(idempotent.alreadyExecuted()).isFalse();
      idempotent.succeeded(TRUE);
    }
    try (IdempotentLock<BooleanIdempotentResult> idempotent = idempotentRegistry.create(id)) {
      assertThat(idempotent).isNotNull();
      assertThat(idempotent.alreadyExecuted()).isTrue();
    }
    sleep(ofMillis(1510));
    try (IdempotentLock<BooleanIdempotentResult> idempotent = idempotentRegistry.create(id)) {
      assertThat(idempotent).isNotNull();
      assertThat(idempotent.alreadyExecuted()).isFalse();
    }
  }

  public void concurrencyTest(IdempotentRegistry idempotentRegistry) {
    final ArrayList<Integer> integers = new ArrayList<>();
    SecureRandom random = new SecureRandom();

    Concurrent.test(10, i -> {
      // We need at least one thread to execute positive scenario, else the test will fail
      if (i == 0 || random.nextBoolean()) {
        try (IdempotentLock idempotent = IdempotentLock.create(id, idempotentRegistry)) {
          if (idempotent.alreadyExecuted()) {
            return;
          }
          integers.add(1);
          idempotent.succeeded(TRUE);
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
  public void testInprocRegistryConcurrency() {
    for (int i = 0; i < 10; i++) {
      final IdempotentRegistry idempotentRegistry = new InprocIdempotentRegistry();
      concurrencyTest(idempotentRegistry);
    }
  }
}
