/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.manage;

import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.manage.GlobalContextManager.ensureGlobalContextGuard;
import static io.harness.manage.GlobalContextManager.initGlobalContextGuard;
import static io.harness.manage.GlobalContextManager.obtainGlobalContext;
import static io.harness.manage.GlobalContextManager.obtainGlobalContextCopy;
import static io.harness.manage.GlobalContextManager.upsertGlobalContextRecord;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.GEORGE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.context.GlobalContextData;
import io.harness.context.MdcGlobalContextData;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.logging.logcontext.AutoLogRemoveContext;
import io.harness.manage.GlobalContextManager.GlobalContextGuard;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.MDC;

public class ManagedExecutorServiceTest extends CategoryTest {
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testEnsureGlobalContext() {
    try (GlobalContextGuard contextGuard1 = ensureGlobalContextGuard()) {
      try (GlobalContextGuard contextGuard2 = ensureGlobalContextGuard()) {
        assertThat(GlobalContextManager.isAvailable()).isTrue();
      }
      assertThat(GlobalContextManager.isAvailable()).isTrue();
    }
    assertThat(GlobalContextManager.isAvailable()).isFalse();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testMdcGlobalContext() throws Exception {
    try (GlobalContextGuard globalContextGuard = initGlobalContextGuard(new GlobalContext());
         AutoLogContext ignore = new AutoLogContext(ImmutableMap.of("foo", "bar"), OVERRIDE_ERROR)) {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      ManagedExecutorService managedExecutorService = new ManagedExecutorService(executor);

      Future future = managedExecutorService.submit(() -> { return MDC.get("foo"); });

      assertThat(future.get()).isEqualTo("bar");

      executor.shutdown();
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testObtainGlobalContext() {
    ImmutableMap<String, String> map = ImmutableMap.of("foo", "bar");
    GlobalContext globalContext = null;
    try (GlobalContextGuard globalContextGuard = initGlobalContextGuard(new GlobalContext());
         AutoLogContext ignore = new AutoLogContext(map, OVERRIDE_ERROR)) {
      globalContext = obtainGlobalContext();

      assertThat(((MdcGlobalContextData) globalContext.get(MdcGlobalContextData.MDC_ID)).getMap()).isEqualTo(map);
    }

    try (GlobalContextGuard globalContextGuard = initGlobalContextGuard(globalContext);
         AutoLogRemoveContext ignore = new AutoLogRemoveContext("foo")) {
      GlobalContext globalContext2 = obtainGlobalContext();
      assertThat((GlobalContextData) globalContext2.get(MdcGlobalContextData.MDC_ID)).isNull();
    }
  }
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testObtainGlobalContextCopy() {
    ImmutableMap<String, String> map = ImmutableMap.of("foo", "bar");
    GlobalContext globalContext = null;
    try (GlobalContextGuard globalContextGuard = initGlobalContextGuard(new GlobalContext());
         AutoLogContext ignore1 = new AutoLogContext(map, OVERRIDE_ERROR)) {
      globalContext = obtainGlobalContextCopy();

      assertThat(((MdcGlobalContextData) globalContext.get(MdcGlobalContextData.MDC_ID)).getMap()).isEqualTo(map);

      try (AutoLogRemoveContext ignore2 = new AutoLogRemoveContext("foo")) {
        GlobalContext globalContext2 = obtainGlobalContextCopy();
        assertThat((GlobalContextData) globalContext2.get(MdcGlobalContextData.MDC_ID)).isNull();
        assertThat(((MdcGlobalContextData) globalContext.get(MdcGlobalContextData.MDC_ID)).getMap()).isEqualTo(map);
      }
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSubmitRunnable() throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    final AtomicBoolean isGlobalContextSetInChildThread = new AtomicBoolean(false);
    final AtomicBoolean isTestContextSetInChildThread = new AtomicBoolean(false);

    ManagedExecutorService managedExecutorService = new ManagedExecutorService(executor);
    try (GlobalContextGuard ignore = initGlobalContextGuard(new GlobalContext())) {
      upsertGlobalContextRecord(() -> "AUDIT_KEY");
      upsertGlobalContextRecord(() -> "TEST_KEY");

      // managedExecutorService.submit(Runnable task) is expected to make sure, new thread executing runnable task,
      // gets GlobalContext from thread creating it.
      Future future = managedExecutorService.submit(() -> {
        GlobalContext globalContext = obtainGlobalContext();
        isGlobalContextSetInChildThread.set(globalContext.get("AUDIT_KEY") != null);
        isTestContextSetInChildThread.set(globalContext.get("TEST_KEY") != null);
      });

      future.get();

      assertThat(isGlobalContextSetInChildThread.get()).isTrue();
      assertThat(isTestContextSetInChildThread.get()).isTrue();
      executor.shutdown();
    }
  }
}
