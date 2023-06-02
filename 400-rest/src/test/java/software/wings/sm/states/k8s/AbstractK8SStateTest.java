/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.k8s;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.KUSTOMIZE_PATCHES_CG;
import static io.harness.beans.FeatureName.OPTIMIZED_GIT_FETCH_FILES;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.k8s.manifest.ManifestHelper.values_filename;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.PARDHA;
import static io.harness.rule.OwnerRule.TARUN_UBA;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.GcpKubernetesInfrastructureMapping.Builder.aGcpKubernetesInfrastructureMapping;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.TaskType.CUSTOM_MANIFEST_FETCH_TASK;
import static software.wings.beans.appmanifest.AppManifestKind.K8S_MANIFEST;
import static software.wings.beans.appmanifest.ManifestFile.VALUES_YAML_KEY;
import static software.wings.beans.appmanifest.StoreType.CUSTOM;
import static software.wings.beans.appmanifest.StoreType.CUSTOM_OPENSHIFT_TEMPLATE;
import static software.wings.beans.appmanifest.StoreType.HelmChartRepo;
import static software.wings.beans.appmanifest.StoreType.HelmSourceRepo;
import static software.wings.beans.appmanifest.StoreType.KustomizeSourceRepo;
import static software.wings.beans.appmanifest.StoreType.Local;
import static software.wings.beans.appmanifest.StoreType.Remote;
import static software.wings.delegatetasks.GitFetchFilesTask.GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT;
import static software.wings.settings.SettingVariableTypes.GCP;
import static software.wings.sm.ExecutionContextImpl.PHASE_PARAM;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.StateType.K8S_CANARY_DEPLOY;
import static software.wings.sm.StateType.K8S_DEPLOYMENT_ROLLING;
import static software.wings.sm.StepType.K8S_SCALE;
import static software.wings.sm.WorkflowStandardParams.Builder.aWorkflowStandardParams;
import static software.wings.sm.states.k8s.K8sScale.K8S_SCALE_COMMAND_NAME;
import static software.wings.sm.states.k8s.K8sTestConstants.VALUES_YAML_WITH_ARTIFACT_REFERENCE;
import static software.wings.sm.states.pcf.PcfStateTestHelper.PHASE_NAME;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_NAME;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.COMPUTE_PROVIDER_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.STATE_NAME;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.delegate.task.manifests.request.CustomManifestValuesFetchParams;
import io.harness.delegate.task.manifests.response.CustomManifestValuesFetchResponse;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.deployment.InstanceDetails;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.expression.VariableResolverTracker;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.model.K8sPod;
import io.harness.logging.CommandExecutionStatus;
import io.harness.manifest.CustomSourceConfig;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.ContextElementParamMapperFactory;
import software.wings.api.DeploymentType;
import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementListParam;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.instancedetails.InstanceInfoVariables;
import software.wings.api.k8s.K8sCanaryDeleteServiceElement;
import software.wings.api.k8s.K8sElement;
import software.wings.api.k8s.K8sGitConfigMapInfo;
import software.wings.api.k8s.K8sGitFetchInfo;
import software.wings.api.k8s.K8sHelmDeploymentElement;
import software.wings.api.k8s.K8sStateExecutionData;
import software.wings.beans.Account;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFetchFilesTaskParams;
import software.wings.beans.GitFileConfig;
import software.wings.beans.HelmChartConfig;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.K8sDummyCommandUnit;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.common.VariableProcessor;
import software.wings.delegatetasks.aws.AwsCommandHelper;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.helpers.ext.helm.request.HelmValuesFetchTaskParameters;
import software.wings.helpers.ext.helm.response.HelmValuesFetchTaskResponse;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.helpers.ext.k8s.request.K8sRollingDeployTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.helpers.ext.k8s.response.K8sInstanceSyncResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.helpers.ext.kustomize.KustomizeHelper;
import software.wings.helpers.ext.openshift.OpenShiftManagerService;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.impl.EventEmitter;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.impl.GitFileConfigHelperService;
import software.wings.service.impl.HelmChartConfigHelperService;
import software.wings.service.impl.servicetemplates.ServiceTemplateHelper;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.utils.ApplicationManifestUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@TargetModule(_870_CG_ORCHESTRATION)
@OwnedBy(CDP)
public class AbstractK8SStateTest extends WingsBaseTest {
  @Mock private ActivityService activityService;
  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;
  @Mock private EventEmitter eventEmitter;
  @Mock private ApplicationManifestUtils applicationManifestUtils;
  @Mock private GitFileConfigHelperService gitFileConfigHelperService;
  @Mock private SettingsService settingsService;
  @Mock private SecretManager secretManager;
  @Mock private HelmChartConfigHelperService helmChartConfigHelperService;
  @Mock private KustomizeHelper kustomizeHelper;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private DelegateService delegateService;
  @Mock private ServiceTemplateHelper serviceTemplateHelper;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private ArtifactService artifactService;
  @Mock private SweepingOutputService mockedSweepingOutputService;
  @Mock private ManagerExpressionEvaluator evaluator;
  @Mock private VariableProcessor variableProcessor;
  @Mock private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private AccountService accountService;
  @Mock private InfrastructureDefinitionService infrastructureDefinitionService;
  @Mock private OpenShiftManagerService openShiftManagerService;
  @Mock private ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @Mock private InstanceService instanceService;
  @Mock private GitConfigHelperService gitConfigHelperService;
  @Mock private AwsCommandHelper awsCommandHelper;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;

  @InjectMocks private AbstractK8sState abstractK8SState = mock(AbstractK8sState.class, CALLS_REAL_METHODS);
  @InjectMocks K8sCanaryDeploy k8sCanaryDeploy = spy(new K8sCanaryDeploy(K8S_CANARY_DEPLOY.name()));
  @InjectMocks K8sRollingDeploy k8sRollingDeploy = spy(new K8sRollingDeploy(K8S_DEPLOYMENT_ROLLING.name()));

  @Inject private HPersistence persistence;
  @Inject KryoSerializer kryoSerializer;
  @Inject private K8sStateHelper k8sStateHelper;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private ApplicationManifestService applicationManifestService;

  private static final String APPLICATION_MANIFEST_ID = "AppManifestId";
  private static Map<String, GitFetchFilesResult> GIT_FETCH_FILES_RESULT_MAP = new HashMap<>();
  @InjectMocks
  private WorkflowStandardParams workflowStandardParams = aWorkflowStandardParams()
                                                              .withAppId(APP_ID)
                                                              .withEnvId(ENV_ID)
                                                              .withArtifactIds(Lists.newArrayList(ARTIFACT_ID))
                                                              .build();
  private ServiceElement serviceElement = ServiceElement.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build();

  @InjectMocks
  private PhaseElement phaseElement = PhaseElement.builder()
                                          .uuid(generateUuid())
                                          .serviceElement(serviceElement)
                                          .infraMappingId(INFRA_MAPPING_ID)
                                          .workflowExecutionId(WORKFLOW_EXECUTION_ID)
                                          .phaseName(PHASE_NAME)
                                          .deploymentType(DeploymentType.HELM.name())
                                          .build();

  private ExecutionContextImpl context;
  private StateExecutionInstance stateExecutionInstance =
      aStateExecutionInstance()
          .displayName(STATE_NAME)
          .addContextElement(workflowStandardParams)
          .addContextElement(phaseElement)
          .addStateExecutionData(K8sStateExecutionData.builder().build())
          .build();
  private Application application;

  @Before
  public void setup() {
    context = new ExecutionContextImpl(stateExecutionInstance);
    on(context).set("infrastructureMappingService", infrastructureMappingService);
    on(context).set("serviceResourceService", serviceResourceService);
    on(context).set("artifactService", artifactService);
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("evaluator", evaluator);
    on(context).set("featureFlagService", featureFlagService);
    on(context).set("stateExecutionInstance", stateExecutionInstance);
    on(context).set("sweepingOutputService", sweepingOutputService);

    WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService =
        new WorkflowStandardParamsExtensionService(
            appService, accountService, artifactService, environmentService, null, null, featureFlagService);
    on(context).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(k8sStateHelper).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(abstractK8SState).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(k8sRollingDeploy).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(k8sCanaryDeploy).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);

    on(abstractK8SState).set("kryoSerializer", kryoSerializer);
    on(abstractK8SState).set("sweepingOutputService", sweepingOutputService);
    on(abstractK8SState).set("applicationManifestService", applicationManifestService);
    on(abstractK8SState).set("k8sStateHelper", k8sStateHelper);
    on(k8sRollingDeploy).set("k8sStateHelper", k8sStateHelper);
    on(k8sCanaryDeploy).set("k8sStateHelper", k8sStateHelper);

    on(k8sStateHelper).set("sweepingOutputService", mockedSweepingOutputService);
    on(k8sStateHelper).set("infrastructureMappingService", infrastructureMappingService);

    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn("baseUrl");
    ContextElementParamMapperFactory contextElementParamMapperFactory =
        new ContextElementParamMapperFactory(subdomainUrlHelper, null, artifactService, null,
            applicationManifestService, featureFlagService, null, workflowStandardParamsExtensionService);
    on(context).set("contextElementParamMapperFactory", contextElementParamMapperFactory);

    when(accountService.getAccountWithDefaults(ACCOUNT_ID))
        .thenReturn(Account.Builder.anAccount().withUuid(ACCOUNT_ID).withAccountName(ACCOUNT_NAME).build());
    application = anApplication().appId(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).uuid(APP_ID).build();
    when(appService.getApplicationWithDefaults(APP_ID)).thenReturn(application);
    when(appService.get(any())).thenReturn(application);
    when(environmentService.get(APP_ID, ENV_ID, false))
        .thenReturn(anEnvironment().appId(APP_ID).environmentType(EnvironmentType.PROD).uuid(ENV_ID).build());
    when(evaluator.substitute(
             nullable(String.class), anyMap(), any(VariableResolverTracker.class), nullable(String.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(K8sClusterConfig.builder().build())
        .when(containerDeploymentManagerHelper)
        .getK8sClusterConfig(any(), any());

    GIT_FETCH_FILES_RESULT_MAP.put(
        "ServiceOverride", GitFetchFilesResult.builder().latestCommitSHA("commit example").build());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCreateK8sActivity() {
    List<CommandUnit> commandUnits = new ArrayList<>();
    commandUnits.add(new K8sDummyCommandUnit(K8sCommandUnitConstants.Init));
    commandUnits.add(new K8sDummyCommandUnit(K8sCommandUnitConstants.Scale));

    abstractK8SState.createK8sActivity(
        context, K8S_SCALE_COMMAND_NAME, K8S_SCALE.name(), activityService, commandUnits);

    ArgumentCaptor<Activity> activityArgumentCaptor = ArgumentCaptor.forClass(Activity.class);
    verify(activityService, times(1)).save(activityArgumentCaptor.capture());
    Activity activity = activityArgumentCaptor.getValue();
    assertThat(activity.getAppId()).isEqualTo(APP_ID);
    assertThat(activity.getEnvironmentId()).isEqualTo(ENV_ID);
    assertThat(activity.getCommandName()).isEqualTo(K8S_SCALE_COMMAND_NAME);
    assertThat(activity.getCommandType()).isEqualTo(K8S_SCALE.name());
    assertThat(activity.getCommandUnits()).isEqualTo(commandUnits);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCreateDelegateManifestConfig_GitSourceRepo() {
    ExecutionContext context = mock(ExecutionContext.class);

    GitFileConfig gitConfigAtService = GitFileConfig.builder().branch("1").filePath("abc").connectorId("c1").build();
    GitFileConfig gitConfigAtEnvOverride =
        GitFileConfig.builder().branch("2").filePath("def").connectorId("d1").build();

    ApplicationManifest appManifest = ApplicationManifest.builder()
                                          .gitFileConfig(gitConfigAtService)
                                          .kind(AppManifestKind.HELM_CHART_OVERRIDE)
                                          .storeType(StoreType.HelmSourceRepo)
                                          .serviceId("serviceId")
                                          .build();

    ApplicationManifest appManifestOverride = ApplicationManifest.builder()
                                                  .gitFileConfig(gitConfigAtEnvOverride)
                                                  .kind(AppManifestKind.HELM_CHART_OVERRIDE)
                                                  .storeType(StoreType.HelmSourceRepo)
                                                  .build();

    doReturn(appManifest)
        .doReturn(appManifestOverride)
        .when(applicationManifestUtils)
        .getAppManifestByApplyingHelmChartOverride(context);

    doReturn(SweepingOutputInquiry.builder()).when(context).prepareSweepingOutputInquiryBuilder();
    doReturn(true).when(featureFlagService).isEnabled(eq(OPTIMIZED_GIT_FETCH_FILES), any());
    when(gitFileConfigHelperService.renderGitFileConfig(any(), any())).thenAnswer(new Answer<GitFileConfig>() {
      @Override
      public GitFileConfig answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        return (GitFileConfig) args[1];
      }
    });

    doReturn(GitConfig.builder().build()).when(settingsService).fetchGitConfigFromConnectorId(nullable(String.class));
    doReturn(emptyList()).when(secretManager).getEncryptionDetails(any(), nullable(String.class), any());

    K8sDelegateManifestConfig delegateManifestConfig =
        abstractK8SState.createDelegateManifestConfig(context, appManifest);
    assertThat(delegateManifestConfig.getGitFileConfig()).isEqualTo(gitConfigAtService);
    assertThat(delegateManifestConfig.getGitFileConfig().getFilePath()).isEqualTo("abc/");
    assertThat(delegateManifestConfig.getGitFileConfig().getConnectorId()).isEqualTo("c1");
    assertThat(delegateManifestConfig.getGitFileConfig().getBranch()).isEqualTo("1");

    delegateManifestConfig = abstractK8SState.createDelegateManifestConfig(context, appManifest);
    assertThat(delegateManifestConfig.getGitFileConfig()).isEqualTo(gitConfigAtEnvOverride);
    assertThat(delegateManifestConfig.getGitFileConfig().getFilePath()).isEqualTo("def/");
    assertThat(delegateManifestConfig.getGitFileConfig().getConnectorId()).isEqualTo("d1");
    assertThat(delegateManifestConfig.getGitFileConfig().getBranch()).isEqualTo("2");
    assertThat(delegateManifestConfig.isOptimizedFilesFetch()).isTrue();
    verify(gitConfigHelperService, times(2))
        .convertToRepoGitConfig(
            delegateManifestConfig.getGitConfig(), delegateManifestConfig.getGitFileConfig().getRepoName());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCreateDelegateManifestConfig_HelmChartRepo() {
    ExecutionContext context = mock(ExecutionContext.class);

    HelmChartConfig helmChartConfigAtService =
        HelmChartConfig.builder().chartName("n1").chartVersion("v1").connectorId("c1").build();
    HelmChartConfig helmChartConfigOverride =
        HelmChartConfig.builder().chartName("m1").chartVersion("w1").connectorId("d1").build();

    ApplicationManifest appManifest = ApplicationManifest.builder()
                                          .helmChartConfig(helmChartConfigAtService)
                                          .kind(AppManifestKind.HELM_CHART_OVERRIDE)
                                          .storeType(StoreType.HelmChartRepo)
                                          .build();

    ApplicationManifest appManifestOverride = ApplicationManifest.builder()
                                                  .helmChartConfig(helmChartConfigOverride)
                                                  .kind(AppManifestKind.HELM_CHART_OVERRIDE)
                                                  .storeType(StoreType.HelmChartRepo)
                                                  .build();

    doReturn(appManifest)
        .doReturn(appManifestOverride)
        .when(applicationManifestUtils)
        .getAppManifestByApplyingHelmChartOverride(context);
    when(helmChartConfigHelperService.getHelmChartConfigTaskParams(any(), any()))
        .thenAnswer(new Answer<HelmChartConfigParams>() {
          @Override
          public HelmChartConfigParams answer(InvocationOnMock invocation) throws Throwable {
            Object[] args = invocation.getArguments();
            ApplicationManifest applicationManifest = (ApplicationManifest) args[1];
            HelmChartConfig helmChartConfig = applicationManifest.getHelmChartConfig();
            return HelmChartConfigParams.builder()
                .chartName(helmChartConfig.getChartName())
                .chartVersion(helmChartConfig.getChartVersion())
                .build();
          }
        });
    K8sDelegateManifestConfig delegateManifestConfig =
        abstractK8SState.createDelegateManifestConfig(context, appManifest);
    assertThat(delegateManifestConfig.getHelmChartConfigParams().getChartName()).isEqualTo("n1");
    assertThat(delegateManifestConfig.getHelmChartConfigParams().getChartVersion()).isEqualTo("v1");

    delegateManifestConfig = abstractK8SState.createDelegateManifestConfig(context, appManifest);
    assertThat(delegateManifestConfig.getHelmChartConfigParams().getChartName()).isEqualTo("m1");
    assertThat(delegateManifestConfig.getHelmChartConfigParams().getChartVersion()).isEqualTo("w1");
  }

  private ManifestFile createManifestFile() {
    return ManifestFile.builder()
        .applicationManifestId(APPLICATION_MANIFEST_ID)
        .fileName(values_filename)
        .fileContent(VALUES_YAML_WITH_ARTIFACT_REFERENCE)
        .build();
  }

  @NotNull
  private ApplicationManifest createApplicationManifest() {
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .kind(AppManifestKind.K8S_MANIFEST)
                                                  .storeType(StoreType.Local)
                                                  .serviceId(SERVICE_ID)
                                                  .build();
    applicationManifest.setAppId(APP_ID);
    applicationManifest.setUuid(APPLICATION_MANIFEST_ID);
    return applicationManifest;
  }

  private InfrastructureMapping createGCPInfraMapping() {
    return aGcpKubernetesInfrastructureMapping()
        .withNamespace("default")
        .withAppId(APP_ID)
        .withEnvId(ENV_ID)
        .withServiceId(SERVICE_ID)
        .withServiceTemplateId(TEMPLATE_ID)
        .withComputeProviderType(GCP.name())
        .withComputeProviderSettingId(COMPUTE_PROVIDER_ID)
        .withUuid(INFRA_MAPPING_ID)
        .build();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidateK8sV2TypeServiceUsed() {
    PhaseElement phaseElement =
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build()).build();
    context.pushContextElement(phaseElement);

    try {
      when(serviceResourceService.get(SERVICE_ID))
          .thenReturn(Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build());
      abstractK8SState.validateK8sV2TypeServiceUsed(context);

      fail("Should not reach here");
    } catch (InvalidRequestException ex) {
      assertThat(ex.getMessage())
          .isEqualTo("Service SERVICE_NAME used in workflow is of incompatible type. Use Kubernetes V2 type service");
    }

    when(serviceResourceService.get(SERVICE_ID))
        .thenReturn(Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).isK8sV2(true).build());
    abstractK8SState.validateK8sV2TypeServiceUsed(context);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testRenderForKustomizeInDelegateManifestConfig() {
    when(settingsService.fetchGitConfigFromConnectorId(nullable(String.class))).thenReturn(new GitConfig());
    when(gitFileConfigHelperService.renderGitFileConfig(any(ExecutionContext.class), any(GitFileConfig.class)))
        .thenReturn(new GitFileConfig());

    ApplicationManifest appManifest = buildKustomizeAppManifest();
    K8sDelegateManifestConfig delegateManifestConfig =
        abstractK8SState.createDelegateManifestConfig(context, appManifest);

    verify(gitFileConfigHelperService, times(1)).renderGitFileConfig(context, appManifest.getGitFileConfig());
    verify(kustomizeHelper, times(1)).renderKustomizeConfig(context, appManifest.getKustomizeConfig());
    assertThat(delegateManifestConfig.getKustomizeConfig()).isEqualTo(appManifest.getKustomizeConfig());
  }

  private ApplicationManifest buildKustomizeAppManifest() {
    GitFileConfig gitFileConfig = GitFileConfig.builder()
                                      .filePath("${filePath}")
                                      .connectorId("connector-id")
                                      .useBranch(true)
                                      .branch("${branch}")
                                      .build();
    return ApplicationManifest.builder()
        .kind(K8S_MANIFEST)
        .gitFileConfig(gitFileConfig)
        .storeType(KustomizeSourceRepo)
        .serviceId("serviceId")
        .build();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testTimeoutInQueueK8sDelegateTask() throws Exception {
    SettingAttribute settingAttribute = aSettingAttribute()
                                            .withUuid(SETTING_ID)
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(KubernetesClusterConfig.builder().build())
                                            .build();
    persistence.save(settingAttribute);

    DirectKubernetesInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder().namespace("env").accountId(ACCOUNT_ID).build();
    infrastructureMapping.setComputeProviderSettingId(SETTING_ID);

    K8sTaskExecutionResponse response = K8sTaskExecutionResponse.builder().build();
    when(infrastructureMappingService.get(nullable(String.class), nullable(String.class)))
        .thenReturn(infrastructureMapping);
    when(delegateService.executeTaskV2(any())).thenReturn(response);
    when(serviceTemplateHelper.fetchServiceTemplateId(any())).thenReturn(SETTING_ID);
    when(evaluator.substitute(nullable(String.class), any(), any(), nullable(String.class))).thenReturn("default");
    when(serviceResourceService.getHelmVersionWithDefault(nullable(String.class), nullable(String.class)))
        .thenReturn(HelmVersion.V2);
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().skipVersioningForAllK8sObjects(true).storeType(Local).build();
    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    applicationManifestMap.put(K8sValuesLocation.Service, applicationManifest);

    K8sRollingDeployTaskParameters taskParameters = K8sRollingDeployTaskParameters.builder().build();
    abstractK8SState.queueK8sDelegateTask(context, taskParameters, applicationManifestMap);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask.getData().getTimeout()).isEqualTo(10 * 60 * 1000);

    taskParameters.setTimeoutIntervalInMin(300);
    abstractK8SState.queueK8sDelegateTask(context, taskParameters, applicationManifestMap);
    captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(2)).queueTaskV2(captor.capture());
    delegateTask = captor.getValue();
    assertThat(delegateTask.getData().getTimeout()).isEqualTo(300 * 60 * 1000);

    taskParameters.setTimeoutIntervalInMin(0);
    abstractK8SState.queueK8sDelegateTask(context, taskParameters, applicationManifestMap);
    captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(3)).queueTaskV2(captor.capture());
    delegateTask = captor.getValue();
    assertThat(delegateTask.getData().getTimeout()).isEqualTo(60 * 1000);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testTagsInQueueK8sDelegateTask() throws Exception {
    DirectKubernetesInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder().namespace("env").accountId(ACCOUNT_ID).build();
    infrastructureMapping.setComputeProviderSettingId(SETTING_ID);

    K8sTaskExecutionResponse response = K8sTaskExecutionResponse.builder()
                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                            .k8sTaskResponse(K8sInstanceSyncResponse.builder().build())
                                            .build();
    when(delegateService.executeTaskV2(any())).thenReturn(response);
    when(serviceTemplateHelper.fetchServiceTemplateId(any())).thenReturn(SETTING_ID);
    when(infrastructureMappingService.get(nullable(String.class), nullable(String.class)))
        .thenReturn(infrastructureMapping);
    when(evaluator.substitute(nullable(String.class), any(), any(), nullable(String.class))).thenReturn("default");
    when(serviceResourceService.getHelmVersionWithDefault(nullable(String.class), nullable(String.class)))
        .thenReturn(HelmVersion.V2);
    doReturn(K8sClusterConfig.builder().build())
        .when(containerDeploymentManagerHelper)
        .getK8sClusterConfig(any(ContainerInfrastructureMapping.class), eq(context));
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().skipVersioningForAllK8sObjects(true).storeType(Local).build();
    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    applicationManifestMap.put(K8sValuesLocation.Service, applicationManifest);

    SettingAttribute settingAttribute = aSettingAttribute()
                                            .withUuid(SETTING_ID)
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(KubernetesClusterConfig.builder().build())
                                            .build();
    persistence.save(settingAttribute);

    abstractK8SState.queueK8sDelegateTask(
        context, K8sRollingDeployTaskParameters.builder().build(), applicationManifestMap);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask.getTags()).isEmpty();

    settingAttribute.setValue(
        KubernetesClusterConfig.builder().useKubernetesDelegate(true).delegateName("delegateName").build());
    persistence.save(settingAttribute);
    abstractK8SState.queueK8sDelegateTask(
        context, K8sRollingDeployTaskParameters.builder().build(), applicationManifestMap);
    captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(2)).queueTaskV2(captor.capture());
    delegateTask = captor.getValue();
    assertThat(delegateTask.getTags()).isEmpty();

    doReturn(K8sClusterConfig.builder()
                 .cloudProvider(KubernetesClusterConfig.builder()
                                    .useKubernetesDelegate(true)
                                    .delegateSelectors(new HashSet<>(singletonList("delegateSelectors")))
                                    .build())
                 .build())
        .when(containerDeploymentManagerHelper)
        .getK8sClusterConfig(any(ContainerInfrastructureMapping.class), eq(context));
    persistence.save(settingAttribute);
    abstractK8SState.queueK8sDelegateTask(
        context, K8sRollingDeployTaskParameters.builder().build(), applicationManifestMap);
    captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(3)).queueTaskV2(captor.capture());
    delegateTask = captor.getValue();

    K8sRollingDeployTaskParameters taskParameters =
        (K8sRollingDeployTaskParameters) delegateTask.getData().getParameters()[0];

    KubernetesClusterConfig clusterConfig =
        (KubernetesClusterConfig) taskParameters.getK8sClusterConfig().getCloudProvider();
    assertThat(clusterConfig.getDelegateSelectors()).contains("delegateSelectors");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetNewPods() {
    assertThat(abstractK8SState.fetchNewPods(null)).isEmpty();
    assertThat(abstractK8SState.fetchNewPods(emptyList())).isEmpty();

    final List<K8sPod> newPods = abstractK8SState.fetchNewPods(
        asList(K8sPod.builder().name("pod-1").build(), K8sPod.builder().name("pod-2").newPod(true).build()));

    assertThat(newPods).hasSize(1);
    assertThat(newPods.get(0).isNewPod()).isTrue();
    assertThat(newPods.get(0).getName()).isEqualTo("pod-2");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetInstanceDetails() {
    assertThat(abstractK8SState.fetchInstanceDetails(null, false)).isEmpty();
    assertThat(abstractK8SState.fetchInstanceDetails(emptyList(), false)).isEmpty();

    List<InstanceDetails> instanceDetails;
    instanceDetails =
        abstractK8SState.fetchInstanceDetails(asList(K8sPod.builder().name("pod-1").podIP("ip-1").build(),
                                                  K8sPod.builder().name("pod-2").podIP("ip-2").newPod(true).build()),
            false);

    assertThat(instanceDetails).hasSize(2);
    assertThat(instanceDetails.stream().map(InstanceDetails::getInstanceType).collect(toSet()))
        .hasSize(1)
        .contains(InstanceDetails.InstanceType.K8s);
    assertThat(instanceDetails.stream().map(pod -> pod.getK8s().getPodName()).collect(toList()))
        .containsExactlyInAnyOrder("pod-1", "pod-2");
    assertThat(instanceDetails.stream().map(pod -> pod.getK8s().getIp()).collect(toList()))
        .containsExactlyInAnyOrder("ip-1", "ip-2");
    assertThat(instanceDetails.stream().filter(InstanceDetails::isNewInstance).count()).isEqualTo(1);

    instanceDetails =
        abstractK8SState.fetchInstanceDetails(asList(K8sPod.builder().name("pod-1").podIP("ip-1").build(),
                                                  K8sPod.builder().name("pod-2").podIP("ip-2").newPod(true).build()),
            true);
    assertThat(instanceDetails).hasSize(2);
    assertThat(instanceDetails.stream().map(InstanceDetails::getInstanceType).collect(toSet()))
        .hasSize(1)
        .contains(InstanceDetails.InstanceType.K8s);
    assertThat(instanceDetails.stream().map(pod -> pod.getK8s().getPodName()).collect(toList()))
        .containsExactlyInAnyOrder("pod-1", "pod-2");
    assertThat(instanceDetails.stream().map(pod -> pod.getK8s().getIp()).collect(toList()))
        .containsExactlyInAnyOrder("ip-1", "ip-2");
    assertThat(instanceDetails.stream().filter(InstanceDetails::isNewInstance).count()).isEqualTo(2);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetInstanceElements() {
    assertThat(abstractK8SState.fetchInstanceElementList(null, false)).isEmpty();
    assertThat(abstractK8SState.fetchInstanceElementList(emptyList(), false)).isEmpty();

    List<InstanceElement> instanceElements;
    instanceElements = abstractK8SState.fetchInstanceElementList(
        asList(K8sPod.builder().name("pod-1").podIP("ip-1").build(),
            K8sPod.builder().name("pod-2").podIP("ip-2").newPod(true).build()),
        false);

    assertThat(instanceElements).hasSize(2);
    assertThat(instanceElements.stream().map(InstanceElement::getPodName).collect(toList()))
        .containsExactlyInAnyOrder("pod-1", "pod-2");
    assertThat(instanceElements.stream().filter(InstanceElement::isNewInstance).count()).isEqualTo(1);

    instanceElements = abstractK8SState.fetchInstanceElementList(
        asList(K8sPod.builder().name("pod-1").podIP("ip-1").build(),
            K8sPod.builder().name("pod-2").podIP("ip-2").newPod(true).build()),
        true);
    assertThat(instanceElements).hasSize(2);
    assertThat(instanceElements.stream().map(InstanceElement::getPodName).collect(toList()))
        .containsExactlyInAnyOrder("pod-1", "pod-2");
    assertThat(instanceElements.stream().filter(InstanceElement::isNewInstance).count()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForHelmFetchTask() {
    HelmValuesFetchTaskResponse valuesFetchTaskResponse =
        HelmValuesFetchTaskResponse.builder().commandExecutionStatus(FAILURE).build();
    Map<String, ResponseData> response = new HashMap<>();
    response.put(ACTIVITY_ID, valuesFetchTaskResponse);

    K8sStateExecutor k8sStateExecutor = mock(K8sStateExecutor.class);
    K8sStateExecutionData k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    k8sStateExecutionData.setCurrentTaskType(TaskType.HELM_VALUES_FETCH);
    k8sStateExecutionData.setActivityId(ACTIVITY_ID);
    k8sStateExecutionData.setValuesFiles(new HashMap<>());

    ExecutionResponse executionResponse =
        abstractK8SState.handleAsyncResponseWrapper(k8sStateExecutor, context, response);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    verify(activityService, times(1)).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.FAILED);

    when(applicationManifestUtils.getMapK8sValuesLocationToNonEmptyContents(anyMap())).thenCallRealMethod();
    Map<String, List<String>> mapK8sValuesLocationToFileContents = new HashMap<>();
    mapK8sValuesLocationToFileContents.put(K8sValuesLocation.Service.name(), singletonList("VALUES_FILE_CONTENT"));
    valuesFetchTaskResponse.setMapK8sValuesLocationToContent(mapK8sValuesLocationToFileContents);

    valuesFetchTaskResponse.setCommandExecutionStatus(SUCCESS);
    abstractK8SState.handleAsyncResponseWrapper(k8sStateExecutor, context, response);
    k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();

    ArgumentCaptor<AppManifestKind> argumentCaptor = ArgumentCaptor.forClass(AppManifestKind.class);
    verify(applicationManifestUtils, times(1))
        .getApplicationManifests(any(ExecutionContextImpl.class), argumentCaptor.capture());
    assertThat(argumentCaptor.getValue()).isEqualTo(AppManifestKind.VALUES);
    assertThat(k8sStateExecutionData.getValuesFiles().get(K8sValuesLocation.Service))
        .containsExactly("VALUES_FILE_CONTENT");

    mapK8sValuesLocationToFileContents.put(K8sValuesLocation.Service.name(), singletonList(""));
    k8sStateExecutionData.setValuesFiles(new HashMap<>());
    valuesFetchTaskResponse.setMapK8sValuesLocationToContent(mapK8sValuesLocationToFileContents);
    valuesFetchTaskResponse.setCommandExecutionStatus(SUCCESS);
    abstractK8SState.handleAsyncResponseWrapper(k8sStateExecutor, context, response);
    k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    assertThat(k8sStateExecutionData.getValuesFiles().containsKey(K8sValuesLocation.Service)).isFalse();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForGitTaskWrapper() {
    GitCommandExecutionResponse gitCommandExecutionResponse =
        GitCommandExecutionResponse.builder()
            .gitCommandStatus(GitCommandExecutionResponse.GitCommandStatus.FAILURE)
            .build();
    Map<String, ResponseData> response = new HashMap<>();
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    response.put(ACTIVITY_ID, gitCommandExecutionResponse);

    K8sStateExecutor k8sStateExecutor = mock(K8sStateExecutor.class);
    K8sStateExecutionData k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    k8sStateExecutionData.setCurrentTaskType(TaskType.GIT_COMMAND);
    k8sStateExecutionData.setActivityId(ACTIVITY_ID);
    k8sStateExecutionData.setValuesFiles(new HashMap<>());
    k8sStateExecutionData.setApplicationManifestMap(appManifestMap);

    ExecutionResponse executionResponse =
        abstractK8SState.handleAsyncResponseWrapper(k8sStateExecutor, context, response);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    verify(activityService, times(1)).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.FAILED);

    Map<K8sValuesLocation, Collection<String>> valuesMap = new HashMap<>();
    valuesMap.put(K8sValuesLocation.Environment, singletonList("EnvValues"));
    when(applicationManifestUtils.getValuesFilesFromGitFetchFilesResponse(appManifestMap, gitCommandExecutionResponse))
        .thenReturn(valuesMap);
    gitCommandExecutionResponse.setGitCommandStatus(GitCommandExecutionResponse.GitCommandStatus.SUCCESS);
    abstractK8SState.handleAsyncResponseWrapper(k8sStateExecutor, context, response);
    k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    assertThat(k8sStateExecutionData.getValuesFiles().get(K8sValuesLocation.Environment)).containsExactly("EnvValues");
    verify(applicationManifestUtils, times(1))
        .getValuesFilesFromGitFetchFilesResponse(appManifestMap, gitCommandExecutionResponse);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForGitTaskWrapper_FF_InheritManifest() {
    GitCommandExecutionResponse gitCommandExecutionResponse =
        GitCommandExecutionResponse.builder()
            .gitCommandStatus(GitCommandExecutionResponse.GitCommandStatus.SUCCESS)
            .gitCommandResult(
                GitFetchFilesFromMultipleRepoResult.builder().gitFetchFilesConfigMap(new HashMap<>()).build())
            .build();
    Map<String, ResponseData> response = new HashMap<>();
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    response.put(ACTIVITY_ID, gitCommandExecutionResponse);

    K8sStateExecutor k8sStateExecutor = mock(K8sStateExecutor.class);
    K8sStateExecutionData k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    k8sStateExecutionData.setCurrentTaskType(TaskType.GIT_COMMAND);
    k8sStateExecutionData.setActivityId(ACTIVITY_ID);
    k8sStateExecutionData.setValuesFiles(new HashMap<>());
    k8sStateExecutionData.setApplicationManifestMap(appManifestMap);

    Map<K8sValuesLocation, Collection<String>> valuesMap = new HashMap<>();
    valuesMap.put(K8sValuesLocation.Environment, singletonList("EnvValues"));
    when(applicationManifestUtils.getValuesFilesFromGitFetchFilesResponse(appManifestMap, gitCommandExecutionResponse))
        .thenReturn(valuesMap);
    doReturn(true).when(featureFlagService).isEnabled(any(), any());
    doReturn(Service.builder().uuid("serviceId").build()).when(applicationManifestUtils).fetchServiceFromContext(any());
    k8sCanaryDeploy.handleAsyncResponseWrapper(k8sStateExecutor, context, response);

    ArgumentCaptor<SweepingOutputInstance> argumentCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(mockedSweepingOutputService, times(1)).save(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().getName())
        .isEqualTo(K8sGitConfigMapInfo.SWEEPING_OUTPUT_NAME_PREFIX + "-serviceId");
    assertThat(argumentCaptor.getValue().getAppId()).isEqualTo(APP_ID);

    ArgumentCaptor<SweepingOutputInquiry> sweepingOutputInquiryCaptor =
        ArgumentCaptor.forClass(SweepingOutputInquiry.class);
    verify(mockedSweepingOutputService, times(1)).findSweepingOutput(sweepingOutputInquiryCaptor.capture());
    assertThat(sweepingOutputInquiryCaptor.getValue().getName())
        .isEqualTo(K8sGitConfigMapInfo.SWEEPING_OUTPUT_NAME_PREFIX + "-serviceId");
    assertThat(sweepingOutputInquiryCaptor.getValue().getAppId()).isEqualTo(APP_ID);

    reset(mockedSweepingOutputService);
    k8sRollingDeploy.handleAsyncResponseWrapper(k8sStateExecutor, context, response);
    verify(mockedSweepingOutputService, times(0)).save(any());
    verify(mockedSweepingOutputService, times(0)).findSweepingOutput(any());

    reset(featureFlagService);
    doReturn(false).when(featureFlagService).isEnabled(any(), any());
    k8sCanaryDeploy.handleAsyncResponseWrapper(k8sStateExecutor, context, response);
    k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    assertThat(k8sStateExecutionData.getValuesFiles().get(K8sValuesLocation.Environment)).containsExactly("EnvValues");
    verify(mockedSweepingOutputService, times(0)).save(any());
    verify(mockedSweepingOutputService, times(0)).findSweepingOutput(any());

    reset(mockedSweepingOutputService);
    k8sRollingDeploy.handleAsyncResponseWrapper(k8sStateExecutor, context, response);
    verify(mockedSweepingOutputService, times(0)).save(any());
    verify(mockedSweepingOutputService, times(0)).findSweepingOutput(any());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testExceptionInHandleAsyncResponseWrapper() {
    K8sStateExecutor k8sStateExecutor = mock(K8sStateExecutor.class);
    K8sStateExecutionData k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    k8sStateExecutionData.setCurrentTaskType(TaskType.GIT_FETCH_FILES_TASK);

    try {
      abstractK8SState.handleAsyncResponseWrapper(k8sStateExecutor, context, null);
      fail("Should not reach here");
    } catch (WingsException ex) {
      assertThat(ex.getMessage()).isEqualTo("Unhandled task type GIT_FETCH_FILES_TASK");
    }

    k8sStateExecutionData.setCurrentTaskType(TaskType.HELM_VALUES_FETCH);
    try {
      abstractK8SState.handleAsyncResponseWrapper(k8sStateExecutor, context, null);
      fail("Should not reach here");
    } catch (Exception ex) {
      assertThat(ex).isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForCustomFetchValuesTaskWrapper() {
    CustomManifestValuesFetchResponse failedValuesFetchResponse =
        CustomManifestValuesFetchResponse.builder().commandExecutionStatus(FAILURE).build();
    Map<String, ResponseData> response = new HashMap<>();
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    response.put(ACTIVITY_ID, failedValuesFetchResponse);

    K8sStateExecutor k8sStateExecutor = mock(K8sStateExecutor.class);
    K8sStateExecutionData k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    k8sStateExecutionData.setCurrentTaskType(TaskType.CUSTOM_MANIFEST_VALUES_FETCH_TASK);
    k8sStateExecutionData.setActivityId(ACTIVITY_ID);
    k8sStateExecutionData.setValuesFiles(new HashMap<>());
    k8sStateExecutionData.setApplicationManifestMap(appManifestMap);

    doReturn(true).when(featureFlagService).isEnabled(FeatureName.CUSTOM_MANIFEST, ACCOUNT_ID);

    ExecutionResponse executionResponse =
        abstractK8SState.handleAsyncResponseWrapper(k8sStateExecutor, context, response);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    verify(activityService, times(1)).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.FAILED);

    CustomManifestValuesFetchResponse successfulFetchResponse = CustomManifestValuesFetchResponse.builder()
                                                                    .commandExecutionStatus(SUCCESS)
                                                                    .zippedManifestFileId("ZIP_FILE_ID")
                                                                    .build();
    response.put(ACTIVITY_ID, successfulFetchResponse);

    Map<K8sValuesLocation, Collection<String>> valuesMap = new HashMap<>();
    valuesMap.put(K8sValuesLocation.ServiceOverride, singletonList("values"));
    when(applicationManifestUtils.getValuesFilesFromCustomFetchValuesResponse(
             context, appManifestMap, successfulFetchResponse, VALUES_YAML_KEY))
        .thenReturn(valuesMap);
    abstractK8SState.handleAsyncResponseWrapper(k8sStateExecutor, context, response);
    k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    assertThat(k8sStateExecutionData.getValuesFiles().get(K8sValuesLocation.ServiceOverride)).containsExactly("values");
    assertThat(k8sStateExecutionData.getZippedManifestFileId()).isEqualTo("ZIP_FILE_ID");
    verify(applicationManifestUtils, times(1))
        .getValuesFilesFromCustomFetchValuesResponse(context, appManifestMap, successfulFetchResponse, VALUES_YAML_KEY);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetReleaseName() {
    DirectKubernetesInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder().build();

    infrastructureMapping.setReleaseName("release-name");
    String releaseName = abstractK8SState.fetchReleaseName(context, infrastructureMapping);
    assertThat(releaseName).isEqualTo("release-name");

    infrastructureMapping.setUuid(INFRA_MAPPING_ID);
    infrastructureMapping.setReleaseName(null);
    releaseName = abstractK8SState.fetchReleaseName(context, infrastructureMapping);
    assertThat(releaseName).isEqualTo("64317ae8-c2a8-3fd8-af26-68f2e717431a");

    infrastructureMapping.setReleaseName("-release-name");
    releaseName = abstractK8SState.fetchReleaseName(context, infrastructureMapping);
    assertThat(releaseName).isEqualTo("-release-name");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetRenderedValuesFiles() {
    when(openShiftManagerService.isOpenShiftManifestConfig(context)).thenReturn(false);

    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    K8sStateExecutionData k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    k8sStateExecutionData.setValuesFiles(new HashMap<>());
    k8sStateExecutionData.getValuesFiles().put(K8sValuesLocation.Environment, singletonList("envValues"));
    k8sStateExecutionData.getValuesFiles().put(
        K8sValuesLocation.ServiceOverride, singletonList("serviceOverrideValues"));
    k8sStateExecutionData.getValuesFiles().put(K8sValuesLocation.Service, singletonList("serviceValues"));
    k8sStateExecutionData.getValuesFiles().put(K8sValuesLocation.EnvironmentGlobal, singletonList("envGlobalValues"));

    List<String> valuesFiles = abstractK8SState.fetchRenderedValuesFiles(appManifestMap, context);
    assertThat(valuesFiles).hasSize(4);
    assertThat(valuesFiles.get(0)).isEqualTo("serviceValues");
    assertThat(valuesFiles.get(1)).isEqualTo("serviceOverrideValues");
    assertThat(valuesFiles.get(2)).isEqualTo("envGlobalValues");
    assertThat(valuesFiles.get(3)).isEqualTo("envValues");

    k8sStateExecutionData.getValuesFiles().put(K8sValuesLocation.ServiceOverride, singletonList(" "));
    k8sStateExecutionData.getValuesFiles().put(K8sValuesLocation.Environment, singletonList(""));
    valuesFiles = abstractK8SState.fetchRenderedValuesFiles(appManifestMap, context);
    assertThat(valuesFiles).hasSize(2);
    assertThat(valuesFiles.get(0)).isEqualTo("serviceValues");
    assertThat(valuesFiles.get(1)).isEqualTo("envGlobalValues");

    k8sStateExecutionData.getValuesFiles().remove(K8sValuesLocation.ServiceOverride);
    k8sStateExecutionData.getValuesFiles().remove(K8sValuesLocation.Service);
    k8sStateExecutionData.getValuesFiles().put(K8sValuesLocation.Environment, singletonList("envValues"));
    k8sStateExecutionData.getValuesFiles().put(K8sValuesLocation.EnvironmentGlobal, singletonList("envGlobalValues"));

    valuesFiles = abstractK8SState.fetchRenderedValuesFiles(appManifestMap, context);
    assertThat(valuesFiles).hasSize(2);
    assertThat(valuesFiles.get(0)).isEqualTo("envGlobalValues");
    assertThat(valuesFiles.get(1)).isEqualTo("envValues");

    when(openShiftManagerService.isOpenShiftManifestConfig(context)).thenReturn(true);
    k8sStateExecutionData.getValuesFiles().put(
        K8sValuesLocation.ServiceOverride, singletonList("serviceOverrideValues"));
    k8sStateExecutionData.getValuesFiles().put(K8sValuesLocation.Service, singletonList("serviceValues"));
    valuesFiles = abstractK8SState.fetchRenderedValuesFiles(appManifestMap, context);
    assertThat(valuesFiles).hasSize(4);
    assertThat(valuesFiles.get(0)).isEqualTo("envValues");
    assertThat(valuesFiles.get(1)).isEqualTo("envGlobalValues");
    assertThat(valuesFiles.get(2)).isEqualTo("serviceOverrideValues");
    assertThat(valuesFiles.get(3)).isEqualTo("serviceValues");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testGetRenderedValuesFilesWithStepOverride() {
    when(openShiftManagerService.isOpenShiftManifestConfig(context)).thenReturn(false);

    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    K8sStateExecutionData k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    k8sStateExecutionData.setValuesFiles(new HashMap<>());
    k8sStateExecutionData.getValuesFiles().put(K8sValuesLocation.Environment, singletonList("envValues"));
    k8sStateExecutionData.getValuesFiles().put(
        K8sValuesLocation.ServiceOverride, singletonList("serviceOverrideValues"));
    k8sStateExecutionData.getValuesFiles().put(K8sValuesLocation.Service, singletonList("serviceValues"));
    k8sStateExecutionData.getValuesFiles().put(K8sValuesLocation.EnvironmentGlobal, singletonList("envGlobalValues"));
    k8sStateExecutionData.getValuesFiles().put(
        K8sValuesLocation.Step, Arrays.asList("stepValues1", "stepValues2", "stepValues3"));

    List<String> valuesFiles = abstractK8SState.fetchRenderedValuesFiles(appManifestMap, context);
    assertThat(valuesFiles).hasSize(7);
    assertThat(valuesFiles.get(0)).isEqualTo("serviceValues");
    assertThat(valuesFiles.get(1)).isEqualTo("serviceOverrideValues");
    assertThat(valuesFiles.get(2)).isEqualTo("envGlobalValues");
    assertThat(valuesFiles.get(3)).isEqualTo("envValues");
    assertThat(valuesFiles.get(4)).isEqualTo("stepValues1");
    assertThat(valuesFiles.get(5)).isEqualTo("stepValues2");
    assertThat(valuesFiles.get(6)).isEqualTo("stepValues3");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetRenderedValuesFilesWithMultipleFiles() {
    when(openShiftManagerService.isOpenShiftManifestConfig(context)).thenReturn(false);

    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    K8sStateExecutionData k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    k8sStateExecutionData.setValuesFiles(new HashMap<>());

    k8sStateExecutionData.getValuesFiles().put(K8sValuesLocation.Environment, asList("envValues1", "envValues2"));
    k8sStateExecutionData.getValuesFiles().put(K8sValuesLocation.ServiceOverride,
        asList("serviceOverrideValues1", "serviceOverrideValues2", "serviceOverrideValues3"));
    k8sStateExecutionData.getValuesFiles().put(K8sValuesLocation.Service, asList("serviceValues1", "serviceValues2"));
    k8sStateExecutionData.getValuesFiles().put(K8sValuesLocation.EnvironmentGlobal, singletonList("envGlobalValues"));
    List<String> valuesFiles = abstractK8SState.fetchRenderedValuesFiles(appManifestMap, context);
    assertThat(valuesFiles).hasSize(8);
    assertThat(valuesFiles)
        .containsExactly("serviceValues1", "serviceValues2", "serviceOverrideValues1", "serviceOverrideValues2",
            "serviceOverrideValues3", "envGlobalValues", "envValues1", "envValues2");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testExecuteGitTask() {
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    K8sStateExecutionData k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    k8sStateExecutionData.setValuesFiles(new HashMap<>());
    GitFetchFilesTaskParams fetchFilesTaskParams = GitFetchFilesTaskParams.builder().build();
    fetchFilesTaskParams.setBindTaskFeatureSet(true);

    DirectKubernetesInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder().build();
    infrastructureMapping.setUuid(INFRA_MAPPING_ID);

    when(applicationManifestUtils.createGitFetchFilesTaskParams(context, application, appManifestMap))
        .thenReturn(fetchFilesTaskParams);
    when(infrastructureMappingService.get(APP_ID, null)).thenReturn(infrastructureMapping);

    ExecutionResponse executionResponse =
        abstractK8SState.executeGitTask(context, appManifestMap, ACTIVITY_ID, "commandName");
    assertThat(executionResponse.isAsync()).isTrue();
    K8sStateExecutionData responseStateExecutionData =
        (K8sStateExecutionData) executionResponse.getStateExecutionData();
    assertThat(responseStateExecutionData.getCurrentTaskType()).isEqualTo(TaskType.GIT_COMMAND);
    assertThat(responseStateExecutionData.getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(responseStateExecutionData.getCommandName()).isEqualTo("commandName");
    verify(applicationManifestUtils, times(1)).setValuesPathInGitFetchFilesTaskParams(fetchFilesTaskParams);
    verify(applicationManifestUtils, times(1)).populateRemoteGitConfigFilePathList(context, appManifestMap);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(APP_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.ENV_ID_FIELD)).isEqualTo(ENV_ID);
    assertThat(delegateTask.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD))
        .isEqualTo(INFRA_MAPPING_ID);
    assertThat(delegateTask.getData().getTaskType()).isEqualTo(TaskType.GIT_FETCH_FILES_TASK.name());
    assertThat(delegateTask.getData().isAsync()).isTrue();
    assertThat(delegateTask.getData().getTimeout())
        .isEqualTo(TimeUnit.MINUTES.toMillis(GIT_FETCH_FILES_TASK_ASYNC_TIMEOUT));
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testExecuteGitTask_FF_InheritManifest() {
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    K8sStateExecutionData k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    k8sStateExecutionData.setValuesFiles(new HashMap<>());
    GitFetchFilesTaskParams fetchFilesTaskParams = GitFetchFilesTaskParams.builder().build();
    fetchFilesTaskParams.setBindTaskFeatureSet(true);

    DirectKubernetesInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder().build();
    infrastructureMapping.setUuid(INFRA_MAPPING_ID);

    doReturn(true).when(featureFlagService).isEnabled(any(), any());
    when(applicationManifestUtils.createGitFetchFilesTaskParams(context, application, appManifestMap))
        .thenReturn(fetchFilesTaskParams);
    when(infrastructureMappingService.get(APP_ID, null)).thenReturn(infrastructureMapping);
    doReturn(Service.builder().uuid("serviceId").build()).when(applicationManifestUtils).fetchServiceFromContext(any());

    k8sRollingDeploy.executeGitTask(context, appManifestMap, ACTIVITY_ID, "commandName");
    ArgumentCaptor<SweepingOutputInquiry> sweepingOutputInquiryCaptor =
        ArgumentCaptor.forClass(SweepingOutputInquiry.class);
    verify(mockedSweepingOutputService, times(1)).findSweepingOutput(sweepingOutputInquiryCaptor.capture());
    assertThat(sweepingOutputInquiryCaptor.getValue().getName())
        .isEqualTo(K8sGitConfigMapInfo.SWEEPING_OUTPUT_NAME_PREFIX + "-serviceId");
    assertThat(sweepingOutputInquiryCaptor.getValue().getAppId()).isEqualTo(APP_ID);

    reset(mockedSweepingOutputService);
    k8sCanaryDeploy.executeGitTask(context, appManifestMap, ACTIVITY_ID, "commandName");
    verify(mockedSweepingOutputService, times(0)).findSweepingOutput(any());

    reset(featureFlagService);
    doReturn(false).when(featureFlagService).isEnabled(any(), any());
    k8sRollingDeploy.executeGitTask(context, appManifestMap, ACTIVITY_ID, "commandName");
    verify(mockedSweepingOutputService, times(0)).findSweepingOutput(any());

    reset(mockedSweepingOutputService);
    k8sCanaryDeploy.executeGitTask(context, appManifestMap, ACTIVITY_ID, "commandName");
    verify(mockedSweepingOutputService, times(0)).findSweepingOutput(any());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testExecuteHelmValuesFetchTask() {
    ApplicationManifest appManifest = ApplicationManifest.builder().storeType(Remote).build();
    appManifest.setStoreType(HelmChartRepo);
    HelmChartConfigParams helmChartConfigParams = HelmChartConfigParams.builder().build();
    DirectKubernetesInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder().build();
    infrastructureMapping.setUuid(INFRA_MAPPING_ID);

    when(infrastructureMappingService.get(APP_ID, null)).thenReturn(infrastructureMapping);
    when(applicationManifestUtils.getAppManifestByApplyingHelmChartOverride(context)).thenReturn(appManifest);
    when(helmChartConfigHelperService.getHelmChartConfigTaskParams(context, appManifest))
        .thenReturn(helmChartConfigParams);

    Map<K8sValuesLocation, ApplicationManifest> mapK8sValuesLocationToApplicationManifest =
        applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES);

    ExecutionResponse executionResponse = abstractK8SState.executeHelmValuesFetchTask(
        context, ACTIVITY_ID, "commandName", 10 * 60 * 1000L, mapK8sValuesLocationToApplicationManifest);
    assertThat(executionResponse.isAsync()).isTrue();
    K8sStateExecutionData responseStateExecutionData =
        (K8sStateExecutionData) executionResponse.getStateExecutionData();
    assertThat(responseStateExecutionData.getCurrentTaskType()).isEqualTo(TaskType.HELM_VALUES_FETCH);
    assertThat(responseStateExecutionData.getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(responseStateExecutionData.getCommandName()).isEqualTo("commandName");
    verify(applicationManifestUtils, times(1)).getAppManifestByApplyingHelmChartOverride(context);
    verify(helmChartConfigHelperService, times(1)).getHelmChartConfigTaskParams(context, appManifest);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(APP_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.ENV_ID_FIELD)).isEqualTo(ENV_ID);
    assertThat(delegateTask.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD))
        .isEqualTo(INFRA_MAPPING_ID);
    assertThat(delegateTask.getData().getTaskType()).isEqualTo(TaskType.HELM_VALUES_FETCH.name());
    assertThat(delegateTask.getData().isAsync()).isTrue();
    assertThat(delegateTask.getData().getTimeout()).isEqualTo(TimeUnit.MINUTES.toMillis(10));

    when(infrastructureMappingService.get(APP_ID, null)).thenReturn(null);
    abstractK8SState.executeHelmValuesFetchTask(
        context, ACTIVITY_ID, "commandName", 10 * 60 * 1000L, mapK8sValuesLocationToApplicationManifest);
    captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(2)).queueTaskV2(captor.capture());
    assertThat(captor.getValue().getSetupAbstractions().get(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD))
        .isEqualTo(null);
    assertThat(captor.getValue().getSetupAbstractions().get(Cd1SetupFields.SERVICE_ID_FIELD)).isEqualTo(null);

    when(applicationManifestUtils.getAppManifestByApplyingHelmChartOverride(context)).thenReturn(null);
    try {
      abstractK8SState.executeHelmValuesFetchTask(
          context, ACTIVITY_ID, "commandName", 10 * 60 * 1000L, mapK8sValuesLocationToApplicationManifest);
      fail("Should not reach here");
    } catch (Exception ex) {
      assertThat(ex.getMessage())
          .isEqualTo("Application Manifest not found while preparing helm values fetch task params");
    }

    appManifest.setStoreType(HelmSourceRepo);
    when(applicationManifestUtils.getAppManifestByApplyingHelmChartOverride(context)).thenReturn(appManifest);
    try {
      abstractK8SState.executeHelmValuesFetchTask(
          context, ACTIVITY_ID, "commandName", 10 * 60 * 1000L, mapK8sValuesLocationToApplicationManifest);
      fail("Should not reach here");
    } catch (Exception ex) {
      assertThat(ex.getMessage())
          .isEqualTo("Application Manifest not found while preparing helm values fetch task params");
    }
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testHelmValueFetchParamInExecuteHelmValuesFetchTask() {
    ApplicationManifest appManifest = ApplicationManifest.builder().storeType(Remote).build();
    appManifest.setStoreType(HelmChartRepo);
    HelmChartConfigParams helmChartConfigParams =
        HelmChartConfigParams.builder().connectorConfig(AwsConfig.builder().tag("aws-delegate").build()).build();
    DirectKubernetesInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder().build();
    infrastructureMapping.setUuid(INFRA_MAPPING_ID);

    Map<K8sValuesLocation, ApplicationManifest> mapK8sValuesLocationToApplicationManifest =
        applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES);

    when(infrastructureMappingService.get(APP_ID, null)).thenReturn(infrastructureMapping);
    when(applicationManifestUtils.getAppManifestByApplyingHelmChartOverride(context)).thenReturn(appManifest);
    when(helmChartConfigHelperService.getHelmChartConfigTaskParams(context, appManifest))
        .thenReturn(helmChartConfigParams);
    when(containerDeploymentManagerHelper.getContainerServiceParams(any(), any(), any()))
        .thenReturn(ContainerServiceParams.builder().clusterName("us-east-1").build());
    when(featureFlagService.isEnabled(FeatureName.BIND_FETCH_FILES_TASK_TO_DELEGATE, ACCOUNT_ID)).thenReturn(true);

    abstractK8SState.executeHelmValuesFetchTask(
        context, ACTIVITY_ID, "commandName", 10 * 60 * 1000L, mapK8sValuesLocationToApplicationManifest);

    verify(applicationManifestUtils, times(1)).getAppManifestByApplyingHelmChartOverride(context);
    verify(helmChartConfigHelperService, times(1)).getHelmChartConfigTaskParams(context, appManifest);
    verify(containerDeploymentManagerHelper, times(1)).getContainerServiceParams(infrastructureMapping, "", context);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    HelmValuesFetchTaskParameters helmValuesFetchTaskParameters =
        (HelmValuesFetchTaskParameters) delegateTask.getData().getParameters()[0];

    assertThat(helmValuesFetchTaskParameters.isBindTaskFeatureSet()).isTrue();
    assertThat(helmValuesFetchTaskParameters.getContainerServiceParams().getClusterName()).isEqualTo("us-east-1");
    assertThat(helmValuesFetchTaskParameters.getDelegateSelectors()).containsExactly("aws-delegate");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteCustomManifestFetchValuesTask() {
    CustomManifestValuesFetchParams mockParams = CustomManifestValuesFetchParams.builder().build();
    CustomSourceConfig customSourceConfig = CustomSourceConfig.builder().build();
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = ImmutableMap.of(K8sValuesLocation.Service,
        ApplicationManifest.builder().customSourceConfig(customSourceConfig).storeType(CUSTOM).build());
    K8sStateExecutor k8sStateExecutor = mock(K8sStateExecutor.class);
    DirectKubernetesInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder().build();
    infrastructureMapping.setUuid(INFRA_MAPPING_ID);
    String serviceTemplateId = "serviceTemplateId";

    doReturn(true).when(featureFlagService).isEnabled(FeatureName.CUSTOM_MANIFEST, ACCOUNT_ID);
    doReturn(infrastructureMapping).when(infrastructureMappingService).get(APP_ID, null);
    doReturn(Activity.builder().uuid(ACTIVITY_ID).build()).when(activityService).save(any(Activity.class));
    doReturn(appManifestMap).when(applicationManifestUtils).getApplicationManifests(context, AppManifestKind.VALUES);
    doReturn(mockParams)
        .when(applicationManifestUtils)
        .createCustomManifestValuesFetchParams(context, appManifestMap, VALUES_YAML_KEY);
    doReturn(serviceTemplateId).when(serviceTemplateHelper).fetchServiceTemplateId(infrastructureMapping);
    abstractK8SState.executeWrapperWithManifest(k8sStateExecutor, context, 90000);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(1)).queueTaskV2(captor.capture());
    DelegateTask queuedTask = captor.getValue();
    assertThat(queuedTask.isSelectionLogsTrackingEnabled())
        .isEqualTo(abstractK8SState.isSelectionLogsTrackingForTasksEnabled());
    assertThat(queuedTask.getData()).isNotNull();
    assertThat(queuedTask.getData().getParameters()).isNotEmpty();
    assertThat(queuedTask.getData().getParameters()[0]).isSameAs(mockParams);
    assertThat(queuedTask.getSetupAbstractions().get(Cd1SetupFields.SERVICE_TEMPLATE_ID_FIELD))
        .isEqualTo(serviceTemplateId);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteCustomBindManifestFetchTask() {
    CustomManifestValuesFetchParams mockParams = CustomManifestValuesFetchParams.builder().build();
    CustomSourceConfig customSourceConfig =
        CustomSourceConfig.builder().path("FILE_PATH").script("CUSTOM_SCRIPT").build();
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = ImmutableMap.of(K8sValuesLocation.Service,
        ApplicationManifest.builder().customSourceConfig(customSourceConfig).storeType(CUSTOM).build());
    K8sStateExecutor k8sStateExecutor = mock(K8sStateExecutor.class);
    DirectKubernetesInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder().build();
    infrastructureMapping.setUuid(INFRA_MAPPING_ID);
    String serviceTemplateId = "serviceTemplateId";

    doReturn(true).when(featureFlagService).isEnabled(FeatureName.CUSTOM_MANIFEST, ACCOUNT_ID);
    doReturn(true)
        .when(featureFlagService)
        .isEnabled(FeatureName.BIND_CUSTOM_VALUE_AND_MANIFEST_FETCH_TASK, ACCOUNT_ID);

    doReturn(infrastructureMapping).when(infrastructureMappingService).get(APP_ID, null);
    doReturn(Activity.builder().uuid(ACTIVITY_ID).build()).when(activityService).save(any(Activity.class));
    doReturn(appManifestMap).when(applicationManifestUtils).getApplicationManifests(context, AppManifestKind.VALUES);
    doReturn(mockParams)
        .when(applicationManifestUtils)
        .createCustomManifestValuesFetchParams(context, appManifestMap, VALUES_YAML_KEY);
    doReturn(serviceTemplateId).when(serviceTemplateHelper).fetchServiceTemplateId(infrastructureMapping);
    ExecutionResponse executionResponse = abstractK8SState.executeWrapperWithManifest(k8sStateExecutor, context, 90000);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(1)).queueTaskV2(captor.capture());
    DelegateTask queuedTask = captor.getValue();
    assertThat(queuedTask.isSelectionLogsTrackingEnabled())
        .isEqualTo(abstractK8SState.isSelectionLogsTrackingForTasksEnabled());
    assertThat(queuedTask.getData()).isNotNull();
    assertThat(queuedTask.getData().getParameters()).isNotEmpty();
    assertThat(queuedTask.getData().getParameters()[0]).isSameAs(mockParams);
    assertThat(mockParams.getCustomManifestSource()).isNotNull();
    assertThat(mockParams.getCustomManifestSource().getFilePaths()).containsExactly("FILE_PATH");
    assertThat(mockParams.getCustomManifestSource().getScript()).isEqualTo("CUSTOM_SCRIPT");
    assertThat(queuedTask.getSetupAbstractions().get(Cd1SetupFields.SERVICE_TEMPLATE_ID_FIELD))
        .isEqualTo(serviceTemplateId);
    assertThat(((K8sStateExecutionData) executionResponse.getStateExecutionData()).getCurrentTaskType())
        .isEqualTo(CUSTOM_MANIFEST_FETCH_TASK);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetNamespacesFromK8sPodList() {
    Set<String> namespaces = abstractK8SState.fetchNamespacesFromK8sPodList(emptyList());
    assertThat(namespaces).isEmpty();

    namespaces = abstractK8SState.fetchNamespacesFromK8sPodList(
        asList(K8sPod.builder().namespace("default").build(), K8sPod.builder().namespace("namespace").build()));
    assertThat(namespaces).contains("default", "namespace");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetInstanceElementListParam() {
    InstanceElementListParam instanceElementListParam = abstractK8SState.fetchInstanceElementListParam(emptyList());
    assertThat(instanceElementListParam.getInstanceElements()).isEmpty();

    instanceElementListParam = abstractK8SState.fetchInstanceElementListParam(
        asList(K8sPod.builder().namespace("default").name("podName").podIP("127.0.0.1").build()));
    assertThat(instanceElementListParam.getInstanceElements()).isNotEmpty();
    assertThat(instanceElementListParam.getInstanceElements().get(0).getDisplayName()).isEqualTo("podName");
    assertThat(instanceElementListParam.getInstanceElements().get(0).getUuid()).isEqualTo("podName");
    assertThat(instanceElementListParam.getInstanceElements().get(0).getHostName()).isEqualTo("podName");
    assertThat(instanceElementListParam.getInstanceElements().get(0).getHost().getHostName()).isEqualTo("podName");
    assertThat(instanceElementListParam.getInstanceElements().get(0).getHost().getIp()).isEqualTo("127.0.0.1");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetInstanceStatusSummaries() {
    List<InstanceStatusSummary> instanceStatusSummaries =
        abstractK8SState.fetchInstanceStatusSummaries(emptyList(), ExecutionStatus.SUCCESS);
    assertThat(instanceStatusSummaries).isEmpty();

    instanceStatusSummaries =
        abstractK8SState.fetchInstanceStatusSummaries(asList(anInstanceElement().build()), ExecutionStatus.SUCCESS);
    assertThat(instanceStatusSummaries.get(0).getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCreateDelegateManifestConfig_Local() {
    ApplicationManifest appManifest = ApplicationManifest.builder().storeType(Local).kind(K8S_MANIFEST).build();
    appManifest.setUuid(APPLICATION_MANIFEST_ID);
    appManifest.setAppId(APP_ID);
    ManifestFile manifestFile = ManifestFile.builder()
                                    .fileContent("fileContent")
                                    .fileName("fileName")
                                    .applicationManifestId(APPLICATION_MANIFEST_ID)
                                    .build();
    manifestFile.setAppId(APP_ID);
    persistence.save(appManifest);
    persistence.save(manifestFile);

    K8sDelegateManifestConfig delegateManifestConfig =
        abstractK8SState.createDelegateManifestConfig(context, appManifest);
    assertThat(delegateManifestConfig.getManifestFiles().get(0).getFileName()).isEqualTo("fileName");
    assertThat(delegateManifestConfig.getManifestFiles().get(0).getFileContent()).isEqualTo("fileContent");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateDelegateManifestConfig_Custom() {
    testCreateDelegateManifestConfig_CustomSource(CUSTOM);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateDelegateManifestConfig_CustomOpenshiftTemplate() {
    testCreateDelegateManifestConfig_CustomSource(CUSTOM_OPENSHIFT_TEMPLATE);
  }

  private void testCreateDelegateManifestConfig_CustomSource(StoreType storeType) {
    String customScript = "echo manifest/template.yaml";
    String manifestPath = "manifest/template.yaml";
    ApplicationManifest appManifest =
        ApplicationManifest.builder()
            .storeType(storeType)
            .kind(K8S_MANIFEST)
            .customSourceConfig(CustomSourceConfig.builder().script(customScript).path(manifestPath).build())
            .build();

    doReturn(false).when(featureFlagService).isEnabled(FeatureName.CUSTOM_MANIFEST, ACCOUNT_ID);
    K8sDelegateManifestConfig delegateManifestConfig =
        abstractK8SState.createDelegateManifestConfig(context, appManifest);
    assertCustomManifestSource(delegateManifestConfig, false, customScript, manifestPath);

    doReturn(true).when(featureFlagService).isEnabled(FeatureName.CUSTOM_MANIFEST, ACCOUNT_ID);
    delegateManifestConfig = abstractK8SState.createDelegateManifestConfig(context, appManifest);
    assertCustomManifestSource(delegateManifestConfig, true, customScript, manifestPath);
  }

  private void assertCustomManifestSource(
      K8sDelegateManifestConfig delegateManifestConfig, boolean customManifestEnabled, String script, String path) {
    assertThat(delegateManifestConfig.isCustomManifestEnabled()).isEqualTo(customManifestEnabled);
    if (customManifestEnabled) {
      assertThat(delegateManifestConfig.getCustomManifestSource().getScript()).isEqualTo(script);
      assertThat(delegateManifestConfig.getCustomManifestSource().getFilePaths()).containsExactly(path);
    } else {
      assertThat(delegateManifestConfig.getCustomManifestSource()).isNull();
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testSaveK8sElement() {
    K8sElement k8sElement = K8sElement.builder().releaseName("releaseName").releaseNumber(12).build();
    abstractK8SState.saveK8sElement(context, k8sElement);

    K8sElement savedK8sElement = k8sStateHelper.fetchK8sElement(context);
    assertThat(savedK8sElement).isNull();

    SweepingOutputInstance sweepingOutputInstance =
        context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
            .name("k8s")
            .output(kryoSerializer.asDeflatedBytes(k8sElement))
            .build();

    doReturn(sweepingOutputInstance).when(mockedSweepingOutputService).find(any());
    savedK8sElement = k8sStateHelper.fetchK8sElement(context);
    assertThat(savedK8sElement.getReleaseName()).isEqualTo("releaseName");
    assertThat(savedK8sElement.getReleaseNumber()).isEqualTo(12);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testUpdateManifestsArtifactVariableNames() {
    try {
      abstractK8SState.updateManifestsArtifactVariableNames(APP_ID, INFRA_MAPPING_ID, emptySet());
    } catch (Exception ex) {
      assertThatExceptionOfType(InvalidRequestException.class);
      assertThat(ex.getMessage()).isEqualTo("Infra mapping not found for appId APP_ID infraMappingId INFRA_MAPPING_ID");
    }

    ApplicationManifest applicationManifest = createApplicationManifest();
    ManifestFile manifestFile = createManifestFile();
    persistence.save(applicationManifest);
    persistence.save(manifestFile);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(createGCPInfraMapping());

    Set<String> serviceArtifactVariableNames = new HashSet<>();
    abstractK8SState.updateManifestsArtifactVariableNames(APP_ID, INFRA_MAPPING_ID, serviceArtifactVariableNames);
    assertThat(serviceArtifactVariableNames).contains("artifact");
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testExecuteWrapperWithManifestKustomize() {
    K8sStateExecutor k8sStateExecutor = mock(K8sStateExecutor.class);
    K8sStateExecutionData k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    k8sStateExecutionData.setValuesFiles(new HashMap<>());

    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    appManifestMap.put(K8sValuesLocation.Service,
        ApplicationManifest.builder().storeType(Remote).kind(AppManifestKind.K8S_MANIFEST).build());
    when(applicationManifestUtils.getOverrideApplicationManifests(context, AppManifestKind.KUSTOMIZE_PATCHES))
        .thenReturn(appManifestMap);
    when(openShiftManagerService.isOpenShiftManifestConfig(context)).thenReturn(false);
    when(applicationManifestUtils.isValuesInHelmChartRepo(context)).thenReturn(false);
    when(applicationManifestUtils.isKustomizeSource(context)).thenReturn(true);
    when(featureFlagService.isEnabled(KUSTOMIZE_PATCHES_CG, context.getAccountId())).thenReturn(true);
    when(activityService.save(any(Activity.class))).thenReturn(Activity.builder().uuid(ACTIVITY_ID).build());
    when(applicationManifestUtils.createGitFetchFilesTaskParams(context, application, appManifestMap))
        .thenReturn(GitFetchFilesTaskParams.builder().build());
    ExecutionResponse executionResponse =
        abstractK8SState.executeWrapperWithManifest(k8sStateExecutor, context, 10 * 60 * 1000L);
    assertThat(((K8sStateExecutionData) executionResponse.getStateExecutionData()).getCurrentTaskType())
        .isEqualTo(TaskType.GIT_COMMAND);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testExecuteWrapperWithManifest() {
    K8sStateExecutor k8sStateExecutor = mock(K8sStateExecutor.class);
    K8sStateExecutionData k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    k8sStateExecutionData.setValuesFiles(new HashMap<>());

    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    appManifestMap.put(K8sValuesLocation.Service,
        ApplicationManifest.builder().storeType(Remote).kind(AppManifestKind.K8S_MANIFEST).build());
    when(applicationManifestUtils.getOverrideApplicationManifests(context, AppManifestKind.OC_PARAMS))
        .thenReturn(appManifestMap);
    when(openShiftManagerService.isOpenShiftManifestConfig(context)).thenReturn(true);
    when(activityService.save(any(Activity.class))).thenReturn(Activity.builder().uuid(ACTIVITY_ID).build());
    when(applicationManifestUtils.createGitFetchFilesTaskParams(context, application, appManifestMap))
        .thenReturn(GitFetchFilesTaskParams.builder().build());
    ExecutionResponse executionResponse =
        abstractK8SState.executeWrapperWithManifest(k8sStateExecutor, context, 10 * 60 * 1000L);
    assertThat(((K8sStateExecutionData) executionResponse.getStateExecutionData()).getCurrentTaskType())
        .isEqualTo(TaskType.GIT_COMMAND);

    when(applicationManifestUtils.getAppManifestByApplyingHelmChartOverride(context))
        .thenReturn(ApplicationManifest.builder().storeType(HelmChartRepo).kind(AppManifestKind.K8S_MANIFEST).build());
    when(applicationManifestUtils.isValuesInHelmChartRepo(context)).thenReturn(true);
    when(openShiftManagerService.isOpenShiftManifestConfig(context)).thenReturn(false);
    executionResponse = abstractK8SState.executeWrapperWithManifest(k8sStateExecutor, context, 10 * 60 * 1000L);
    assertThat(((K8sStateExecutionData) executionResponse.getStateExecutionData()).getCurrentTaskType())
        .isEqualTo(TaskType.HELM_VALUES_FETCH);

    appManifestMap.put(K8sValuesLocation.Service,
        ApplicationManifest.builder().storeType(HelmSourceRepo).kind(AppManifestKind.K8S_MANIFEST).build());
    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(appManifestMap);
    when(openShiftManagerService.isOpenShiftManifestConfig(context)).thenReturn(false);
    when(applicationManifestUtils.isValuesInHelmChartRepo(context)).thenReturn(false);
    executionResponse = abstractK8SState.executeWrapperWithManifest(k8sStateExecutor, context, 10 * 60 * 1000L);
    assertThat(((K8sStateExecutionData) executionResponse.getStateExecutionData()).getCurrentTaskType())
        .isEqualTo(TaskType.GIT_COMMAND);

    when(k8sStateExecutor.executeK8sTask(context, ACTIVITY_ID))
        .thenThrow(new InvalidRequestException("App not found"))
        .thenThrow(new UnsupportedOperationException("asd"));
    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(emptyMap());
    try {
      abstractK8SState.executeWrapperWithManifest(k8sStateExecutor, context, 10 * 60 * 1000L);
    } catch (Exception ex) {
      assertThatExceptionOfType(InvalidRequestException.class);
      assertThat(ex.getMessage()).isEqualTo("App not found");
    }

    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(emptyMap());
    try {
      abstractK8SState.executeWrapperWithManifest(k8sStateExecutor, context, 10 * 60 * 1000L);
    } catch (Exception ex) {
      assertThatExceptionOfType(InvalidRequestException.class);
      assertThat(ex.getCause()).isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testExecuteWrapperWithManifestStepOverride() {
    K8sApplyState k8sApplyState = mock(K8sApplyState.class);
    GitFileConfig remoteOverride = GitFileConfig.builder()
                                       .branch("master")
                                       .connectorId("git-connector")
                                       .filePathList(Arrays.asList("folder/v1.yaml", "folder/v2.yaml"))
                                       .build();
    k8sApplyState.setRemoteStepOverride(remoteOverride);

    K8sStateExecutionData k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    k8sStateExecutionData.setValuesFiles(new HashMap<>());

    ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);

    when(openShiftManagerService.isOpenShiftManifestConfig(context)).thenReturn(false);
    when(applicationManifestUtils.isKustomizeSource(context)).thenReturn(false);
    when(abstractK8SState.getStepRemoteOverrideGitConfig()).thenReturn(remoteOverride);
    doReturn(Activity.builder().uuid(ACTIVITY_ID).build())
        .when(abstractK8SState)
        .createK8sActivity(eq(context), any(), any(), any(), any());

    doReturn(ExecutionResponse.builder()
                 .stateExecutionData(K8sStateExecutionData.builder().currentTaskType(TaskType.GIT_COMMAND).build())
                 .build())
        .when(abstractK8SState)
        .executeGitTask(eq(context), argumentCaptor.capture(), any(), any());

    ExecutionResponse executionResponse =
        abstractK8SState.executeWrapperWithManifest(k8sApplyState, context, 10 * 60 * 1000L);
    assertThat(((K8sStateExecutionData) executionResponse.getStateExecutionData()).getCurrentTaskType())
        .isEqualTo(TaskType.GIT_COMMAND);
    assertThat(argumentCaptor.getValue().get(K8sValuesLocation.Step))
        .extracting(appManifest -> ((ApplicationManifest) appManifest).getGitFileConfig().equals(remoteOverride));
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetApplicationManifests() {
    when(openShiftManagerService.isOpenShiftManifestConfig(context)).thenReturn(true);
    abstractK8SState.fetchApplicationManifests(context);
    ArgumentCaptor<AppManifestKind> argumentCaptor = ArgumentCaptor.forClass(AppManifestKind.class);
    verify(applicationManifestUtils, times(1)).getApplicationManifests(any(), argumentCaptor.capture());
    assertThat(argumentCaptor.getValue()).isEqualTo(AppManifestKind.OC_PARAMS);

    when(openShiftManagerService.isOpenShiftManifestConfig(context)).thenReturn(false);
    abstractK8SState.fetchApplicationManifests(context);
    argumentCaptor = ArgumentCaptor.forClass(AppManifestKind.class);
    verify(applicationManifestUtils, times(2)).getApplicationManifests(any(), argumentCaptor.capture());
    assertThat(argumentCaptor.getValue()).isEqualTo(AppManifestKind.VALUES);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testSaveInstanceInfoToSweepingOutput() {
    on(abstractK8SState).set("sweepingOutputService", mockedSweepingOutputService);
    abstractK8SState.saveInstanceInfoToSweepingOutput(context, asList(anInstanceElement().dockerId("dockerId").build()),
        asList(InstanceDetails.builder().hostName("hostName").build()));

    ArgumentCaptor<SweepingOutputInstance> argumentCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(mockedSweepingOutputService, times(1)).save(argumentCaptor.capture());

    InstanceInfoVariables instanceInfoVariables = (InstanceInfoVariables) argumentCaptor.getValue().getValue();
    assertThat(instanceInfoVariables.getInstanceDetails().get(0).getHostName()).isEqualTo("hostName");
    assertThat(instanceInfoVariables.getInstanceElements().get(0).getDockerId()).isEqualTo("dockerId");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetK8sHelmDeploymentElement() {
    on(abstractK8SState).set("sweepingOutputService", mockedSweepingOutputService);
    ArgumentCaptor<SweepingOutputInquiry> inquiryCaptor = ArgumentCaptor.forClass(SweepingOutputInquiry.class);
    abstractK8SState.fetchK8sHelmDeploymentElement(context);
    verify(mockedSweepingOutputService, times(1)).findSweepingOutput(inquiryCaptor.capture());

    SweepingOutputInquiry inquiry = inquiryCaptor.getValue();
    assertThat(inquiry.getName()).isEqualTo(K8sHelmDeploymentElement.SWEEPING_OUTPUT_NAME);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testStorePreviousHelmDeploymentInfo() {
    on(abstractK8SState).set("sweepingOutputService", mockedSweepingOutputService);
    long epochNow = Instant.now().toEpochMilli();
    HelmChartInfo chartInfo = HelmChartInfo.builder().name("chart").version("1.1.0").build();
    HelmChartInfo oldChartInfo = HelmChartInfo.builder().name("chart").version("1.0.0").build();
    List<Instance> singleInstanceList = singletonList(instanceWithHelmChartInfoAndDeployedAt(chartInfo, epochNow));
    List<Instance> multipleInstancesWithNulls = asList(instanceWithHelmChartInfoAndDeployedAt(null, epochNow),
        instanceWithHelmChartInfoAndDeployedAt(null, epochNow), instanceWithHelmChartInfoAndDeployedAt(null, epochNow));
    List<Instance> multipleInstancesWithDifferentDeployedTime =
        asList(instanceWithHelmChartInfoAndDeployedAt(oldChartInfo, epochNow - 1000),
            instanceWithHelmChartInfoAndDeployedAt(oldChartInfo, epochNow - 2000),
            instanceWithHelmChartInfoAndDeployedAt(chartInfo, epochNow));

    // Not helm chart deployment
    testStorePreviousHelmDeploymentInfoForNonHelmDeployment();
    // Shouldn't override existing K8sHelmDeploymentElement
    testDoNotOverrideExistingK8sHelmDeploymentElement();
    // With single existing instance
    testStorePreviousHelmDeploymentInfoForHelmDeployment(HelmChartRepo, singleInstanceList, chartInfo);
    // With multiple existing instances and null values
    testStorePreviousHelmDeploymentInfoForHelmDeployment(HelmSourceRepo, multipleInstancesWithNulls, null);
    // With multiple existing instances with different deployed time
    testStorePreviousHelmDeploymentInfoForHelmDeployment(
        HelmChartRepo, multipleInstancesWithDifferentDeployedTime, chartInfo);
    // With empty existing instances
    testStorePreviousHelmDeploymentInfoForHelmDeployment(HelmSourceRepo, emptyList(), null);
  }

  private Instance instanceWithHelmChartInfoAndDeployedAt(HelmChartInfo helmChartInfo, long deployedAt) {
    return Instance.builder()
        .instanceInfo(K8sPodInfo.builder().helmChartInfo(helmChartInfo).build())
        .lastDeployedAt(deployedAt)
        .build();
  }

  private void testStorePreviousHelmDeploymentInfoForNonHelmDeployment() {
    ApplicationManifest applicationManifest = ApplicationManifest.builder().storeType(Local).build();
    abstractK8SState.storePreviousHelmDeploymentInfo(context, applicationManifest);
    verify(mockedSweepingOutputService, never()).findSweepingOutput(any(SweepingOutputInquiry.class));
    verify(mockedSweepingOutputService, never()).ensure(any(SweepingOutputInstance.class));
    verify(instanceService, never()).getInstancesForAppAndInframapping(nullable(String.class), nullable(String.class));
  }

  private void testStorePreviousHelmDeploymentInfoForHelmDeployment(
      StoreType storeType, List<Instance> instances, HelmChartInfo expectedChartInfo) {
    reset(mockedSweepingOutputService);
    ApplicationManifest applicationManifest = ApplicationManifest.builder().storeType(storeType).build();
    doReturn(instances)
        .when(instanceService)
        .getInstancesForAppAndInframapping(nullable(String.class), nullable(String.class));
    doReturn(null).when(mockedSweepingOutputService).findSweepingOutput(any(SweepingOutputInquiry.class));

    abstractK8SState.storePreviousHelmDeploymentInfo(context, applicationManifest);
    ArgumentCaptor<SweepingOutputInstance> instanceCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(mockedSweepingOutputService, times(1)).ensure(instanceCaptor.capture());

    SweepingOutputInstance instance = instanceCaptor.getValue();
    assertThat(instance.getValue()).isExactlyInstanceOf(K8sHelmDeploymentElement.class);
    K8sHelmDeploymentElement k8sHelmDeploymentElement = (K8sHelmDeploymentElement) instance.getValue();
    assertThat(k8sHelmDeploymentElement.getPreviousDeployedHelmChart()).isEqualTo(expectedChartInfo);
  }

  private void testDoNotOverrideExistingK8sHelmDeploymentElement() {
    reset(mockedSweepingOutputService);
    ApplicationManifest applicationManifest = ApplicationManifest.builder().storeType(HelmChartRepo).build();
    K8sHelmDeploymentElement existingElement = K8sHelmDeploymentElement.builder().build();
    doReturn(existingElement).when(mockedSweepingOutputService).findSweepingOutput(any(SweepingOutputInquiry.class));

    abstractK8SState.storePreviousHelmDeploymentInfo(context, applicationManifest);
    verify(mockedSweepingOutputService, times(1)).findSweepingOutput(any(SweepingOutputInquiry.class));
    verify(instanceService, never()).getInstancesForAppAndInframapping(nullable(String.class), nullable(String.class));
    verify(mockedSweepingOutputService, never()).ensure(any(SweepingOutputInstance.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetAppId() {
    WorkflowStandardParams standardParams =
        WorkflowStandardParams.Builder.aWorkflowStandardParams().withAppId(APP_ID).build();
    context.pushContextElement(standardParams);

    assertThat(abstractK8SState.fetchAppId(context)).isEqualTo(APP_ID);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testsaveInstanceInfoToSweepingOutputDontSkipVerification() {
    on(abstractK8SState).set("sweepingOutputService", mockedSweepingOutputService);
    abstractK8SState.saveInstanceInfoToSweepingOutput(context, asList(anInstanceElement().dockerId("dockerId").build()),
        asList(InstanceDetails.builder().hostName("hostName").newInstance(true).build(),
            InstanceDetails.builder().hostName("hostName").newInstance(false).build()));

    ArgumentCaptor<SweepingOutputInstance> argumentCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(mockedSweepingOutputService, times(1)).save(argumentCaptor.capture());

    InstanceInfoVariables instanceInfoVariables = (InstanceInfoVariables) argumentCaptor.getValue().getValue();
    assertThat(instanceInfoVariables.isSkipVerification()).isEqualTo(false);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testsaveInstanceInfoToSweepingOutputSkipVerification() {
    on(abstractK8SState).set("sweepingOutputService", mockedSweepingOutputService);
    abstractK8SState.saveInstanceInfoToSweepingOutput(context, asList(anInstanceElement().dockerId("dockerId").build()),
        asList(InstanceDetails.builder().hostName("hostName").newInstance(false).build(),
            InstanceDetails.builder().hostName("hostName").newInstance(false).build()));

    ArgumentCaptor<SweepingOutputInstance> argumentCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(mockedSweepingOutputService, times(1)).save(argumentCaptor.capture());

    InstanceInfoVariables instanceInfoVariables = (InstanceInfoVariables) argumentCaptor.getValue().getValue();
    assertThat(instanceInfoVariables.isSkipVerification()).isEqualTo(true);
  }

  @Test
  @Owner(developers = PARDHA)
  @Category(UnitTests.class)
  public void testRenderedDelegateSelectorsQueueK8sDelegateTask() {
    ExecutionContext executionContext = mock(DeploymentExecutionContext.class);
    SettingAttribute settingAttribute = aSettingAttribute()
                                            .withUuid(SETTING_ID)
                                            .withAccountId(ACCOUNT_ID)
                                            .withValue(KubernetesClusterConfig.builder().build())
                                            .build();
    persistence.save(settingAttribute);
    abstractK8SState.setDelegateSelectors(Collections.singletonList("delegate"));
    DirectKubernetesInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder().namespace("env").accountId(ACCOUNT_ID).build();
    infrastructureMapping.setComputeProviderSettingId(SETTING_ID);
    when(executionContext.renderExpression("delegate")).thenReturn("renderedDelegate");
    when(executionContext.renderExpression("default")).thenReturn("default");
    when(executionContext.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);
    when(executionContext.getContextElement(ContextElementType.PARAM, PHASE_PARAM)).thenReturn(phaseElement);
    when(((DeploymentExecutionContext) executionContext).getArtifactForService(any())).thenReturn(new Artifact());
    when(infrastructureMappingService.get(nullable(String.class), nullable(String.class)))
        .thenReturn(infrastructureMapping);
    when(serviceTemplateHelper.fetchServiceTemplateId(any())).thenReturn(SETTING_ID);
    when(evaluator.substitute(nullable(String.class), any(), any(), nullable(String.class))).thenReturn("default");
    when(serviceResourceService.getHelmVersionWithDefault(nullable(String.class), nullable(String.class)))
        .thenReturn(HelmVersion.V2);
    doReturn(K8sClusterConfig.builder().namespace("default").build())
        .when(containerDeploymentManagerHelper)
        .getK8sClusterConfig(any(ContainerInfrastructureMapping.class), eq(executionContext));

    abstractK8SState.queueK8sDelegateTask(executionContext, K8sRollingDeployTaskParameters.builder().build(), null);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(1)).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    K8sTaskParameters taskParams = (K8sTaskParameters) delegateTask.getData().getParameters()[0];

    assertThat(taskParams.getDelegateSelectors()).isEqualTo(Collections.singleton("renderedDelegate"));
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetK8sCanaryDeleteServiceElement() {
    on(abstractK8SState).set("sweepingOutputService", mockedSweepingOutputService);
    ArgumentCaptor<SweepingOutputInquiry> inquiryCaptor = ArgumentCaptor.forClass(SweepingOutputInquiry.class);
    abstractK8SState.fetchK8sCanaryDeleteServiceElement(context);
    verify(mockedSweepingOutputService, times(1)).findSweepingOutput(inquiryCaptor.capture());

    SweepingOutputInquiry inquiry = inquiryCaptor.getValue();
    assertThat(inquiry.getName()).isEqualTo(K8sCanaryDeleteServiceElement.SWEEPING_OUTPUT_NAME);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testSaveK8sCanaryDeployRun() {
    on(abstractK8SState).set("sweepingOutputService", mockedSweepingOutputService);
    abstractK8SState.saveK8sCanaryDeployRun(context);

    ArgumentCaptor<SweepingOutputInstance> argumentCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(mockedSweepingOutputService, times(1)).save(argumentCaptor.capture());

    assertThat(argumentCaptor.getValue().getName()).isEqualTo(K8sCanaryDeleteServiceElement.SWEEPING_OUTPUT_NAME);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForGitTaskWrapper_FF_COMMIT() {
    GitCommandExecutionResponse gitCommandExecutionResponse =
        GitCommandExecutionResponse.builder()
            .gitCommandStatus(GitCommandExecutionResponse.GitCommandStatus.SUCCESS)
            .gitCommandResult(GitFetchFilesFromMultipleRepoResult.builder()
                                  .gitFetchFilesConfigMap(new HashMap<>())
                                  .filesFromMultipleRepo(GIT_FETCH_FILES_RESULT_MAP)
                                  .build())
            .fetchedCommitIdsMap(Collections.singletonMap("Service", "CommitId"))
            .build();
    Map<String, ResponseData> response = new HashMap<>();
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    response.put(ACTIVITY_ID, gitCommandExecutionResponse);

    K8sStateExecutor k8sStateExecutor = mock(K8sStateExecutor.class);
    K8sStateExecutionData k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    k8sStateExecutionData.setCurrentTaskType(TaskType.GIT_COMMAND);
    k8sStateExecutionData.setActivityId(ACTIVITY_ID);
    k8sStateExecutionData.setValuesFiles(new HashMap<>());
    k8sStateExecutionData.setApplicationManifestMap(appManifestMap);

    Map<K8sValuesLocation, Collection<String>> valuesMap = new HashMap<>();
    valuesMap.put(K8sValuesLocation.Environment, singletonList("EnvValues"));
    when(applicationManifestUtils.getValuesFilesFromGitFetchFilesResponse(appManifestMap, gitCommandExecutionResponse))
        .thenReturn(valuesMap);
    doReturn(true).when(featureFlagService).isEnabled(any(), any());
    doReturn(Service.builder().uuid("serviceId").build()).when(applicationManifestUtils).fetchServiceFromContext(any());
    k8sCanaryDeploy.handleAsyncResponseWrapper(k8sStateExecutor, context, response);

    ArgumentCaptor<SweepingOutputInstance> argumentCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(mockedSweepingOutputService, times(2)).save(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().getName())
        .isEqualTo(K8sGitConfigMapInfo.SWEEPING_OUTPUT_NAME_PREFIX + "-serviceId");
    assertThat(argumentCaptor.getValue().getAppId()).isEqualTo(APP_ID);

    ArgumentCaptor<SweepingOutputInquiry> sweepingOutputInquiryCaptor =
        ArgumentCaptor.forClass(SweepingOutputInquiry.class);
    verify(mockedSweepingOutputService, times(2)).findSweepingOutput(sweepingOutputInquiryCaptor.capture());
    assertThat(sweepingOutputInquiryCaptor.getAllValues().get(0).getName())
        .isEqualTo(K8sGitFetchInfo.SWEEPING_OUTPUT_NAME_PREFIX);
    assertThat(sweepingOutputInquiryCaptor.getAllValues().get(0).getAppId()).isEqualTo(APP_ID);
    reset(mockedSweepingOutputService);
    k8sRollingDeploy.handleAsyncResponseWrapper(k8sStateExecutor, context, response);
    verify(mockedSweepingOutputService, times(1)).save(any());
    verify(mockedSweepingOutputService, times(1)).findSweepingOutput(any());

    reset(featureFlagService);
    doReturn(false).when(featureFlagService).isEnabled(any(), any());
    k8sCanaryDeploy.handleAsyncResponseWrapper(k8sStateExecutor, context, response);
    k8sStateExecutionData = (K8sStateExecutionData) context.getStateExecutionData();
    assertThat(k8sStateExecutionData.getValuesFiles().get(K8sValuesLocation.Environment)).containsExactly("EnvValues");
    verify(mockedSweepingOutputService, times(1)).save(any());
    verify(mockedSweepingOutputService, times(1)).findSweepingOutput(any());

    reset(mockedSweepingOutputService);
    k8sRollingDeploy.handleAsyncResponseWrapper(k8sStateExecutor, context, response);
    verify(mockedSweepingOutputService, times(0)).save(any());
    verify(mockedSweepingOutputService, times(0)).findSweepingOutput(any());
  }
}
