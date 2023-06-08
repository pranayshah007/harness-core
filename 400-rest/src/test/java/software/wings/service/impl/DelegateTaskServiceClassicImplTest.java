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
import io.dropwizard.util.Sets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void sortedEligibleListTestRand() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate1 = createDelegate(accountId, "delelgate1");
    Delegate delegate2 = createDelegate(accountId, "delegate2");
    Delegate delegate3 = createDelegate(accountId, "delegate3");
    Delegate delegate4 = createDelegate(accountId, "delegate4");

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .status(DelegateTask.Status.QUEUED)
            .stageId(generateUuid())
            .taskDataV2(TaskDataV2.builder().taskType(TaskType.INITIALIZATION_PHASE.name()).build())
            .build();
    persistence.save(delegateTask);

    Set<String> allExecutions = new HashSet<>();
    for (int i = 0; i < 10; ++i) {
      List<String> sortedList =
          delegateTaskServiceClassic.getEligibleDelegateListOrderedNumberByTaskAssigned(delegateTask,
              Arrays.asList(delegate1.getUuid(), delegate2.getUuid(), delegate3.getUuid(), delegate4.getUuid()));
      allExecutions.add(String.join("-", sortedList));
    }
    // Assert
    assertThat(allExecutions.size() > 8);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void sortedEligibleListTest1() throws ExecutionException {
    // Total three delegates. One delegate with 1 task, and others with >1. Check the first one is the delegate with 1
    // task.
    String accountId = generateUuid();
    Delegate delegate1 = createDelegate(accountId, "delelgate1");
    Delegate delegate2 = createDelegate(accountId, "delegate2");
    Delegate delegate3 = createDelegate(accountId, "delegate3");

    IntStream.range(0, 1).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate1.getUuid()));
    IntStream.range(1, 3).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate2.getUuid()));
    IntStream.range(3, 5).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate3.getUuid()));

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .status(DelegateTask.Status.QUEUED)
            .stageId(generateUuid())
            .taskDataV2(TaskDataV2.builder().taskType(TaskType.INITIALIZATION_PHASE.name()).build())
            .build();
    persistence.save(delegateTask);

    List<String> eligibleDelegates = Arrays.asList(delegate1.getUuid(), delegate2.getUuid(), delegate3.getUuid());
    Collections.shuffle(eligibleDelegates);
    List<String> sortedList =
        delegateTaskServiceClassic.getEligibleDelegateListOrderedNumberByTaskAssigned(delegateTask, eligibleDelegates);
    assertThat(sortedList.get(0)).isEqualTo(delegate1.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void sortedEligibleListTest2() throws ExecutionException {
    // 4 delegates. Each with different number of task. Check if the returned list is sorted.
    String accountId = generateUuid();
    Delegate delegate1 = createDelegate(accountId, "delelgate1");
    Delegate delegate2 = createDelegate(accountId, "delegate2");
    Delegate delegate3 = createDelegate(accountId, "delegate3");
    Delegate delegate4 = createDelegate(accountId, "delegate4");

    IntStream.range(0, 1).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate1.getUuid()));
    IntStream.range(1, 3).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate2.getUuid()));
    IntStream.range(3, 6).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate3.getUuid()));
    IntStream.range(6, 10).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate4.getUuid()));

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .status(DelegateTask.Status.QUEUED)
            .stageId(generateUuid())
            .taskDataV2(TaskDataV2.builder().taskType(TaskType.INITIALIZATION_PHASE.name()).build())
            .build();
    persistence.save(delegateTask);
    List<String> eligibleDelegates =
        Arrays.asList(delegate1.getUuid(), delegate2.getUuid(), delegate3.getUuid(), delegate4.getUuid());
    Collections.shuffle(eligibleDelegates);
    List<String> sortedList =
        delegateTaskServiceClassic.getEligibleDelegateListOrderedNumberByTaskAssigned(delegateTask,
            Arrays.asList(delegate1.getUuid(), delegate2.getUuid(), delegate3.getUuid(), delegate4.getUuid()));
    assertThat(sortedList).hasSize(4);
    assertThat(sortedList.get(0)).isEqualTo(delegate1.getUuid());
    assertThat(sortedList.get(1)).isEqualTo(delegate2.getUuid());
    assertThat(sortedList.get(2)).isEqualTo(delegate3.getUuid());
    assertThat(sortedList.get(3)).isEqualTo(delegate4.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void sortedEligibleListTest3() throws ExecutionException {
    // 9 delegates. One with 2 tasks, other two with 3 and 4 tasks each.
    // Check 0th index is 2-task delegate, 2nd index delegate has 3 tasks and 5th index has a delegate with 4 tasks
    String accountId = generateUuid();
    Delegate delegate1 = createDelegate(accountId, "delelgate1");
    Delegate delegate2 = createDelegate(accountId, "delegate2");
    Delegate delegate3 = createDelegate(accountId, "delegate3");
    Delegate delegate4 = createDelegate(accountId, "delegate4");
    Delegate delegate5 = createDelegate(accountId, "delegate5");
    Delegate delegate6 = createDelegate(accountId, "delegate6");
    Delegate delegate7 = createDelegate(accountId, "delegate7");
    Delegate delegate8 = createDelegate(accountId, "delegate8");
    Delegate delegate9 = createDelegate(accountId, "delegate9");

    //[2, 2, 3, 3, 3, 4, 4, 4, 4]
    IntStream.range(0, 2).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate1.getUuid()));
    IntStream.range(2, 4).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate2.getUuid()));

    IntStream.range(4, 7).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate3.getUuid()));
    IntStream.range(7, 10).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate4.getUuid()));
    IntStream.range(10, 13).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate5.getUuid()));

    IntStream.range(13, 17).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate6.getUuid()));
    IntStream.range(17, 21).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate7.getUuid()));
    IntStream.range(21, 25).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate8.getUuid()));
    IntStream.range(25, 30).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate9.getUuid()));

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .status(DelegateTask.Status.QUEUED)
            .stageId(generateUuid())
            .taskDataV2(TaskDataV2.builder().taskType(TaskType.INITIALIZATION_PHASE.name()).build())
            .build();
    persistence.save(delegateTask);
    List<String> eligibleDelegates =
        Arrays.asList(delegate1.getUuid(), delegate2.getUuid(), delegate3.getUuid(), delegate4.getUuid(),
            delegate5.getUuid(), delegate6.getUuid(), delegate7.getUuid(), delegate8.getUuid(), delegate9.getUuid());
    Collections.shuffle(eligibleDelegates);
    List<String> sortedList =
        delegateTaskServiceClassic.getEligibleDelegateListOrderedNumberByTaskAssigned(delegateTask, eligibleDelegates);
    assertThat(Sets.of(delegate1.getUuid(), delegate2.getUuid()).contains(sortedList.get(0)));
    assertThat(Sets.of(delegate3.getUuid(), delegate4.getUuid(), delegate5.getUuid())).contains(sortedList.get(2));
    assertThat(Sets.of(delegate6.getUuid(), delegate7.getUuid(), delegate8.getUuid(), delegate9.getUuid()))
        .contains(sortedList.get(5));
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
