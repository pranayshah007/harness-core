/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.JENNY;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.TaskDataV2;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.TaskType;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DelegateTaskServiceClassicImplTest extends WingsBaseTest {
  @Inject private DelegateTaskServiceClassicImpl delegateTaskServiceClassic;
  @Inject private HPersistence persistence;

  private static final String VERSION = "1.0.0";
  private static final String DELEGATE_TYPE = "dockerType";
  private static final List<String> supportedTasks = Arrays.stream(TaskType.values()).map(Enum::name).collect(toList());

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void sortedEligibleListTest() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate1 = createDelegate(accountId, "delelgate1");
    Delegate delegate2 = createDelegate(accountId, "delegate2");
    Delegate delegate3 = createDelegate(accountId, "delegate3");
    Delegate delegate4 = createDelegate(accountId, "delegate4");

    IntStream.range(0, 2).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate2.getUuid()));
    IntStream.range(2, 5).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate3.getUuid()));

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .status(DelegateTask.Status.QUEUED)
            .stageId(generateUuid())
            .taskDataV2(TaskDataV2.builder().taskType(TaskType.INITIALIZATION_PHASE.name()).build())
            .build();
    persistence.save(delegateTask);

    List<String> sortedList =
        delegateTaskServiceClassic.getEligibleDelegateListOrderedNumberByTaskAssigned(delegateTask,
            Arrays.asList(delegate1.getUuid(), delegate2.getUuid(), delegate3.getUuid(), delegate4.getUuid()));
    assertThat(sortedList).hasSize(4);
    assertThat(sortedList.get(2)).isEqualTo(delegate2.getUuid());
    assertThat(sortedList.get(3)).isEqualTo(delegate3.getUuid());
  }

  private Delegate createDelegate(String accountId, String des) {
    Delegate delegate = createDelegateBuilder(accountId).build();
    delegate.setDescription(des);
    persistence.save(delegate);
    return delegate;
  }
  private DelegateBuilder createDelegateBuilder(String accountId) {
    return Delegate.builder()
        .accountId(accountId)
        .ip("127.0.0.1")
        .hostName("localhost")
        .delegateName("testDelegateName")
        .delegateType(DELEGATE_TYPE)
        .version(VERSION)
        .supportedTaskTypes(supportedTasks)
        .tags(ImmutableList.of("aws-delegate", "sel1", "sel2"))
        .status(DelegateInstanceStatus.ENABLED)
        .lastHeartBeat(System.currentTimeMillis());
  }

  private void createDelegateTaskWithStatusStarted(String accountId, String delegateId) {
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .status(DelegateTask.Status.STARTED)
            .stageId(generateUuid())
            .delegateId(delegateId)
            .taskDataV2(TaskDataV2.builder().taskType(TaskType.INITIALIZATION_PHASE.name()).build())
            .build();
    persistence.save(delegateTask);
  }
}
