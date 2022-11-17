/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.beans.DelegateTask.Status.STARTED;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARPIT;
import static io.harness.rule.OwnerRule.GEORGE;

import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofMillis;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.harness.DelegateServiceTestBase;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskBuilder;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.TaskData;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.threading.Morpheus;

import com.google.inject.Inject;
import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DelegateTaskServiceTest extends DelegateServiceTestBase {
  @Inject HPersistence persistence;
  @Inject DelegateTaskService delegateTaskService;

  @Inject DelegateCache delegateCache;

  private static final String TEST_ACCOUNT_ID = "testAccount";

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testTouchExecutingTasksWithEmpty() {
    assertThatCode(() -> delegateTaskService.touchExecutingTasks(null, null, null)).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testTouchExecutingTasks() {
    String delegateId = generateUuid();
    DelegateTaskBuilder delegateTaskBuilder = DelegateTask.builder()
                                                  .accountId(TEST_ACCOUNT_ID)
                                                  .delegateId(delegateId)
                                                  .status(STARTED)
                                                  .data(TaskData.builder().timeout(1000L).build())
                                                  .expiry(currentTimeMillis() + 1000L);
    DelegateTask delegateTask1 = delegateTaskBuilder.uuid(generateUuid()).build();
    persistence.save(delegateTask1);
    DelegateTask delegateTask2 = delegateTaskBuilder.uuid(generateUuid()).status(QUEUED).build();
    persistence.save(delegateTask2);

    Morpheus.sleep(ofMillis(1));

    delegateTaskService.touchExecutingTasks(
        TEST_ACCOUNT_ID, delegateId, asList(delegateTask1.getUuid(), delegateTask2.getUuid()));

    DelegateTask updatedDelegateTask1 = persistence.get(DelegateTask.class, delegateTask1.getUuid());
    assertThat(updatedDelegateTask1.getExpiry()).isGreaterThan(delegateTask1.getExpiry());

    DelegateTask updatedDelegateTask2 = persistence.get(DelegateTask.class, delegateTask2.getUuid());
    assertThat(updatedDelegateTask2.getExpiry()).isEqualTo(delegateTask2.getExpiry());
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void testSupportedTaskType() {
    DelegateBuilder delegateBuilder =
        Delegate.builder().accountId(TEST_ACCOUNT_ID).lastHeartBeat(System.currentTimeMillis());

    Delegate delegate1 = delegateBuilder.supportedTaskTypes(Arrays.asList("type1", "type2")).build();
    Delegate delegate2 = delegateBuilder.supportedTaskTypes(Arrays.asList("type1", "type3")).build();

    String delegateId1 = persistence.save(delegate1);
    String delegateId2 = persistence.save(delegate2);

    boolean isTaskType1Supported = delegateTaskService.isTaskTypeSupportedByAllDelegates(TEST_ACCOUNT_ID, "type1");
    boolean isTaskType2Supported = delegateTaskService.isTaskTypeSupportedByAllDelegates(TEST_ACCOUNT_ID, "type2");

    assertThat(isTaskType1Supported).isTrue();
    assertThat(isTaskType2Supported).isFalse();
  }
}
