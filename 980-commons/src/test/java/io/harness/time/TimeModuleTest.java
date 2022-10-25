/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.time;

import static io.harness.rule.OwnerRule.AVMOHAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.concurrent.HTimeLimiter;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.rule.Owner;
import io.harness.threading.ExecutorModule;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.MDC;

public class TimeModuleTest extends CategoryTest {
  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldPropagateLogContextInTimelimiterCall() throws Exception {
    List<Module> modules = new ArrayList<>();
    ExecutorModule.getInstance().setExecutorService(Executors.newSingleThreadExecutor());
    modules.add(TimeModule.getInstance());
    Injector injector = Guice.createInjector(modules);
    TimeLimiter timeLimiter = injector.getInstance(TimeLimiter.class);
    try (AutoLogContext context = new TestLogContext("foo", "bar", AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofMinutes(1), () -> {
        assertThat(MDC.get("foo")).isEqualTo("bar");
        return null;
      });
    }
  }

  private static class TestLogContext extends AutoLogContext {
    TestLogContext(String key, String value, OverrideBehavior behavior) {
      super(key, value, behavior);
    }
  }
}
