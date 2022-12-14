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
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    when(delegateCache.getCurrentlyAssignedTask(accountId))
        .thenReturn(
            Arrays.asList(DelegateTask.builder()
                              .accountId(accountId)
                              .delegateId(delegate.getUuid())
                              .taskDataV2(TaskDataV2.builder().taskType(TaskType.INITIALIZATION_PHASE.name()).build())
                              .build()));
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
    Delegate delegate1 = createDelegate(accountId, "delelgate1");
    Delegate delegate2 = createDelegate(accountId, "delegate2");
    Delegate delegate3 = createDelegate(accountId, "delegate3");

    List<Delegate> eligibleDelegateIds = Lists.newArrayList(delegate1, delegate2, delegate3);
    when(delegateCache.get(accountId, delegate1.getUuid(), false)).thenReturn(delegate1);
    when(delegateCache.get(accountId, delegate2.getUuid(), false)).thenReturn(delegate2);
    when(delegateCache.get(accountId, delegate3.getUuid(), false)).thenReturn(delegate3);

    DelegateTask delegateTask1 = createDelegateTaskWithDelegateAssigned(accountId, delegate1.getUuid());
    DelegateTask delegateTask2 = createDelegateTaskWithDelegateAssigned(accountId, delegate2.getUuid());
    DelegateTask delegateTask3 = createDelegateTaskWithDelegateAssigned(accountId, delegate2.getUuid());
    DelegateTask delegateTask4 = createDelegateTaskWithDelegateAssigned(accountId, delegate3.getUuid());
    DelegateTask delegateTask5 = createDelegateTaskWithDelegateAssigned(accountId, delegate3.getUuid());
    DelegateTask delegateTask6 = createDelegateTaskWithDelegateAssigned(accountId, delegate3.getUuid());

    when(delegateCache.getCurrentlyAssignedTask(accountId))
        .thenReturn(
            Arrays.asList(delegateTask1, delegateTask2, delegateTask3, delegateTask4, delegateTask5, delegateTask6));

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
  @Description("Verify delegate with least number of currently task assigned, comes first in the list. Five delegates")
  public void testOrderByTotalNumberOfTaskAssignedCriteria_fiveDelegates() {
    String accountId = generateUuid();
    Delegate delegate1 = createDelegate(accountId, "delelgate1");
    Delegate delegate2 = createDelegate(accountId, "delegate2");
    Delegate delegate3 = createDelegate(accountId, "delegate3");
    Delegate delegate4 = createDelegate(accountId, "delegate3");
    Delegate delegate5 = createDelegate(accountId, "delegate3");

    List<Delegate> eligibleDelegateIds = Lists.newArrayList(delegate1, delegate2, delegate3, delegate4, delegate5);
    when(delegateCache.get(accountId, delegate1.getUuid(), false)).thenReturn(delegate1);
    when(delegateCache.get(accountId, delegate2.getUuid(), false)).thenReturn(delegate2);
    when(delegateCache.get(accountId, delegate3.getUuid(), false)).thenReturn(delegate3);
    when(delegateCache.get(accountId, delegate4.getUuid(), false)).thenReturn(delegate4);
    when(delegateCache.get(accountId, delegate5.getUuid(), false)).thenReturn(delegate5);

    DelegateTask delegateTask1 = createDelegateTaskWithDelegateAssigned(accountId, delegate1.getUuid());
    DelegateTask delegateTask2 = createDelegateTaskWithDelegateAssigned(accountId, delegate2.getUuid());
    DelegateTask delegateTask3 = createDelegateTaskWithDelegateAssigned(accountId, delegate2.getUuid());
    DelegateTask delegateTask4 = createDelegateTaskWithDelegateAssigned(accountId, delegate3.getUuid());
    DelegateTask delegateTask5 = createDelegateTaskWithDelegateAssigned(accountId, delegate3.getUuid());
    DelegateTask delegateTask6 = createDelegateTaskWithDelegateAssigned(accountId, delegate3.getUuid());
    DelegateTask delegateTask7 = createDelegateTaskWithDelegateAssigned(accountId, delegate4.getUuid());
    DelegateTask delegateTask8 = createDelegateTaskWithDelegateAssigned(accountId, delegate4.getUuid());
    DelegateTask delegateTask9 = createDelegateTaskWithDelegateAssigned(accountId, delegate4.getUuid());
    DelegateTask delegateTask10 = createDelegateTaskWithDelegateAssigned(accountId, delegate4.getUuid());
    DelegateTask delegateTask11 = createDelegateTaskWithDelegateAssigned(accountId, delegate5.getUuid());
    DelegateTask delegateTask12 = createDelegateTaskWithDelegateAssigned(accountId, delegate5.getUuid());
    DelegateTask delegateTask13 = createDelegateTaskWithDelegateAssigned(accountId, delegate5.getUuid());
    DelegateTask delegateTask14 = createDelegateTaskWithDelegateAssigned(accountId, delegate5.getUuid());
    DelegateTask delegateTask15 = createDelegateTaskWithDelegateAssigned(accountId, delegate5.getUuid());

    when(delegateCache.getCurrentlyAssignedTask(accountId))
        .thenReturn(Arrays.asList(delegateTask1, delegateTask2, delegateTask3, delegateTask4, delegateTask5,
            delegateTask6, delegateTask7, delegateTask8, delegateTask9, delegateTask10, delegateTask11, delegateTask12,
            delegateTask13, delegateTask14, delegateTask15));

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
  public void testOrderByTotalNumberOfTaskAssignedCriteria_NoDelegate() {
    String accountId = generateUuid();
    when(delegateCache.getCurrentlyAssignedTask(accountId))
        .thenReturn(
            Arrays.asList(DelegateTask.builder()
                              .accountId(accountId)
                              .taskDataV2(TaskDataV2.builder().taskType(TaskType.INITIALIZATION_PHASE.name()).build())
                              .build()));
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
    when(delegateCache.get(accountId, delegate1.getUuid(), false)).thenReturn(delegate1);
    when(delegateCache.get(accountId, delegate2.getUuid(), false)).thenReturn(delegate2);
    when(delegateCache.get(accountId, delegate3.getUuid(), false)).thenReturn(delegate3);

    DelegateTask delegateTask1 = createDelegateTaskWithDelegateAssigned(accountId, delegate1.getUuid());
    DelegateTask delegateTask2 = createDelegateTaskWithDelegateAssigned(accountId, delegate2.getUuid());
    DelegateTask delegateTask3 = createDelegateTaskWithDelegateAssigned(accountId, delegate2.getUuid());
    DelegateTask delegateTask4 = createDelegateTaskWithDelegateAssigned(accountId, delegate3.getUuid());
    DelegateTask delegateTask5 = createDelegateTaskWithDelegateAssigned(accountId, delegate3.getUuid());
    DelegateTask delegateTask6 = createDelegateTaskWithDelegateAssigned(accountId, delegate3.getUuid());

    when(delegateCache.getCurrentlyAssignedTask(accountId))
        .thenReturn(
            Arrays.asList(delegateTask1, delegateTask2, delegateTask3, delegateTask4, delegateTask5, delegateTask6));

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
    when(delegateCache.get(accountId, delegate1.getUuid(), false)).thenReturn(delegate1);
    when(delegateCache.get(accountId, delegate2.getUuid(), false)).thenReturn(delegate2);
    when(delegateCache.get(accountId, delegate3.getUuid(), false)).thenReturn(delegate3);
    when(delegateCache.get(accountId, delegate4.getUuid(), false)).thenReturn(delegate4);
    when(delegateCache.get(accountId, delegate5.getUuid(), false)).thenReturn(delegate5);

    DelegateTask delegateTask1 = createDelegateTaskWithDelegateAssigned(accountId, delegate1.getUuid());
    DelegateTask delegateTask2 = createDelegateTaskWithDelegateAssigned(accountId, delegate2.getUuid());
    DelegateTask delegateTask3 = createDelegateTaskWithDelegateAssigned(accountId, delegate2.getUuid());
    DelegateTask delegateTask4 = createDelegateTaskWithDelegateAssigned(accountId, delegate3.getUuid());
    DelegateTask delegateTask5 = createDelegateTaskWithDelegateAssigned(accountId, delegate3.getUuid());
    DelegateTask delegateTask6 = createDelegateTaskWithDelegateAssigned(accountId, delegate3.getUuid());
    DelegateTask delegateTask7 = createDelegateTaskWithDelegateAssigned(accountId, delegate4.getUuid());
    DelegateTask delegateTask8 = createDelegateTaskWithDelegateAssigned(accountId, delegate4.getUuid());
    DelegateTask delegateTask9 = createDelegateTaskWithDelegateAssigned(accountId, delegate4.getUuid());
    DelegateTask delegateTask10 = createDelegateTaskWithDelegateAssigned(accountId, delegate4.getUuid());
    DelegateTask delegateTask11 = createDelegateTaskWithDelegateAssigned(accountId, delegate5.getUuid());
    DelegateTask delegateTask12 = createDelegateTaskWithDelegateAssigned(accountId, delegate5.getUuid());
    DelegateTask delegateTask13 = createDelegateTaskWithDelegateAssigned(accountId, delegate5.getUuid());
    DelegateTask delegateTask14 = createDelegateTaskWithDelegateAssigned(accountId, delegate5.getUuid());
    DelegateTask delegateTask15 = createDelegateTaskWithDelegateAssigned(accountId, delegate5.getUuid());

    when(delegateCache.getCurrentlyAssignedTask(accountId))
        .thenReturn(Arrays.asList(delegateTask1, delegateTask2, delegateTask3, delegateTask4, delegateTask5,
            delegateTask6, delegateTask7, delegateTask8, delegateTask9, delegateTask10, delegateTask11, delegateTask12,
            delegateTask13, delegateTask14, delegateTask15));

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

  private DelegateTask createDelegateTaskWithDelegateAssigned(String accountId, String delegateId) {
    return DelegateTask.builder()
        .accountId(accountId)
        .delegateId(delegateId)
        .taskDataV2(TaskDataV2.builder().taskType(TaskType.INITIALIZATION_PHASE.name()).build())
        .build();
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
