/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.delegate;

import static io.harness.beans.DelegateTask.DELEGATE_QUEUE_TIMEOUT;
import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.DelegateType.KUBERNETES;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.utils.DelegateOwner.NG_DELEGATE_ENABLED_CONSTANT;
import static io.harness.utils.DelegateOwner.NG_DELEGATE_OWNER_CONSTANT;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.TaskType.SPLUNK_COLLECT_LOG_DATAV2;
import static software.wings.beans.TaskType.TERRAFORM_PROVISION_TASK;
import static software.wings.service.InstanceSyncConstants.VALIDATION_TIMEOUT_MINUTES;
import static software.wings.service.impl.AssignDelegateServiceImpl.SCOPE_WILDCARD;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.URL;
import static software.wings.utils.WingsTestConstants.USER_NAME_DECRYPTED;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.common.NGTaskType;
import io.harness.delegate.NoEligibleDelegatesInAccountException;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateBuilder;
import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.beans.DelegateProfile.DelegateProfileBuilder;
import io.harness.delegate.beans.DelegateProfileScopingRule;
import io.harness.delegate.beans.DelegateScope;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskGroup;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.jira.JiraTaskNGParameters;
import io.harness.delegate.task.jira.JiraTaskNGParameters.JiraTaskNGParametersBuilder;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.request.CfInfraMappingDataRequest;
import io.harness.delegate.task.shell.ShellScriptApprovalTaskParameters;
import io.harness.encryption.SecretRefData;
import io.harness.eventsframework.api.Producer;
import io.harness.jira.JiraActionNG;
import io.harness.logstreaming.LogStreamingServiceConfig;
import io.harness.network.LocalhostUtils;
import io.harness.ng.core.BaseNGAccess;
import io.harness.observer.Subject;
import io.harness.outbox.api.OutboxService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateProfileObserver;
import io.harness.service.intfc.DelegateSyncService;
import io.harness.service.intfc.DelegateTaskRetryObserver;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.shell.ScriptType;
import io.harness.time.Timestamp;
import io.harness.version.VersionInfo;
import io.harness.version.VersionInfoManager;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.FeatureTestHelper;
import software.wings.WingsBaseTest;
import software.wings.app.DelegateGrpcConfig;
import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;
import software.wings.beans.Account;
import software.wings.beans.AccountStatus;
import software.wings.beans.AwsConfig;
import software.wings.beans.Environment;
import software.wings.beans.GcpConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.LicenseInfo;
import software.wings.beans.PcfConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.TerraformSourceType;
import software.wings.beans.delegation.TerraformProvisionParameters;
import software.wings.beans.settings.helm.HelmRepoConfigValidationTaskParams;
import software.wings.beans.yaml.GitCommand.GitCommandType;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.delegatetasks.validation.core.DelegateConnectionResult;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sInstanceSyncTaskParameters;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.jre.JreConfig;
import software.wings.licensing.LicenseService;
import software.wings.service.impl.AssignDelegateServiceImpl;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.DelegateDao;
import software.wings.service.impl.DelegateObserver;
import software.wings.service.impl.DelegateServiceImpl;
import software.wings.service.impl.DelegateTaskBroadcastHelper;
import software.wings.service.impl.DelegateTaskServiceClassicImpl;
import software.wings.service.impl.EventEmitter;
import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.analysis.TimeSeriesMlAnalysisType;
import software.wings.service.impl.aws.model.AwsEc2ListInstancesRequest;
import software.wings.service.impl.aws.model.AwsIamListInstanceRolesRequest;
import software.wings.service.impl.aws.model.AwsIamRequest;
import software.wings.service.impl.aws.model.AwsLambdaExecuteFunctionRequest;
import software.wings.service.impl.infra.InfraDownloadService;
import software.wings.service.impl.splunk.SplunkDataCollectionInfoV2;
import software.wings.service.impl.stackdriver.StackDriverDataCollectionInfo;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.DelegateProfileService;
import software.wings.service.intfc.DelegateSelectionLogsService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.mappers.artifact.CfConfigToInternalMapper;
import software.wings.verification.stackdriver.StackDriverMetricDefinition;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.annotation.Description;

public class DelegateTaskProcessTest extends WingsBaseTest {
  private static final String VERSION = "1.0.0";
  private static final String DELEGATE_TYPE = "dockerType";
  private static final String SCRIPT_APPROVAL_COMMAND = "Execute Approval Script";
  private static final String SCRIPT_APPROVAL_ENV_VARIABLE = "HARNESS_APPROVAL_STATUS";
  private static final String SCRIPT_APPROVAL_DIRECTORY = "/tmp";
  private static final String SCOPING_ENTITY_KEY_APP_ID = "APP_ID";
  private static final String SCOPING_ENTITY_KEY_ENV_ID = "ENV_ID";

  private static final String PROJ_ID = "projectId";
  private static final String ORG_ID = "orgId";
  private static final String ACCOUNT_ID = "accountId";

  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private AccountService accountService;
  @Mock private LicenseService licenseService;
  @Mock private EventEmitter eventEmitter;
  @Mock private MainConfiguration mainConfiguration;
  @Mock private BroadcasterFactory broadcasterFactory;
  @Mock private Broadcaster broadcaster;
  @Mock private DelegateProfileService delegateProfileService;
  @Mock private InfraDownloadService infraDownloadService;
  @Mock private SecretManager secretManager;
  @Mock private ManagerDecryptionService managerDecryptionService;
  @Mock private FileService fileService;
  @Mock private AlertService alertService;
  @Mock private VersionInfoManager versionInfoManager;
  @Mock private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Mock private ConfigurationController configurationController;
  @Mock private AuditServiceHelper auditServiceHelper;
  @Mock private DelegateGrpcConfig delegateGrpcConfig;
  @Mock private DelegateSyncService delegateSyncService;
  @Mock private DelegateSelectionLogsService delegateSelectionLogsService;
  @Mock private Producer eventProducer;
  @Mock private OutboxService outboxService;
  @Mock private LoadingCache<String, List<Delegate>> accountDelegatesCache;
  @Mock private LoadingCache<String, String> logStreamingAccountTokenCache;

  @Mock
  private LoadingCache<ImmutablePair<String, String>, Optional<DelegateConnectionResult>> delegateConnectionResultCache;

  @Inject private FeatureTestHelper featureTestHelper;
  @Inject private DelegateDao delegateDao;
  @Inject private KryoSerializer kryoSerializer;

  private final int port = LocalhostUtils.findFreePort();
  @Rule public WireMockRule wireMockRule = new WireMockRule(port);
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Mock private DelegateCache delegateCache;
  @InjectMocks @Inject private DelegateTaskBroadcastHelper delegateTaskBroadcastHelper;
  @InjectMocks @Inject private DelegateServiceImpl delegateService;
  @InjectMocks @Inject private DelegateTaskServiceClassicImpl delegateTaskServiceClassic;
  @InjectMocks @Inject private DelegateTaskService delegateTaskService;
  @Inject @InjectMocks private AssignDelegateServiceImpl assignDelegateService;

  @Inject private HPersistence persistence;

  @Mock private Subject<DelegateProfileObserver> delegateProfileSubject;
  @Mock private Subject<DelegateTaskRetryObserver> retryObserverSubject;
  @Mock private Subject<DelegateObserver> subject;
  @Mock private EnvironmentService environmentService;

  private static final String HELM_SOURCE_REPO_URL = "https://github.com/helm/charts.git";
  private static final String HELM_SOURCE_REPO = "Helm Source Repo";
  private final Account account =
      anAccount().withLicenseInfo(LicenseInfo.builder().accountStatus(AccountStatus.ACTIVE).build()).build();

  private DelegateProfileBuilder createDelegateProfileBuilder() {
    return DelegateProfile.builder().name("DELEGATE_PROFILE_NAME").description("DELEGATE_PROFILE_DESC");
  }

  private static final List<String> supportedTasks = Arrays.stream(TaskType.values()).map(Enum::name).collect(toList());

  @Before
  public void setUp() throws IllegalAccessException, ExecutionException {
    when(mainConfiguration.getLogStreamingServiceConfig())
        .thenReturn(LogStreamingServiceConfig.builder().build().builder().baseUrl("/").build());

    LogStreamingServiceConfig logSteamingServiceConfig =
        LogStreamingServiceConfig.builder().serviceToken("token").baseUrl("http://localhost:8079").build();
    when(mainConfiguration.getLogStreamingServiceConfig()).thenReturn(logSteamingServiceConfig);

    PortalConfig portalConfig = new PortalConfig();
    portalConfig.setImportantDelegateTaskRejectAtLimit(1000);
    portalConfig.setJwtNextGenManagerSecret("**********");
    when(mainConfiguration.getPortal()).thenReturn(portalConfig);
    when(delegateGrpcConfig.getPort()).thenReturn(8080);

    when(accountService.getDelegateConfiguration(anyString()))
        .thenReturn(DelegateConfiguration.builder().delegateVersions(singletonList("0.0.0")).build());
    when(accountService.get(ACCOUNT_ID)).thenReturn(account);
    when(accountService.getFromCacheWithFallback(anyString())).thenReturn(account);
    when(infraDownloadService.getDownloadUrlForDelegate(anyString(), any()))
        .thenReturn("http://localhost:" + port + "/builds/9/delegate.jar");
    when(infraDownloadService.getCdnWatcherBaseUrl()).thenReturn("http://localhost:9500/builds");
    wireMockRule.stubFor(get(urlEqualTo("/delegateci.txt"))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withBody("9.9.9 jobs/delegateci/9/delegate.jar")
                                             .withHeader("Content-Type", "text/plain")));

    wireMockRule.stubFor(head(urlEqualTo("/builds/9/delegate.jar")).willReturn(aResponse().withStatus(200)));

    wireMockRule.stubFor(get(urlEqualTo("/watcherci.txt"))
                             .willReturn(aResponse()
                                             .withStatus(200)
                                             .withBody("8.8.8 jobs/deploy-ci-watcher/8/watcher.jar")
                                             .withHeader("Content-Type", "text/plain")));

    when(broadcasterFactory.lookup(anyString(), anyBoolean())).thenReturn(broadcaster);
    when(versionInfoManager.getVersionInfo()).thenReturn(VersionInfo.builder().version(VERSION).build());
    //    when(delegatesFeature.getMaxUsageAllowedForAccount(ACCOUNT_ID)).thenReturn(Integer.MAX_VALUE);

    FieldUtils.writeField(delegateService, "delegateProfileSubject", delegateProfileSubject, true);
    FieldUtils.writeField(delegateService, "subject", subject, true);
    FieldUtils.writeField(delegateTaskService, "retryObserverSubject", retryObserverSubject, true);
    when(accountDelegatesCache.get(anyString())).thenReturn(Collections.emptyList());
    when(logStreamingAccountTokenCache.get(anyString())).thenReturn("");
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Task Criteria match with delegate, TaskType.AWS_IAM_TASK")
  public void shouldProcessDelegateTask_CriteriaMatch() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createDelegate(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    AwsIamRequest request = AwsIamListInstanceRolesRequest.builder().awsConfig(AwsConfig.builder().build()).build();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
            .data(TaskData.builder()
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
                                                    .accountId(accountId)
                                                    .delegateId(delegate.getUuid())
                                                    .criteria(matchingExecutionCapability.fetchCapabilityBasis())
                                                    .validated(true)
                                                    .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    delegateTaskServiceClassic.scheduleSyncTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(QUEUED);
    assertThat(task.getExpiry()).isNotNull();
    assertThat(task.getEligibleToExecuteDelegateIds()).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Task Criteria mismatch with delegate, TaskType.AWS_IAM_TASK")
  public void failOnTaskProcess_CriteriaMisMatch() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createDelegate(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    AwsIamRequest request = AwsIamListInstanceRolesRequest.builder().awsConfig(AwsConfig.builder().build()).build();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.AWS_IAM_TASK.name())
                      .parameters(new Object[] {request})
                      .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                      .build())
            .build();

    HttpConnectionExecutionCapability matchingExecutionCapability =
        buildHttpConnectionExecutionCapability("https//google.com", null);
    delegateTask.setExecutionCapabilities(Arrays.asList(matchingExecutionCapability));
    DelegateConnectionResult connectionResult = DelegateConnectionResult.builder()
                                                    .accountId(accountId)
                                                    .delegateId(delegate.getUuid())
                                                    .criteria(matchingExecutionCapability.fetchCapabilityBasis())
                                                    .validated(true)
                                                    .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    delegateTaskServiceClassic.scheduleSyncTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(QUEUED);
    assertThat(task.getExpiry()).isNotNull();
    assertThat(task.getEligibleToExecuteDelegateIds()).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("process sync task, TaskType.SHELL_SCRIPT_APPROVAL")
  public void shouldProcessDelegateTask_Sync() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createDelegate(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    ShellScriptApprovalTaskParameters shellScriptApprovalTaskParameters =
        ShellScriptApprovalTaskParameters.builder()
            .accountId(accountId)
            .appId(APP_ID)
            .commandName(SCRIPT_APPROVAL_COMMAND)
            .outputVars(SCRIPT_APPROVAL_ENV_VARIABLE)
            .workingDirectory(SCRIPT_APPROVAL_DIRECTORY)
            .scriptType(ScriptType.BASH)
            .script("")
            .delegateSelectors(Arrays.asList("sel1", "sel2"))
            .build();

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(accountId)
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
                                    .description("Shell Script Approval")
                                    .data(TaskData.builder()
                                              .async(false)
                                              .taskType(TaskType.SHELL_SCRIPT_APPROVAL.name())
                                              .parameters(new Object[] {shellScriptApprovalTaskParameters})
                                              .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                              .build())
                                    .selectionLogsTrackingEnabled(true)
                                    .build();
    delegateTaskServiceClassic.scheduleSyncTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(QUEUED);
    assertThat(task.getExpiry()).isNotNull();
    assertThat(task.getEligibleToExecuteDelegateIds()).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("process async task, Task_Type.SPLUNK_COLLECT_LOG_DATAV2")
  public void shouldProcessDelegateTask_async() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createDelegate(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);

    DataCollectionInfoV2 dataCollectionInfoV2 = SplunkDataCollectionInfoV2.builder()
                                                    .accountId(accountId)
                                                    .connectorId(generateUuid())
                                                    .stateExecutionId(generateUuid())
                                                    .startTime(Instant.now().minus(10, ChronoUnit.MINUTES))
                                                    .endTime(Instant.now())
                                                    .applicationId(generateUuid())
                                                    .query("query")
                                                    .hostnameField("hostnameField")
                                                    .build();
    SplunkDataCollectionInfoV2 params = (SplunkDataCollectionInfoV2) dataCollectionInfoV2;
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(accountId)
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
                                    .waitId(generateUuid())
                                    .data(TaskData.builder()
                                              .async(true)
                                              .taskType(SPLUNK_COLLECT_LOG_DATAV2.name())
                                              .parameters(new Object[] {params})
                                              .timeout(TimeUnit.MINUTES.toMillis(5))
                                              .build())
                                    .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, ENV_ID)
                                    .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, EnvironmentType.NON_PROD.name())
                                    .build();
    delegateTaskServiceClassic.queueTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(QUEUED);
    assertThat(task.getExpiry()).isNotNull();
    assertThat(task.getEligibleToExecuteDelegateIds()).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Process task when more than one criteria match, Task_Type.TERRAFORM_PROVISION_TASK")
  public void shouldProcessDelegateTask2_TwoCriteriaMatch() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createDelegate(accountId);
    HostConnectionAttributes hostConnectionAttributes = new HostConnectionAttributes();
    hostConnectionAttributes.setSshPort(22);
    SettingAttribute sshSettingAttribute = new SettingAttribute();
    sshSettingAttribute.setValue(hostConnectionAttributes);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    TerraformProvisionParameters parameters = TerraformProvisionParameters.builder()
                                                  .sourceRepo(GitConfig.builder()
                                                                  .repoUrl("https://github.com/testtp")
                                                                  .sshSettingAttribute(sshSettingAttribute.toDTO())
                                                                  .build())
                                                  .sourceType(TerraformSourceType.GIT)
                                                  .secretManagerConfig(null)
                                                  .isGitHostConnectivityCheck(true)
                                                  .build();
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(accountId)
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, requireNonNull(APP_ID))
                                    .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, ENV_ID)
                                    .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, EnvironmentType.NON_PROD.name())
                                    .selectionLogsTrackingEnabled(true)
                                    .description("Terraform provision task execution")
                                    .tags(Arrays.asList("sel1"))
                                    .data(TaskData.builder()
                                              .async(true)
                                              .taskType(TERRAFORM_PROVISION_TASK.name())
                                              .parameters(new Object[] {parameters})
                                              .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                              .build())
                                    .build();
    DelegateConnectionResult connectionResult1 = DelegateConnectionResult.builder()
                                                     .accountId(accountId)
                                                     .delegateId(delegate.getUuid())
                                                     .criteria("https://github.com/testtp")
                                                     .validated(true)
                                                     .build();
    DelegateConnectionResult connectionResult2 = DelegateConnectionResult.builder()
                                                     .accountId(accountId)
                                                     .delegateId(delegate.getUuid())
                                                     .criteria("terraform")
                                                     .validated(true)
                                                     .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate.getUuid(), connectionResult1.getCriteria())))
        .thenReturn(of(connectionResult1));
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate.getUuid(), connectionResult2.getCriteria())))
        .thenReturn(of(connectionResult2));

    delegateTaskServiceClassic.queueTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(QUEUED);
    assertThat(task.getExpiry()).isNotNull();
    assertThat(task.getEligibleToExecuteDelegateIds()).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("when task selectors mismatch with delegate selectors, TaskType.K8S_COMMAND_TASK")
  public void failOnTaskProcess_WhenHasTaskSelectorsMisMatchWithDelegateSelectors() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createDelegate(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    thrown.expect(NoEligibleDelegatesInAccountException.class);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .waitId(generateUuid())
            .tags(asList("sel1", "sel3"))
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.K8S_COMMAND_TASK.name())
                      .parameters(
                          new Object[] {K8sInstanceSyncTaskParameters.builder()
                                            .accountId(ACCOUNT_ID)
                                            .appId(APP_ID)
                                            .namespace("namespace")
                                            .releaseName("release_name")
                                            .k8sClusterConfig(K8sClusterConfig.builder().namespace("namespace").build())
                                            .build()})
                      .timeout(TimeUnit.MINUTES.toMillis(VALIDATION_TIMEOUT_MINUTES))
                      .build())
            .expiry(System.currentTimeMillis() + DELEGATE_QUEUE_TIMEOUT)
            .build();
    delegateTaskServiceClassic.executeTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("NGTaskType.JIRA_TASK_NG with JIRA Connector")
  public void shouldProcessTask_whenProjectLevelTaskOwnerMatchWithDelegateOwner() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createNGProjectLevelDelegateWithOwner(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    BaseNGAccess ngAccess =
        BaseNGAccess.builder().projectIdentifier(PROJ_ID).orgIdentifier(ORG_ID).accountIdentifier(accountId).build();
    Map<String, String> ngTaskSetupAbstractionsWithOwner = getNGTaskSetupAbstractionsWithOwner(
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    JiraTaskNGParametersBuilder paramsBuilder =
        JiraTaskNGParameters.builder().action(JiraActionNG.VALIDATE_CREDENTIALS);
    final JiraConnectorDTO connector = JiraConnectorDTO.builder()
                                           .jiraUrl("http://gira-url")
                                           .username("username")
                                           .passwordRef(SecretRefData.builder().build())
                                           .build();
    JiraTaskNGParameters taskNGParameters = paramsBuilder.encryptionDetails(null)
                                                .jiraConnectorDTO(connector)
                                                .delegateSelectors(Arrays.asList("sel1"))
                                                .build();
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(ngAccess.getAccountIdentifier())
                                    .setupAbstraction(NG_DELEGATE_ENABLED_CONSTANT,
                                        ngTaskSetupAbstractionsWithOwner.get(NG_DELEGATE_ENABLED_CONSTANT))
                                    .setupAbstraction(NG_DELEGATE_OWNER_CONSTANT,
                                        ngTaskSetupAbstractionsWithOwner.get(NG_DELEGATE_OWNER_CONSTANT))
                                    .data(TaskData.builder()
                                              .async(true)
                                              .taskType(NGTaskType.JIRA_TASK_NG.name())
                                              .parameters(new Object[] {taskNGParameters})
                                              .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                              .build())
                                    .build();
    when(logStreamingAccountTokenCache.get(delegateTask.getAccountId())).thenReturn("");

    DelegateConnectionResult connectionResult = DelegateConnectionResult.builder()
                                                    .accountId(accountId)
                                                    .delegateId(delegate.getUuid())
                                                    .criteria("http://gira-url")
                                                    .validated(true)
                                                    .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    delegateTaskServiceClassic.queueTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(QUEUED);
    assertThat(task.getExpiry()).isNotNull();
    assertThat(task.getEligibleToExecuteDelegateIds()).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("NGTaskType.DOCKER_ARTIFACT_TASK_NG")
  public void shouldProcessTask_WhenOrgLevelTaskOwnerMatchWithDelegateOwner() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createNGOrgLevelDelegateWithOwner(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    BaseNGAccess ngAccess =
        BaseNGAccess.builder().projectIdentifier(PROJ_ID).orgIdentifier(ORG_ID).accountIdentifier(accountId).build();
    DockerArtifactDelegateRequest dockerArtifactDelegateRequest =
        DockerArtifactDelegateRequest.builder()
            .imagePath("imagePath")
            .tagRegex("tagRegex")
            .dockerConnectorDTO(DockerConnectorDTO.builder()
                                    .dockerRegistryUrl("http://docker-url")
                                    .auth(DockerAuthenticationDTO.builder()
                                              .credentials(DockerUserNamePasswordDTO.builder()
                                                               .username("username")
                                                               .passwordRef(SecretRefData.builder().build())
                                                               .build())
                                              .build())
                                    .build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .artifactTaskType(ArtifactTaskType.GET_BUILDS)
                                                        .attributes(dockerArtifactDelegateRequest)
                                                        .build();

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(ngAccess.getAccountIdentifier())
                                    .setupAbstraction("orgIdentifier", ngAccess.getOrgIdentifier())
                                    .setupAbstraction("ng", "true")
                                    .setupAbstraction("owner", ngAccess.getOrgIdentifier())
                                    .data(TaskData.builder()
                                              .async(true)
                                              .taskType(NGTaskType.DOCKER_ARTIFACT_TASK_NG.name())
                                              .parameters(new Object[] {artifactTaskParameters})
                                              .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                              .build())
                                    .build();
    when(logStreamingAccountTokenCache.get(delegateTask.getAccountId())).thenReturn("");
    DelegateConnectionResult connectionResult = DelegateConnectionResult.builder()
                                                    .accountId(accountId)
                                                    .delegateId(delegate.getUuid())
                                                    .criteria("http://docker-url")
                                                    .validated(true)
                                                    .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    delegateTaskServiceClassic.queueTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(QUEUED);
    assertThat(task.getExpiry()).isNotNull();
    assertThat(task.getEligibleToExecuteDelegateIds()).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Process task when there is no delegate owner, TaskType.HELM_REPO_CONFIG_VALIDATION")
  public void shouldProcessTask_WhenNoDelegateOwner() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createDelegate(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    HelmRepoConfigValidationTaskParams taskParams =
        HelmRepoConfigValidationTaskParams.builder().delegateSelectors(Collections.singleton("sel1")).build();

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(accountId)
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
                                    .data(TaskData.builder()
                                              .async(false)
                                              .taskType(TaskType.HELM_REPO_CONFIG_VALIDATION.name())
                                              .parameters(new Object[] {taskParams})
                                              .timeout(TimeUnit.MINUTES.toMillis(2))
                                              .build())
                                    .build();
    delegateTaskServiceClassic.scheduleSyncTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(QUEUED);
    assertThat(task.getExpiry()).isNotNull();
    assertThat(task.getEligibleToExecuteDelegateIds()).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Owner match on project level task with org level delegate, TaskType.NG_AWS_TAS")
  public void shouldProcessTask_WhenProjectLevelTaskWithOrgLevelDelegate() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createNGOrgLevelDelegateWithOwner(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    BaseNGAccess ngAccess =
        BaseNGAccess.builder().projectIdentifier(PROJ_ID).orgIdentifier(ORG_ID).accountIdentifier(accountId).build();

    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.IRSA).build())
            .build();
    AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                      .awsConnector(awsConnectorDTO)
                                      .awsTaskType(AwsTaskType.VALIDATE)
                                      .encryptionDetails(Collections.emptyList())
                                      .build();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(ngAccess.getAccountIdentifier())
            .setupAbstraction("ng", "true")
            .setupAbstraction("owner", ngAccess.getOrgIdentifier() + "/" + ngAccess.getProjectIdentifier())
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.NG_AWS_TASK.name())
                      .parameters(new Object[] {awsTaskParams})
                      .timeout(TimeUnit.MINUTES.toMillis(2))
                      .build())
            .build();
    delegateTaskServiceClassic.scheduleSyncTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(QUEUED);
    assertThat(task.getExpiry()).isNotNull();
    assertThat(task.getEligibleToExecuteDelegateIds()).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("Owner match on project level task with project level delegate, TaskType.NG_AWS_TASK")
  public void shouldProcessTask_WhenProjectLevelTaskWithProjectLevelDelegate() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createNGOrgLevelDelegateWithOwner(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    BaseNGAccess ngAccess =
        BaseNGAccess.builder().projectIdentifier(PROJ_ID).orgIdentifier(ORG_ID).accountIdentifier(accountId).build();

    AwsConnectorDTO awsConnectorDTO =
        AwsConnectorDTO.builder()
            .credential(AwsCredentialDTO.builder().awsCredentialType(AwsCredentialType.IRSA).build())
            .build();
    AwsTaskParams awsTaskParams = AwsTaskParams.builder()
                                      .awsConnector(awsConnectorDTO)
                                      .awsTaskType(AwsTaskType.VALIDATE)
                                      .encryptionDetails(Collections.emptyList())
                                      .build();
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(ngAccess.getAccountIdentifier())
                                    .setupAbstraction("ng", "true")
                                    .setupAbstraction("owner", ngAccess.getOrgIdentifier())
                                    .data(TaskData.builder()
                                              .async(false)
                                              .taskType(TaskType.NG_AWS_TASK.name())
                                              .parameters(new Object[] {awsTaskParams})
                                              .timeout(TimeUnit.MINUTES.toMillis(2))
                                              .build())
                                    .build();
    delegateTaskServiceClassic.scheduleSyncTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(QUEUED);
    assertThat(task.getExpiry()).isNotNull();
    assertThat(task.getEligibleToExecuteDelegateIds()).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("NGTaskType.DOCKER_ARTIFACT_TASK_NG, fail when org level task on project level delegate")
  public void failOnTaskProcess_WhenOwnerLevelMismatch() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createNGProjectLevelDelegateWithOwner(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    BaseNGAccess ngAccess =
        BaseNGAccess.builder().projectIdentifier(PROJ_ID).orgIdentifier(ORG_ID).accountIdentifier(accountId).build();
    DockerArtifactDelegateRequest dockerArtifactDelegateRequest =
        DockerArtifactDelegateRequest.builder()
            .imagePath("imagePath")
            .tagRegex("tagRegex")
            .dockerConnectorDTO(DockerConnectorDTO.builder()
                                    .dockerRegistryUrl("http://docker-url")
                                    .auth(DockerAuthenticationDTO.builder()
                                              .credentials(DockerUserNamePasswordDTO.builder()
                                                               .username("username")
                                                               .passwordRef(SecretRefData.builder().build())
                                                               .build())
                                              .build())
                                    .build())
            .build();
    ArtifactTaskParameters artifactTaskParameters = ArtifactTaskParameters.builder()
                                                        .accountId(ngAccess.getAccountIdentifier())
                                                        .artifactTaskType(ArtifactTaskType.GET_BUILDS)
                                                        .attributes(dockerArtifactDelegateRequest)
                                                        .build();

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(ngAccess.getAccountIdentifier())
                                    .setupAbstraction("orgIdentifier", ngAccess.getOrgIdentifier())
                                    .setupAbstraction("ng", "true")
                                    .setupAbstraction("owner", ngAccess.getOrgIdentifier())
                                    .data(TaskData.builder()
                                              .async(true)
                                              .taskType(NGTaskType.DOCKER_ARTIFACT_TASK_NG.name())
                                              .parameters(new Object[] {artifactTaskParameters})
                                              .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                              .build())
                                    .build();
    delegateTaskServiceClassic.queueTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("AWS_EC2_TASK")
  public void shouldProcessTask_WhenScopeWithAppIdAsWildCard() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createDelegate(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    AwsConfig awsConfig = new AwsConfig("ACCESS_KEY".toCharArray(), null, "", "", false, "aws-delegate", null, false,
        false, null, null, null, false, null);
    AwsEc2ListInstancesRequest request = AwsEc2ListInstancesRequest.builder()
                                             .awsConfig(awsConfig)
                                             .encryptionDetails(new ArrayList<>())
                                             .region("us-east-1")
                                             .filters(new ArrayList<>())
                                             .build();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, SCOPE_WILDCARD)
            .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.AWS_EC2_TASK.name())
                      .parameters(new Object[] {request})
                      .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                      .build())
            .build();
    DelegateConnectionResult connectionResult = DelegateConnectionResult.builder()
                                                    .accountId(accountId)
                                                    .delegateId(delegate.getUuid())
                                                    .criteria("https://aws.amazon.com")
                                                    .validated(true)
                                                    .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    delegateTaskServiceClassic.scheduleSyncTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(QUEUED);
    assertThat(task.getExpiry()).isNotNull();
    assertThat(task.getEligibleToExecuteDelegateIds()).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("PCF_COMMAND_TASK ")
  public void shouldProcessTask_WhenScopeWithEnvIdAsWildCard() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createDelegateWithScope(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    PcfConfig pcfConfig =
        PcfConfig.builder().accountId(accountId).endpointUrl(URL).username(USER_NAME_DECRYPTED).build();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(pcfConfig.getAccountId())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, SCOPE_WILDCARD)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.PCF_COMMAND_TASK.name())
                      .parameters(new Object[] {CfInfraMappingDataRequest.builder()
                                                    .pcfConfig(CfConfigToInternalMapper.toCfInternalConfig(pcfConfig))
                                                    .pcfCommandType(CfCommandRequest.PcfCommandType.VALIDATE)
                                                    .limitPcfThreads(false)
                                                    .timeoutIntervalInMin(2)
                                                    .build(),
                          null})
                      .timeout(TimeUnit.MINUTES.toMillis(2))
                      .build())
            .build();
    DelegateConnectionResult connectionResult = DelegateConnectionResult.builder()
                                                    .accountId(accountId)
                                                    .delegateId(delegate.getUuid())
                                                    .criteria("Pcf:url")
                                                    .validated(true)
                                                    .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    delegateTaskServiceClassic.scheduleSyncTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(QUEUED);
    assertThat(task.getExpiry()).isNotNull();
    assertThat(task.getEligibleToExecuteDelegateIds()).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("GIT_COMMAND, when delegate scope match with appid and task type")
  public void shouldProcessTask_WhenScopeMatch() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createDelegateWithScope(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    GitConfig gitConfig =
        GitConfig.builder().repoUrl(HELM_SOURCE_REPO_URL).branch("master").accountId(accountId).build();

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(gitConfig.getAccountId())
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
                                    .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, SCOPE_WILDCARD)
                                    .data(TaskData.builder()
                                              .async(false)
                                              .taskType(TaskType.GIT_COMMAND.name())
                                              .parameters(new Object[] {GitCommandType.VALIDATE, gitConfig, null})
                                              .timeout(TimeUnit.SECONDS.toMillis(60))
                                              .build())
                                    .build();
    DelegateConnectionResult connectionResult = DelegateConnectionResult.builder()
                                                    .accountId(accountId)
                                                    .delegateId(delegate.getUuid())
                                                    .criteria("GIT:https://github.com/helm/charts.git")
                                                    .validated(true)
                                                    .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    delegateTaskServiceClassic.scheduleSyncTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(QUEUED);
    assertThat(task.getExpiry()).isNotNull();
    assertThat(task.getEligibleToExecuteDelegateIds()).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("TaskType.AWS_LAMBDA_TASK, when delegate scope match with environment")
  public void shouldProcessTask_WhenScopeMatchWithEnvironment() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createDelegateWithScopeAndEnvironmentType(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    AwsLambdaExecuteFunctionRequest request = AwsLambdaExecuteFunctionRequest.builder()
                                                  .awsConfig(AwsConfig.builder().build())
                                                  .encryptionDetails(emptyList())
                                                  .region("us-east-1")
                                                  .functionName("fxName")
                                                  .payload("payload")
                                                  .qualifier("qual")
                                                  .build();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, ENV_ID)
            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, INFRA_MAPPING_ID)
            .tags(isNotEmpty(request.getAwsConfig().getTag()) ? singletonList(request.getAwsConfig().getTag()) : null)
            .selectionLogsTrackingEnabled(true)
            .description("Aws Lambda Verification task")
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.AWS_LAMBDA_TASK.name())
                      .parameters(new Object[] {request})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .build();
    when(environmentService.get(APP_ID, ENV_ID, false))
        .thenReturn(Environment.Builder.anEnvironment().accountId(accountId).appId(APP_ID).name(ENV_NAME).build());
    DelegateConnectionResult connectionResult = DelegateConnectionResult.builder()
                                                    .accountId(accountId)
                                                    .delegateId(delegate.getUuid())
                                                    .criteria("https://aws.amazon.com")
                                                    .validated(true)
                                                    .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    delegateTaskServiceClassic.executeTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(QUEUED);
    assertThat(task.getExpiry()).isNotNull();
    assertThat(task.getEligibleToExecuteDelegateIds()).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("GIT_COMMAND: when delegate has exclude scope from task scope")
  public void failOnTaskProcess_WhenDelegateHasExcludeScopeOfTaskScope() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createDelegateWithExcludeScope(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    GitConfig gitConfig =
        GitConfig.builder().repoUrl(HELM_SOURCE_REPO_URL).branch("master").accountId(accountId).build();

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(gitConfig.getAccountId())
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
                                    .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, ENV_ID)
                                    .data(TaskData.builder()
                                              .async(false)
                                              .taskType(TaskType.GIT_COMMAND.name())
                                              .parameters(new Object[] {GitCommandType.VALIDATE, gitConfig, null})
                                              .timeout(TimeUnit.SECONDS.toMillis(60))
                                              .build())
                                    .build();
    DelegateConnectionResult connectionResult = DelegateConnectionResult.builder()
                                                    .accountId(accountId)
                                                    .delegateId(delegate.getUuid())
                                                    .criteria("GIT:https://github.com/helm/charts.git")
                                                    .validated(true)
                                                    .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    thrown.expect(NoEligibleDelegatesInAccountException.class);
    delegateTaskServiceClassic.scheduleSyncTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("GIT_COMMAND, when delegate scope match with appid and task type")
  public void failOnTaskProcess_WhenScopeMisMatch() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createDelegateWithScope(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    thrown.expect(NoEligibleDelegatesInAccountException.class);
    GitConfig gitConfig =
        GitConfig.builder().repoUrl(HELM_SOURCE_REPO_URL).branch("master").accountId(accountId).build();

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(gitConfig.getAccountId())
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, "APP_ID2")
                                    .data(TaskData.builder()
                                              .async(false)
                                              .taskType(TaskType.GIT_COMMAND.name())
                                              .parameters(new Object[] {GitCommandType.VALIDATE, gitConfig, null})
                                              .timeout(TimeUnit.SECONDS.toMillis(60))
                                              .build())
                                    .build();
    DelegateConnectionResult connectionResult = DelegateConnectionResult.builder()
                                                    .accountId(accountId)
                                                    .delegateId(delegate.getUuid())
                                                    .criteria("GIT:https://github.com/helm/charts.git")
                                                    .validated(true)
                                                    .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    delegateTaskServiceClassic.scheduleSyncTask(delegateTask);

    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("")
  public void failOnTaskProcess_WhenScopeInDelegateButNoScopesInTask() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createDelegateWithScope(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    thrown.expect(NoEligibleDelegatesInAccountException.class);
    PcfConfig pcfConfig =
        PcfConfig.builder().accountId(accountId).endpointUrl(URL).username(USER_NAME_DECRYPTED).build();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(pcfConfig.getAccountId())
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.PCF_COMMAND_TASK.name())
                      .parameters(new Object[] {CfInfraMappingDataRequest.builder()
                                                    .pcfConfig(CfConfigToInternalMapper.toCfInternalConfig(pcfConfig))
                                                    .pcfCommandType(CfCommandRequest.PcfCommandType.VALIDATE)
                                                    .limitPcfThreads(false)
                                                    .timeoutIntervalInMin(2)
                                                    .build(),
                          null})
                      .timeout(TimeUnit.MINUTES.toMillis(2))
                      .build())
            .build();
    DelegateConnectionResult connectionResult = DelegateConnectionResult.builder()
                                                    .accountId(accountId)
                                                    .delegateId(delegate.getUuid())
                                                    .criteria("Pcf:url")
                                                    .validated(true)
                                                    .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    delegateTaskServiceClassic.scheduleSyncTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("STACKDRIVER_COLLECT_METRIC_DATA: when scope dont support task type")
  public void failOnTaskProcess_WhenTaskTypeNotMatchWithScopeTaskType() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createDelegateWithScope(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    StackDriverDataCollectionInfo dataCollectionInfo =
        StackDriverDataCollectionInfo.builder()
            .collectionTime(10)
            .applicationId(APP_ID)
            .stateExecutionId(generateUuid())
            .initialDelayMinutes(0)
            .timeSeriesMlAnalysisType(TimeSeriesMlAnalysisType.COMPARATIVE)
            .timeSeriesToCollect(
                Collections.singletonList(StackDriverMetricDefinition.builder()
                                              .metricName("MemoryRequestUtilization")
                                              .txnName("Memory Request Utilization")
                                              .metricType("VALUE")
                                              .aggregation(new StackDriverMetricDefinition.Aggregation())
                                              .build()))
            .gcpConfig(GcpConfig.builder().accountId(accountId).build())
            .startTime(Timestamp.currentMinuteBoundary() - TimeUnit.MINUTES.toMillis(2))
            .build();
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(accountId)
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
                                    .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, ENV_ID)
                                    .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, INFRA_MAPPING_ID)
                                    .data(TaskData.builder()
                                              .async(true)
                                              .taskType(TaskType.STACKDRIVER_COLLECT_METRIC_DATA.name())
                                              .parameters(new Object[] {dataCollectionInfo})
                                              .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                              .build())
                                    .build();
    delegateTaskServiceClassic.queueTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("K8S_COMMAND_TASK")
  public void shouldProcessTask_WhenSelectorMatchWithTaskTag() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createDelegate(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .waitId(generateUuid())
            .tags(asList("sel1", "sel2"))
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.K8S_COMMAND_TASK.name())
                      .parameters(
                          new Object[] {K8sInstanceSyncTaskParameters.builder()
                                            .accountId(ACCOUNT_ID)
                                            .appId(APP_ID)
                                            .namespace("namespace")
                                            .releaseName("release_name")
                                            .k8sClusterConfig(K8sClusterConfig.builder().namespace("namespace").build())
                                            .build()})
                      .timeout(TimeUnit.MINUTES.toMillis(VALIDATION_TIMEOUT_MINUTES))
                      .build())
            .expiry(System.currentTimeMillis() + DELEGATE_QUEUE_TIMEOUT)
            .build();
    delegateTaskServiceClassic.executeTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(QUEUED);
    assertThat(task.getExpiry()).isNotNull();
    assertThat(task.getEligibleToExecuteDelegateIds()).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("K8S_COMMAND_TASK")
  public void shouldProcessTask_WhenTaskTagMatchWitTagFromDelegateGroup() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createDelegateWithDelegateGroup(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .waitId(generateUuid())
            .tags(asList("custom-grp-tag1", "custom-grp-tag2"))
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.K8S_COMMAND_TASK.name())
                      .parameters(
                          new Object[] {K8sInstanceSyncTaskParameters.builder()
                                            .accountId(ACCOUNT_ID)
                                            .appId(APP_ID)
                                            .namespace("namespace")
                                            .releaseName("release_name")
                                            .k8sClusterConfig(K8sClusterConfig.builder().namespace("namespace").build())
                                            .build()})
                      .timeout(TimeUnit.MINUTES.toMillis(VALIDATION_TIMEOUT_MINUTES))
                      .build())
            .expiry(System.currentTimeMillis() + DELEGATE_QUEUE_TIMEOUT)
            .build();
    delegateTaskServiceClassic.executeTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNotNull();
    assertThat(task.getStatus()).isEqualTo(QUEUED);
    assertThat(task.getExpiry()).isNotNull();
    assertThat(task.getEligibleToExecuteDelegateIds()).contains(delegate.getUuid());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("")
  public void shouldProcessTask_WhenScopingRulesInDelegateProfileMatchWithTaskAbstractionValue()
      throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createDelegateWithDelegateProfile(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    thrown.expect(NoEligibleDelegatesInAccountException.class);
    GitConfig gitConfig =
        GitConfig.builder().repoUrl(HELM_SOURCE_REPO_URL).branch("master").accountId(accountId).build();

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(gitConfig.getAccountId())
                                    .setupAbstraction(SCOPING_ENTITY_KEY_APP_ID, APP_ID)
                                    .data(TaskData.builder()
                                              .async(false)
                                              .taskType(TaskType.GIT_COMMAND.name())
                                              .parameters(new Object[] {GitCommandType.VALIDATE, gitConfig, null})
                                              .timeout(TimeUnit.SECONDS.toMillis(60))
                                              .build())
                                    .build();
    DelegateConnectionResult connectionResult = DelegateConnectionResult.builder()
                                                    .accountId(accountId)
                                                    .delegateId(delegate.getUuid())
                                                    .criteria("GIT:https://github.com/helm/charts.git")
                                                    .validated(true)
                                                    .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    delegateTaskServiceClassic.scheduleSyncTask(delegateTask);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  @Description("")
  public void failOnTaskProcess_WhenNoTaskAbstractionButWithDelegateProfileScope() throws ExecutionException {
    String accountId = generateUuid();
    Delegate delegate = createDelegateWithDelegateProfile(accountId);
    when(accountDelegatesCache.get(accountId)).thenReturn(singletonList(delegate));
    when(delegateCache.get(accountId, delegate.getUuid(), false)).thenReturn(delegate);
    thrown.expect(NoEligibleDelegatesInAccountException.class);
    GitConfig gitConfig =
        GitConfig.builder().repoUrl(HELM_SOURCE_REPO_URL).branch("master").accountId(accountId).build();

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(gitConfig.getAccountId())
                                    .data(TaskData.builder()
                                              .async(false)
                                              .taskType(TaskType.GIT_COMMAND.name())
                                              .parameters(new Object[] {GitCommandType.VALIDATE, gitConfig, null})
                                              .timeout(TimeUnit.SECONDS.toMillis(60))
                                              .build())
                                    .build();
    DelegateConnectionResult connectionResult = DelegateConnectionResult.builder()
                                                    .accountId(accountId)
                                                    .delegateId(delegate.getUuid())
                                                    .criteria("GIT:https://github.com/helm/charts.git")
                                                    .validated(true)
                                                    .build();
    when(delegateConnectionResultCache.get(ImmutablePair.of(delegate.getUuid(), connectionResult.getCriteria())))
        .thenReturn(of(connectionResult));
    delegateTaskServiceClassic.scheduleSyncTask(delegateTask);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task).isNull();
  }

  private Delegate createDelegateWithScope(String accountId) {
    Delegate delegate = createDelegateBuilder(accountId).build();
    String delegateScopeId = "delegateScope22";
    List<String> applicationList = Arrays.asList(APP_ID, "APP_ID2");
    List<String> environmentList = Arrays.asList(ENV_ID);
    List<TaskGroup> taskGroups = Arrays.asList(TaskGroup.JIRA, TaskGroup.AWS, TaskGroup.GIT, TaskGroup.PCF);
    DelegateScope delegateScope = DelegateScope.builder()
                                      .accountId(accountId)
                                      .name("DELEGATE_SCOPE_TEST")
                                      .environments(environmentList)
                                      .applications(applicationList)
                                      .uuid(delegateScopeId)
                                      .taskTypes(taskGroups)
                                      .build();
    persistence.save(delegateScope);
    delegate.setIncludeScopes(Arrays.asList(delegateScope));
    persistence.save(delegate);
    return delegate;
  }

  private Delegate createDelegateWithExcludeScope(String accountId) {
    Delegate delegate = createDelegateBuilder(accountId).build();
    String delegateScopeId = "delegateScope22";
    List<String> applicationList = Arrays.asList(APP_ID);
    List<String> environmentList = Arrays.asList(ENV_ID);
    List<TaskGroup> taskGroups = Arrays.asList(TaskGroup.JIRA, TaskGroup.AWS, TaskGroup.GIT, TaskGroup.PCF);
    DelegateScope delegateScope = DelegateScope.builder()
                                      .accountId(accountId)
                                      .name("DELEGATE_SCOPE_TEST")
                                      .environments(environmentList)
                                      .applications(applicationList)
                                      .uuid(delegateScopeId)
                                      .taskTypes(taskGroups)
                                      .build();
    persistence.save(delegateScope);
    delegate.setExcludeScopes(Arrays.asList(delegateScope));
    persistence.save(delegate);
    return delegate;
  }

  private Delegate createDelegateWithScopeAndEnvironmentType(String accountId) {
    Delegate delegate = createDelegateBuilder(accountId).build();
    String delegateScopeId = "delegateScope22";
    List<String> applicationList = Arrays.asList(APP_ID, "APP_ID2");
    List<String> environmentList = Arrays.asList(ENV_ID);
    List<TaskGroup> taskGroups = Arrays.asList(TaskGroup.JIRA, TaskGroup.AWS, TaskGroup.GIT, TaskGroup.PCF);
    DelegateScope delegateScope = DelegateScope.builder()
                                      .accountId(accountId)
                                      .name("DELEGATE_SCOPE_TEST")
                                      .environments(environmentList)
                                      .applications(applicationList)
                                      .environmentTypes(Arrays.asList(EnvironmentType.NON_PROD))
                                      .uuid(delegateScopeId)
                                      .taskTypes(taskGroups)
                                      .build();
    persistence.save(delegateScope);
    delegate.setIncludeScopes(Arrays.asList(delegateScope));
    persistence.save(delegate);
    return delegate;
  }

  private Delegate createDelegate(String accountId) {
    Delegate delegate = createDelegateBuilder(accountId).build();
    persistence.save(delegate);
    return delegate;
  }
  private Delegate createNGProjectLevelDelegateWithOwner(String accountId) {
    Delegate delegate = createDelegateBuilder(accountId).build();
    delegate.setOwner(DelegateEntityOwner.builder().identifier("orgId/projectId").build());
    delegate.setNg(true);
    persistence.save(delegate);
    return delegate;
  }

  private Delegate createNGOrgLevelDelegateWithOwner(String accountId) {
    Delegate delegate = createDelegateBuilder(accountId).build();
    delegate.setOwner(DelegateEntityOwner.builder().identifier("orgId").build());
    delegate.setNg(true);
    persistence.save(delegate);
    return delegate;
  }

  private Delegate createDelegateWithDelegateGroup(String accountId) {
    Delegate delegate = createDelegateBuilder(accountId).build();
    DelegateGroup delegateGroup = DelegateGroup.builder()
                                      .name("grp1")
                                      .accountId(accountId)
                                      .ng(true)
                                      .delegateType(KUBERNETES)
                                      .description("description")
                                      .tags(ImmutableSet.of("custom-grp-tag1", "custom-grp-tag2"))
                                      .build();
    persistence.save(delegateGroup);
    delegate.setDelegateGroupId(delegateGroup.getUuid());
    persistence.save(delegate);
    return delegate;
  }

  private Delegate createDelegateWithDelegateProfile(String accountId) {
    Delegate delegate = createDelegateBuilder(accountId).build();
    Map<String, Set<String>> scopingEntities = new HashMap<>();
    scopingEntities.put(SCOPING_ENTITY_KEY_APP_ID, new HashSet<>(Arrays.asList(APP_ID, "app_id2")));
    scopingEntities.put(SCOPING_ENTITY_KEY_ENV_ID, new HashSet<>(Collections.singletonList(ENV_ID)));

    DelegateProfileScopingRule rule =
        DelegateProfileScopingRule.builder().description("test").scopingEntities(scopingEntities).build();
    DelegateProfile delegateProfile = createDelegateProfileBuilder()
                                          .startupScript("script")
                                          .scopingRules(Collections.singletonList(rule))
                                          .approvalRequired(false)
                                          .build();
    persistence.save(delegateProfile);
    delegate.setDelegateProfileId(delegateProfile.getUuid());
    persistence.save(delegate);
    when(delegateCache.getDelegateProfile(delegate.getAccountId(), delegate.getDelegateProfileId()))
        .thenReturn(delegateProfile);
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

  private static JreConfig getOpenjdkJreConfig() {
    return JreConfig.builder()
        .version("1.8.0_242")
        .jreDirectory("jdk8u242-b08-jre")
        .jreMacDirectory("jdk8u242-b08-jre")
        .jreTarPath("jre/openjdk-8u242/jre_x64_${OS}_8u242b08.tar.gz")
        .build();
  }
}
