package io.harness.engine;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class NoopTaskExecutorTest extends CategoryTest {
  NoopTaskExecutor noopTaskExecutorWithANoopTest = new NoopTaskExecutor();
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testQueueTask() {
    assertThat(noopTaskExecutorWithANoopTest.queueTask(null, null, null)).isNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testExpireTask() {
    noopTaskExecutorWithANoopTest.expireTask(null, null);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testAbortTask() {
    assertThat(noopTaskExecutorWithANoopTest.abortTask(null, null)).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testExecuteTask() throws InterruptedException {
    ResponseData responseData = noopTaskExecutorWithANoopTest.executeTask(null, null);
    assertThat(responseData).isNull();
  }
}