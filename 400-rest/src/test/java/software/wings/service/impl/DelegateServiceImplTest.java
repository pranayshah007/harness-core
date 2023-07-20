/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.DelegateType.KUBERNETES;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANUPAM;
import static io.harness.rule.OwnerRule.ARPIT;
import static io.harness.rule.OwnerRule.BOJAN;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.GAURAV;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.JOHANNES;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.NICOLAS;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.UTSAV;
import static io.harness.rule.OwnerRule.VLAD;
import static io.harness.rule.OwnerRule.VUK;

import static software.wings.utils.Utils.uuidToIdentifier;
import static software.wings.utils.WingsTestConstants.DELEGATE_ID;
import static software.wings.utils.WingsTestConstants.HOST_NAME;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.agent.beans.AgentMtlsEndpoint;
import io.harness.agent.sdk.HarnessAlwaysRun;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.audit.ResourceTypeConstants;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delegate.DelegateGlobalAccountController;
import io.harness.delegate.NoEligibleDelegatesInAccountException;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateInitializationDetails;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateSetupDetails;
import io.harness.delegate.beans.DelegateSetupDetails.DelegateSetupDetailsBuilder;
import io.harness.delegate.beans.DelegateSize;
import io.harness.delegate.beans.DelegateStringResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.DelegateTaskResponse.ResponseCode;
import io.harness.delegate.beans.DelegateTokenDetails;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.delegate.beans.DelegateType;
import io.harness.delegate.beans.DelegateUnregisterRequest;
import io.harness.delegate.beans.K8sConfigDetails;
import io.harness.delegate.beans.K8sPermissionType;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskDataV2;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.events.DelegateDeleteEvent;
import io.harness.delegate.events.DelegateUnregisterEvent;
import io.harness.delegate.events.DelegateUpsertEvent;
import io.harness.delegate.service.DelegateVersionService;
import io.harness.delegate.service.intfc.DelegateNgTokenService;
import io.harness.delegate.task.http.HttpTaskParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.model.response.CEK8sDelegatePrerequisite;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.observer.Subject;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.filter.OutboxEventFilter;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.serializer.KryoSerializer;
import io.harness.service.dto.RetryDelegate;
import io.harness.service.impl.DelegateSyncServiceImpl;
import io.harness.service.impl.DelegateTaskServiceImpl;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateCallbackRegistry;
import io.harness.service.intfc.DelegateCallbackService;
import io.harness.service.intfc.DelegateTaskRetryObserver;
import io.harness.version.VersionInfoManager;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.CEDelegateStatus;
import software.wings.beans.HttpStateExecutionResponse;
import software.wings.beans.KmsConfig;
import software.wings.beans.TaskType;
import software.wings.beans.VaultConfig;
import software.wings.events.TestUtils;
import software.wings.expression.ManagerPreviewExpressionEvaluator;
import software.wings.features.api.UsageLimitedFeature;
import software.wings.persistence.mail.EmailData;
import software.wings.service.impl.TemplateParameters.TemplateParametersBuilder;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AssignDelegateService;
import software.wings.service.intfc.DelegateProfileService;
import software.wings.service.intfc.EmailNotificationService;
import software.wings.service.intfc.SettingsService;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.serializer.HObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class DelegateServiceImplTest extends WingsBaseTest {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final String APP_ID = "APP_ID";
  private static final String DELEGATE_NAME = "delegateName";
  private static final String DELEGATE_GROUP_IDENTIFIER = "_IdentifierSample";
  private static final String ORG_IDENTIFIER = "orgId";
  private static final String PROJECT_IDENTIFIER = "projectId";

  private static final String HTTP_VAUTL_URL = "http://vautl.com";
  private static final String GOOGLE_COM = "http://google.com";
  private static final String US_EAST_2 = "us-east-2";
  private static final String AWS_KMS_URL = "https://kms.us-east-2.amazonaws.com";
  private static final String SECRET_URL = "http://google.com/?q=${secretManager.obtain(\"test\", 1234)}";

  private static final String VERSION = "1.0.0";
  private static final String TEST_DELEGATE_PROFILE_ID = generateUuid();
  private static final long TEST_PROFILE_EXECUTION_TIME = System.currentTimeMillis();

  private static final String TEST_DELEGATE_NAME = "testDelegateName";
  private static final String TEST_DELEGATE_GROUP_NAME = "testDelegateGroupName";
  private static final String TEST_DELEGATE_GROUP_NAME_IDENTIFIER = "_testDelegateGroupName";
  private static final String TOKEN_NAME = "tokenName";
  public static final String GLOBAL_DELEGATE_ACCOUNT_ID = "GLOBAL_DELEGATE_ACCOUNT_ID";

  @Mock private UsageLimitedFeature delegatesFeature;
  @Mock private Broadcaster broadcaster;
  @Mock private BroadcasterFactory broadcasterFactory;
  @Mock private DelegateTaskBroadcastHelper broadcastHelper;
  @Mock private DelegateCallbackRegistry delegateCallbackRegistry;
  @Mock private EmailNotificationService emailNotificationService;
  @Mock private AccountService accountService;
  @Mock private DelegateVersionService delegateVersionService;
  @Mock private Account account;
  @Mock private DelegateProfileService delegateProfileService;
  @Mock private DelegateCache delegateCache;
  @Mock private DelegateNgTokenService delegateNgTokenService;
  @InjectMocks @Inject private DelegateServiceImpl delegateService;
  @InjectMocks @Inject private DelegateTaskServiceClassicImpl delegateTaskServiceClassic;
  @InjectMocks @Inject private DelegateSyncServiceImpl delegateSyncService;
  @InjectMocks @Inject private DelegateTaskServiceImpl delegateTaskService;
  @InjectMocks @Inject private DelegateGlobalAccountController delegateGlobalAccountController;

  @Mock private AssignDelegateService assignDelegateService;
  @Mock private SettingsService settingsService;

  @InjectMocks @Spy private DelegateTaskServiceClassicImpl spydelegateTaskServiceClassic;
  @Inject private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private VersionInfoManager versionInfoManager;
  @Mock private Subject<DelegateTaskRetryObserver> retryObserverSubject;
  @Inject private HPersistence persistence;
  @Inject private OutboxService outboxService;
  @Inject private TestUtils testUtils;
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setUp() throws IllegalAccessException {
    when(accountService.getFromCacheWithFallback(ACCOUNT_ID)).thenReturn(Account.Builder.anAccount().build());
    when(broadcasterFactory.lookup(anyString(), anyBoolean())).thenReturn(broadcaster);
    FieldUtils.writeField(delegateTaskService, "retryObserverSubject", retryObserverSubject, true);
    FieldUtils.writeField(delegateService, "subject", new Subject<>(), true);
  }

  private DelegateBuilder createDelegateBuilder() {
    return Delegate.builder()
        .accountId(ACCOUNT_ID)
        .ip("127.0.0.1")
        .hostName("localhost")
        .version(VERSION)
        .status(DelegateInstanceStatus.ENABLED)
        .lastHeartBeat(System.currentTimeMillis());
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  @HarnessAlwaysRun
  public void shouldExecuteTask() {
    Delegate delegate = createDelegateBuilder().build();
    persistence.save(delegate);
    DelegateTask delegateTask = getDelegateTaskV2();
    when(assignDelegateService.getEligibleDelegatesToExecuteTaskV2(any()))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    when(assignDelegateService.getConnectedDelegateList(any(), any()))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    when(assignDelegateService.canAssign(eq(delegateTask.getDelegateId()), any())).thenReturn(true);
    when(assignDelegateService.retrieveActiveDelegates(eq(delegateTask.getAccountId()), any()))
        .thenReturn(Collections.singletonList(delegate.getUuid()));

    RetryDelegate retryDelegate = RetryDelegate.builder().retryPossible(true).delegateTask(delegateTask).build();
    when(retryObserverSubject.fireProcess(any(), any())).thenReturn(retryDelegate);

    Thread thread = new Thread(() -> {
      await().atMost(5L, TimeUnit.SECONDS).until(() -> isNotEmpty(delegateSyncService.syncTaskWaitMap));
      DelegateTask task =
          persistence.createQuery(DelegateTask.class).filter("accountId", delegateTask.getAccountId()).get();

      delegateTaskService.processDelegateResponse(task.getAccountId(), delegate.getUuid(), task.getUuid(),
          DelegateTaskResponse.builder()
              .accountId(task.getAccountId())
              .response(HttpStateExecutionResponse.builder().executionStatus(ExecutionStatus.SUCCESS).build())
              .responseCode(ResponseCode.OK)
              .build());
      new Thread(delegateSyncService).start();
    });
    thread.start();
    when(assignDelegateService.getEligibleDelegatesToExecuteTask(any(DelegateTask.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));

    DelegateResponseData responseData = delegateTaskServiceClassic.executeTaskV2(delegateTask);
    assertThat(responseData).isInstanceOf(HttpStateExecutionResponse.class);
    HttpStateExecutionResponse httpResponse = (HttpStateExecutionResponse) responseData;
    assertThat(httpResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void shouldSaveDelegateTaskWithPreAssignedDelegateId_Sync() {
    DelegateTask delegateTask = getDelegateTaskV2();
    delegateTask.getData().setAsync(false);
    when(assignDelegateService.getDelegateTaskAssignmentFailureMessage(any(), any()))
        .thenReturn("No eligible delegate(s) in account to execute task. ");
    thrown.expect(NoEligibleDelegatesInAccountException.class);
    delegateTaskServiceClassic.processDelegateTaskV2(delegateTask, DelegateTask.Status.QUEUED);
    assertThat(delegateTask.getBroadcastCount()).isZero();
    verify(broadcastHelper, times(0)).rebroadcastDelegateTask(any());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void shouldSaveDelegateTaskWithPreAssignedDelegateId_Async() {
    DelegateTask delegateTask = getDelegateTaskV2();
    delegateTask.getData().setAsync(true);
    delegateTaskServiceClassic.processDelegateTaskV2(delegateTask, DelegateTask.Status.QUEUED);
    assertThat(delegateTask.getBroadcastCount()).isZero();
    verify(broadcastHelper, times(0)).rebroadcastDelegateTask(any());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldSaveDelegateTaskWithoutPreAssignedDelegateIdSetToMustExecuteOn() {
    String delegateId = generateUuid();
    String taskId = generateUuid();
    when(assignDelegateService.getEligibleDelegatesToExecuteTaskV2(any(DelegateTask.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    when(assignDelegateService.getConnectedDelegateList(any(), any()))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));

    DelegateTask delegateTask = getDelegateTaskV2();
    delegateTask.getTaskDataV2().setAsync(false);
    delegateTask.setUuid(taskId);
    when(assignDelegateService.getEligibleDelegatesToExecuteTaskV2(any(DelegateTask.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));

    delegateTaskServiceClassic.processDelegateTaskV2(delegateTask, DelegateTask.Status.QUEUED);
    assertThat(persistence.get(DelegateTask.class, taskId).getPreAssignedDelegateId()).isNotEqualTo(delegateId);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldObtainDelegateName() {
    when(delegatesFeature.getMaxUsageAllowedForAccount(anyString())).thenReturn(Integer.MAX_VALUE);

    String delegateId = generateUuid();
    assertThat(delegateService.obtainDelegateName(null, delegateId, true)).isEmpty();
    assertThat(delegateService.obtainDelegateName("accountId", delegateId, true)).isEqualTo(delegateId);

    DelegateBuilder delegateBuilder = Delegate.builder();

    when(delegateProfileService.fetchCgPrimaryProfile(ACCOUNT_ID))
        .thenReturn(DelegateProfile.builder().uuid(TEST_DELEGATE_PROFILE_ID).build());
    delegateService.add(delegateBuilder.uuid(delegateId).accountId(ACCOUNT_ID).build());
    assertThat(delegateService.obtainDelegateName("accountId", delegateId, true)).isEqualTo(delegateId);

    delegateService.add(delegateBuilder.accountId(ACCOUNT_ID).build());
    assertThat(delegateService.obtainDelegateName(ACCOUNT_ID, delegateId, true)).isEqualTo(delegateId);

    Delegate delegate = delegateBuilder.hostName("hostName").build();
    delegateService.add(delegate);
    when(delegateCache.get(ACCOUNT_ID, delegateId, true)).thenReturn(delegate);
    assertThat(delegateService.obtainDelegateName(ACCOUNT_ID, delegateId, true)).isEqualTo("hostName");

    Delegate delegate2 = delegateBuilder.hostName("delegateName").build();
    delegateService.add(delegate2);
    when(delegateCache.get(ACCOUNT_ID, delegateId, true)).thenReturn(delegate2);
    assertThat(delegateService.obtainDelegateName(ACCOUNT_ID, delegateId, true)).isEqualTo("delegateName");
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldAcquireDelegateTaskWhitelistedDelegateAndFFisOFF() {
    final String taskId = "XYZ";
    final Delegate delegate = createDelegateBuilder().build();
    when(delegateCache.get(ACCOUNT_ID, delegate.getUuid())).thenReturn(delegate);
    when(assignDelegateService.getEligibleDelegatesToExecuteTaskV2(any(DelegateTask.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    when(assignDelegateService.getConnectedDelegateList(any(), any()))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));

    final DelegateTask delegateTask = getDelegateTaskV2();
    doReturn(delegateTask)
        .when(spydelegateTaskServiceClassic)
        .getUnassignedDelegateTask(ACCOUNT_ID, "XYZ", delegate.getUuid());

    doReturn(getDelegateTaskPackage())
        .when(spydelegateTaskServiceClassic)
        .assignTask(delegate.getUuid(), taskId, delegateTask, null);

    when(assignDelegateService.canAssign(delegate.getUuid(), delegateTask)).thenReturn(true);
    when(assignDelegateService.isWhitelisted(delegateTask, delegate.getUuid())).thenReturn(true);
    when(assignDelegateService.shouldValidate(delegateTask, delegate.getUuid())).thenReturn(false);

    spydelegateTaskServiceClassic.acquireDelegateTask(ACCOUNT_ID, delegate.getUuid(), taskId, null);

    verify(spydelegateTaskServiceClassic).assignTask(delegate.getUuid(), taskId, delegateTask, null);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldNotAcquireDelegateTaskIfTaskIsNull() {
    Delegate delegate = createDelegateBuilder().build();
    doReturn(delegate).when(delegateCache).get(ACCOUNT_ID, delegate.getUuid(), false);
    doReturn(null).when(spydelegateTaskServiceClassic).getUnassignedDelegateTask(ACCOUNT_ID, "XYZ", delegate.getUuid());
    assertThat(spydelegateTaskServiceClassic.acquireDelegateTask(ACCOUNT_ID, delegate.getUuid(), "XYZ", null))
        .isNotNull();
  }

  private DelegateTaskPackage getDelegateTaskPackage() {
    DelegateTask delegateTask = getDelegateTask();
    return DelegateTaskPackage.builder().delegateTaskId(delegateTask.getUuid()).data(delegateTask.getData()).build();
  }

  private DelegateTask getDelegateTask() {
    return DelegateTask.builder()
        .uuid(generateUuid())
        .accountId(ACCOUNT_ID)
        .waitId(generateUuid())
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
        .version(VERSION)
        .data(TaskData.builder()
                  .async(false)
                  .taskType(TaskType.HTTP.name())
                  .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                  .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                  .build())
        .tags(new ArrayList<>())
        .build();
  }

  private DelegateTask getDelegateTaskV2() {
    return DelegateTask.builder()
        .uuid(generateUuid())
        .accountId(ACCOUNT_ID)
        .waitId(generateUuid())
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
        .version(VERSION)
        .data(TaskData.builder()
                  .async(false)
                  .taskType(TaskType.HTTP.name())
                  .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                  .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                  .build())
        .taskDataV2(TaskDataV2.builder()
                        .async(false)
                        .taskType(TaskType.HTTP.name())
                        .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                        .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                        .build())
        .tags(new ArrayList<>())
        .build();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testValidateThatDelegateNameIsUnique() {
    String delegateName = "delegateName";
    Delegate delegate = createDelegateBuilder().build();
    delegate.setDelegateName(delegateName);
    persistence.save(delegate);
    boolean validationResult = delegateService.validateThatDelegateNameIsUnique(ACCOUNT_ID, delegateName);
    assertThat(validationResult).isFalse();

    // If the delegate doesn't exists
    boolean checkingValidationForRandomName =
        delegateService.validateThatDelegateNameIsUnique(ACCOUNT_ID, String.valueOf(System.currentTimeMillis()));
    assertThat(checkingValidationForRandomName).isTrue();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testValidateCEDelegate() {
    long lastHeartBeat = DateTime.now().getMillis();
    Delegate delegate = createDelegateBuilder()
                            .accountId(ACCOUNT_ID)
                            .delegateName(DELEGATE_NAME)
                            .lastHeartBeat(lastHeartBeat)
                            .uuid(generateUuid())
                            .build();
    persistence.save(delegate);

    when(settingsService.validateCEDelegateSetting(any(), any()))
        .thenReturn(CEK8sDelegatePrerequisite.builder().build());

    CEDelegateStatus ceDelegateStatus = delegateService.validateCEDelegate(ACCOUNT_ID, DELEGATE_NAME);

    verify(settingsService, times(1)).validateCEDelegateSetting(ACCOUNT_ID, DELEGATE_NAME);

    assertThat(ceDelegateStatus).isNotNull();
    assertThat(ceDelegateStatus.getFound()).isTrue();
    assertThat(ceDelegateStatus.getUuid()).isEqualTo(delegate.getUuid());
    assertThat(ceDelegateStatus.getMetricsServerCheck()).isNull();
    assertThat(ceDelegateStatus.getPermissionRuleList()).isNull();
    assertThat(ceDelegateStatus.getLastHeartBeat()).isGreaterThanOrEqualTo(lastHeartBeat);
    assertThat(ceDelegateStatus.getDelegateName()).isEqualTo(DELEGATE_NAME);

    assertThat(ceDelegateStatus.getConnections()).hasSizeGreaterThan(0);
    assertThat(ceDelegateStatus.getConnections().get(0).getLastHeartbeat()).isEqualTo(lastHeartBeat);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testHandleDriverResponseWithoutArguments() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(generateUuid())
                                    .accountId(generateUuid())
                                    .driverId(generateUuid())
                                    .data(TaskData.builder().async(false).build())
                                    .build();

    DelegateTaskResponse delegateTaskResponse =
        DelegateTaskResponse.builder().response(DelegateStringResponseData.builder().data("OK").build()).build();

    delegateTaskServiceClassic.handleDriverResponse(null, delegateTaskResponse);
    verify(delegateCallbackRegistry, never()).obtainDelegateCallbackService(any());

    delegateTaskServiceClassic.handleDriverResponse(delegateTask, null);
    verify(delegateCallbackRegistry, never()).obtainDelegateCallbackService(any());

    delegateTaskServiceClassic.handleDriverResponse(null, null);
    verify(delegateCallbackRegistry, never()).obtainDelegateCallbackService(any());
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testHandleDriverResponseWithNonExistingDriver() {
    DelegateTask delegateTask = mock(DelegateTask.class);
    DelegateTaskResponse delegateTaskResponse = mock(DelegateTaskResponse.class);

    when(delegateCallbackRegistry.obtainDelegateCallbackService(delegateTask.getDriverId())).thenReturn(null);

    delegateTaskServiceClassic.handleDriverResponse(delegateTask, delegateTaskResponse);

    verify(delegateTask, never()).getUuid();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testHandleDriverSyncResponse() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(generateUuid())
                                    .accountId(generateUuid())
                                    .driverId(generateUuid())
                                    .data(TaskData.builder().async(false).build())
                                    .build();

    DelegateTaskResponse delegateTaskResponse =
        DelegateTaskResponse.builder().response(DelegateStringResponseData.builder().data("OK").build()).build();

    DelegateCallbackService delegateCallbackService = mock(DelegateCallbackService.class);
    when(delegateCallbackRegistry.obtainDelegateCallbackService(delegateTask.getDriverId()))
        .thenReturn(delegateCallbackService);
    byte[] responseData = referenceFalseKryoSerializer.asDeflatedBytes(delegateTaskResponse.getResponse());

    delegateTaskServiceClassic.handleDriverResponse(delegateTask, delegateTaskResponse);

    verify(delegateCallbackService).publishSyncTaskResponse(delegateTask.getUuid(), responseData);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testHandleDriverAsyncResponse() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(generateUuid())
                                    .accountId(generateUuid())
                                    .driverId(generateUuid())
                                    .data(TaskData.builder().async(true).build())
                                    .build();

    DelegateTaskResponse delegateTaskResponse =
        DelegateTaskResponse.builder().response(DelegateStringResponseData.builder().data("OK").build()).build();

    DelegateCallbackService delegateCallbackService = mock(DelegateCallbackService.class);
    when(delegateCallbackRegistry.obtainDelegateCallbackService(delegateTask.getDriverId()))
        .thenReturn(delegateCallbackService);
    byte[] responseData = referenceFalseKryoSerializer.asDeflatedBytes(delegateTaskResponse.getResponse());

    delegateTaskServiceClassic.handleDriverResponse(delegateTask, delegateTaskResponse);

    verify(delegateCallbackService).publishAsyncTaskResponse(delegateTask.getUuid(), responseData);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testDelegateDisconnected() {
    DelegateObserver delegateObserver = mock(DelegateObserver.class);
    delegateService.getSubject().register(delegateObserver);
    String delegateConnectionId = generateUuid();
    String delegateId = persistence.save(createDelegateBuilder().delegateConnectionId(delegateConnectionId).build());
    delegateService.delegateDisconnected(ACCOUNT_ID, delegateId, delegateConnectionId);
    Delegate delegate = persistence.get(Delegate.class, delegateId);
    assertThat(delegate).isNotNull();
    assertThat(delegate.isDisconnected()).isTrue();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateObserverEventOnDelegateDisconnected() {
    DelegateObserver delegateObserver = mock(DelegateObserver.class);
    delegateService.getSubject().register(delegateObserver);
    String delegateId = generateUuid();
    String accountId = generateUuid();
    delegateService.onDelegateDisconnected(accountId, delegateId);
    verify(delegateObserver).onDisconnected(accountId, delegateId);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldSelectDelegateToRetain() {
    Delegate delegate1 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .ip("127.0.0.1")
                             .hostName("localhost")
                             .version(VERSION)
                             .disconnected(true)
                             .lastHeartBeat(System.currentTimeMillis())
                             .build();

    Delegate delegate2 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .ip("127.0.0.1")
                             .hostName("localhost")
                             .version(VERSION)
                             .lastHeartBeat(0)
                             .build();

    persistence.save(delegate1);
    persistence.save(delegate2);

    when(delegatesFeature.getMaxUsageAllowedForAccount(ACCOUNT_ID)).thenReturn(1);

    delegateService.deleteAllDelegatesExceptOne(ACCOUNT_ID, 1);

    Delegate delegateToRetain1 =
        persistence.createQuery(Delegate.class).filter(DelegateKeys.uuid, delegate1.getUuid()).get();
    Delegate delegateToRetain2 =
        persistence.createQuery(Delegate.class).filter(DelegateKeys.uuid, delegate2.getUuid()).get();

    assertThat(delegateToRetain1).isNotNull();
    assertThat(delegateToRetain2).isNull();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldSelectDelegateToRetainSendEmailAboutDelegatesOverUsage() {
    Delegate delegate1 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .ip("127.0.0.1")
                             .hostName("localhost")
                             .version(VERSION)
                             .lastHeartBeat(System.currentTimeMillis())
                             .disconnected(true)
                             .build();

    Delegate delegate2 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .ip("127.0.0.1")
                             .hostName("localhost")
                             .version(VERSION)
                             .lastHeartBeat(0)
                             .build();

    persistence.save(delegate1);
    persistence.save(delegate2);

    EmailData emailData =
        EmailData.builder()
            .hasHtml(false)
            .body(
                "Account is using more than [0] delegates. Account Id : [ACCOUNT_ID], Company Name : [NCR], Account Name : [testAccountName], Delegate Count : [1]")
            .subject("Found account with more than 0 delegates")
            .to(Lists.newArrayList("support@harness.io"))
            .build();

    when(accountService.get(ACCOUNT_ID)).thenReturn(account);
    when(account.getCompanyName()).thenReturn("NCR");
    when(account.getAccountName()).thenReturn("testAccountName");
    when(delegatesFeature.getMaxUsageAllowedForAccount(ACCOUNT_ID)).thenReturn(0);

    when(emailNotificationService.send(emailData)).thenReturn(true);

    delegateService.deleteAllDelegatesExceptOne(ACCOUNT_ID, 1);

    Delegate delegateToRetain1 =
        persistence.createQuery(Delegate.class).filter(DelegateKeys.uuid, delegate1.getUuid()).get();
    Delegate delegateToRetain2 =
        persistence.createQuery(Delegate.class).filter(DelegateKeys.uuid, delegate2.getUuid()).get();

    assertThat(delegateToRetain1).isNotNull();
    assertThat(delegateToRetain2).isNull();

    verify(emailNotificationService, atLeastOnce()).send(emailData);
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void testObtainDelegateIdShouldReturnEmpty() {
    assertThat(delegateService.obtainDelegateIdsUsingName(generateUuid(), generateUuid())).isEmpty();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testObtainDelegateIdShouldReturnDelegateId() {
    Delegate delegate = createDelegateBuilder().ng(true).delegateName(TEST_DELEGATE_NAME).build();
    String delegateId = persistence.save(delegate);

    List<String> delegateIds =
        delegateService.obtainDelegateIdsUsingName(delegate.getAccountId(), delegate.getDelegateName());

    assertThat(delegateIds).size().isEqualTo(1);
    assertThat(delegateIds.get(0)).isEqualTo(delegateId);
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void testGetConnectedDelegates() {
    List<Delegate> delegates = new ArrayList<>();

    Delegate delegate1 = createDelegateBuilder()
                             .accountId(ACCOUNT_ID)
                             .version(versionInfoManager.getVersionInfo().getVersion())
                             .disconnected(false)
                             .lastHeartBeat(System.currentTimeMillis())
                             .build();
    String delegateId1 = persistence.save(delegate1);
    delegates.add(delegate1);

    Delegate delegate2 = createDelegateBuilder()
                             .accountId(ACCOUNT_ID)
                             .version(versionInfoManager.getVersionInfo().getVersion())
                             .disconnected(true)
                             .lastHeartBeat(System.currentTimeMillis())
                             .build();
    String delegateId2 = persistence.save(delegate2);
    delegates.add(delegate2);

    when(accountService.getAccountPrimaryDelegateVersion(any())).thenReturn(versionInfoManager.getFullVersion());
    List<Delegate> connectedDelegates = delegateService.getConnectedDelegates(ACCOUNT_ID, delegates);

    assertThat(connectedDelegates.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testEmbedCapabilitiesInDelegateTask_HTTP_VaultConfig() {
    DelegateTask task =
        DelegateTask.builder()
            .taskDataV2(TaskDataV2.builder()
                            .parameters(new Object[] {HttpTaskParameters.builder().url(GOOGLE_COM).build()})
                            .build())
            .build();

    Collection<EncryptionConfig> encryptionConfigs = new ArrayList<>();
    EncryptionConfig encryptionConfig = VaultConfig.builder().vaultUrl(HTTP_VAUTL_URL).build();
    encryptionConfigs.add(encryptionConfig);

    DelegateTaskServiceClassicImpl.embedCapabilitiesInDelegateTaskV2(task, encryptionConfigs, null);
    assertThat(task.getExecutionCapabilities()).isNotNull();
    assertThat(task.getExecutionCapabilities()).hasSize(2);

    assertThat(
        task.getExecutionCapabilities().stream().map(ExecutionCapability::fetchCapabilityBasis).collect(toList()))
        .containsExactlyInAnyOrder(HTTP_VAUTL_URL, GOOGLE_COM);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testEmbedCapabilitiesInDelegateTask_HTTP_KmsConfig() {
    TaskDataV2 taskData =
        TaskDataV2.builder().parameters(new Object[] {HttpTaskParameters.builder().url(GOOGLE_COM).build()}).build();
    DelegateTask task = DelegateTask.builder().taskDataV2(taskData).build();

    Collection<EncryptionConfig> encryptionConfigs = new ArrayList<>();
    EncryptionConfig encryptionConfig = KmsConfig.builder().region(US_EAST_2).build();
    encryptionConfigs.add(encryptionConfig);

    DelegateTaskServiceClassicImpl.embedCapabilitiesInDelegateTaskV2(task, encryptionConfigs, null);
    assertThat(task.getExecutionCapabilities()).isNotNull();
    assertThat(task.getExecutionCapabilities()).hasSize(2);

    assertThat(
        task.getExecutionCapabilities().stream().map(ExecutionCapability::fetchCapabilityBasis).collect(toList()))
        .containsExactlyInAnyOrder(AWS_KMS_URL, GOOGLE_COM);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testEmbedCapabilitiesInDelegateTask_HTTP_SecretInUrl() {
    DelegateTask task =
        DelegateTask.builder()
            .taskDataV2(TaskDataV2.builder()
                            .parameters(new Object[] {HttpTaskParameters.builder().url(SECRET_URL).build()})
                            .build())
            .build();

    Collection<EncryptionConfig> encryptionConfigs = new ArrayList<>();

    DelegateTaskServiceClassicImpl.embedCapabilitiesInDelegateTaskV2(
        task, encryptionConfigs, new ManagerPreviewExpressionEvaluator());
    assertThat(task.getExecutionCapabilities()).isNotNull().hasSize(1);

    assertThat(
        task.getExecutionCapabilities().stream().map(ExecutionCapability::fetchCapabilityBasis).collect(toList()))
        .containsExactlyInAnyOrder("http://google.com/?q=<<<test>>>");
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void testGetDelegateInitializationDetails_isProfileError() {
    Delegate delegate = Delegate.builder()
                            .accountId(ACCOUNT_ID)
                            .version(VERSION)
                            .lastHeartBeat(System.currentTimeMillis())
                            .profileError(true)
                            .build();

    String delegateId = persistence.save(delegate);

    when(delegateCache.get(ACCOUNT_ID, delegateId)).thenReturn(delegate);

    DelegateInitializationDetails delegateInitializationDetails =
        delegateService.getDelegateInitializationDetails(ACCOUNT_ID, delegateId);

    assertThat(delegateInitializationDetails).isNotNull();
    assertThat(delegateInitializationDetails.getDelegateId()).isEqualTo(delegateId);
    assertThat(delegateInitializationDetails.isInitialized()).isFalse();
    assertThat(delegateInitializationDetails.isProfileError()).isTrue();
    assertThat(delegateInitializationDetails.getProfileExecutedAt()).isEqualTo(delegate.getProfileExecutedAt());
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void testGetDelegateInitializationDetails_notProfileErrorAndExecutionTime() {
    Delegate delegate = Delegate.builder()
                            .accountId(ACCOUNT_ID)
                            .version(VERSION)
                            .lastHeartBeat(System.currentTimeMillis())
                            .profileError(false)
                            .profileExecutedAt(TEST_PROFILE_EXECUTION_TIME)
                            .build();

    String delegateId = persistence.save(delegate);

    when(delegateCache.get(ACCOUNT_ID, delegateId)).thenReturn(delegate);

    DelegateInitializationDetails delegateInitializationDetails =
        delegateService.getDelegateInitializationDetails(ACCOUNT_ID, delegateId);

    assertThat(delegateInitializationDetails).isNotNull();
    assertThat(delegateInitializationDetails.getDelegateId()).isEqualTo(delegateId);
    assertThat(delegateInitializationDetails.isInitialized()).isTrue();
    assertThat(delegateInitializationDetails.isProfileError()).isFalse();
    assertThat(delegateInitializationDetails.getProfileExecutedAt()).isEqualTo(delegate.getProfileExecutedAt());
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void testGetDelegateInitializationDetails_notProfileErrorBlankScript() {
    Delegate delegate = Delegate.builder()
                            .accountId(ACCOUNT_ID)
                            .version(VERSION)
                            .lastHeartBeat(System.currentTimeMillis())
                            .profileError(false)
                            .profileExecutedAt(0L)
                            .delegateProfileId(TEST_DELEGATE_PROFILE_ID)
                            .build();

    String delegateId = persistence.save(delegate);

    DelegateProfile delegateProfile =
        DelegateProfile.builder().accountId(ACCOUNT_ID).uuid(TEST_DELEGATE_PROFILE_ID).startupScript("").build();

    when(delegateProfileService.get(ACCOUNT_ID, TEST_DELEGATE_PROFILE_ID)).thenReturn(delegateProfile);

    when(delegateCache.get(ACCOUNT_ID, delegateId)).thenReturn(delegate);

    DelegateInitializationDetails delegateInitializationDetails =
        delegateService.getDelegateInitializationDetails(ACCOUNT_ID, delegateId);

    verify(delegateProfileService, times(1)).get(ACCOUNT_ID, TEST_DELEGATE_PROFILE_ID);

    assertThat(delegateInitializationDetails).isNotNull();
    assertThat(delegateInitializationDetails.getDelegateId()).isEqualTo(delegateId);
    assertThat(delegateInitializationDetails.isInitialized()).isTrue();
    assertThat(delegateInitializationDetails.isProfileError()).isFalse();
    assertThat(delegateInitializationDetails.getProfileExecutedAt()).isEqualTo(delegate.getProfileExecutedAt());
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void testGetDelegateInitializationDetails_pendingInitialization() {
    Delegate delegate = Delegate.builder()
                            .accountId(ACCOUNT_ID)
                            .version(VERSION)
                            .lastHeartBeat(System.currentTimeMillis())
                            .profileError(false)
                            .profileExecutedAt(0L)
                            .delegateProfileId(TEST_DELEGATE_PROFILE_ID)
                            .build();

    String delegateId = persistence.save(delegate);

    DelegateProfile delegateProfile = DelegateProfile.builder()
                                          .accountId(ACCOUNT_ID)
                                          .uuid(TEST_DELEGATE_PROFILE_ID)
                                          .startupScript("testScript")
                                          .build();

    when(delegateProfileService.get(ACCOUNT_ID, TEST_DELEGATE_PROFILE_ID)).thenReturn(delegateProfile);
    when(delegateCache.get(ACCOUNT_ID, delegateId)).thenReturn(delegate);

    DelegateInitializationDetails delegateInitializationDetails =
        delegateService.getDelegateInitializationDetails(ACCOUNT_ID, delegateId);

    verify(delegateProfileService, times(1)).get(ACCOUNT_ID, TEST_DELEGATE_PROFILE_ID);

    assertThat(delegateInitializationDetails).isNotNull();
    assertThat(delegateInitializationDetails.getDelegateId()).isEqualTo(delegateId);
    assertThat(delegateInitializationDetails.isInitialized()).isFalse();
    assertThat(delegateInitializationDetails.isProfileError()).isFalse();
    assertThat(delegateInitializationDetails.getProfileExecutedAt()).isEqualTo(delegate.getProfileExecutedAt());
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void obtainDelegateInitializationDetails() {
    List<String> delegateIds = setUpDelegatesForInitializationTest();

    List<DelegateInitializationDetails> delegateInitializationDetails =
        delegateService.obtainDelegateInitializationDetails(ACCOUNT_ID, delegateIds);

    assertThat(delegateInitializationDetails).isNotEmpty();
    assertThat(delegateIds.size()).isEqualTo(delegateInitializationDetails.size());

    assertThat(delegateInitializationDetails.get(0).getDelegateId()).isEqualTo(delegateIds.get(0));
    assertThat(delegateInitializationDetails.get(0).isInitialized()).isFalse();
    assertThat(delegateInitializationDetails.get(0).isProfileError()).isTrue();

    assertThat(delegateInitializationDetails.get(1).getDelegateId()).isEqualTo(delegateIds.get(1));
    assertThat(delegateInitializationDetails.get(1).isInitialized()).isTrue();
    assertThat(delegateInitializationDetails.get(1).isProfileError()).isFalse();
    assertThat(delegateInitializationDetails.get(1).getProfileExecutedAt()).isEqualTo(TEST_PROFILE_EXECUTION_TIME);

    assertThat(delegateInitializationDetails.get(2).getDelegateId()).isEqualTo(delegateIds.get(2));
    assertThat(delegateInitializationDetails.get(2).isInitialized()).isTrue();
    assertThat(delegateInitializationDetails.get(2).isProfileError()).isFalse();
    assertThat(delegateInitializationDetails.get(2).getProfileExecutedAt()).isZero();

    assertThat(delegateInitializationDetails.get(3).getDelegateId()).isEqualTo(delegateIds.get(3));
    assertThat(delegateInitializationDetails.get(3).isInitialized()).isFalse();
    assertThat(delegateInitializationDetails.get(3).isProfileError()).isFalse();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testAuditEntryForDelegateGroup() throws IOException {
    K8sConfigDetails k8sConfigDetails =
        K8sConfigDetails.builder().k8sPermissionType(K8sPermissionType.NAMESPACE_ADMIN).namespace("namespace").build();
    final ImmutableSet<String> tags = ImmutableSet.of("sometag", "anothertag");
    when(delegateNgTokenService.getDelegateToken(ACCOUNT_ID, TOKEN_NAME))
        .thenReturn(DelegateTokenDetails.builder()
                        .name(TOKEN_NAME)
                        .accountId(ACCOUNT_ID)
                        .status(DelegateTokenStatus.ACTIVE)
                        .build());
    DelegateSetupDetails delegateSetupDetails = DelegateSetupDetails.builder()
                                                    .name(TEST_DELEGATE_GROUP_NAME)
                                                    .orgIdentifier(ORG_ID)
                                                    .tokenName(TOKEN_NAME)
                                                    .projectIdentifier(PROJECT_ID)
                                                    .k8sConfigDetails(k8sConfigDetails)
                                                    .description("description")
                                                    .size(DelegateSize.LAPTOP)
                                                    .identifier(DELEGATE_GROUP_IDENTIFIER)
                                                    .tags(tags)
                                                    .delegateType(DelegateType.KUBERNETES)
                                                    .build();
    String delegateGroup = delegateService.createDelegateGroup(ACCOUNT_ID, delegateSetupDetails);

    assertThat(delegateGroup).isNotNull();

    List<OutboxEvent> outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(100).build());
    assertThat(outboxEvents.size()).isEqualTo(1);
    OutboxEvent outboxEvent = outboxEvents.get(0);
    assertThat(outboxEvent.getResourceScope().equals(new ProjectScope(ACCOUNT_ID, ORG_ID, PROJECT_ID))).isTrue();
    assertThat(outboxEvent.getResource())
        .isEqualTo(Resource.builder()
                       .type(ResourceTypeConstants.DELEGATE)
                       .identifier(DELEGATE_GROUP_IDENTIFIER)
                       .labels(Collections.singletonMap("resourceName", "testDelegateGroupName"))
                       .build());
    assertThat(outboxEvent.getEventType()).isEqualTo(DelegateUpsertEvent.builder().build().getEventType());
    DelegateUpsertEvent delegateUpsertEvent =
        HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(outboxEvent.getEventData(), DelegateUpsertEvent.class);
    assertThat(delegateUpsertEvent.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(delegateUpsertEvent.getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(delegateUpsertEvent.getProjectIdentifier()).isEqualTo(PROJECT_ID);
    assertThat(delegateUpsertEvent.getDelegateSetupDetails())
        .isEqualTo(DelegateSetupDetails.builder()
                       .name(TEST_DELEGATE_GROUP_NAME)
                       .orgIdentifier(ORG_ID)
                       .projectIdentifier(PROJECT_ID)
                       .tokenName(TOKEN_NAME)
                       .k8sConfigDetails(k8sConfigDetails)
                       .description("description")
                       .size(DelegateSize.LAPTOP)
                       .identifier(DELEGATE_GROUP_IDENTIFIER)
                       .tags(tags)
                       .delegateType(DelegateType.KUBERNETES)
                       .build());

    // test delete event
    delegateService.deleteDelegateGroup(ACCOUNT_ID, delegateGroup);
    outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(100).build());
    assertThat(outboxEvents.size()).isEqualTo(2);
    outboxEvent = outboxEvents.get(1);
    assertThat(outboxEvent.getResourceScope().equals(new ProjectScope(ACCOUNT_ID, ORG_ID, PROJECT_ID))).isTrue();
    assertThat(outboxEvent.getResource())
        .isEqualTo(Resource.builder()
                       .type(ResourceTypeConstants.DELEGATE)
                       .identifier(DELEGATE_GROUP_IDENTIFIER)
                       .labels(Collections.singletonMap("resourceName", "testDelegateGroupName"))
                       .build());
    assertThat(outboxEvent.getEventType()).isEqualTo(DelegateDeleteEvent.builder().build().getEventType());
    DelegateDeleteEvent delegateDeleteEvent =
        HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(outboxEvent.getEventData(), DelegateDeleteEvent.class);
    assertThat(delegateDeleteEvent.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(delegateDeleteEvent.getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(delegateDeleteEvent.getProjectIdentifier()).isEqualTo(PROJECT_ID);
    assertThat(delegateDeleteEvent.getDelegateSetupDetails())
        .isEqualTo(DelegateSetupDetails.builder()
                       .orgIdentifier(ORG_ID)
                       .name("testDelegateGroupName")
                       .projectIdentifier(PROJECT_ID)
                       .k8sConfigDetails(k8sConfigDetails)
                       .description("description")
                       .size(DelegateSize.LAPTOP)
                       .identifier(null)
                       .delegateType(DelegateType.KUBERNETES)
                       .build());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testAuditEntryForDelegateUnRegister() {
    String accountId = generateUuid();
    Delegate delegate = Delegate.builder()
                            .accountId(accountId)
                            .version(VERSION)
                            .hostName(HOST_NAME)
                            .ng(true)
                            .delegateType(DelegateType.KUBERNETES)
                            .ip("xx")
                            .owner(DelegateEntityOwner.builder().identifier("ORG_ID/PROJECT_ID").build())
                            .lastHeartBeat(System.currentTimeMillis())
                            .build();
    persistence.save(delegate);
    DelegateUnregisterRequest request = new DelegateUnregisterRequest(delegate.getUuid(), delegate.getHostName(),
        delegate.isNg(), delegate.getDelegateType(), "xx", ORG_ID, PROJECT_ID);
    delegateService.unregister(accountId, request);

    List<OutboxEvent> outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    assertThat(outboxEvents.size()).isEqualTo(1);
    OutboxEvent outboxEvent = outboxEvents.get(0);
    assertThat(outboxEvent.getResource())
        .isEqualTo(Resource.builder()
                       .type(ResourceTypeConstants.DELEGATE)
                       .identifier(HOST_NAME)
                       .labels(Collections.singletonMap("resourceName", HOST_NAME))
                       .build());
    assertThat(outboxEvent.getResourceScope().equals(new ProjectScope(accountId, ORG_ID, PROJECT_ID))).isTrue();
    assertThat(outboxEvent.getEventType()).isEqualTo(DelegateUnregisterEvent.builder().build().getEventType());
  }

  @Test
  @Owner(developers = NICOLAS)
  @Category(UnitTests.class)
  public void testAddDelegateToGroup_existingGroupUpdate() {
    DelegateGroup delegateGroup = setUpDefaultDelegateGroupForTests();

    DelegateGroup returnedDelegateGroup =
        delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, null);

    assertThat(returnedDelegateGroup).isNotNull();
    assertThat(returnedDelegateGroup.getUuid()).isNotEmpty();
    assertThat(returnedDelegateGroup.getUuid()).isEqualTo(delegateGroup.getUuid());
    assertThat(returnedDelegateGroup.getAccountId()).isEqualTo(delegateGroup.getAccountId());
    assertThat(returnedDelegateGroup.getOwner()).isNull();
    assertThat(returnedDelegateGroup.getName()).isEqualTo(delegateGroup.getName());
    assertThat(returnedDelegateGroup.getK8sConfigDetails()).isNull();
    assertThat(returnedDelegateGroup.isNg()).isFalse();
    assertThat(returnedDelegateGroup.getIdentifier()).isEqualTo(TEST_DELEGATE_GROUP_NAME_IDENTIFIER);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldUpsertDelegateGroupWithExistingIdentifier_DescriptionChange() {
    String groupIdentifier = "testGroupIdentifier";

    DelegateSetupDetails delegateSetupDetails1 =
        createDelegateSetupDetails().description("description1").identifier(groupIdentifier).build();
    DelegateSetupDetails delegateSetupDetails2 =
        createDelegateSetupDetails().description("description2").identifier(groupIdentifier).build();

    DelegateGroup delegateGroup1 =
        delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, delegateSetupDetails1);
    DelegateGroup delegateGroup2 =
        delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, delegateSetupDetails2);

    assertThat(delegateGroup2.getIdentifier()).isEqualTo(groupIdentifier);
    assertThat(delegateGroup2.getName()).isEqualTo(TEST_DELEGATE_GROUP_NAME);
    assertThat(delegateGroup2.getDescription()).isEqualTo("description2");
    assertThat(delegateGroup1.getUuid()).isEqualTo(delegateGroup2.getUuid());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldUpsertDelegateGroupWithSameAccountAndName() {
    DelegateSetupDetails delegateSetupDetails1 = createDelegateSetupDetails().identifier("_123").build();
    DelegateSetupDetails delegateSetupDetails2 = createDelegateSetupDetails().identifier("_1234").build();

    DelegateGroup delegateGroup1 =
        delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, delegateSetupDetails1);
    DelegateGroup delegateGroup2 =
        delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, delegateSetupDetails2);

    assertThat(delegateGroup1.getUuid()).isEqualTo(delegateGroup2.getUuid());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldUpsertDelegateGroupWithEmptyIdentifier() {
    DelegateSetupDetails delegateSetupDetails = createDelegateSetupDetails().identifier("").build();

    DelegateGroup delegateGroup1 =
        delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, delegateSetupDetails);
    DelegateGroup delegateGroup2 =
        delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, delegateSetupDetails);

    assertThat(delegateGroup2.getUuid()).isEqualTo(delegateGroup1.getUuid());
    assertThat(delegateGroup2.getIdentifier()).isEqualTo(TEST_DELEGATE_GROUP_NAME_IDENTIFIER);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldUpsertDelegateGroupWithNoIdentifier() {
    DelegateSetupDetails delegateSetupDetails = createDelegateSetupDetails().build();

    DelegateGroup delegateGroup1 =
        delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, delegateSetupDetails);
    DelegateGroup delegateGroup2 =
        delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, delegateSetupDetails);

    assertThat(delegateGroup2.getUuid()).isEqualTo(delegateGroup1.getUuid());
    assertThat(delegateGroup2.getIdentifier()).isEqualTo(TEST_DELEGATE_GROUP_NAME_IDENTIFIER);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldUpsertDelegateGroupWithNoDelegateSetupDetails() {
    DelegateGroup delegateGroup = delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, null);

    assertThat(delegateGroup.getIdentifier()).isEqualTo(TEST_DELEGATE_GROUP_NAME_IDENTIFIER);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldUpsertDelegateGroupWithNoDelegateSetupDetails_SameName() {
    DelegateGroup delegateGroup1 = delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, null);
    DelegateGroup delegateGroup2 = delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, null);

    assertThat(delegateGroup1.getIdentifier()).isEqualTo(TEST_DELEGATE_GROUP_NAME_IDENTIFIER);
    assertThat(delegateGroup2.getIdentifier()).isEqualTo(TEST_DELEGATE_GROUP_NAME_IDENTIFIER);
    assertThat(delegateGroup2.getUuid()).isEqualTo(delegateGroup1.getUuid());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldUpsertDelegateGroupWithNoDelegateSetupDetails_DifferentName() {
    DelegateGroup delegateGroup1 = delegateService.upsertDelegateGroup("name1", ACCOUNT_ID, null);
    DelegateGroup delegateGroup2 = delegateService.upsertDelegateGroup("name2", ACCOUNT_ID, null);

    assertThat(delegateGroup1.getIdentifier()).isEqualTo("_name1");
    assertThat(delegateGroup2.getIdentifier()).isEqualTo("_name2");
    assertThat(delegateGroup2.getUuid()).isNotEqualTo(delegateGroup1.getUuid());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldUpsertDelegateGroupWithNoDelegateSetupDetails_DifferentAccount() {
    DelegateGroup delegateGroup1 = delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, "account1", null);
    DelegateGroup delegateGroup2 = delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, "account2", null);

    assertThat(delegateGroup1.getIdentifier()).isEqualTo(TEST_DELEGATE_GROUP_NAME_IDENTIFIER);
    assertThat(delegateGroup2.getIdentifier()).isEqualTo(TEST_DELEGATE_GROUP_NAME_IDENTIFIER);
    assertThat(delegateGroup2.getUuid()).isNotEqualTo(delegateGroup1.getUuid());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldUpsertDelegateGroupWithNoDelegateSetupDetails_UpdateWithExistingIdentifierMigratedFromUUID() {
    String uuid = generateUuid();
    DelegateGroup delegateGroupAfterMigration = setUpDefaultDelegateGroupAfterMigrationForTests(uuid);

    DelegateGroup delegateGroup = delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, null);

    assertThat(delegateGroup.getIdentifier()).isEqualTo(uuidToIdentifier(uuid));
    assertThat(delegateGroupAfterMigration.getUuid()).isEqualTo(delegateGroup.getUuid());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldNotUpsertDelegateGroupWithDifferentOwnerButSameName() {
    DelegateSetupDetails delegateSetupDetails1 = createDelegateSetupDetails()
                                                     .identifier(DELEGATE_GROUP_IDENTIFIER)
                                                     .name(TEST_DELEGATE_GROUP_NAME)
                                                     .orgIdentifier("orgId1")
                                                     .projectIdentifier("projId1")
                                                     .build();
    DelegateSetupDetails delegateSetupDetails2 = createDelegateSetupDetails()
                                                     .identifier(DELEGATE_GROUP_IDENTIFIER)
                                                     .name(TEST_DELEGATE_GROUP_NAME)
                                                     .orgIdentifier("orgId2")
                                                     .projectIdentifier("projId2")
                                                     .build();

    delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, delegateSetupDetails1);
    assertThatThrownBy(
        () -> delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, delegateSetupDetails2))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Unable to create delegate group. Delegate with same name exists. Delegate name must be unique across account.");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldUpsertDelegateGroupWithTheSameOwner() {
    DelegateSetupDetails delegateSetupDetails1 = createDelegateSetupDetails()
                                                     .identifier(DELEGATE_GROUP_IDENTIFIER)
                                                     .name(TEST_DELEGATE_GROUP_NAME)
                                                     .orgIdentifier("orgId1")
                                                     .projectIdentifier("projId1")
                                                     .build();
    DelegateSetupDetails delegateSetupDetails2 = createDelegateSetupDetails()
                                                     .identifier(DELEGATE_GROUP_IDENTIFIER)
                                                     .name(TEST_DELEGATE_GROUP_NAME)
                                                     .orgIdentifier("orgId1")
                                                     .projectIdentifier("projId1")
                                                     .build();

    DelegateGroup delegateGroup1 =
        delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, delegateSetupDetails1);
    DelegateGroup delegateGroup2 =
        delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, delegateSetupDetails2);

    assertThat(delegateGroup1.getIdentifier()).isEqualTo(delegateGroup2.getIdentifier());
    assertThat(delegateGroup1.getName()).isEqualTo(delegateGroup2.getName());
    assertThat(delegateGroup1.getOwner()).isEqualTo(delegateGroup2.getOwner());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldUpsertDelegateGroupWithTheSameOwnerNoIdentifier() {
    DelegateSetupDetails delegateSetupDetails1 =
        createDelegateSetupDetails().orgIdentifier("orgId1").projectIdentifier("projId1").build();
    DelegateSetupDetails delegateSetupDetails2 =
        createDelegateSetupDetails().orgIdentifier("orgId1").projectIdentifier("projId1").build();

    DelegateGroup delegateGroup1 =
        delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, delegateSetupDetails1);
    DelegateGroup delegateGroup2 =
        delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, delegateSetupDetails2);

    assertThat(delegateGroup1.getUuid()).isEqualTo(delegateGroup2.getUuid());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void shouldUpsertDelegateGroupWithoutOwnerAndWithOwner() {
    String identifier = "_123";
    String delegateGroupName = "groupName";

    DelegateSetupDetails delegateSetupDetails1 = DelegateSetupDetails.builder()
                                                     .identifier(identifier)
                                                     .name(delegateGroupName)
                                                     .delegateType(DelegateType.KUBERNETES)
                                                     .build();
    DelegateSetupDetails delegateSetupDetails2 =
        createDelegateSetupDetails().orgIdentifier(ORG_IDENTIFIER).projectIdentifier(PROJECT_IDENTIFIER).build();

    DelegateGroup delegateGroup1 =
        delegateService.upsertDelegateGroup(delegateGroupName, ACCOUNT_ID, delegateSetupDetails1);
    DelegateGroup delegateGroup2 =
        delegateService.upsertDelegateGroup(TEST_DELEGATE_GROUP_NAME, ACCOUNT_ID, delegateSetupDetails2);

    assertThat(delegateGroup1.getUuid()).isNotEqualTo(delegateGroup2.getUuid());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void shouldUpsertDelegateGroupWithIllegalCharactersInName() {
    String name = "1 ~`!@#$%^&*()_-+={}[]|\\:;\"'<>,.?///";

    DelegateSetupDetails delegateSetupDetails = createDelegateSetupDetails().name(name).build();

    delegateService.upsertDelegateGroup(name, ACCOUNT_ID, delegateSetupDetails);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void shouldUpsertDelegateGroupDifferentDetailsName() {
    DelegateSetupDetails delegateSetupDetails = createDelegateSetupDetails().name("name1").build();

    DelegateGroup delegateGroup = delegateService.upsertDelegateGroup("name2", ACCOUNT_ID, delegateSetupDetails);

    assertThat(delegateGroup.getIdentifier()).isEqualTo("_name1");
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldValidateDelegateProfileWrongProfileId() {
    setUpDelegatesForInitializationTest();
    String delegateProfileId = TEST_DELEGATE_PROFILE_ID + "_12345";
    thrown.expect(InvalidRequestException.class);
    delegateService.validateDelegateProfileId(ACCOUNT_ID, delegateProfileId);
  }

  @Test()
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldValidateDelegateProfileIdExists() {
    setUpDelegatesForInitializationTest();
    String delegateProfileId = TEST_DELEGATE_PROFILE_ID + "_1";
    delegateService.validateDelegateProfileId(ACCOUNT_ID, delegateProfileId);
  }

  @Test()
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void shouldValidateDelegateProfileIdEmpty() {
    setUpDelegatesForInitializationTest();
    delegateService.validateDelegateProfileId(ACCOUNT_ID, null);
  }

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void testGetConnectedRatioWithPrimary() {
    Delegate delegate = createDelegateBuilder().build();
    persistence.save(delegate);
    DelegateConfiguration delegateConfiguration =
        DelegateConfiguration.builder().delegateVersions(singletonList(VERSION)).build();
    when(accountService.getDelegateConfiguration(ACCOUNT_ID)).thenReturn(delegateConfiguration);

    Double ratio = delegateService.getConnectedRatioWithPrimary(VERSION, ACCOUNT_ID, null);
    assertThat(ratio).isEqualTo(1.0);
  }

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void testGetConnectedRatioWithPrimaryWithNoAccountID() {
    Delegate delegate = createDelegateBuilder().build();
    persistence.save(delegate);

    DelegateConfiguration delegateConfiguration =
        DelegateConfiguration.builder().delegateVersions(singletonList(VERSION)).build();
    when(accountService.getDelegateConfiguration(Account.GLOBAL_ACCOUNT_ID)).thenReturn(delegateConfiguration);

    Double ratio = delegateService.getConnectedRatioWithPrimary(VERSION, null, null);
    assertThat(ratio).isEqualTo(1.0);
  }

  @Test
  @Owner(developers = GAURAV)
  @Category(UnitTests.class)
  public void testGetConnectedRatioWithPrimaryWithRing() {
    Delegate delegate = createDelegateBuilder().build();
    persistence.save(delegate);

    DelegateConfiguration delegateConfiguration =
        DelegateConfiguration.builder().delegateVersions(singletonList(VERSION)).build();
    when(accountService.getDelegateConfiguration(ACCOUNT_ID)).thenReturn(delegateConfiguration);

    final String ringName = "ring3";
    when(delegateVersionService.getDelegateJarVersions(ringName, ACCOUNT_ID))
        .thenReturn(Collections.singletonList(VERSION));
    Double ratio = delegateService.getConnectedRatioWithPrimary(VERSION, ACCOUNT_ID, ringName);
    assertThat(ratio).isEqualTo(1.0);
  }

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void testGetConnectedDelegatesRatio() {
    persistence.save(createDelegateBuilder()
                         .delegateConnectionId(generateUuid())
                         .version(VERSION)
                         .disconnected(false)
                         .lastHeartBeat(System.currentTimeMillis())
                         .build());
    persistence.save(createDelegateBuilder()
                         .delegateConnectionId(generateUuid())
                         .version(VERSION)
                         .disconnected(true)
                         .lastHeartBeat(System.currentTimeMillis())
                         .build());
    Double ratio = delegateService.getConnectedDelegatesRatio(VERSION, ACCOUNT_ID);
    assertThat(ratio).isEqualTo(0.5);
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testFinalizeTemplateParametersWithMtlsIfRequiredSmokeTest() {
    final String accountId = "abc21";
    this.persistence.save(
        AgentMtlsEndpoint.builder().accountId(accountId).fqdn("customer.agent.ut.harness.io").build());

    when(accountService.isImmutableDelegateEnabled(accountId)).thenReturn(true);

    // test some arbitrary values that shouldn't get modified by the function
    String delegateType = KUBERNETES;
    double delegateCpu = 4.2d;
    String version = "someVersion";
    boolean ciEnabled = true;
    TemplateParametersBuilder builder = TemplateParameters.builder()
                                            .accountId(accountId)
                                            .managerHost("https://app.harness.io")
                                            .logStreamingServiceBaseUrl("https://app.harness.io/log-service")
                                            .delegateType(delegateType)
                                            .delegateCpu(delegateCpu)
                                            .ciEnabled(ciEnabled)
                                            .version(version);
    TemplateParameters updatedParameters = delegateService.finalizeTemplateParametersWithMtlsIfRequired(builder);

    // ensure returned parameters are updated correctly
    assertThat(updatedParameters.isMtlsEnabled()).isTrue();
    assertThat(updatedParameters.getManagerHost()).isEqualTo("https://customer.agent.ut.harness.io");
    assertThat(updatedParameters.getLogStreamingServiceBaseUrl())
        .isEqualTo("https://customer.agent.ut.harness.io/log-service");

    // ensure returned parameters contain mtls unrelated fields
    assertThat(updatedParameters.getAccountId()).isEqualTo(accountId);
    assertThat(updatedParameters.getDelegateType()).isEqualTo(delegateType);
    assertThat(updatedParameters.getDelegateCpu()).isEqualTo(delegateCpu);
    assertThat(updatedParameters.getVersion()).isEqualTo(version);
    assertThat(updatedParameters.isCiEnabled()).isEqualTo(ciEnabled);

    // ensure original builder wasn't modified (assumes builder is idempotent)
    TemplateParameters originalParameters = builder.build();
    assertThat(originalParameters.isMtlsEnabled()).isFalse();
    assertThat(originalParameters.getManagerHost()).isEqualTo("https://app.harness.io");
    assertThat(originalParameters.getLogStreamingServiceBaseUrl()).isEqualTo("https://app.harness.io/log-service");
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testFinalizeTemplateParametersWithMtlsIfRequiredForNonImmutable() {
    final String accountId = "abc21";
    this.persistence.save(
        AgentMtlsEndpoint.builder().accountId(accountId).fqdn("customer.agent.ut.harness.io").build());

    TemplateParameters templateParameters =
        delegateService.finalizeTemplateParametersWithMtlsIfRequired(TemplateParameters.builder().accountId(accountId));

    assertThat(templateParameters.isMtlsEnabled()).isFalse();
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testFinalizeTemplateParametersWithMtlsIfRequiredForNonMtls() {
    final String accountId = "abc21";
    TemplateParameters templateParameters =
        delegateService.finalizeTemplateParametersWithMtlsIfRequired(TemplateParameters.builder().accountId(accountId));

    assertThat(templateParameters.isMtlsEnabled()).isFalse();
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testUpdateUriToTargetMtlsEndpointForProd() {
    String managerUri = this.delegateService.updateUriToTargetMtlsEndpoint(
        "https://app.harness.io", "https://app.harness.io", "customer.agent.harness.io");

    assertThat(managerUri).isEqualTo("https://customer.agent.harness.io");

    String logServiceUri = this.delegateService.updateUriToTargetMtlsEndpoint(
        "https://app.harness.io/log-service", "https://app.harness.io", "customer.agent.harness.io");

    assertThat(logServiceUri).isEqualTo("https://customer.agent.harness.io/log-service");
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testUpdateUriToTargetMtlsEndpointForGratis() {
    String managerUri = this.delegateService.updateUriToTargetMtlsEndpoint(
        "https://app.harness.io/gratis", "https://app.harness.io/gratis", "customer.agent.harness.io");

    assertThat(managerUri).isEqualTo("https://customer.agent.harness.io");

    String logServiceUri = this.delegateService.updateUriToTargetMtlsEndpoint(
        "https://app.harness.io/gratis/log-service", "https://app.harness.io/gratis", "customer.agent.harness.io");

    assertThat(logServiceUri).isEqualTo("https://customer.agent.harness.io/log-service");
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testUpdateUriToTargetMtlsEndpointForCompliance() {
    String managerUri = this.delegateService.updateUriToTargetMtlsEndpoint(
        "https://app3.harness.io", "https://app3.harness.io", "customer.agent.harness.io");

    assertThat(managerUri).isEqualTo("https://customer.agent.harness.io");

    String logServiceUri = this.delegateService.updateUriToTargetMtlsEndpoint(
        "https://app3.harness.io/log-service", "https://app3.harness.io", "customer.agent.harness.io");

    assertThat(logServiceUri).isEqualTo("https://customer.agent.harness.io/log-service");
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testUpdateUriToTargetMtlsEndpointForVanity() {
    String managerUri = this.delegateService.updateUriToTargetMtlsEndpoint(
        "https://vanityapp.harness.io", "https://vanityapp.harness.io", "customer.agent.harness.io");

    assertThat(managerUri).isEqualTo("https://customer.agent.harness.io");

    String logServiceUri = this.delegateService.updateUriToTargetMtlsEndpoint(
        "https://vanityapp.harness.io/log-service", "https://vanityapp.harness.io", "customer.agent.harness.io");

    assertThat(logServiceUri).isEqualTo("https://customer.agent.harness.io/log-service");
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testUpdateUriToTargetMtlsEndpointForQa() {
    String managerUri = this.delegateService.updateUriToTargetMtlsEndpoint(
        "https://qa.harness.io", "https://qa.harness.io", "customer.agent.qa.harness.io");

    assertThat(managerUri).isEqualTo("https://customer.agent.qa.harness.io");

    String logServiceUri = this.delegateService.updateUriToTargetMtlsEndpoint(
        "https://qa.harness.io/log-service", "https://qa.harness.io", "customer.agent.qa.harness.io");

    assertThat(logServiceUri).isEqualTo("https://customer.agent.qa.harness.io/log-service");
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testUpdateUriToTargetMtlsEndpointForPr() {
    String managerUri = this.delegateService.updateUriToTargetMtlsEndpoint(
        "https://pr.harness.io/del-42", "https://pr.harness.io/del-42", "customer.agent.pr.harness.io");

    assertThat(managerUri).isEqualTo("https://customer.agent.pr.harness.io");

    String logServiceUri = this.delegateService.updateUriToTargetMtlsEndpoint(
        "https://pr.harness.io/del-42/log-service", "https://pr.harness.io/del-42", "customer.agent.pr.harness.io");

    assertThat(logServiceUri).isEqualTo("https://customer.agent.pr.harness.io/log-service");
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testUpdateUriToTargetMtlsEndpointIgnoresProtocol() {
    String output = this.delegateService.updateUriToTargetMtlsEndpoint(
        "sftp://app.harness.io", "http://app.harness.io", "customer.agent.harness.io");

    assertThat(output).isEqualTo("https://customer.agent.harness.io");
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testUpdateUriToTargetMtlsEndpointIgnoresPort() {
    String output = this.delegateService.updateUriToTargetMtlsEndpoint(
        "http://app.harness.io:9876", "https://app.harness.io:9090", "customer.agent.harness.io");

    assertThat(output).isEqualTo("https://customer.agent.harness.io");
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetConnectedDelegatesForGlobalDelegateAccount() {
    List<Delegate> delegates = new ArrayList<>();

    Delegate delegate1 = createDelegateBuilder()
                             .accountId(GLOBAL_DELEGATE_ACCOUNT_ID)
                             .version(versionInfoManager.getVersionInfo().getVersion())
                             .lastHeartBeat(System.currentTimeMillis())
                             .build();
    String delegateId1 = persistence.save(delegate1);
    delegates.add(delegate1);
    Delegate delegate2 = createDelegateBuilder()
                             .accountId(GLOBAL_DELEGATE_ACCOUNT_ID)
                             .version(versionInfoManager.getVersionInfo().getVersion())
                             .build();
    String delegateId2 = persistence.save(delegate2);
    delegates.add(delegate2);
    when(accountService.getAccountPrimaryDelegateVersion(any())).thenReturn(versionInfoManager.getFullVersion());
    List<Delegate> connectedDelegates = delegateService.getConnectedDelegates(GLOBAL_DELEGATE_ACCOUNT_ID, delegates);

    assertThat(connectedDelegates.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @HarnessAlwaysRun
  public void shouldExecuteTaskForGlobalDelegate() {
    Delegate delegate = createDelegateBuilder().accountId(GLOBAL_DELEGATE_ACCOUNT_ID).build();
    persistence.save(delegate);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .uuid(generateUuid())
            .accountId(ACCOUNT_ID)
            .waitId(generateUuid())
            .executeOnHarnessHostedDelegates(true)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .version(VERSION)
            .taskDataV2(
                TaskDataV2.builder()
                    .async(false)
                    .taskType(TaskType.HTTP.name())
                    .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                    .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                    .build())
            .tags(new ArrayList<>())
            .build();
    Account globalAccount = testUtils.createAccount();
    globalAccount.setGlobalDelegateAccount(true);
    persistence.save(globalAccount);
    when(assignDelegateService.getEligibleDelegatesToExecuteTaskV2(any()))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    when(assignDelegateService.getConnectedDelegateList(any(), any()))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));
    when(assignDelegateService.canAssign(eq(delegateTask.getDelegateId()), any())).thenReturn(true);
    when(assignDelegateService.retrieveActiveDelegates(eq(delegateTask.getAccountId()), any()))
        .thenReturn(Collections.singletonList(delegate.getUuid()));

    RetryDelegate retryDelegate = RetryDelegate.builder().retryPossible(true).delegateTask(delegateTask).build();
    when(retryObserverSubject.fireProcess(any(), any())).thenReturn(retryDelegate);

    Thread thread = new Thread(() -> {
      await().atMost(5L, TimeUnit.SECONDS).until(() -> isNotEmpty(delegateSyncService.syncTaskWaitMap));
      DelegateTask task =
          persistence.createQuery(DelegateTask.class).filter("accountId", delegateTask.getAccountId()).get();

      delegateTaskService.processDelegateResponse(task.getAccountId(), delegate.getUuid(), task.getUuid(),
          DelegateTaskResponse.builder()
              .accountId(task.getAccountId())
              .response(HttpStateExecutionResponse.builder().executionStatus(ExecutionStatus.SUCCESS).build())
              .responseCode(ResponseCode.OK)
              .build());
      new Thread(delegateSyncService).start();
    });
    thread.start();
    when(assignDelegateService.getEligibleDelegatesToExecuteTaskV2(any(DelegateTask.class)))
        .thenReturn(new ArrayList<>(singletonList(DELEGATE_ID)));

    DelegateResponseData responseData = delegateTaskServiceClassic.executeTaskV2(delegateTask);
    assertThat(responseData).isInstanceOf(HttpStateExecutionResponse.class);
    HttpStateExecutionResponse httpResponse = (HttpStateExecutionResponse) responseData;
    assertThat(httpResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandleDriverSyncResponseForGlobalDelegate() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(generateUuid())
                                    .accountId(GLOBAL_DELEGATE_ACCOUNT_ID)
                                    .driverId(generateUuid())
                                    .data(TaskData.builder().async(false).build())
                                    .build();

    DelegateTaskResponse delegateTaskResponse =
        DelegateTaskResponse.builder().response(DelegateStringResponseData.builder().data("OK").build()).build();

    DelegateCallbackService delegateCallbackService = mock(DelegateCallbackService.class);
    when(delegateCallbackRegistry.obtainDelegateCallbackService(delegateTask.getDriverId()))
        .thenReturn(delegateCallbackService);
    byte[] responseData = referenceFalseKryoSerializer.asDeflatedBytes(delegateTaskResponse.getResponse());

    delegateTaskServiceClassic.handleDriverResponse(delegateTask, delegateTaskResponse);

    verify(delegateCallbackService).publishSyncTaskResponse(delegateTask.getUuid(), responseData);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandleDriverAsyncResponseForGlobalDelegate() {
    DelegateTask delegateTask = DelegateTask.builder()
                                    .uuid(generateUuid())
                                    .accountId(GLOBAL_DELEGATE_ACCOUNT_ID)
                                    .driverId(generateUuid())
                                    .data(TaskData.builder().async(true).build())
                                    .build();

    DelegateTaskResponse delegateTaskResponse =
        DelegateTaskResponse.builder().response(DelegateStringResponseData.builder().data("OK").build()).build();

    DelegateCallbackService delegateCallbackService = mock(DelegateCallbackService.class);
    when(delegateCallbackRegistry.obtainDelegateCallbackService(delegateTask.getDriverId()))
        .thenReturn(delegateCallbackService);
    byte[] responseData = referenceFalseKryoSerializer.asDeflatedBytes(delegateTaskResponse.getResponse());

    delegateTaskServiceClassic.handleDriverResponse(delegateTask, delegateTaskResponse);

    verify(delegateCallbackService).publishAsyncTaskResponse(delegateTask.getUuid(), responseData);
  }

  private List<String> setUpDelegatesForInitializationTest() {
    List<String> delegateIds = new ArrayList<>();

    Delegate delegate1 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .version(VERSION)
                             .lastHeartBeat(System.currentTimeMillis())
                             .profileError(true)
                             .build();

    Delegate delegate2 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .version(VERSION)
                             .lastHeartBeat(System.currentTimeMillis())
                             .profileError(false)
                             .profileExecutedAt(TEST_PROFILE_EXECUTION_TIME)
                             .build();

    String delegateProfileId1 = TEST_DELEGATE_PROFILE_ID + "_1";
    Delegate delegate3 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .version(VERSION)
                             .lastHeartBeat(System.currentTimeMillis())
                             .profileError(false)
                             .profileExecutedAt(0L)
                             .delegateProfileId(delegateProfileId1)
                             .build();

    DelegateProfile delegateProfile1 =
        DelegateProfile.builder().accountId(ACCOUNT_ID).uuid(delegateProfileId1).startupScript("").build();

    when(delegateProfileService.get(ACCOUNT_ID, delegateProfileId1)).thenReturn(delegateProfile1);

    String delegateProfileId2 = TEST_DELEGATE_PROFILE_ID + "_2";
    Delegate delegate4 = Delegate.builder()
                             .accountId(ACCOUNT_ID)
                             .version(VERSION)
                             .lastHeartBeat(System.currentTimeMillis())
                             .profileError(false)
                             .profileExecutedAt(0L)
                             .delegateProfileId(delegateProfileId2)
                             .build();

    DelegateProfile delegateProfile2 =
        DelegateProfile.builder().accountId(ACCOUNT_ID).uuid(delegateProfileId2).startupScript("testScript").build();

    when(delegateProfileService.get(ACCOUNT_ID, delegateProfileId2)).thenReturn(delegateProfile2);

    String delegateId_1 = persistence.save(delegate1);
    String delegateId_2 = persistence.save(delegate2);
    String delegateId_3 = persistence.save(delegate3);
    String delegateId_4 = persistence.save(delegate4);

    when(delegateCache.get(ACCOUNT_ID, delegateId_1)).thenReturn(delegate1);
    when(delegateCache.get(ACCOUNT_ID, delegateId_2)).thenReturn(delegate2);
    when(delegateCache.get(ACCOUNT_ID, delegateId_3)).thenReturn(delegate3);
    when(delegateCache.get(ACCOUNT_ID, delegateId_4)).thenReturn(delegate4);

    delegateIds.add(delegateId_1);
    delegateIds.add(delegateId_2);
    delegateIds.add(delegateId_3);
    delegateIds.add(delegateId_4);

    return delegateIds;
  }

  private DelegateGroup setUpDefaultDelegateGroupForTests() {
    DelegateGroup delegateGroup = DelegateGroup.builder()
                                      .name(TEST_DELEGATE_GROUP_NAME)
                                      .accountId(ACCOUNT_ID)
                                      .identifier(TEST_DELEGATE_GROUP_NAME_IDENTIFIER)
                                      .build();

    String delegateGroupId = persistence.save(delegateGroup);
    delegateGroup.setUuid(delegateGroupId);

    return delegateGroup;
  }

  private DelegateGroup setUpDefaultDelegateGroupAfterMigrationForTests(String uuid) {
    DelegateGroup delegateGroup = DelegateGroup.builder()
                                      .uuid(uuid)
                                      .name(TEST_DELEGATE_GROUP_NAME)
                                      .accountId(ACCOUNT_ID)
                                      .identifier(uuidToIdentifier(uuid))
                                      .build();

    persistence.save(delegateGroup);

    return delegateGroup;
  }

  private DelegateSetupDetailsBuilder createDelegateSetupDetails() {
    return DelegateSetupDetails.builder()
        .name(TEST_DELEGATE_GROUP_NAME)
        .size(DelegateSize.LAPTOP)
        .delegateConfigurationId("configId")
        .delegateType(DelegateType.KUBERNETES);
  }
}
