/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.EnvironmentType.PROD;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.BROADCASTING_DELEGATES;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.CAN_NOT_ASSIGN_CG_NG_TASK_GROUP;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.CAN_NOT_ASSIGN_DELEGATE_SCOPE_GROUP;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.CAN_NOT_ASSIGN_OWNER;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.CAN_NOT_ASSIGN_PROFILE_SCOPE_GROUP;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.CAN_NOT_ASSIGN_SELECTOR_TASK_GROUP;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.CAN_NOT_ASSIGN_TASK_GROUP;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.ELIGIBLE_DELEGATES;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.NO_ELIGIBLE_DELEGATES;

import static java.time.Duration.of;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateSelectionLogParams;
import io.harness.delegate.beans.executioncapability.CapabilityType;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.selection.log.DelegateSelectionLog;
import io.harness.selection.log.DelegateSelectionLog.DelegateSelectionLogKeys;
import io.harness.service.intfc.DelegateCache;
import io.harness.threading.Concurrent;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.dl.WingsPersistence;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@BreakDependencyOn("io.harness.beans.Cd1SetupFields")
@BreakDependencyOn("software.wings.WingsBaseTest")
@BreakDependencyOn("software.wings.beans.Application")
@BreakDependencyOn("software.wings.beans.Environment")
@BreakDependencyOn("software.wings.beans.Service")
@BreakDependencyOn("software.wings.dl.WingsPersistence")
public class DelegateSelectionLogsServiceImplTest extends WingsBaseTest {
  private static final String INFO = "Info";
  private static final String REJECTED = "Rejected";
  private static final String SELECTED = "Selected";
  private static final String NON_SELECTED = "Not Selected";
  private static final String BROADCAST = "Broadcast";
  private static final String ASSIGNED = "Assigned";

  private static final String MISSING_SELECTOR_MESSAGE = "missing selector";

  @Inject protected WingsPersistence wingsPersistence;
  @Mock protected FeatureFlagService featureFlagService;
  @InjectMocks @Inject DelegateSelectionLogsServiceImpl delegateSelectionLogsService;
  @Inject private HPersistence persistence;
  @Mock private DelegateCache delegateCache;

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldLogNoEligibleDelegates() {
    String taskId = generateUuid();
    String accountId = generateUuid();
    DelegateTask task =
        DelegateTask.builder().uuid(taskId).accountId(accountId).selectionLogsTrackingEnabled(true).build();
    delegateSelectionLogsService.logNoEligibleDelegatesToExecuteTask(task);
    List<DelegateSelectionLogParams> delegateSelectionLogParams = fetchSelectionLogs(accountId, taskId);

    assertThat(delegateSelectionLogParams).isNotEmpty();
    assertThat(delegateSelectionLogParams.size()).isEqualTo(1);
    assertThat(delegateSelectionLogParams.get(0).getConclusion()).isEqualTo(REJECTED);
    assertThat(delegateSelectionLogParams.get(0).getMessage()).isEqualTo(NO_ELIGIBLE_DELEGATES);
    assertThat(delegateSelectionLogParams.get(0).getEventTimestamp()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldLogEligibleDelegatesToExecuteTask() {
    String taskId = generateUuid();
    String accountId = generateUuid();
    String delegate = generateUuid();
    Set<String> delegateIds = Sets.newHashSet(delegate);
    DelegateTask task =
        DelegateTask.builder().uuid(taskId).accountId(accountId).selectionLogsTrackingEnabled(true).build();
    delegateSelectionLogsService.logEligibleDelegatesToExecuteTask(delegateIds, task, false);
    List<DelegateSelectionLogParams> delegateSelectionLogParams = fetchSelectionLogs(accountId, taskId);

    assertThat(delegateSelectionLogParams).isNotEmpty();
    assertThat(delegateSelectionLogParams.size()).isEqualTo(1);
    assertThat(delegateSelectionLogParams.get(0).getConclusion()).isEqualTo(SELECTED);
    assertThat(delegateSelectionLogParams.get(0).getMessage()).isEqualTo(ELIGIBLE_DELEGATES + " : " + delegateIds);
    assertThat(delegateSelectionLogParams.get(0).getEventTimestamp()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldLogDelegateTaskInfoWithNoExecutionCapability() {
    String taskId = generateUuid();
    String accountId = generateUuid();
    DelegateTask task =
        DelegateTask.builder().uuid(taskId).accountId(accountId).selectionLogsTrackingEnabled(true).build();
    delegateSelectionLogsService.logDelegateTaskInfo(task);
    List<DelegateSelectionLogParams> delegateSelectionLogParams =
        delegateSelectionLogsService.fetchTaskSelectionLogs(accountId, taskId);
    assertThat(delegateSelectionLogParams).isEmpty();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldLogDelegateTaskInfo() {
    String taskId = generateUuid();
    String accountId = generateUuid();
    SelectorCapability selectorCapability = SelectorCapability.builder()
                                                .selectorOrigin("taskSelector")
                                                .capabilityType(CapabilityType.SELECTORS)
                                                .selectors(Sets.newHashSet("sel1", "sel2", "sel2"))
                                                .build();
    DelegateTask task = DelegateTask.builder()
                            .uuid(taskId)
                            .accountId(accountId)
                            .selectionLogsTrackingEnabled(true)
                            .executionCapabilities(Collections.singletonList(selectorCapability))
                            .build();

    delegateSelectionLogsService.logDelegateTaskInfo(task);
    List<DelegateSelectionLogParams> delegateSelectionLogParams = fetchSelectionLogs(accountId, taskId);

    assertThat(delegateSelectionLogParams).isNotEmpty();
    assertThat(delegateSelectionLogParams.size()).isEqualTo(1);
    assertThat(delegateSelectionLogParams.get(0).getConclusion()).isEqualTo(INFO);
    assertThat(delegateSelectionLogParams.get(0).getEventTimestamp()).isNotNull();
    assertThat(delegateSelectionLogParams.get(0).getMessage())
        .isEqualTo("[Selector(s) originated from taskSelector[sel2, sel1] ]");
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldLogDelegateTaskInfoWithNoExecutionCapabilityString() {
    String taskId = generateUuid();
    String accountId = generateUuid();

    DelegateTask task = DelegateTask.builder()
                            .uuid(taskId)
                            .accountId(accountId)
                            .selectionLogsTrackingEnabled(true)
                            .executionCapabilities(asList(HttpConnectionExecutionCapability.builder().url("").build()))
                            .build();
    delegateSelectionLogsService.logDelegateTaskInfo(task);
    List<DelegateSelectionLogParams> delegateSelectionLogParams =
        delegateSelectionLogsService.fetchTaskSelectionLogs(accountId, taskId);
    assertThat(delegateSelectionLogParams).isEmpty();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldLogDelegateTaskInfoWithExecutionCapabilityString() {
    String taskId = generateUuid();
    String accountId = generateUuid();

    DelegateTask task = DelegateTask.builder()
                            .uuid(taskId)
                            .accountId(accountId)
                            .selectionLogsTrackingEnabled(true)
                            .executionCapabilities(
                                asList(HttpConnectionExecutionCapability.builder().url("https://google.com").build()))
                            .build();
    delegateSelectionLogsService.logDelegateTaskInfo(task);
    delegateSelectionLogsService.purgeSelectionLogs();
    List<DelegateSelectionLogParams> delegateSelectionLogParams = fetchSelectionLogs(accountId, taskId);
    assertThat(delegateSelectionLogParams).isNotEmpty();
    assertThat(delegateSelectionLogParams.get(0).getMessage()).isEqualTo("[Capability reach URL: https://google.com ]");
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldNotLogEligibleDelegates() {
    DelegateTask task = DelegateTask.builder()
                            .uuid(generateUuid())
                            .accountId(generateUuid())
                            .selectionLogsTrackingEnabled(true)
                            .build();
    assertThatCode(() -> delegateSelectionLogsService.logEligibleDelegatesToExecuteTask(Sets.newHashSet(), task, false))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldLogNonSelectedDelegates() {
    String taskId = generateUuid();
    String accountId = generateUuid();
    String delegate1 = generateUuid();
    String delegate2 = generateUuid();
    String delegate3 = generateUuid();
    String delegate4 = generateUuid();
    String delegate5 = generateUuid();
    String delegate6 = generateUuid();

    Map<String, List<String>> nonSelected = new HashMap<>();
    nonSelected.put(CAN_NOT_ASSIGN_CG_NG_TASK_GROUP, Lists.newArrayList(delegate1));
    nonSelected.put(CAN_NOT_ASSIGN_DELEGATE_SCOPE_GROUP, Lists.newArrayList(delegate2));
    nonSelected.put(CAN_NOT_ASSIGN_PROFILE_SCOPE_GROUP, Lists.newArrayList(delegate3));
    nonSelected.put(CAN_NOT_ASSIGN_SELECTOR_TASK_GROUP, Lists.newArrayList(delegate4));
    nonSelected.put(CAN_NOT_ASSIGN_TASK_GROUP, Lists.newArrayList(delegate5));
    nonSelected.put(CAN_NOT_ASSIGN_OWNER, Lists.newArrayList(delegate6));

    DelegateTask task =
        DelegateTask.builder().uuid(taskId).selectionLogsTrackingEnabled(true).accountId(accountId).build();
    delegateSelectionLogsService.logNonSelectedDelegates(task, nonSelected);
    List<DelegateSelectionLogParams> delegateSelectionLogParams = fetchSelectionLogs(accountId, taskId);

    assertThat(delegateSelectionLogParams).isNotEmpty();
    assertThat(delegateSelectionLogParams.size()).isEqualTo(4);
    assertThat(delegateSelectionLogParams.get(0).getConclusion()).isEqualTo(NON_SELECTED);
    assertThat(delegateSelectionLogParams.get(1).getConclusion()).isEqualTo(NON_SELECTED);
    assertThat(delegateSelectionLogParams.get(2).getConclusion()).isEqualTo(NON_SELECTED);
    assertThat(delegateSelectionLogParams.get(3).getConclusion()).isEqualTo(NON_SELECTED);

    assertThat(delegateSelectionLogParams.get(0).getEventTimestamp()).isNotNull();
    assertThat(delegateSelectionLogParams.get(1).getEventTimestamp()).isNotNull();
    assertThat(delegateSelectionLogParams.get(2).getEventTimestamp()).isNotNull();
    assertThat(delegateSelectionLogParams.get(3).getEventTimestamp()).isNotNull();
    List<String> delegateSelectionLogMessages =
        delegateSelectionLogParams.stream().map(DelegateSelectionLogParams::getMessage).collect(Collectors.toList());
    assertThat(delegateSelectionLogMessages).contains(CAN_NOT_ASSIGN_PROFILE_SCOPE_GROUP + " : " + delegate3);
    assertThat(delegateSelectionLogMessages).contains(CAN_NOT_ASSIGN_TASK_GROUP + " : " + delegate5);
    assertThat(delegateSelectionLogMessages).contains(CAN_NOT_ASSIGN_DELEGATE_SCOPE_GROUP + " : " + delegate2);
    assertThat(delegateSelectionLogMessages).contains(CAN_NOT_ASSIGN_SELECTOR_TASK_GROUP + " : " + delegate4);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldLogBroadcastToDelegate() {
    String taskId = generateUuid();
    String accountId = generateUuid();
    String delegateId = generateUuid();
    DelegateTask task =
        DelegateTask.builder().uuid(taskId).accountId(accountId).selectionLogsTrackingEnabled(true).build();
    delegateSelectionLogsService.logBroadcastToDelegate(Sets.newHashSet(delegateId), task);
    List<DelegateSelectionLogParams> delegateSelectionLogParams = fetchSelectionLogs(accountId, taskId);
    assertThat(delegateSelectionLogParams).isNotEmpty();
    assertThat(delegateSelectionLogParams.size()).isEqualTo(1);
    assertThat(delegateSelectionLogParams.get(0).getConclusion()).isEqualTo(BROADCAST);
    assertThat(delegateSelectionLogParams.get(0).getMessage()).startsWith(BROADCASTING_DELEGATES);
    assertThat(delegateSelectionLogParams.get(0).getEventTimestamp()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldNotGenerateSelectionLog() {
    String accountId = generateUuid();
    String taskId = generateUuid();
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(true);
    assertThat(persistence.get(DelegateSelectionLog.class, taskId)).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void shouldSaveDuplicates() {
    String taskId = generateUuid();
    String accountId = generateUuid();

    wingsPersistence.ensureIndexForTesting(DelegateSelectionLog.class);

    Concurrent.test(10, n -> {
      List<DelegateSelectionLog> delegateSelectionLogs = createDelegateSelectionLogs(taskId, accountId);
      delegateSelectionLogs.forEach(delegateSelectionLog -> delegateSelectionLogsService.save(delegateSelectionLog));
    });

    fetchSelectionLogs(accountId, taskId);
    assertThat(wingsPersistence.createQuery(DelegateSelectionLog.class)
                   .filter(DelegateSelectionLogKeys.taskId, taskId)
                   .filter(DelegateSelectionLogKeys.accountId, accountId)
                   .count())
        .isEqualTo(20L);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void fetchSelectionLogWhenExecuteOnHarnessHostedDelegates() {
    String taskId = generateUuid();
    String accountId = generateUuid();
    String globalAccountId = generateUuid();
    DelegateTask task = DelegateTask.builder()
                            .uuid(taskId)
                            .accountId(globalAccountId)
                            .executeOnHarnessHostedDelegates(true)
                            .secondaryAccountId(accountId)
                            .selectionLogsTrackingEnabled(true)
                            .build();
    delegateSelectionLogsService.logNoEligibleDelegatesToExecuteTask(task);
    List<DelegateSelectionLogParams> delegateSelectionLogParams = fetchSelectionLogs(accountId, taskId);
    assertThat(delegateSelectionLogParams).isNotEmpty();
    assertThat(delegateSelectionLogParams.size()).isEqualTo(1);
    assertThat(delegateSelectionLogParams.get(0).getConclusion()).isEqualTo(REJECTED);
    assertThat(delegateSelectionLogParams.get(0).getMessage()).isEqualTo(NO_ELIGIBLE_DELEGATES);
    assertThat(delegateSelectionLogParams.get(0).getEventTimestamp()).isNotNull();
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testLogTaskAssigned_selectionLogsTrackingEnabled() {
    String delegateId = generateUuid();
    String taskId = generateUuid();
    String delegateName = "delegateName";
    String hotsName = "hotsName";
    String accountId = generateUuid();
    Delegate delegate =
        Delegate.builder().accountId(accountId).uuid(delegateId).delegateName(delegateName).hostName(hotsName).build();
    DelegateTask task =
        DelegateTask.builder().uuid(taskId).accountId(accountId).selectionLogsTrackingEnabled(true).build();
    when(delegateCache.get(accountId, delegateId)).thenReturn(delegate);

    delegateSelectionLogsService.logTaskAssigned(delegateId, task);
    List<DelegateSelectionLogParams> delegateSelectionLogParams = fetchSelectionLogs(accountId, taskId);
    assertThat(delegateSelectionLogParams).isNotEmpty();
    assertThat(delegateSelectionLogParams.size()).isEqualTo(1);
    assertThat(delegateSelectionLogParams.get(0).getConclusion()).isEqualTo(ASSIGNED);
    assertThat(delegateSelectionLogParams.get(0).getMessage())
        .isEqualTo("Delegate assigned for task execution : [hotsName]");
    assertThat(delegateSelectionLogParams.get(0).getEventTimestamp()).isNotNull();
    assertThat(delegateSelectionLogParams.get(0).getDelegateHostName()).isEqualTo(hotsName);
    assertThat(delegateSelectionLogParams.get(0).getDelegateId()).isEqualTo(delegateId);
    assertThat(delegateSelectionLogParams.get(0).getDelegateName()).isEqualTo(delegateName);
  }

  private Map<String, String> obtainTaskSetupAbstractions() {
    String envId = generateUuid();
    Environment env = new Environment();
    env.setUuid(envId);
    env.setName("env-" + envId);
    env.setEnvironmentType(PROD);
    wingsPersistence.save(env);

    String serviceId = generateUuid();
    Service service = new Service();
    service.setUuid(serviceId);
    service.setName("srv-" + serviceId);
    wingsPersistence.save(service);

    String appId = generateUuid();
    Application app = new Application();
    app.setUuid(appId);
    app.setName("app-" + appId);
    wingsPersistence.save(app);

    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(Cd1SetupFields.APP_ID_FIELD, appId);
    setupAbstractions.put(Cd1SetupFields.SERVICE_ID_FIELD, serviceId);
    setupAbstractions.put(Cd1SetupFields.ENV_ID_FIELD, envId);

    return setupAbstractions;
  }

  private List<DelegateSelectionLog> createDelegateSelectionLogs(String taskId, String accountId) {
    DelegateSelectionLog selectionLog1 = DelegateSelectionLog.builder()
                                             .accountId(generateUuid())
                                             .taskId(taskId)
                                             .accountId(accountId)
                                             .message(MISSING_SELECTOR_MESSAGE)
                                             .build();

    DelegateSelectionLog selectionLog2 = DelegateSelectionLog.builder()
                                             .accountId(generateUuid())
                                             .taskId(taskId)
                                             .accountId(accountId)
                                             .message(MISSING_SELECTOR_MESSAGE)
                                             .build();

    return Arrays.asList(selectionLog1, selectionLog2);
  }

  private List<DelegateSelectionLogParams> fetchSelectionLogs(String accountId, String taskId) {
    List<DelegateSelectionLogParams> delegateSelectionLogParams = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      delegateSelectionLogParams = delegateSelectionLogsService.fetchTaskSelectionLogs(accountId, taskId);
      if (EmptyPredicate.isNotEmpty(delegateSelectionLogParams)) {
        return delegateSelectionLogParams;
      }
      sleep(of(1, SECONDS));
    }

    return delegateSelectionLogParams;
  }
}
