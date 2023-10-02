/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package software.wings.service.delegate;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.JENNY;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.DelegateCapacity;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.TaskDataV2;
import io.harness.persistence.HPersistence;
import io.harness.queueservice.ResourceBasedDelegateSelectionCheckForTask;
import io.harness.queueservice.impl.FilterByDelegateCapacity;
import io.harness.queueservice.impl.OrderByTotalNumberOfTaskAssignedCriteria;
import io.harness.queueservice.infc.DelegateCapacityManagementService;
import io.harness.rule.Owner;
import io.harness.service.intfc.DelegateCache;

import software.wings.WingsBaseTest;
import software.wings.beans.TaskType;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.annotation.Description;
import wiremock.com.google.common.collect.Lists;

public class DelegateResourceCriteriaCheckForTaskTest extends WingsBaseTest {
  @Inject private ResourceBasedDelegateSelectionCheckForTask resourceBasedDelegateSelectionCheckForTask;
  @Inject @InjectMocks private OrderByTotalNumberOfTaskAssignedCriteria orderByTotalNumberOfTaskAssignedCriteria;
  @Inject @InjectMocks private FilterByDelegateCapacity filterByDelegateCapacity;
  @Inject private DelegateCapacityManagementService delegateCapacityManagementService;

  @Inject private HPersistence persistence;

  private static final String VERSION = "1.0.0";
  private static final String DELEGATE_TYPE = "dockerType";
  private static final List<String> supportedTasks = Arrays.stream(TaskType.values()).map(Enum::name).collect(toList());

  @Mock private DelegateCache delegateCache;

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Verify delegate with least number of currently task assigned, comes first in the list. One delegate")
  public void testOrderByTotalNumberOfTaskAssignedCriteria_OneDelegate() {
    String accountId = generateUuid();
    Delegate delegate = createDelegate(accountId, "");
    when(delegateCache.get(accountId, delegate.getUuid())).thenReturn(delegate);
    createDelegateTaskWithStatusStarted(accountId, delegate.getUuid());
    List<Delegate> eligibleDelegateIds = Collections.singletonList(delegate);
    List<Delegate> delegateList = orderByTotalNumberOfTaskAssignedCriteria.getFilteredEligibleDelegateList(
        eligibleDelegateIds, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateList.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Verify delegate with least number of currently task assigned, comes first in the list. Three delegates")
  public void testOrderByTotalNumberOfTaskAssignedCriteria_3Delegates() {
    String accountId = generateUuid();
    Delegate delegate1 = createDelegate(accountId, "delegate1");
    Delegate delegate2 = createDelegate(accountId, "delegate2");
    Delegate delegate3 = createDelegate(accountId, "delegate3");

    List<Delegate> eligibleDelegateIds = Lists.newArrayList(delegate1, delegate2, delegate3);
    when(delegateCache.get(accountId, delegate1.getUuid())).thenReturn(delegate1);
    when(delegateCache.get(accountId, delegate2.getUuid())).thenReturn(delegate2);
    when(delegateCache.get(accountId, delegate3.getUuid())).thenReturn(delegate3);

    createDelegateTaskWithStatusStarted(accountId, delegate1.getUuid());
    IntStream.range(0, 2).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate2.getUuid()));
    IntStream.range(2, 5).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate3.getUuid()));

    List<Delegate> delegateList = orderByTotalNumberOfTaskAssignedCriteria.getFilteredEligibleDelegateList(
        eligibleDelegateIds, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateList.size()).isEqualTo(3);
    assertThat(delegateList.get(0).getUuid()).isEqualTo(delegate1.getUuid());
    assertThat(delegateList.get(1).getUuid()).isEqualTo(delegate2.getUuid());
    assertThat(delegateList.get(2).getUuid()).isEqualTo(delegate3.getUuid());
    assertThat(delegateList).containsExactly(delegate1, delegate2, delegate3);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Verify delegate with least number of currently task assigned, comes first in the list. Three delegates")
  public void testOrderByTotalNumberOfTaskAssignedCriteria_3Delegates_NoTaskCurrentlyAssigned() {
    String accountId = generateUuid();
    Delegate delegate1 = createDelegate(accountId, "delegate1");

    List<Delegate> eligibleDelegateIds = Lists.newArrayList(delegate1);
    when(delegateCache.get(accountId, delegate1.getUuid())).thenReturn(delegate1);
    List<Delegate> delegateList = orderByTotalNumberOfTaskAssignedCriteria.getFilteredEligibleDelegateList(
        eligibleDelegateIds, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateList.size()).isEqualTo(1);
    assertThat(delegateList.get(0).getUuid()).isEqualTo(delegate1.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Verify delegate with least number of currently task assigned, comes first in the list. Five delegates")
  public void testOrderByTotalNumberOfTaskAssignedCriteria_fiveDelegates() {
    String accountId = generateUuid();
    Delegate delegate1 = createDelegate(accountId, "delegate1");
    Delegate delegate2 = createDelegate(accountId, "delegate2");
    Delegate delegate3 = createDelegate(accountId, "delegate3");
    Delegate delegate4 = createDelegate(accountId, "delegate3");
    Delegate delegate5 = createDelegate(accountId, "delegate3");

    List<Delegate> eligibleDelegateIds = Lists.newArrayList(delegate1, delegate2, delegate3, delegate4, delegate5);
    when(delegateCache.get(accountId, delegate1.getUuid())).thenReturn(delegate1);
    when(delegateCache.get(accountId, delegate2.getUuid())).thenReturn(delegate2);
    when(delegateCache.get(accountId, delegate3.getUuid())).thenReturn(delegate3);
    when(delegateCache.get(accountId, delegate4.getUuid())).thenReturn(delegate4);
    when(delegateCache.get(accountId, delegate5.getUuid())).thenReturn(delegate5);

    createDelegateTaskWithStatusStarted(accountId, delegate1.getUuid());
    IntStream.range(0, 2).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate2.getUuid()));
    IntStream.range(2, 5).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate3.getUuid()));
    IntStream.range(5, 9).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate4.getUuid()));
    IntStream.range(9, 14).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate5.getUuid()));

    List<Delegate> delegateList = orderByTotalNumberOfTaskAssignedCriteria.getFilteredEligibleDelegateList(
        eligibleDelegateIds, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateList.size() == 5);
    assertThat(delegateList.get(0).getUuid()).isEqualTo(delegate1.getUuid());
    assertThat(delegateList.get(1).getUuid()).isEqualTo(delegate2.getUuid());
    assertThat(delegateList.get(2).getUuid()).isEqualTo(delegate3.getUuid());
    assertThat(delegateList.get(3).getUuid()).isEqualTo(delegate4.getUuid());
    assertThat(delegateList.get(4).getUuid()).isEqualTo(delegate5.getUuid());

    assertThat(delegateList).containsExactly(delegate1, delegate2, delegate3, delegate4, delegate5);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Verify delegate with least number of currently task assigned, comes first in the list. Five delegates")
  public void testSortOrderByTotalNumberOfTaskAssignedCriteria_fiveDelegates() {
    String accountId = generateUuid();
    Delegate delegate1 = createDelegate(accountId, "delegate1");
    Delegate delegate2 = createDelegate(accountId, "delegate2");
    Delegate delegate3 = createDelegate(accountId, "delegate3");
    Delegate delegate4 = createDelegate(accountId, "delegate3");
    Delegate delegate5 = createDelegate(accountId, "delegate3");

    List<Delegate> eligibleDelegateIds = Lists.newArrayList(delegate1, delegate2, delegate3, delegate4, delegate5);
    when(delegateCache.get(accountId, delegate1.getUuid())).thenReturn(delegate1);
    when(delegateCache.get(accountId, delegate2.getUuid())).thenReturn(delegate2);
    when(delegateCache.get(accountId, delegate3.getUuid())).thenReturn(delegate3);
    when(delegateCache.get(accountId, delegate4.getUuid())).thenReturn(delegate4);
    when(delegateCache.get(accountId, delegate5.getUuid())).thenReturn(delegate5);

    createDelegateTaskWithStatusStarted(accountId, delegate2.getUuid());
    IntStream.range(0, 2).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate1.getUuid()));
    IntStream.range(2, 5).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate4.getUuid()));
    IntStream.range(5, 9).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate3.getUuid()));
    IntStream.range(9, 14).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate5.getUuid()));

    List<Delegate> delegateList = orderByTotalNumberOfTaskAssignedCriteria.getFilteredEligibleDelegateList(
        eligibleDelegateIds, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateList.size() == 5);
    assertThat(delegateList).containsExactly(delegate2, delegate1, delegate4, delegate3, delegate5);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Verify delegate with least number of currently task assigned, comes first in the list. Five delegates")
  public void testSortOrderByTotalNumberOfTaskAssignedCriteria_SameStageIds() {
    String accountId = generateUuid();
    Delegate delegate1 = createDelegate(accountId, "delegate1");
    Delegate delegate2 = createDelegate(accountId, "delegate2");
    Delegate delegate3 = createDelegate(accountId, "delegate3");
    Delegate delegate4 = createDelegate(accountId, "delegate4");
    Delegate delegate5 = createDelegate(accountId, "delegate5");

    List<Delegate> eligibleDelegateIds = Lists.newArrayList(delegate1, delegate2, delegate3, delegate4, delegate5);
    when(delegateCache.get(accountId, delegate1.getUuid())).thenReturn(delegate1);
    when(delegateCache.get(accountId, delegate2.getUuid())).thenReturn(delegate2);
    when(delegateCache.get(accountId, delegate3.getUuid())).thenReturn(delegate3);
    when(delegateCache.get(accountId, delegate4.getUuid())).thenReturn(delegate4);
    when(delegateCache.get(accountId, delegate5.getUuid())).thenReturn(delegate5);

    createDelegateTaskWithStatusStartedWithStageId(accountId, delegate2.getUuid(), generateUuid());
    String stageId1 = generateUuid();
    IntStream.range(0, 2).forEach(
        i -> createDelegateTaskWithStatusStartedWithStageId(accountId, delegate1.getUuid(), stageId1));
    String stageId2 = generateUuid();
    IntStream.range(2, 5).forEach(
        i -> createDelegateTaskWithStatusStartedWithStageId(accountId, delegate4.getUuid(), stageId2));
    IntStream.range(5, 9).forEach(
        i -> createDelegateTaskWithStatusStartedWithStageId(accountId, delegate3.getUuid(), stageId2));
    String stageId3 = generateUuid();
    IntStream.range(9, 14).forEach(
        i -> createDelegateTaskWithStatusStartedWithStageId(accountId, delegate5.getUuid(), stageId3));
    String stageId4 = generateUuid();
    IntStream.range(14, 30).forEach(
        i -> createDelegateTaskWithStatusStartedWithStageId(accountId, delegate5.getUuid(), stageId4));

    List<Delegate> delegateList = orderByTotalNumberOfTaskAssignedCriteria.getFilteredEligibleDelegateList(
        eligibleDelegateIds, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateList).hasSize(5);
    assertThat(delegateList.get(0).getNumberOfTaskAssigned()).isEqualTo(1);
    assertThat(delegateList.get(4).getNumberOfTaskAssigned()).isEqualTo(2);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testOrderByTotalNumberOfTaskAssignedCriteria_NoDelegate() {
    String accountId = generateUuid();
    List<Delegate> eligibleDelegateIds = Collections.emptyList();
    List<Delegate> delegateList = orderByTotalNumberOfTaskAssignedCriteria.getFilteredEligibleDelegateList(
        eligibleDelegateIds, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateList.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Test filterBy capacity with no capacity set.")
  public void testFilterByCapacity_WithNoCapacitySet() {
    String accountId = generateUuid();
    Delegate delegate = createDelegateWithTaskAssignedField(accountId, 3);
    List<Delegate> eligibleDelegateIds = Lists.newArrayList(delegate);
    List<Delegate> delegateList = filterByDelegateCapacity.getFilteredEligibleDelegateList(
        eligibleDelegateIds, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateList.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Test filterBy capacity with capacity set and has capability to assign task.")
  public void testFilterByCapacity_WithInCapacity_OneDelegate() {
    String accountId = generateUuid();
    Delegate delegate = createDelegateWithTaskAssignedField(accountId, 3);
    delegate.setDelegateCapacity(DelegateCapacity.builder().maximumNumberOfBuilds(10).build());
    persistence.save(delegate);
    List<Delegate> eligibleDelegateIds = Lists.newArrayList(delegate);
    List<Delegate> delegateList = filterByDelegateCapacity.getFilteredEligibleDelegateList(
        eligibleDelegateIds, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateList.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Test filterBy capacity with capacity set but already reached max capacity.")
  public void testFilterByCapacity_NoCapacity_OneDelegate() {
    String accountId = generateUuid();
    Delegate delegate = createDelegateWithTaskAssignedField(accountId, 6);
    delegate.setDelegateCapacity(DelegateCapacity.builder().maximumNumberOfBuilds(5).build());
    persistence.save(delegate);
    List<Delegate> eligibleDelegateIds = Lists.newArrayList(delegate);
    List<Delegate> delegateList = filterByDelegateCapacity.getFilteredEligibleDelegateList(
        eligibleDelegateIds, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateList.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Test filterBy capacity with capacity set and has capability to assign task.")
  public void testFilterByCapacity_ReachCapacity_OneDelegate() {
    String accountId = generateUuid();
    Delegate delegate = createDelegateWithTaskAssignedField(accountId, 4);
    delegate.setDelegateCapacity(DelegateCapacity.builder().maximumNumberOfBuilds(4).build());
    persistence.save(delegate);
    List<Delegate> eligibleDelegateIds = Lists.newArrayList(delegate);
    List<Delegate> delegateList = filterByDelegateCapacity.getFilteredEligibleDelegateList(
        eligibleDelegateIds, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateList.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Test filterBy capacity with max capacity reached for one delegate out of 3 eligible delegates.")
  public void testFilterByCapacity_ReachCapacity_ThreeDelegates() {
    String accountId = generateUuid();
    Delegate delegate1 = createDelegate(accountId, "delelgate1");
    Delegate delegate2 = createDelegate(accountId, "delegate2");
    Delegate delegate3 = createDelegate(accountId, "delegate3");
    delegate3.setDelegateCapacity(DelegateCapacity.builder().maximumNumberOfBuilds(2).build());
    persistence.save(delegate3);

    List<Delegate> eligibleDelegateIds = Lists.newArrayList(delegate1, delegate2, delegate3);
    when(delegateCache.get(accountId, delegate1.getUuid())).thenReturn(delegate1);
    when(delegateCache.get(accountId, delegate2.getUuid())).thenReturn(delegate2);
    when(delegateCache.get(accountId, delegate3.getUuid())).thenReturn(delegate3);

    createDelegateTaskWithStatusStarted(accountId, delegate1.getUuid());
    IntStream.range(0, 2).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate2.getUuid()));
    IntStream.range(2, 5).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate3.getUuid()));

    List<Delegate> delegateList = orderByTotalNumberOfTaskAssignedCriteria.getFilteredEligibleDelegateList(
        eligibleDelegateIds, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateList.size() == 3);
    assertThat(delegateList.size()).isEqualTo(3);
    List<Delegate> delegateWithCapacityList = filterByDelegateCapacity.getFilteredEligibleDelegateList(
        delegateList, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateWithCapacityList.size()).isEqualTo(2);
    assertThat(delegateWithCapacityList.get(0).getUuid()).isEqualTo(delegate1.getUuid());
    assertThat(delegateWithCapacityList.get(1).getUuid()).isEqualTo(delegate2.getUuid());
    assertThat(delegateWithCapacityList).containsExactly(delegate1, delegate2);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Test filterBy capacity with max capacity reached for two delegate out of 5 eligible delegates.")
  public void testFilterByCapacity_ReachCapacity_FiveDelegates() {
    String accountId = generateUuid();
    Delegate delegate1 = createDelegate(accountId, "delelgate1");
    Delegate delegate2 = createDelegate(accountId, "delegate2");
    Delegate delegate3 = createDelegate(accountId, "delegate3");
    delegate3.setDelegateCapacity(DelegateCapacity.builder().maximumNumberOfBuilds(2).build());
    persistence.save(delegate3);
    Delegate delegate4 = createDelegate(accountId, "delegate3");
    Delegate delegate5 = createDelegate(accountId, "delegate3");
    delegate5.setDelegateCapacity(DelegateCapacity.builder().maximumNumberOfBuilds(4).build());
    persistence.save(delegate5);

    List<Delegate> eligibleDelegateIds = Lists.newArrayList(delegate1, delegate2, delegate3, delegate4, delegate5);
    when(delegateCache.get(accountId, delegate1.getUuid())).thenReturn(delegate1);
    when(delegateCache.get(accountId, delegate2.getUuid())).thenReturn(delegate2);
    when(delegateCache.get(accountId, delegate3.getUuid())).thenReturn(delegate3);
    when(delegateCache.get(accountId, delegate4.getUuid())).thenReturn(delegate4);
    when(delegateCache.get(accountId, delegate5.getUuid())).thenReturn(delegate5);

    createDelegateTaskWithStatusStarted(accountId, delegate1.getUuid());
    IntStream.range(0, 2).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate2.getUuid()));
    IntStream.range(2, 5).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate3.getUuid()));
    IntStream.range(5, 9).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate4.getUuid()));
    IntStream.range(9, 14).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate5.getUuid()));

    List<Delegate> delegateList = orderByTotalNumberOfTaskAssignedCriteria.getFilteredEligibleDelegateList(
        eligibleDelegateIds, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateList.size()).isEqualTo(5);
    List<Delegate> delegateWithCapacityList = filterByDelegateCapacity.getFilteredEligibleDelegateList(
        delegateList, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateWithCapacityList.size()).isEqualTo(3);
    assertThat(delegateWithCapacityList.get(0).getUuid()).isEqualTo(delegate1.getUuid());
    assertThat(delegateWithCapacityList.get(1).getUuid()).isEqualTo(delegate2.getUuid());
    assertThat(delegateWithCapacityList.get(2).getUuid()).isEqualTo(delegate4.getUuid());
    assertThat(delegateWithCapacityList).containsExactly(delegate1, delegate2, delegate4);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Test filterBy capacity with max capacity reached for two delegate out of 5 eligible delegates.")
  public void testFilterByCapacity_ReachCapacity_SameStageId() {
    String accountId = generateUuid();
    Delegate delegate1 = createDelegate(accountId, "delelgate1");
    Delegate delegate2 = createDelegate(accountId, "delegate2");
    Delegate delegate3 = createDelegate(accountId, "delegate3");
    delegate3.setDelegateCapacity(DelegateCapacity.builder().maximumNumberOfBuilds(10).build());
    persistence.save(delegate3);
    Delegate delegate4 = createDelegate(accountId, "delegate4");
    Delegate delegate5 = createDelegate(accountId, "delegate5");
    delegate5.setDelegateCapacity(DelegateCapacity.builder().maximumNumberOfBuilds(1).build());
    persistence.save(delegate5);

    List<Delegate> eligibleDelegateIds = Lists.newArrayList(delegate1, delegate2, delegate3, delegate4, delegate5);
    when(delegateCache.get(accountId, delegate1.getUuid())).thenReturn(delegate1);
    when(delegateCache.get(accountId, delegate2.getUuid())).thenReturn(delegate2);
    when(delegateCache.get(accountId, delegate3.getUuid())).thenReturn(delegate3);
    when(delegateCache.get(accountId, delegate4.getUuid())).thenReturn(delegate4);
    when(delegateCache.get(accountId, delegate5.getUuid())).thenReturn(delegate5);

    createDelegateTaskWithStatusStartedWithStageId(accountId, delegate2.getUuid(), generateUuid());
    String stageId1 = generateUuid();
    IntStream.range(0, 2).forEach(
        i -> createDelegateTaskWithStatusStartedWithStageId(accountId, delegate1.getUuid(), stageId1));
    String stageId2 = generateUuid();
    IntStream.range(2, 5).forEach(
        i -> createDelegateTaskWithStatusStartedWithStageId(accountId, delegate4.getUuid(), stageId2));
    IntStream.range(5, 9).forEach(
        i -> createDelegateTaskWithStatusStartedWithStageId(accountId, delegate3.getUuid(), stageId2));
    String stageId3 = generateUuid();
    IntStream.range(9, 14).forEach(
        i -> createDelegateTaskWithStatusStartedWithStageId(accountId, delegate5.getUuid(), stageId3));
    String stageId4 = generateUuid();
    IntStream.range(14, 30).forEach(
        i -> createDelegateTaskWithStatusStartedWithStageId(accountId, delegate5.getUuid(), stageId4));

    List<Delegate> delegateList = orderByTotalNumberOfTaskAssignedCriteria.getFilteredEligibleDelegateList(
        eligibleDelegateIds, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateList.size()).isEqualTo(5);
    List<Delegate> delegateWithCapacityList = filterByDelegateCapacity.getFilteredEligibleDelegateList(
        delegateList, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateWithCapacityList.size()).isEqualTo(4);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Verify And Criteria - one delegate")
  public void testANDDelegateResourceCriteria() {
    String accountId = generateUuid();
    Delegate delegate = createDelegate(accountId, "delegate1");
    delegate.setDelegateCapacity(DelegateCapacity.builder().maximumNumberOfBuilds(2).build());
    persistence.save(delegate);
    List<Delegate> eligibleDelegateIds = Lists.newArrayList(delegate);
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);

    createDelegateTaskWithStatusStarted(accountId, delegate.getUuid());
    Optional<List<String>> delegateList = resourceBasedDelegateSelectionCheckForTask.perform(
        eligibleDelegateIds, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateList.isPresent()).isTrue();
    assertThat(delegateList.get().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Verify And Criteria - 3 delegates")
  public void testANDDelegateResourceCriteria_ThreeDelegate() {
    String accountId = generateUuid();
    Delegate delegate1 = createDelegate(accountId, "delelgate1");
    Delegate delegate2 = createDelegate(accountId, "delegate2");
    Delegate delegate3 = createDelegate(accountId, "delegate3");
    delegate3.setDelegateCapacity(DelegateCapacity.builder().maximumNumberOfBuilds(2).build());
    persistence.save(delegate3);

    List<Delegate> eligibleDelegateIds = Lists.newArrayList(delegate1, delegate2, delegate3);
    when(delegateCache.get(accountId, delegate1.getUuid())).thenReturn(delegate1);
    when(delegateCache.get(accountId, delegate2.getUuid())).thenReturn(delegate2);
    when(delegateCache.get(accountId, delegate3.getUuid())).thenReturn(delegate3);

    createDelegateTaskWithStatusStarted(accountId, delegate1.getUuid());
    IntStream.range(0, 2).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate2.getUuid()));
    IntStream.range(2, 5).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate3.getUuid()));

    Optional<List<String>> delegateList = resourceBasedDelegateSelectionCheckForTask.perform(
        eligibleDelegateIds, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateList.isPresent()).isTrue();
    assertThat(delegateList.get().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Verify And Criteria - 5 delegates")
  public void testANDDelegateResourceCriteria_FiveDelegates() {
    String accountId = generateUuid();
    Delegate delegate1 = createDelegate(accountId, "delelgate1");
    Delegate delegate2 = createDelegate(accountId, "delegate2");
    Delegate delegate3 = createDelegate(accountId, "delegate3");
    delegate3.setDelegateCapacity(DelegateCapacity.builder().maximumNumberOfBuilds(2).build());
    persistence.save(delegate3);
    Delegate delegate4 = createDelegate(accountId, "delegate3");
    Delegate delegate5 = createDelegate(accountId, "delegate3");
    delegate5.setDelegateCapacity(DelegateCapacity.builder().maximumNumberOfBuilds(4).build());
    persistence.save(delegate5);

    List<Delegate> eligibleDelegateIds = Lists.newArrayList(delegate1, delegate2, delegate3, delegate4, delegate5);
    when(delegateCache.get(accountId, delegate1.getUuid())).thenReturn(delegate1);
    when(delegateCache.get(accountId, delegate2.getUuid())).thenReturn(delegate2);
    when(delegateCache.get(accountId, delegate3.getUuid())).thenReturn(delegate3);
    when(delegateCache.get(accountId, delegate4.getUuid())).thenReturn(delegate4);
    when(delegateCache.get(accountId, delegate5.getUuid())).thenReturn(delegate5);

    createDelegateTaskWithStatusStarted(accountId, delegate1.getUuid());
    IntStream.range(0, 2).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate2.getUuid()));
    IntStream.range(2, 5).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate3.getUuid()));
    IntStream.range(5, 9).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate4.getUuid()));
    IntStream.range(9, 14).forEach(i -> createDelegateTaskWithStatusStarted(accountId, delegate5.getUuid()));

    Optional<List<String>> delegateList = resourceBasedDelegateSelectionCheckForTask.perform(
        eligibleDelegateIds, TaskType.INITIALIZATION_PHASE, accountId);
    assertThat(delegateList.isPresent()).isTrue();
    assertThat(delegateList.get().size()).isEqualTo(3);
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

  private void createDelegateTaskWithStatusStartedWithStageId(String accountId, String delegateId, String stageId) {
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .status(DelegateTask.Status.STARTED)
            .stageId(stageId)
            .delegateId(delegateId)
            .taskDataV2(TaskDataV2.builder().taskType(TaskType.INITIALIZATION_PHASE.name()).build())
            .build();
    persistence.save(delegateTask);
  }

  private Delegate createDelegateWithTaskAssignedField(String accountId, int numberOfTaskAssigned) {
    Delegate delegate = createDelegate(accountId, "description");
    delegate.setNumberOfTaskAssigned(numberOfTaskAssigned);
    persistence.save(delegate);
    return delegate;
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
}
