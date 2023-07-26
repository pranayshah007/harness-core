/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.beans.DelegateTask.Status.STARTED;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability;
import static io.harness.rule.OwnerRule.JENNY;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static java.util.Collections.singletonList;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.TaskDataV2;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.task.TaskFailureReason;
import io.harness.iterator.DelegateDisconnectDetectorIterator;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.network.LocalhostUtils;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskServiceImpl;
import io.harness.perpetualtask.PerpetualTaskState;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.perpetualtask.internal.PerpetualTaskRecordDao;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateCache;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AwsConfig;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.validation.core.DelegateConnectionResult;
import software.wings.service.impl.DelegateDao;
import software.wings.service.impl.DelegateObserver;
import software.wings.service.impl.DelegateServiceImpl;
import software.wings.service.impl.DelegateTaskServiceClassicImpl;
import software.wings.service.impl.aws.model.AwsIamListInstanceRolesRequest;
import software.wings.service.impl.aws.model.AwsIamRequest;
import software.wings.service.impl.instance.InstanceSyncTestConstants;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AssignDelegateService;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.protobuf.util.Durations;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DelegateDisconnectDetectorIteratorTest extends WingsBaseTest {
  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @InjectMocks @Inject private DelegateDisconnectDetectorIterator delegateDisconnectDetectorIterator;
  @Mock private AssignDelegateService assignDelegateService;
  @InjectMocks @Inject private DelegateTaskServiceClassicImpl delegateTaskServiceClassic;
  @Inject private HPersistence persistence;

  @Mock private LoadingCache<String, List<Delegate>> accountDelegatesCache;
  @Inject private PerpetualTaskRecordDao perpetualTaskRecordDao;
  @Inject private Clock clock;

  @Mock
  private LoadingCache<ImmutablePair<String, String>, Optional<DelegateConnectionResult>> delegateConnectionResultCache;

  @Inject private DelegateDao delegateDao;
  @Inject private KryoSerializer kryoSerializer;

  private final int port = LocalhostUtils.findFreePort();
  @Rule public WireMockRule wireMockRule = new WireMockRule(port);
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Mock private DelegateCache delegateCache;

  @Mock private AccountService accountService;
  @InjectMocks @Inject private DelegateServiceImpl delegateService;
  @InjectMocks @Inject private PerpetualTaskServiceImpl perpetualTaskService;

  private static final String VERSION = "1.0.0";
  private static final String DELEGATE_TYPE = "dockerType";
  private final String REGION = "region";
  private final String SETTING_ID = "settingId";
  private final String CLUSTER_NAME = "clusterName";

  private static final List<String> supportedTasks = Arrays.stream(TaskType.values()).map(Enum::name).collect(toList());

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDeleteDelegateTaskAssignedOnDelegateDisconnect() throws ExecutionException {
    Delegate delegate = createDelegate(ACCOUNT_ID);
    when(accountService.getFromCacheWithFallback(ACCOUNT_ID)).thenReturn(Account.Builder.anAccount().build());
    DelegateTask task1 = createDelegateTask(delegate);
    task1.setDelegateId(delegate.getUuid());
    task1.setStatus(STARTED);
    persistence.save(task1);
    DelegateTask task2 = createDelegateTask(delegate);
    task2.setDelegateId(delegate.getUuid());
    task2.setStatus(STARTED);
    persistence.save(task2);
    DelegateTask task3 = createDelegateTask(delegate);
    task3.setDelegateId(delegate.getUuid());
    task3.setStatus(STARTED);
    persistence.save(task3);
    assertThat(getAlreadyStartedDelegateTask(ACCOUNT_ID, delegate.getUuid())).hasSize(3);
    when(delegateCache.get(ACCOUNT_ID, delegate.getUuid(), false)).thenReturn(delegate);
    when(assignDelegateService.getDelegateTaskAssignmentFailureMessage(task1, TaskFailureReason.DELEGATE_DISCONNECTED))
        .thenReturn("ERROR1");
    when(assignDelegateService.getDelegateTaskAssignmentFailureMessage(task2, TaskFailureReason.DELEGATE_DISCONNECTED))
        .thenReturn("ERROR2");
    when(assignDelegateService.getDelegateTaskAssignmentFailureMessage(task3, TaskFailureReason.DELEGATE_DISCONNECTED))
        .thenReturn("ERROR3");

    delegateDisconnectDetectorIterator.handle(delegate);
    delegateTaskServiceClassic.markAllTasksFailedForDelegate(ACCOUNT_ID, delegate.getUuid());
    assertThat(getAlreadyStartedDelegateTask(ACCOUNT_ID, delegate.getUuid())).hasSize(0);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDeleteDelegateTaskAssignedOnDelegateDisconnect_NoAssignedTask() throws ExecutionException {
    Delegate delegate = createDelegate(ACCOUNT_ID);
    delegateDisconnectDetectorIterator.handle(delegate);
    assertThat(getAlreadyStartedDelegateTask(ACCOUNT_ID, delegate.getUuid())).hasSize(0);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDeleteDelegateMarkedAsDisconnected() throws ExecutionException {
    Delegate delegate = createDelegate(ACCOUNT_ID);
    delegateDisconnectDetectorIterator.handle(delegate);
    Delegate updatedDelegate = persistence.get(Delegate.class, delegate.getUuid());
    assertThat(updatedDelegate.isDisconnected()).isTrue();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDeletePerpetualTaskOnDelegateDisconnect() throws ExecutionException {
    DelegateObserver delegateObserver = mock(DelegateObserver.class);
    delegateService.getSubject().register(delegateObserver);

    Delegate delegate = createDelegate(ACCOUNT_ID);
    createPerpetualTaskRecordAndAssign(delegate.getUuid());
    assertThat(perpetualTaskService.listAssignedTasks(delegate.getUuid(), ACCOUNT_ID)).hasSize(1);
    delegateDisconnectDetectorIterator.handle(delegate);
    verify(delegateObserver).onDisconnected(ACCOUNT_ID, delegate.getUuid());
  }

  private DelegateTask createDelegateTask(Delegate delegate) throws ExecutionException {
    when(accountDelegatesCache.get(ACCOUNT_ID)).thenReturn(singletonList(delegate));
    when(delegateCache.get(ACCOUNT_ID, delegate.getUuid())).thenReturn(delegate);
    AwsIamRequest request = AwsIamListInstanceRolesRequest.builder().awsConfig(AwsConfig.builder().build()).build();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(ACCOUNT_ID)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, InstanceSyncTestConstants.APP_ID)
            .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
            .taskDataV2(TaskDataV2.builder()
                            .async(false)
                            .taskType(TaskType.AWS_IAM_TASK.name())
                            .parameters(new Object[] {request})
                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                            .build())
            .build();

    HttpConnectionExecutionCapability matchingExecutionCapability =
        buildHttpConnectionExecutionCapability("https//aws.amazon.com", null);
    delegateTask.setExecutionCapabilities(Arrays.asList(matchingExecutionCapability));
    DelegateConnectionResult connectionResult = DelegateConnectionResult.builder()
                                                    .accountId(ACCOUNT_ID)
                                                    .delegateId(delegate.getUuid())
                                                    .criteria(matchingExecutionCapability.fetchCapabilityBasis())
                                                    .validated(true)
                                                    .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    when(assignDelegateService.getEligibleDelegatesToExecuteTaskV2(delegateTask))
        .thenReturn(Arrays.asList(delegate.getUuid()));
    when(assignDelegateService.getConnectedDelegateList(Arrays.asList(delegate.getUuid()), delegateTask))
        .thenReturn(Arrays.asList(delegate.getUuid()));
    delegateTaskServiceClassic.processDelegateTaskV2(delegateTask, QUEUED);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    return task;
  }

  private Delegate createDelegate(String accountId) {
    Delegate delegate = createDelegateBuilder(accountId).build();
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

  private List<DelegateTask> getAlreadyStartedDelegateTask(String accountId, String delegateId) {
    return persistence.createQuery(DelegateTask.class)
        .filter(DelegateTaskKeys.accountId, accountId)
        .filter(DelegateTaskKeys.delegateId, delegateId)
        .filter(DelegateTaskKeys.status, STARTED)
        .asList();
  }

  public PerpetualTaskClientContext clientContext() {
    Map<String, String> clientParamMap = new HashMap<>();
    clientParamMap.put(REGION, REGION);
    clientParamMap.put(SETTING_ID, SETTING_ID);
    clientParamMap.put(CLUSTER_NAME, CLUSTER_NAME);
    return PerpetualTaskClientContext.builder().clientParams(clientParamMap).build();
  }

  private PerpetualTaskRecord createPerpetualTaskRecordAndAssign(String delegateId) {
    PerpetualTaskClientContext clientContext = clientContext();
    PerpetualTaskRecord perpetualTaskRecord = PerpetualTaskRecord.builder()
                                                  .accountId(ACCOUNT_ID)
                                                  .perpetualTaskType(PerpetualTaskType.K8S_WATCH)
                                                  .clientContext(clientContext())
                                                  .delegateId(delegateId)
                                                  .build();
    perpetualTaskRecord.setClientContext(clientContext);
    String taskId = perpetualTaskService.createTask(PerpetualTaskType.ECS_CLUSTER, ACCOUNT_ID, clientContext,
        PerpetualTaskSchedule.newBuilder()
            .setInterval(Durations.fromSeconds(600))
            .setTimeout(Durations.fromMillis(180000))
            .build(),
        false, "");
    assertThat(taskId).isNotNull();
    perpetualTaskRecord.setState(PerpetualTaskState.TASK_ASSIGNED);
    perpetualTaskRecordDao.save(perpetualTaskRecord);

    return perpetualTaskRecordDao.getTask(taskId);
  }
}
