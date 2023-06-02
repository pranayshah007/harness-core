/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.OPTIMIZED_GIT_FETCH_FILES;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.pcf.ResizeStrategy.RESIZE_NEW_FIRST;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.RIHAZ;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.GcpKubernetesInfrastructureMapping.Builder.aGcpKubernetesInfrastructureMapping;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.appmanifest.AppManifestKind.HELM_CHART_OVERRIDE;
import static software.wings.beans.appmanifest.ManifestFile.VALUES_YAML_KEY;
import static software.wings.beans.appmanifest.StoreType.CUSTOM;
import static software.wings.beans.appmanifest.StoreType.HelmChartRepo;
import static software.wings.helpers.ext.k8s.request.K8sValuesLocation.ServiceOverride;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.service.intfc.ServiceTemplateService.EncryptedFieldComputeMode.OBTAIN_VALUE;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.StateType.HELM_DEPLOY;
import static software.wings.sm.WorkflowStandardParams.Builder.aWorkflowStandardParams;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.CLUSTER_NAME;
import static software.wings.utils.WingsTestConstants.COMPUTE_PROVIDER_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.ENV_PROD_FIELD;
import static software.wings.utils.WingsTestConstants.HOST_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.RELEASE_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.STATE_NAME;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.container.ContainerInfo;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskDetails;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.delegate.task.manifests.request.CustomManifestFetchConfig;
import io.harness.delegate.task.manifests.request.CustomManifestValuesFetchParams;
import io.harness.delegate.task.manifests.response.CustomManifestValuesFetchResponse;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.deployment.InstanceDetails;
import io.harness.exception.HelmClientException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.VariableResolverTracker;
import io.harness.ff.FeatureFlagService;
import io.harness.helm.HelmCliCommandType;
import io.harness.helm.HelmCommandType;
import io.harness.helm.HelmSubCommandType;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.model.ImageDetails;
import io.harness.logging.CommandExecutionStatus;
import io.harness.manifest.CustomSourceConfig;
import io.harness.manifest.CustomSourceFile;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.tasks.ResponseData;

import software.wings.api.ContainerServiceElement;
import software.wings.api.ContextElementParamMapperFactory;
import software.wings.api.DeploymentType;
import software.wings.api.HelmDeployContextElement;
import software.wings.api.HelmDeployStateExecutionData;
import software.wings.api.HostElement;
import software.wings.api.InstanceElement;
import software.wings.api.InstanceElement.Builder;
import software.wings.api.InstanceElementListParam;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.ServiceTemplateElement;
import software.wings.api.helm.HelmReleaseInfoElement;
import software.wings.api.instancedetails.InstanceInfoVariables;
import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.GcpConfig;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFetchFilesTaskParams;
import software.wings.beans.GitFileConfig;
import software.wings.beans.HelmChartConfig;
import software.wings.beans.HelmCommandFlagConfig;
import software.wings.beans.HelmCommandFlagConstants.HelmSubCommand;
import software.wings.beans.HelmExecutionSummary;
import software.wings.beans.InfraMappingSweepingOutput;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.TemplateExpression;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.container.HelmChartSpecification;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitCommandExecutionResponse.GitCommandStatus;
import software.wings.common.InfrastructureConstants;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.common.VariableProcessor;
import software.wings.delegatetasks.validation.capabilities.HelmCommandRequest;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.features.api.FeatureService;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.helm.HelmCommandExecutionResponse;
import software.wings.helpers.ext.helm.HelmHelper;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.helpers.ext.helm.request.HelmInstallCommandRequest;
import software.wings.helpers.ext.helm.request.HelmRollbackCommandRequest;
import software.wings.helpers.ext.helm.request.HelmValuesFetchTaskParameters;
import software.wings.helpers.ext.helm.response.HelmInstallCommandResponse;
import software.wings.helpers.ext.helm.response.HelmReleaseHistoryCommandResponse;
import software.wings.helpers.ext.helm.response.HelmValuesFetchTaskResponse;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.infra.GoogleKubernetesEngine;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.impl.GitFileConfigHelperService;
import software.wings.service.impl.HelmChartConfigHelperService;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.impl.servicetemplates.ServiceTemplateHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.InstanceStatusSummary.InstanceStatusSummaryBuilder;
import software.wings.sm.StateExecutionContext;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.k8s.K8sStateHelper;
import software.wings.utils.ApplicationManifestUtils;
import software.wings.utils.WingsTestConstants;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import dev.morphia.Key;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class HelmDeployStateTest extends CategoryTest {
  private static final String HELM_CONTROLLER_NAME = "helm-controller-name";
  private static final String HELM_RELEASE_NAME_PREFIX = "helm-release-name-prefix";
  private static final String CHART_NAME = "chart-name";
  private static final String CHART_VERSION = "0.1.0";
  private static final String CHART_URL = "http://google.com";
  private static final String GIT_CONNECTOR_ID = "connectorId";
  public static final String GIT_BRANCH = "git_branch";
  public static final String GIT_COMMIT_ID = "commit_id";
  public static final String GIT_FILE_PATH_DIRECTORY = "templates/";
  public static final String GIT_FILE_PATH_FULL_DIRECTORY = "/templates/";
  public static final String GIT_YAML_FILE_PATH = "templates/values.yaml";
  public static final String GIT_YML_FILE_PATH = "templates/values.yml";
  public static final String GIT_NOT_YML_FILE_PATH = "templates/values";
  public static final String FILE_PATH_VALIDATION_MSG_KEY = "File path";
  public static final String FILE_PATH_DIRECTORY_VALIDATION_MSG_VALUE =
      "File path cannot be directory if git connector is selected";
  public static final String FILE_PATH_NOT_YAML_FILE_VALIDATION_MSG_VALUE =
      "File path has to be YAML file if git connector is selected";
  private static final String COMMAND_FLAGS = "--tls";
  private static final String PHASE_NAME = "phaseName";
  private static final HelmCommandFlagConfig HELM_COMMAND_FLAG =
      HelmCommandFlagConfig.builder()
          .valueMap(Stream
                        .of(new AbstractMap.SimpleEntry<>(HelmSubCommand.INSTALL, "--debug2"),
                            new AbstractMap.SimpleEntry<>(HelmSubCommand.UPGRADE, "--debug2"))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
          .build();

  @Mock private AppService appService;
  @Mock private ArtifactService artifactService;
  @Mock private ActivityService activityService;
  @Mock private DelegateService delegateService;
  @Mock private EnvironmentService environmentService;
  @Mock private ManagerExpressionEvaluator evaluator;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private InfrastructureDefinitionService infrastructureDefinitionService;
  @Mock private MainConfiguration configuration;
  @Mock private PortalConfig portalConfig;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private ServiceTemplateService serviceTemplateService;
  @Mock private VariableProcessor variableProcessor;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private SecretManager secretManager;
  @Mock private ContainerDeploymentManagerHelper containerDeploymentHelper;
  @Mock private SettingsService settingsService;
  @Mock private GitConfigHelperService gitConfigHelperService;
  @Mock private ArtifactCollectionUtils artifactCollectionUtils;
  @Mock private ApplicationManifestService applicationManifestService;
  @Mock private ApplicationManifestUtils applicationManifestUtils;
  @Mock private HelmChartConfigHelperService helmChartConfigHelperService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private ServiceTemplateHelper serviceTemplateHelper;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private HelmHelper helmHelper;
  @Mock private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private GitFileConfigHelperService gitFileConfigHelperService;
  @Mock private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Mock private LogService logService;
  @Mock private FeatureService featureService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private TemplateExpressionProcessor templateExpressionProcessor;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;

  @Spy @InjectMocks private K8sStateHelper k8sStateHelper;

  @InjectMocks HelmDeployState helmDeployState = new HelmDeployState("helmDeployState");
  @InjectMocks HelmRollbackState helmRollbackState = new HelmRollbackState("helmRollbackState");

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

  private StateExecutionInstance stateExecutionInstance =
      aStateExecutionInstance()
          .displayName(STATE_NAME)
          .addContextElement(workflowStandardParams)
          .addContextElement(phaseElement)
          .addContextElement(ContainerServiceElement.builder()
                                 .uuid(serviceElement.getUuid())
                                 .maxInstances(10)
                                 .clusterName(CLUSTER_NAME)
                                 .namespace("default")
                                 .name(HELM_CONTROLLER_NAME)
                                 .resizeStrategy(RESIZE_NEW_FIRST)
                                 .infraMappingId(INFRA_MAPPING_ID)
                                 .deploymentType(DeploymentType.KUBERNETES)
                                 .build())
          .addStateExecutionData(HelmDeployStateExecutionData.builder().build())
          .build();

  private InfrastructureMapping infrastructureMapping = aGcpKubernetesInfrastructureMapping()
                                                            .withUuid(INFRA_MAPPING_ID)
                                                            .withClusterName(CLUSTER_NAME)
                                                            .withComputeProviderSettingId(COMPUTE_PROVIDER_ID)
                                                            .withDeploymentType(DeploymentType.KUBERNETES.name())
                                                            .build();

  private InfrastructureDefinition infrastructureDefinition =
      InfrastructureDefinition.builder()
          .deploymentType(DeploymentType.HELM)
          .uuid(INFRA_DEFINITION_ID)
          .infrastructure(
              GoogleKubernetesEngine.builder().cloudProviderId(COMPUTE_PROVIDER_ID).clusterName(CLUSTER_NAME).build())
          .build();

  private String outputName = InfrastructureConstants.PHASE_INFRA_MAPPING_KEY_NAME + phaseElement.getUuid();
  private SweepingOutputInstance sweepingOutputInstance =
      SweepingOutputInstance.builder()
          .appId(APP_ID)
          .name(outputName)
          .uuid(generateUuid())
          .workflowExecutionId(WORKFLOW_EXECUTION_ID)
          .stateExecutionId(null)
          .pipelineExecutionId(null)
          .value(InfraMappingSweepingOutput.builder().infraMappingId(INFRA_MAPPING_ID).build())
          .build();

  private Application app = anApplication().uuid(APP_ID).name(APP_NAME).build();
  private Environment env = anEnvironment()
                                .appId(APP_ID)
                                .uuid(ENV_ID)
                                .name(ENV_NAME)
                                .environmentType(EnvironmentType.PROD)
                                .name("Prod Env")
                                .build();
  private Service service = Service.builder().appId(APP_ID).uuid(SERVICE_ID).name(SERVICE_NAME).build();
  private ContainerServiceParams containerServiceParams =
      ContainerServiceParams.builder()
          .settingAttribute(SettingAttribute.Builder.aSettingAttribute()
                                .withValue(KubernetesClusterConfig.builder()
                                               .delegateName("delegateName")
                                               .delegateSelectors(new HashSet<>(singletonList("delegateSelectors")))
                                               .useKubernetesDelegate(true)
                                               .build())
                                .build()
                                .toDTO())
          .build();
  private ExecutionContextImpl context;

  @Before
  public void setup() throws InterruptedException {
    MockitoAnnotations.initMocks(this);
    context = new ExecutionContextImpl(stateExecutionInstance);
    on(context).set("settingsService", settingsService);
    helmDeployState.setHelmReleaseNamePrefix(HELM_RELEASE_NAME_PREFIX);
    infrastructureMapping.setInfrastructureDefinitionId(INFRA_DEFINITION_ID);

    EmbeddedUser currentUser = EmbeddedUser.builder().name("test").email("test@harness.io").build();
    workflowStandardParams.setCurrentUser(currentUser);
    when(sweepingOutputService.find(any())).thenReturn(sweepingOutputInstance);

    when(appService.get(APP_ID)).thenReturn(app);
    when(appService.getApplicationWithDefaults(APP_ID)).thenReturn(app);
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(service);
    when(serviceResourceService.get(APP_ID, SERVICE_ID)).thenReturn(service);
    when(environmentService.get(APP_ID, ENV_ID, false)).thenReturn(env);
    when(environmentService.get(any(), any())).thenReturn(env);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(infrastructureMapping);
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(infrastructureDefinition);

    when(activityService.save(any(Activity.class))).thenReturn(Activity.builder().uuid(ACTIVITY_ID).build());
    when(secretManager.getEncryptionDetails(any(), anyString(), anyString())).thenReturn(Collections.emptyList());
    when(configuration.getPortal()).thenReturn(portalConfig);
    when(portalConfig.getUrl()).thenReturn("http://www.url.com");
    when(serviceTemplateService.getTemplateRefKeysByService(APP_ID, SERVICE_ID, ENV_ID))
        .thenReturn(singletonList(new Key<>(ServiceTemplate.class, "serviceTemplate", TEMPLATE_ID)));

    when(serviceTemplateService.get(APP_ID, TEMPLATE_ID)).thenReturn(aServiceTemplate().withUuid(TEMPLATE_ID).build());
    when(serviceTemplateHelper.fetchServiceTemplateId(any())).thenReturn(SERVICE_TEMPLATE_ID);
    when(serviceTemplateService.computeServiceVariables(APP_ID, ENV_ID, TEMPLATE_ID, null, OBTAIN_VALUE))
        .thenReturn(emptyList());
    when(workflowExecutionService.getExecutionDetails(anyString(), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(WorkflowExecution.builder().build());
    when(containerDeploymentHelper.getContainerServiceParams(any(), any(), any())).thenReturn(containerServiceParams);
    when(artifactCollectionUtils.fetchContainerImageDetails(any(), any())).thenReturn(ImageDetails.builder().build());

    when(delegateService.executeTaskV2(any()))
        .thenReturn(HelmCommandExecutionResponse.builder()
                        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                        .helmCommandResponse(HelmReleaseHistoryCommandResponse.builder().build())
                        .build());
    when(variableProcessor.getVariables(any(), any())).thenReturn(emptyMap());
    when(evaluator.substitute(anyString(), anyMap(), any(VariableResolverTracker.class), anyString()))
        .thenAnswer(i -> i.getArguments()[0]);

    WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService =
        new WorkflowStandardParamsExtensionService(appService, null, artifactService, environmentService,
            artifactStreamServiceBindingService, null, featureFlagService);

    on(helmDeployState).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(helmRollbackState).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(k8sStateHelper).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);

    ContextElementParamMapperFactory contextElementParamMapperFactory = new ContextElementParamMapperFactory(
        subdomainUrlHelper, workflowExecutionService, artifactService, artifactStreamService,
        applicationManifestService, featureFlagService, null, workflowStandardParamsExtensionService);

    on(context).set("infrastructureMappingService", infrastructureMappingService);
    on(context).set("infrastructureDefinitionService", infrastructureDefinitionService);
    on(context).set("serviceResourceService", serviceResourceService);
    on(context).set("artifactService", artifactService);
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("evaluator", evaluator);
    on(context).set("featureFlagService", featureFlagService);
    on(context).set("stateExecutionInstance", stateExecutionInstance);
    on(context).set("sweepingOutputService", sweepingOutputService);
    on(context).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(context).set("contextElementParamMapperFactory", contextElementParamMapperFactory);

    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn("baseUrl");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testExecute() throws InterruptedException {
    helmDeployState.setSteadyStateTimeout(5);
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .storeType(StoreType.HelmSourceRepo)
            .gitFileConfig(GitFileConfig.builder().connectorId(GIT_CONNECTOR_ID).build())
            .build();
    when(serviceResourceService.getHelmChartSpecification(APP_ID, SERVICE_ID))
        .thenReturn(HelmChartSpecification.builder()
                        .chartName(CHART_NAME)
                        .chartUrl(CHART_URL)
                        .chartVersion(CHART_VERSION)
                        .build());
    when(serviceTemplateHelper.fetchServiceTemplateId(any())).thenReturn(SERVICE_TEMPLATE_ID);
    when(applicationManifestService.getAppManifest(APP_ID, null, SERVICE_ID, AppManifestKind.K8S_MANIFEST))
        .thenReturn(applicationManifest);
    when(gitFileConfigHelperService.renderGitFileConfig(any(), any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(1, GitFileConfig.class));
    when(settingsService.fetchGitConfigFromConnectorId(GIT_CONNECTOR_ID)).thenReturn(GitConfig.builder().build());
    ExecutionResponse executionResponse = helmDeployState.execute(context);
    assertStateExecutionResponse(executionResponse);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertExecutedDelegateTask(delegateTask);

    verify(delegateService).executeTaskV2(any());
    verify(gitConfigHelperService, times(1)).renderGitConfig(any(), any());
    verify(gitFileConfigHelperService, times(1)).renderGitFileConfig(any(), any());
    verify(stateExecutionService, times(2)).appendDelegateTaskDetails(any(), any(DelegateTaskDetails.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteWithHelmChartRepo() throws InterruptedException {
    helmDeployState.setSteadyStateTimeout(5);
    HelmChartConfig chartConfigWithConnectorId = HelmChartConfig.builder().connectorId("connectorId").build();
    HelmChartConfig chartConfigWithoutConnectorId =
        HelmChartConfig.builder().chartVersion("1.0.0").chartUrl(CHART_URL).chartName(CHART_NAME).build();
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(HelmChartRepo).helmChartConfig(chartConfigWithConnectorId).build();

    when(serviceResourceService.getHelmChartSpecification(APP_ID, SERVICE_ID))
        .thenReturn(HelmChartSpecification.builder()
                        .chartName(CHART_NAME)
                        .chartUrl(CHART_URL)
                        .chartVersion(CHART_VERSION)
                        .build());
    when(applicationManifestService.getAppManifest(APP_ID, null, SERVICE_ID, AppManifestKind.K8S_MANIFEST))
        .thenReturn(applicationManifest);
    when(helmChartConfigHelperService.getHelmChartConfigTaskParams(context, applicationManifest))
        .thenReturn(HelmChartConfigParams.builder().repoName("repoName").build());
    // Case when connectorId is not blank
    ExecutionResponse executionResponse = helmDeployState.execute(context);
    assertStateExecutionResponse(executionResponse);

    // Case when connectorId is blank
    applicationManifest.setHelmChartConfig(chartConfigWithoutConnectorId);
    when(applicationManifestService.getAppManifest(APP_ID, null, SERVICE_ID, AppManifestKind.K8S_MANIFEST))
        .thenReturn(applicationManifest);
    executionResponse = helmDeployState.execute(context);
    assertStateExecutionResponse(executionResponse);

    verify(applicationManifestUtils, times(0))
        .applyK8sValuesLocationBasedHelmChartOverride(any(), any(Map.class), any());
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(2)).queueTaskV2(captor.capture());
    List<DelegateTask> delegateTasks = captor.getAllValues();

    assertExecutedDelegateTask(delegateTasks.get(0));
    assertExecutedDelegateTask(delegateTasks.get(1));

    verify(delegateService, times(2)).executeTaskV2(any());
  }

  private void assertStateExecutionResponse(ExecutionResponse executionResponse) {
    assertThat(executionResponse.isAsync()).isEqualTo(true);
    assertThat(executionResponse.getCorrelationIds()).contains(ACTIVITY_ID);
    HelmDeployStateExecutionData helmDeployStateExecutionData =
        (HelmDeployStateExecutionData) executionResponse.getStateExecutionData();
    assertThat(helmDeployStateExecutionData.getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(helmDeployStateExecutionData.getChartName()).isEqualTo(CHART_NAME);
    assertThat(helmDeployStateExecutionData.getChartRepositoryUrl()).isEqualTo(CHART_URL);
    assertThat(helmDeployStateExecutionData.getReleaseName()).isEqualTo(HELM_RELEASE_NAME_PREFIX);
    assertThat(helmDeployStateExecutionData.getReleaseOldVersion()).isEqualTo(0);
    assertThat(helmDeployStateExecutionData.getReleaseNewVersion()).isEqualTo(1);
    assertThat(helmDeployStateExecutionData.getCommandFlags()).isNull();
  }

  private void assertExecutedDelegateTask(DelegateTask delegateTask) {
    HelmInstallCommandRequest helmInstallCommandRequest =
        (HelmInstallCommandRequest) delegateTask.getData().getParameters()[0];
    assertThat(delegateTask.getSetupAbstractions()).containsKey(Cd1SetupFields.SERVICE_TEMPLATE_ID_FIELD);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.SERVICE_TEMPLATE_ID_FIELD))
        .isEqualTo(SERVICE_TEMPLATE_ID);
    assertThat(delegateTask.getSetupAbstractions()).containsKey(Cd1SetupFields.ARTIFACT_STREAM_ID_FIELD);
    assertThat(delegateTask.getData().getTimeout()).isEqualTo(300000);
    assertThat(helmInstallCommandRequest.getHelmCommandType()).isEqualTo(HelmCommandType.INSTALL);
    assertThat(helmInstallCommandRequest.getReleaseName()).isEqualTo(HELM_RELEASE_NAME_PREFIX);
    assertThat(helmInstallCommandRequest.getRepoName()).isEqualTo("app-name-service-name");
    assertThat(helmInstallCommandRequest.getCommandFlags()).isNull();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testExecuteWithNullChartSpec() {
    when(serviceResourceService.getHelmChartSpecification(APP_ID, SERVICE_ID)).thenReturn(null);

    helmDeployState.execute(context);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testExecuteWithNullReleaseName() {
    when(serviceResourceService.getHelmChartSpecification(APP_ID, SERVICE_ID)).thenReturn(null);
    helmDeployState.setHelmReleaseNamePrefix(null);

    helmDeployState.execute(context);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testErrorResponseFromDelegate() throws InterruptedException {
    when(serviceResourceService.getHelmChartSpecification(APP_ID, SERVICE_ID))
        .thenReturn(HelmChartSpecification.builder()
                        .chartName(CHART_NAME)
                        .chartUrl(CHART_URL)
                        .chartVersion(CHART_VERSION)
                        .build());

    when(delegateService.executeTaskV2(any())).thenReturn(RemoteMethodReturnValueData.builder().build());

    helmDeployState.execute(context);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testEmptyHelmChartSpec() {
    helmDeployState.execute(context);
    verify(serviceResourceService).getHelmChartSpecification(APP_ID, SERVICE_ID);
    verify(delegateService, never()).queueTaskV2(any());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testEmptyHelmChartSpecWithGit() throws InterruptedException {
    when(settingsService.fetchGitConfigFromConnectorId(GIT_CONNECTOR_ID)).thenReturn(GitConfig.builder().build());
    when(serviceTemplateHelper.fetchServiceTemplateId(any())).thenReturn(SERVICE_TEMPLATE_ID);
    doNothing().when(gitConfigHelperService).setSshKeySettingAttributeIfNeeded(any());
    helmDeployState.setGitFileConfig(GitFileConfig.builder().connectorId(GIT_CONNECTOR_ID).build());
    ExecutionResponse executionResponse = helmDeployState.execute(context);
    assertThat(executionResponse.isAsync()).isEqualTo(true);
    HelmDeployStateExecutionData helmDeployStateExecutionData =
        (HelmDeployStateExecutionData) executionResponse.getStateExecutionData();
    assertThat(helmDeployStateExecutionData.getChartName()).isEqualTo(null);
    assertThat(helmDeployStateExecutionData.getChartRepositoryUrl()).isEqualTo(null);
    verify(delegateService).queueTaskV2(any());
    verify(gitConfigHelperService).convertToRepoGitConfig(any(GitConfig.class), any());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testEmptyHelmChartSpecWithGitConfigAbsent() {
    when(settingsService.fetchGitConfigFromConnectorId(GIT_CONNECTOR_ID)).thenReturn(null);
    when(serviceTemplateHelper.fetchServiceTemplateId(any())).thenReturn(SERVICE_TEMPLATE_ID);
    doNothing().when(gitConfigHelperService).setSshKeySettingAttributeIfNeeded(any());
    helmDeployState.setGitFileConfig(GitFileConfig.builder().connectorId(GIT_CONNECTOR_ID).build());
    ExecutionResponse executionResponse = helmDeployState.execute(context);
    assertThat(executionResponse.isAsync()).isEqualTo(true);
    HelmDeployStateExecutionData helmDeployStateExecutionData =
        (HelmDeployStateExecutionData) executionResponse.getStateExecutionData();
    assertThat(helmDeployStateExecutionData.getChartName()).isEqualTo(null);
    assertThat(helmDeployStateExecutionData.getChartRepositoryUrl()).isEqualTo(null);
    verify(delegateService).queueTaskV2(any());
    verify(gitConfigHelperService, times(0)).convertToRepoGitConfig(any(GitConfig.class), anyString());
  }

  @Test()
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testHelmChartSpecWithGitDirectoryFilePath() {
    helmDeployState.setGitFileConfig(GitFileConfig.builder()
                                         .connectorId(GIT_CONNECTOR_ID)
                                         .commitId(GIT_COMMIT_ID)
                                         .filePath(GIT_FILE_PATH_DIRECTORY)
                                         .build());

    Map<String, String> invalidFields = helmDeployState.validateFields();

    assertThat(invalidFields).containsKey(FILE_PATH_VALIDATION_MSG_KEY);
    assertThat(invalidFields).containsValue(FILE_PATH_DIRECTORY_VALIDATION_MSG_VALUE);
  }

  @Test()
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testHelmChartSpecWithGitFullDirectoryFilePath() {
    helmDeployState.setGitFileConfig(GitFileConfig.builder()
                                         .connectorId(GIT_CONNECTOR_ID)
                                         .branch(GIT_BRANCH)
                                         .filePath(GIT_FILE_PATH_FULL_DIRECTORY)
                                         .build());

    Map<String, String> invalidFields = helmDeployState.validateFields();

    assertThat(invalidFields).containsKey(FILE_PATH_VALIDATION_MSG_KEY);
    assertThat(invalidFields).containsValue(FILE_PATH_DIRECTORY_VALIDATION_MSG_VALUE);
  }

  @Test()
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testHelmChartSpecWithGitYAMLFilePath() {
    helmDeployState.setGitFileConfig(
        GitFileConfig.builder().connectorId(GIT_CONNECTOR_ID).branch(GIT_BRANCH).filePath(GIT_YAML_FILE_PATH).build());

    Map<String, String> invalidFields = helmDeployState.validateFields();

    assertThat(invalidFields).isEmpty();
  }

  @Test()
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testHelmChartSpecWithGitYMLFilePath() {
    helmDeployState.setGitFileConfig(
        GitFileConfig.builder().connectorId(GIT_CONNECTOR_ID).branch(GIT_BRANCH).filePath(GIT_YML_FILE_PATH).build());

    Map<String, String> invalidFields = helmDeployState.validateFields();

    assertThat(invalidFields).isEmpty();
  }

  @Test()
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testHelmChartSpecWithGitNotYAMLFilePath() {
    helmDeployState.setGitFileConfig(GitFileConfig.builder()
                                         .connectorId(GIT_CONNECTOR_ID)
                                         .branch(GIT_BRANCH)
                                         .filePath(GIT_NOT_YML_FILE_PATH)
                                         .build());

    Map<String, String> invalidFields = helmDeployState.validateFields();

    assertThat(invalidFields).containsKey(FILE_PATH_VALIDATION_MSG_KEY);
    assertThat(invalidFields).containsValue(FILE_PATH_NOT_YAML_FILE_VALIDATION_MSG_VALUE);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHelmChartSpecWithGitEmptyYAMLFilePath() {
    helmDeployState.setGitFileConfig(
        GitFileConfig.builder().connectorId(GIT_CONNECTOR_ID).branch(GIT_BRANCH).filePath("").build());

    Map<String, String> invalidFields = helmDeployState.validateFields();

    assertThat(invalidFields).containsKey(FILE_PATH_VALIDATION_MSG_KEY);
    assertThat(invalidFields).containsValue("File path must not be blank if git connector is selected");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHelmChartSpecWithInvalidGitBranch() {
    helmDeployState.setGitFileConfig(
        GitFileConfig.builder().connectorId(GIT_CONNECTOR_ID).filePath(GIT_NOT_YML_FILE_PATH).build());

    Map<String, String> invalidFields = helmDeployState.validateFields();
    assertThat(invalidFields).containsKey("Branch or commit id");
    assertThat(invalidFields).containsValue("Branch or commit id must not be blank if git connector is selected");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHelmChartSpecWithMissingHelmReleaseNamePrefix() {
    Map<String, String> invalidFields;
    helmDeployState.setHelmReleaseNamePrefix(null);

    invalidFields = helmDeployState.validateFields();
    assertThat(invalidFields).containsKey("Helm release name prefix");
    assertThat(invalidFields.get("Helm release name prefix")).isEqualTo("Helm release name prefix must not be blank");

    helmDeployState.setHelmReleaseNamePrefix("");
    invalidFields = helmDeployState.validateFields();
    assertThat(invalidFields).containsKey("Helm release name prefix");
    assertThat(invalidFields.get("Helm release name prefix")).isEqualTo("Helm release name prefix must not be blank");

    helmDeployState.setHelmReleaseNamePrefix("     ");
    invalidFields = helmDeployState.validateFields();
    assertThat(invalidFields).containsKey("Helm release name prefix");
    assertThat(invalidFields.get("Helm release name prefix")).isEqualTo("Helm release name prefix must not be blank");
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testExecuteWithCommandFlags() {
    helmDeployState.setCommandFlags(COMMAND_FLAGS);
    when(serviceResourceService.getHelmChartSpecification(APP_ID, SERVICE_ID))
        .thenReturn(HelmChartSpecification.builder()
                        .chartName(CHART_NAME)
                        .chartUrl(CHART_URL)
                        .chartVersion(CHART_VERSION)
                        .build());
    when(serviceTemplateHelper.fetchServiceTemplateId(any())).thenReturn(SERVICE_TEMPLATE_ID);
    ExecutionResponse executionResponse = helmDeployState.execute(context);
    HelmDeployStateExecutionData helmDeployStateExecutionData =
        (HelmDeployStateExecutionData) executionResponse.getStateExecutionData();
    assertThat(helmDeployStateExecutionData.getCommandFlags()).isEqualTo(COMMAND_FLAGS);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();

    verifyDelegateSelectorInDelegateTaskParams(delegateTask);
    HelmInstallCommandRequest helmInstallCommandRequest =
        (HelmInstallCommandRequest) delegateTask.getData().getParameters()[0];
    assertThat(helmInstallCommandRequest.getCommandFlags()).isEqualTo(COMMAND_FLAGS);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testExecuteWithHelmRollbackForCommandFlags() {
    when(serviceResourceService.getHelmChartSpecification(APP_ID, SERVICE_ID))
        .thenReturn(HelmChartSpecification.builder()
                        .chartName(CHART_NAME)
                        .chartUrl(CHART_URL)
                        .chartVersion(CHART_VERSION)
                        .build());
    context.pushContextElement(HelmDeployContextElement.builder()
                                   .releaseName(HELM_RELEASE_NAME_PREFIX)
                                   .commandFlags(COMMAND_FLAGS)
                                   .newReleaseRevision(2)
                                   .previousReleaseRevision(1)
                                   .build());

    ExecutionResponse executionResponse = helmRollbackState.execute(context);
    HelmDeployStateExecutionData helmDeployStateExecutionData =
        (HelmDeployStateExecutionData) executionResponse.getStateExecutionData();

    assertThat(helmDeployStateExecutionData.getReleaseName()).isEqualTo(HELM_RELEASE_NAME_PREFIX);
    assertThat(helmDeployStateExecutionData.getCommandFlags()).isEqualTo(COMMAND_FLAGS);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();

    HelmRollbackCommandRequest helmRollbackCommandRequest =
        (HelmRollbackCommandRequest) delegateTask.getData().getParameters()[0];
    assertThat(helmRollbackCommandRequest.getCommandFlags()).isEqualTo(COMMAND_FLAGS);
    assertThat(helmRollbackCommandRequest.getReleaseName()).isEqualTo(HELM_RELEASE_NAME_PREFIX);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForHelmTaskInSuccess() {
    HelmCommandExecutionResponse helmCommandResponse =
        HelmCommandExecutionResponse.builder()
            .helmCommandResponse(HelmInstallCommandResponse.builder()
                                     .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                     .containerInfoList(emptyList())
                                     .helmChartInfo(HelmChartInfo.builder().build())
                                     .build())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();

    Map<String, ResponseData> response = new HashMap<>();
    response.put("activityId", helmCommandResponse);

    String instanceElementName = "instanceElement";
    List<InstanceStatusSummary> instanceStatusSummaries = Lists.newArrayList(
        InstanceStatusSummaryBuilder.anInstanceStatusSummary()
            .withStatus(ExecutionStatus.SUCCESS)
            .withInstanceElement(Builder.anInstanceElement().displayName(instanceElementName).build())
            .build());

    when(containerDeploymentHelper.getInstanceStatusSummaries(any(), any())).thenReturn(instanceStatusSummaries);
    ExecutionResponse executionResponse = helmDeployState.handleAsyncResponseForHelmTask(context, response);

    verify(activityService).updateStatus("activityId", APP_ID, ExecutionStatus.SUCCESS);
    verify(containerDeploymentHelper, times(1)).getInstanceStatusSummaries(any(), any());
    verify(workflowExecutionService, times(1)).getWorkflowExecution(any(), any());
    assertThat(executionResponse.getContextElements()).isNotEmpty();
    assertThat(((InstanceElementListParam) executionResponse.getContextElements().get(0))
                   .getInstanceElements()
                   .get(0)
                   .getName())
        .isEqualTo(instanceElementName);

    assertThat(executionResponse.getNotifyElements()).isNotEmpty();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getStateExecutionData()).isNotNull();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForHelmTaskInFailure() {
    HelmCommandExecutionResponse helmCommandResponse = HelmCommandExecutionResponse.builder()
                                                           .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                           .errorMessage("Failed")
                                                           .build();

    Map<String, ResponseData> response = new HashMap<>();
    response.put("activityId", helmCommandResponse);

    ExecutionResponse executionResponse = helmDeployState.handleAsyncResponseForHelmTask(context, response);

    verify(activityService).updateStatus("activityId", APP_ID, ExecutionStatus.FAILED);
    verify(containerDeploymentHelper, times(0)).getInstanceStatusSummaries(any(), any());
    verify(workflowExecutionService, times(0)).getWorkflowExecution(any(), any());
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(executionResponse.getErrorMessage()).isEqualTo("Failed");
    assertThat(executionResponse.getStateExecutionData()).isNotNull();
    assertThat(executionResponse.getNotifyElements()).isEmpty();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForHelmTaskWithInstallCommandResponseFailure() {
    HelmCommandExecutionResponse helmCommandResponse =
        HelmCommandExecutionResponse.builder()
            .errorMessage("Failed")
            .helmCommandResponse(HelmInstallCommandResponse.builder()
                                     .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                     .helmChartInfo(HelmChartInfo.builder().build())
                                     .containerInfoList(emptyList())
                                     .build())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();

    Map<String, ResponseData> response = new HashMap<>();
    response.put("activityId", helmCommandResponse);

    ExecutionResponse executionResponse = helmDeployState.handleAsyncResponseForHelmTask(context, response);

    verify(activityService).updateStatus("activityId", APP_ID, ExecutionStatus.SUCCESS);
    verify(containerDeploymentHelper, times(0)).getInstanceStatusSummaries(any(), any());
    verify(workflowExecutionService, times(1)).getWorkflowExecution(any(), any());
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getErrorMessage()).isEqualTo("Failed");
    assertThat(executionResponse.getStateExecutionData()).isNotNull();
    assertThat(executionResponse.getNotifyElements()).isEmpty();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForGitFetchFilesTaskForFailure() throws InterruptedException {
    GitCommandExecutionResponse gitCommandExecutionResponse =
        GitCommandExecutionResponse.builder().gitCommandStatus(GitCommandStatus.FAILURE).build();

    HelmDeployStateExecutionData helmStateExecutionData =
        (HelmDeployStateExecutionData) context.getStateExecutionData();

    String activityId = "activityId";
    helmStateExecutionData.setActivityId(activityId);
    helmStateExecutionData.setCurrentTaskType(TaskType.GIT_COMMAND);

    Map<String, ResponseData> response = new HashMap<>();
    response.put(activityId, gitCommandExecutionResponse);

    ExecutionResponse executionResponse = helmDeployState.handleAsyncInternal(context, response);

    verify(activityService).updateStatus("activityId", APP_ID, ExecutionStatus.FAILED);
    verify(applicationManifestUtils, times(0))
        .getValuesFilesFromGitFetchFilesResponse(anyMap(), any(GitCommandExecutionResponse.class));

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForHelmFetchTaskForFailure() throws InterruptedException {
    HelmValuesFetchTaskResponse helmValuesFetchTaskResponse =
        HelmValuesFetchTaskResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();

    HelmDeployStateExecutionData helmStateExecutionData =
        (HelmDeployStateExecutionData) context.getStateExecutionData();

    String activityId = "activityId";
    helmStateExecutionData.setActivityId(activityId);
    helmStateExecutionData.setCurrentTaskType(TaskType.HELM_VALUES_FETCH);

    Map<String, ResponseData> response = new HashMap<>();
    response.put(activityId, helmValuesFetchTaskResponse);
    ExecutionResponse executionResponse = helmDeployState.handleAsyncInternal(context, response);

    verify(activityService).updateStatus("activityId", APP_ID, ExecutionStatus.FAILED);
    verify(applicationManifestUtils, times(0))
        .getValuesFilesFromGitFetchFilesResponse(any(), any(GitCommandExecutionResponse.class));
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseOverallFailure() throws Exception {
    HelmDeployState spyDeployState = spy(helmDeployState);
    HelmValuesFetchTaskResponse response = HelmValuesFetchTaskResponse.builder().build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of(ACTIVITY_ID, response);

    // Rethrow instance of WingsException
    doThrow(new HelmClientException("Client exception", HelmCliCommandType.INSTALL))
        .when(spyDeployState)
        .handleAsyncInternal(context, responseDataMap);
    assertThatThrownBy(() -> spyDeployState.handleAsyncResponse(context, responseDataMap))
        .isInstanceOf(HelmClientException.class);

    // Throw InvalidRequestException on RuntimeException
    doThrow(new RuntimeException("Some exception got thrown"))
        .when(spyDeployState)
        .handleAsyncInternal(context, responseDataMap);
    assertThatThrownBy(() -> spyDeployState.handleAsyncResponse(context, responseDataMap))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForHelmFetchTask() {
    HelmDeployStateExecutionData stateExecutionData = (HelmDeployStateExecutionData) context.getStateExecutionData();
    stateExecutionData.setCurrentTaskType(TaskType.HELM_VALUES_FETCH);
    doReturn(
        HelmChartSpecification.builder().chartName(CHART_NAME).chartUrl(CHART_URL).chartVersion(CHART_VERSION).build())
        .when(serviceResourceService)
        .getHelmChartSpecification(APP_ID, SERVICE_ID);

    testHandleAsyncResponseForHelmFetchTaskWithValuesInGit();
    testHandleAsyncResponseForHelmFetchTaskWithNoValuesInGit();
  }

  private void testHandleAsyncResponseForHelmFetchTaskWithValuesInGit() {
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    Map<String, List<String>> mapK8sValuesLocationToContents = new HashMap<>();
    mapK8sValuesLocationToContents.put(K8sValuesLocation.Service.name(), singletonList("fileContent"));
    HelmValuesFetchTaskResponse response = HelmValuesFetchTaskResponse.builder()
                                               .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                               .mapK8sValuesLocationToContent(mapK8sValuesLocationToContents)
                                               .build();

    Map<String, ResponseData> responseDataMap = ImmutableMap.of(ACTIVITY_ID, response);

    doReturn(appManifestMap)
        .when(applicationManifestUtils)
        .getOverrideApplicationManifests(context, AppManifestKind.VALUES);
    doReturn(true).when(featureFlagService).isEnabled(eq(OPTIMIZED_GIT_FETCH_FILES), any());
    doReturn(GitFetchFilesTaskParams.builder().build())
        .when(applicationManifestUtils)
        .createGitFetchFilesTaskParams(context, app, appManifestMap);

    ArgumentCaptor<DelegateTask> delegateTaskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    doReturn("taskId").when(delegateService).queueTaskV2(delegateTaskCaptor.capture());
    doReturn(true).when(applicationManifestUtils).isValuesInGit(appManifestMap);
    helmDeployState.handleAsyncResponse(context, responseDataMap);
    DelegateTask task = delegateTaskCaptor.getValue();
    assertThat(task.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(APP_ID);
    assertThat(task.getSetupAbstractions().get(Cd1SetupFields.ENV_ID_FIELD)).isEqualTo(ENV_ID);
    assertThat(task.getSetupAbstractions().get(Cd1SetupFields.ENV_TYPE_FIELD)).isEqualTo(ENV_PROD_FIELD);
    assertThat(task.getData().isAsync()).isTrue();
    assertThat(task.getData().getTimeout()).isEqualTo(TimeUnit.MINUTES.toMillis(10));
    assertThat(task.getData().getTaskType()).isEqualTo(TaskType.GIT_FETCH_FILES_TASK.name());
    assertThat(((GitFetchFilesTaskParams) task.getData().getParameters()[0]).isOptimizedFilesFetch()).isTrue();
  }

  private void testHandleAsyncResponseForHelmFetchTaskWithNoValuesInGit() {
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    HelmValuesFetchTaskResponse response =
        HelmValuesFetchTaskResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
    Map<String, ResponseData> responseDataMap = ImmutableMap.of(ACTIVITY_ID, response);
    ArgumentCaptor<DelegateTask> delegateTaskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    doReturn("taskId").when(delegateService).queueTaskV2(delegateTaskCaptor.capture());

    doReturn(false).when(applicationManifestUtils).isValuesInGit(appManifestMap);
    helmDeployState.handleAsyncResponse(context, responseDataMap);
    DelegateTask task = delegateTaskCaptor.getValue();
    assertThat(task.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(APP_ID);
    assertThat(task.getSetupAbstractions().get(Cd1SetupFields.ENV_ID_FIELD)).isEqualTo(ENV_ID);
    assertThat(task.getSetupAbstractions().get(Cd1SetupFields.ENV_TYPE_FIELD)).isEqualTo(ENV_PROD_FIELD);
    assertThat(task.getData().isAsync()).isTrue();
    assertThat(task.getData().getTimeout()).isEqualTo(TimeUnit.MINUTES.toMillis(10));
    assertThat(task.getData().getTaskType()).isEqualTo(TaskType.HELM_COMMAND_TASK.name());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testHelmDeployWithCustomArtifact() {
    when(serviceResourceService.getHelmChartSpecification(APP_ID, SERVICE_ID))
        .thenReturn(HelmChartSpecification.builder()
                        .chartName(CHART_NAME)
                        .chartUrl(CHART_URL)
                        .chartVersion(CHART_VERSION)
                        .build());
    when(serviceTemplateHelper.fetchServiceTemplateId(any())).thenReturn(SERVICE_TEMPLATE_ID);
    when(artifactCollectionUtils.fetchContainerImageDetails(any(), any())).thenReturn(ImageDetails.builder().build());
    when(artifactService.get(ARTIFACT_ID)).thenReturn(anArtifact().withArtifactStreamId(ARTIFACT_STREAM_ID).build());
    when(artifactStreamServiceBindingService.listArtifactStreamIds(anyString()))
        .thenReturn(Arrays.asList(ARTIFACT_STREAM_ID));

    HelmDeployStateExecutionData helmStateExecutionData =
        (HelmDeployStateExecutionData) context.getStateExecutionData();
    String valuesFile = "# imageName: ${DOCKER_IMAGE_NAME}\n"
        + "# tag: ${DOCKER_IMAGE_TAG}";
    Map<K8sValuesLocation, Collection<String>> valuesFiles = new HashMap<>();
    valuesFiles.put(K8sValuesLocation.Service, singletonList(valuesFile));
    helmStateExecutionData.setValuesFiles(valuesFiles);

    helmDeployState.execute(context);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    HelmInstallCommandRequest helmInstallCommandRequest =
        (HelmInstallCommandRequest) delegateTask.getData().getParameters()[0];
    assertThat(helmInstallCommandRequest.getVariableOverridesYamlFiles().get(0)).isEqualTo(valuesFile);

    when(artifactCollectionUtils.fetchContainerImageDetails(any(), any()))
        .thenReturn(ImageDetails.builder().name("IMAGE_NAME").tag("TAG").build());
    helmDeployState.execute(context);

    captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(2)).queueTaskV2(captor.capture());
    delegateTask = captor.getValue();
    helmInstallCommandRequest = (HelmInstallCommandRequest) delegateTask.getData().getParameters()[0];
    String renderedValuesFile = "# imageName: IMAGE_NAME\n"
        + "# tag: TAG";
    assertThat(helmInstallCommandRequest.getVariableOverridesYamlFiles().get(0)).isEqualTo(renderedValuesFile);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testPriorityOrderOfValuesYamlFile() {
    Map<K8sValuesLocation, Collection<String>> k8sValuesLocationContentMap = new HashMap<>();
    k8sValuesLocationContentMap.put(ServiceOverride, singletonList("ServiceOverride"));
    k8sValuesLocationContentMap.put(K8sValuesLocation.Service, singletonList("Service"));
    k8sValuesLocationContentMap.put(K8sValuesLocation.Environment, singletonList("Environment"));
    k8sValuesLocationContentMap.put(K8sValuesLocation.EnvironmentGlobal, singletonList("EnvironmentGlobal"));
    List<String> expectedValuesYamlList =
        Arrays.asList("Service", "ServiceOverride", "EnvironmentGlobal", "Environment");

    List<String> actualValuesYamlList = helmDeployState.getOrderedValuesYamlList(k8sValuesLocationContentMap);

    assertThat(actualValuesYamlList).isEqualTo(expectedValuesYamlList);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPriorityOrderOfMultipleValuesYamlFile() {
    Map<K8sValuesLocation, Collection<String>> k8sValuesMap = new HashMap<>();
    k8sValuesMap.put(K8sValuesLocation.Service, Arrays.asList("Service1", "Service2", "Service3"));
    k8sValuesMap.put(ServiceOverride, Arrays.asList("ServiceOverride1", "ServiceOverride2"));
    k8sValuesMap.put(K8sValuesLocation.EnvironmentGlobal, Arrays.asList("EnvironmentGlobal1", "EnvironmentGlobal2"));
    k8sValuesMap.put(K8sValuesLocation.Environment, Arrays.asList("Environment1", "Environment2"));

    List<String> orderedValuesYamlList = helmDeployState.getOrderedValuesYamlList(k8sValuesMap);

    assertThat(orderedValuesYamlList)
        .containsExactly("Service1", "Service2", "Service3", "ServiceOverride1", "ServiceOverride2",
            "EnvironmentGlobal1", "EnvironmentGlobal2", "Environment1", "Environment2");
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void testIsRollBackNotNeeded() {
    assertThat(isInitialRollback()).isTrue();
    assertThat(isNotInitialRollback()).isFalse();
  }

  private boolean isInitialRollback() {
    StateExecutionInstance stateExecutionInstance =
        StateExecutionInstance.Builder.aStateExecutionInstance()
            .addContextElement(HelmDeployContextElement.builder().previousReleaseRevision(0).build())
            .build();
    ExecutionContext context = new ExecutionContextImpl(stateExecutionInstance);
    on(context).set("settingsService", settingsService);

    helmDeployState.setStateType(StateType.HELM_ROLLBACK.name());

    return helmDeployState.isRollBackNotNeeded(context);
  }

  private boolean isNotInitialRollback() {
    StateExecutionInstance stateExecutionInstance =
        StateExecutionInstance.Builder.aStateExecutionInstance()
            .addContextElement(HelmDeployContextElement.builder().previousReleaseRevision(1).build())
            .build();
    ExecutionContext context = new ExecutionContextImpl(stateExecutionInstance);
    on(context).set("settingsService", settingsService);

    helmDeployState.setStateType(StateType.HELM_ROLLBACK.name());

    return helmDeployState.isRollBackNotNeeded(context);
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void testInitialRollbackNotNeeded() {
    StateExecutionInstance stateExecutionInstance =
        StateExecutionInstance.Builder.aStateExecutionInstance()
            .addContextElement(HelmDeployContextElement.builder().previousReleaseRevision(0).build())
            .build();
    ExecutionContext context = new ExecutionContextImpl(stateExecutionInstance);
    on(context).set("settingsService", settingsService);
    HelmDeployStateExecutionData stateExecutionData = HelmDeployStateExecutionData.builder().build();

    doReturn(true).when(logService).batchedSaveCommandUnitLogs(any(), any(), any());

    ExecutionResponse executionResponse =
        helmDeployState.initialRollbackNotNeeded(context, ACTIVITY_ID, stateExecutionData);

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getStateExecutionData()).isEqualTo(stateExecutionData);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteHelmTaskWithNoInitialRollbackNeeded() throws InterruptedException {
    StateExecutionInstance stateExecutionInstance =
        StateExecutionInstance.Builder.aStateExecutionInstance()
            .addContextElement(workflowStandardParams)
            .addContextElement(phaseElement)
            .addContextElement(
                HelmDeployContextElement.builder().releaseName("release").previousReleaseRevision(0).build())
            .build();
    HelmDeployState spyHelmDeployState = spy(helmDeployState);
    on(context).set("stateExecutionInstance", stateExecutionInstance);

    spyHelmDeployState.setStateType(StateType.HELM_ROLLBACK.name());

    doReturn(true).when(logService).batchedSaveCommandUnitLogs(any(), any(), any());
    doReturn(Activity.builder().uuid(ACTIVITY_ID).build())
        .when(spyHelmDeployState)
        .createActivity(eq(context), anyList());

    spyHelmDeployState.executeInternal(context);
    verify(spyHelmDeployState, times(1)).isRollBackNotNeeded(context);
    verify(spyHelmDeployState, times(1))
        .initialRollbackNotNeeded(eq(context), eq(ACTIVITY_ID), any(HelmDeployStateExecutionData.class));
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testTagsInExecuteGitTask() {
    context.pushContextElement(HelmDeployContextElement.builder()
                                   .releaseName(HELM_RELEASE_NAME_PREFIX)
                                   .commandFlags(COMMAND_FLAGS)
                                   .newReleaseRevision(2)
                                   .previousReleaseRevision(1)
                                   .build());

    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    appManifestMap.put(K8sValuesLocation.Environment,
        ApplicationManifest.builder()
            .storeType(StoreType.Remote)
            .gitFileConfig(GitFileConfig.builder().build())
            .build());
    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(appManifestMap);
    when(applicationManifestUtils.isValuesInGit(appManifestMap)).thenReturn(true);
    when(applicationManifestUtils.createGitFetchFilesTaskParams(context, app, appManifestMap))
        .thenReturn(GitFetchFilesTaskParams.builder()
                        .containerServiceParams(containerServiceParams)
                        .isBindTaskFeatureSet(true)
                        .build());

    ExecutionResponse executionResponse = helmDeployState.execute(context);

    HelmDeployStateExecutionData helmDeployStateExecutionData =
        (HelmDeployStateExecutionData) executionResponse.getStateExecutionData();
    assertThat(helmDeployStateExecutionData.getCurrentTaskType()).isEqualTo(TaskType.GIT_COMMAND);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteGitSyncWithPopulateGitFilePathList() {
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = ImmutableMap.of(ServiceOverride,
        ApplicationManifest.builder()
            .storeType(StoreType.Remote)
            .gitFileConfig(GitFileConfig.builder().build())
            .build());
    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(appManifestMap);
    when(applicationManifestUtils.isValuesInGit(appManifestMap)).thenReturn(true);
    when(applicationManifestUtils.createGitFetchFilesTaskParams(context, app, appManifestMap))
        .thenReturn(GitFetchFilesTaskParams.builder().isBindTaskFeatureSet(true).build());

    ExecutionResponse executionResponse = helmDeployState.execute(context);
    verify(applicationManifestUtils, times(1)).populateRemoteGitConfigFilePathList(context, appManifestMap);
    HelmDeployStateExecutionData helmDeployStateExecutionData =
        (HelmDeployStateExecutionData) executionResponse.getStateExecutionData();
    assertThat(helmDeployStateExecutionData.getCurrentTaskType()).isEqualTo(TaskType.GIT_COMMAND);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testTimeout() {
    HelmDeployState state = new HelmDeployState("helm");

    assertThat(state.getTimeoutMillis()).isEqualTo(null);

    state.setSteadyStateTimeout(5);
    assertThat(state.getTimeoutMillis()).isEqualTo(300000);

    state.setSteadyStateTimeout(Integer.MAX_VALUE);
    assertThat(state.getTimeoutMillis()).isNull();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testTagsInExecuteHelmValuesFetchTask() {
    context.pushContextElement(HelmDeployContextElement.builder()
                                   .releaseName(HELM_RELEASE_NAME_PREFIX)
                                   .commandFlags(COMMAND_FLAGS)
                                   .newReleaseRevision(2)
                                   .previousReleaseRevision(1)
                                   .build());

    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    appManifestMap.put(K8sValuesLocation.Environment,
        ApplicationManifest.builder()
            .storeType(StoreType.Remote)
            .helmChartConfig(HelmChartConfig.builder().build())
            .build());

    when(featureFlagService.isEnabled(FeatureName.BIND_FETCH_FILES_TASK_TO_DELEGATE, context.getAccountId()))
        .thenReturn(true);
    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(appManifestMap);
    when(applicationManifestUtils.isValuesInHelmChartRepo(context)).thenReturn(true);

    ExecutionResponse executionResponse = helmDeployState.execute(context);

    HelmDeployStateExecutionData helmDeployStateExecutionData =
        (HelmDeployStateExecutionData) executionResponse.getStateExecutionData();
    assertThat(helmDeployStateExecutionData.getCurrentTaskType()).isEqualTo(TaskType.HELM_VALUES_FETCH);
  }

  private void verifyDelegateSelectorInDelegateTaskParams(DelegateTask delegateTask) {
    HelmCommandRequest helmCommandRequest = (HelmCommandRequest) delegateTask.getData().getParameters()[0];
    KubernetesClusterConfig clusterConfig =
        (KubernetesClusterConfig) helmCommandRequest.getContainerServiceParams().getSettingAttribute().getValue();
    assertThat(clusterConfig.getDelegateSelectors()).contains("delegateSelectors");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getHelmCommandRequestTimeoutValue() {
    HelmInstallCommandRequest commandRequest;

    helmDeployState.setSteadyStateTimeout(0);
    commandRequest = getHelmRollbackCommandRequest(helmDeployState);
    assertThat(commandRequest.getTimeoutInMillis()).isEqualTo(600000);

    helmDeployState.setSteadyStateTimeout(5);
    commandRequest = getHelmRollbackCommandRequest(helmDeployState);
    assertThat(commandRequest.getTimeoutInMillis()).isEqualTo(300000);

    helmDeployState.setSteadyStateTimeout(Integer.MAX_VALUE);
    commandRequest = getHelmRollbackCommandRequest(helmDeployState);
    assertThat(commandRequest.getTimeoutInMillis()).isEqualTo(600000);
  }

  private HelmInstallCommandRequest getHelmRollbackCommandRequest(HelmDeployState helmRollbackState) {
    return (HelmInstallCommandRequest) helmRollbackState.getHelmCommandRequest(context,
        HelmChartSpecification.builder().build(), ContainerServiceParams.builder().build(), "release-name",
        WingsTestConstants.ACCOUNT_ID, WingsTestConstants.APP_ID, WingsTestConstants.ACTIVITY_ID,
        ImageDetails.builder().build(), "repo", GitConfig.builder().build(), Collections.emptyList(), null,
        K8sDelegateManifestConfig.builder().build(), Collections.emptyMap(), HelmVersion.V3, null);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteHelmValuesFetchTaskWithHelmChartServiceManifest() {
    Map<K8sValuesLocation, ApplicationManifest> helmOverrideManifestMap = new HashMap<>();
    helmOverrideManifestMap.put(
        K8sValuesLocation.EnvironmentGlobal, ApplicationManifest.builder().storeType(StoreType.Local).build());

    Map<K8sValuesLocation, ApplicationManifest> mapK8sValuesLocationToApplicationManifest =
        applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES);

    ApplicationManifest serviceHelmChartManifest =
        ApplicationManifest.builder()
            .storeType(HelmChartRepo)
            .helmChartConfig(HelmChartConfig.builder().chartName("helm-chart").build())
            .build();

    doReturn(serviceHelmChartManifest).when(applicationManifestUtils).getApplicationManifestForService(context);
    when(helmChartConfigHelperService.getHelmChartConfigTaskParams(context, serviceHelmChartManifest))
        .thenReturn(HelmChartConfigParams.builder()
                        .connectorConfig(GcpConfig.builder().delegateSelectors(singletonList("gcp-delegate")).build())
                        .repoName("repoName")
                        .build());

    helmDeployState.executeHelmValuesFetchTask(
        context, ACTIVITY_ID, helmOverrideManifestMap, mapK8sValuesLocationToApplicationManifest);

    ArgumentCaptor<DelegateTask> delegateTaskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(applicationManifestUtils, times(1))
        .applyK8sValuesLocationBasedHelmChartOverride(
            serviceHelmChartManifest, helmOverrideManifestMap, K8sValuesLocation.EnvironmentGlobal);
    verify(delegateService, times(1)).queueTaskV2(delegateTaskCaptor.capture());

    DelegateTask task = delegateTaskCaptor.getValue();
    assertThat(task.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(APP_ID);
    assertThat(task.getSetupAbstractions().get(Cd1SetupFields.ENV_ID_FIELD)).isEqualTo(ENV_ID);
    assertThat(task.getSetupAbstractions().get(Cd1SetupFields.ENV_TYPE_FIELD)).isEqualTo(ENV_PROD_FIELD);
    assertThat(task.getData().getTaskType()).isEqualTo(TaskType.HELM_VALUES_FETCH.name());
    assertThat(task.getData().isAsync()).isTrue();
    assertThat(task.getData().getTimeout()).isEqualTo(TimeUnit.MINUTES.toMillis(10));

    HelmValuesFetchTaskParameters taskParameters = (HelmValuesFetchTaskParameters) task.getData().getParameters()[0];
    assertThat(taskParameters.getHelmChartConfigTaskParams()).isNotNull();
    assertThat(taskParameters.getHelmChartConfigTaskParams().getRepoName()).isEqualTo("repoName");
    assertThat(taskParameters.getDelegateSelectors()).containsExactly("gcp-delegate");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleAsyncInternalForGitTask() throws InterruptedException {
    HelmDeployState spyDeployState = spy(helmDeployState);
    GitCommandExecutionResponse gitCommandExecutionResponse =
        GitCommandExecutionResponse.builder().gitCommandStatus(GitCommandStatus.SUCCESS).build();
    HelmDeployStateExecutionData helmStateExecutionData =
        (HelmDeployStateExecutionData) context.getStateExecutionData();

    Map<K8sValuesLocation, ApplicationManifest> helmOverrideManifestMap = new HashMap<>();
    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    String activityId = "activityId";
    helmStateExecutionData.setActivityId(activityId);
    helmStateExecutionData.setCurrentTaskType(TaskType.GIT_COMMAND);
    helmStateExecutionData.setAppManifestMap(applicationManifestMap);
    ExecutionResponse helmTaskExecutionResponse = ExecutionResponse.builder().build();
    Map<String, ResponseData> response = new HashMap<>();
    response.put(activityId, gitCommandExecutionResponse);

    doReturn(helmOverrideManifestMap)
        .when(applicationManifestUtils)
        .getOverrideApplicationManifests(context, HELM_CHART_OVERRIDE);
    doReturn(helmTaskExecutionResponse)
        .when(spyDeployState)
        .executeHelmTask(context, activityId, applicationManifestMap, helmOverrideManifestMap);
    ExecutionResponse executionResponse = spyDeployState.handleAsyncInternal(context, response);

    assertThat(executionResponse).isSameAs(helmTaskExecutionResponse);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateHelmExecutionSummaryNegative() {
    HelmExecutionSummary executionSummary = spy(HelmExecutionSummary.builder().build());
    WorkflowExecution workflowExecution =
        spy(WorkflowExecution.builder().appId(APP_ID).helmExecutionSummary(executionSummary).build());
    doReturn(workflowExecution)
        .when(workflowExecutionService)
        .getWorkflowExecution(context.getAppId(), context.getWorkflowExecutionId());

    testUpdateHelmExecutionSummaryWithReleaseHistoryCommandResponse(executionSummary);
    testUpdateHelmExecutionSummaryWithoutHelmDeployStateType(executionSummary);
    testUpdateHelmExecutionSummaryWithoutHelmChartInfo(executionSummary);
    testUpdateHelmExecutionSummaryWithException();
  }

  private void testUpdateHelmExecutionSummaryWithReleaseHistoryCommandResponse(HelmExecutionSummary executionSummary) {
    reset(executionSummary);
    HelmReleaseHistoryCommandResponse commandResponse = HelmReleaseHistoryCommandResponse.builder().build();
    helmDeployState.updateHelmExecutionSummary(context, commandResponse);
    verifyNoInteractions(executionSummary);
  }

  private void testUpdateHelmExecutionSummaryWithoutHelmDeployStateType(HelmExecutionSummary executionSummary) {
    reset(executionSummary);
    HelmInstallCommandResponse commandResponse = HelmInstallCommandResponse.builder().build();
    helmDeployState.setStateType("NON_HELM_DEPLOY");
    helmDeployState.updateHelmExecutionSummary(context, commandResponse);
    verifyNoInteractions(executionSummary);
  }

  private void testUpdateHelmExecutionSummaryWithoutHelmChartInfo(HelmExecutionSummary executionSummary) {
    reset(executionSummary);
    HelmInstallCommandResponse commandResponse = HelmInstallCommandResponse.builder().helmChartInfo(null).build();
    helmDeployState.setStateType(HELM_DEPLOY.name());
    helmDeployState.updateHelmExecutionSummary(context, commandResponse);
    verifyNoInteractions(executionSummary);
  }

  private void testUpdateHelmExecutionSummaryWithException() {
    HelmInstallCommandResponse commandResponse = HelmInstallCommandResponse.builder().build();
    doThrow(new RuntimeException("Something went wrong"))
        .when(workflowExecutionService)
        .getWorkflowExecution(context.getAppId(), context.getWorkflowExecutionId());
    // Test exception hasn't been propagated upper
    helmDeployState.updateHelmExecutionSummary(context, commandResponse);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateHelmExecutionSummary() {
    HelmExecutionSummary executionSummary = spy(HelmExecutionSummary.builder().build());
    WorkflowExecution workflowExecution =
        spy(WorkflowExecution.builder().appId(APP_ID).helmExecutionSummary(executionSummary).build());
    doReturn(workflowExecution)
        .when(workflowExecutionService)
        .getWorkflowExecution(context.getAppId(), context.getWorkflowExecutionId());

    testUpdateHelmExecutionSummaryWithHelmChartInfoDetails(executionSummary);
    testUpdateHelmExecutionSummaryWithContainerInfoList(executionSummary);
  }

  private void testUpdateHelmExecutionSummaryWithHelmChartInfoDetails(HelmExecutionSummary executionSummary) {
    HelmChartInfo helmChartInfo = HelmChartInfo.builder().build();
    HelmChartInfo summaryHelmChartInfo = spy(HelmChartInfo.builder().build());
    HelmInstallCommandResponse commandResponse =
        HelmInstallCommandResponse.builder().helmChartInfo(helmChartInfo).build();
    helmDeployState.setStateType(HELM_DEPLOY.name());

    // empty helm chart info
    reset(executionSummary);
    helmDeployState.updateHelmExecutionSummary(context, commandResponse);
    verify(executionSummary, times(1)).getHelmChartInfo();
    verify(executionSummary, times(1)).setHelmChartInfo(HelmChartInfo.builder().build());
    verifyNoMoreInteractions(executionSummary);

    // with helm chart values
    reset(executionSummary);
    doReturn(summaryHelmChartInfo).when(executionSummary).getHelmChartInfo();
    helmChartInfo.setName("helm-chart");
    helmChartInfo.setVersion("helm-version");
    helmChartInfo.setRepoUrl("helm-repo-url");
    helmDeployState.updateHelmExecutionSummary(context, commandResponse);
    verify(executionSummary, never()).setHelmChartInfo(HelmChartInfo.builder().build());
    verify(summaryHelmChartInfo, times(1)).setName("helm-chart");
    verify(summaryHelmChartInfo, times(1)).setVersion("helm-version");
    verify(summaryHelmChartInfo, times(1)).setRepoUrl("helm-repo-url");
    verifyNoMoreInteractions(summaryHelmChartInfo);
  }

  private void testUpdateHelmExecutionSummaryWithContainerInfoList(HelmExecutionSummary executionSummary) {
    reset(executionSummary);
    HelmInstallCommandResponse commandResponse =
        HelmInstallCommandResponse.builder()
            .containerInfoList(Arrays.asList(
                ContainerInfo.builder().podName("p1").build(), ContainerInfo.builder().podName("p2").build()))
            .helmChartInfo(HelmChartInfo.builder().build())
            .build();
    helmDeployState.setStateType(HELM_DEPLOY.name());
    helmDeployState.updateHelmExecutionSummary(context, commandResponse);
    ArgumentCaptor<List<ContainerInfo>> containerInfoListCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(executionSummary, times(1)).setContainerInfoList(containerInfoListCaptor.capture());
    List<ContainerInfo> containerInfoList = containerInfoListCaptor.getValue();
    assertThat(containerInfoList.stream().map(ContainerInfo::getPodName)).containsExactlyInAnyOrder("p1", "p2");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteHelmTaskWithInvalidChartSpec() {
    HelmChartSpecification helmChartSpec = HelmChartSpecification.builder().build();
    doReturn(null).when(serviceResourceService).getHelmChartSpecification(anyString(), anyString());

    // Missing HelmChartSpecification
    assertThatThrownBy(() -> helmDeployState.executeHelmTask(context, ACTIVITY_ID, emptyMap(), emptyMap()))
        .hasMessageContaining("Invalid chart specification");

    doReturn(helmChartSpec).when(serviceResourceService).getHelmChartSpecification(anyString(), anyString());

    // Empty chart name and missing chart url
    helmChartSpec.setChartName("");
    helmChartSpec.setChartUrl(null);
    assertThatThrownBy(() -> helmDeployState.executeHelmTask(context, ACTIVITY_ID, emptyMap(), emptyMap()))
        .hasMessageContaining("Invalid chart specification");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteHelmTaskWithTemplatizedGitConfig() throws Exception {
    HelmChartSpecification helmChartSpec = HelmChartSpecification.builder().chartName("name").build();
    List<TemplateExpression> expressions = singletonList(TemplateExpression.builder().fieldName("connectorId").build());
    SettingAttribute attribute = aSettingAttribute().build();
    helmDeployState.setGitFileConfig(GitFileConfig.builder().build());
    helmDeployState.setTemplateExpressions(expressions);

    doReturn(true).when(featureFlagService).isEnabled(eq(OPTIMIZED_GIT_FETCH_FILES), any());
    doReturn(helmChartSpec).when(serviceResourceService).getHelmChartSpecification(anyString(), anyString());
    doReturn(expressions.get(0)).when(templateExpressionProcessor).getTemplateExpression(expressions, "connectorId");
    doReturn(attribute)
        .when(templateExpressionProcessor)
        .resolveSettingAttributeByNameOrId(context, expressions.get(0), SettingVariableTypes.GIT);

    // Invalid connectorId
    attribute.setValue(mock(SettingValue.class)); // Check is !(settingValue instanceof GitConfig)
    assertThatThrownBy(() -> helmDeployState.executeHelmTask(context, ACTIVITY_ID, emptyMap(), emptyMap()))
        .hasMessageContaining("Git connector not found");

    // Valid connectorId
    attribute.setValue(GitConfig.builder().build());
    helmDeployState.executeHelmTask(context, ACTIVITY_ID, emptyMap(), emptyMap());
    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(1)).queueTaskV2(taskCaptor.capture());

    HelmInstallCommandRequest request = (HelmInstallCommandRequest) taskCaptor.getValue().getData().getParameters()[0];
    assertThat(request.getGitConfig()).isEqualTo(attribute.getValue());
    assertThat(request.isOptimizedFilesFetch()).isTrue();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteHelmTaskWithHelmChartAsArtifact() throws Exception {
    HelmChartSpecification helmChartSpec = HelmChartSpecification.builder().chartName("name").build();

    HelmChartConfig chartConfigForServiceManifest =
        HelmChartConfig.builder().chartVersion("3.0.0").chartName("chartConfigForServiceManifest").build();
    ApplicationManifest appManifestUsingServiceId = ApplicationManifest.builder()
                                                        .pollForChanges(true)
                                                        .storeType(HelmChartRepo)
                                                        .helmChartConfig(chartConfigForServiceManifest)
                                                        .build();
    HelmChartConfig chartConfigForManifestAsArtifact =
        HelmChartConfig.builder().chartVersion("2.0.0").chartName("chartConfigForManifestAsArtifact").build();
    ApplicationManifest appManifestUsingManifestIdFromChartConfig =
        ApplicationManifest.builder()
            .pollForChanges(true)
            .storeType(HelmChartRepo)
            .helmChartConfig(chartConfigForManifestAsArtifact)
            .build();

    doReturn(helmChartSpec).when(serviceResourceService).getHelmChartSpecification(anyString(), anyString());
    doReturn(true).when(featureFlagService).isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, null);
    doReturn(appManifestUsingServiceId)
        .when(applicationManifestService)
        .getAppManifest(app.getUuid(), null, serviceElement.getUuid(), AppManifestKind.K8S_MANIFEST);
    doReturn(appManifestUsingManifestIdFromChartConfig)
        .when(applicationManifestUtils)
        .getAppManifestFromFromExecutionContextHelmChart(context, serviceElement.getUuid());
    doReturn(Service.builder().uuid(SERVICE_ID).artifactFromManifest(true).build())
        .when(serviceResourceService)
        .get(APP_ID, SERVICE_ID);

    helmDeployState.executeHelmTask(context, ACTIVITY_ID, emptyMap(), emptyMap());
    verify(applicationManifestUtils, times(1))
        .getAppManifestFromFromExecutionContextHelmChart(context, serviceElement.getUuid());

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();

    verifyDelegateSelectorInDelegateTaskParams(delegateTask);
    HelmInstallCommandRequest helmInstallCommandRequest =
        (HelmInstallCommandRequest) delegateTask.getData().getParameters()[0];

    assertThat(helmInstallCommandRequest.getChartSpecification()).isNotNull();
    assertThat(helmInstallCommandRequest.getChartSpecification().getChartName())
        .isEqualTo("chartConfigForManifestAsArtifact");
    assertThat(helmInstallCommandRequest.getChartSpecification().getChartVersion()).isEqualTo("2.0.0");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetValuesYamlOverridesWithoutImageDetails() {
    String yamlFileContent = "tag: ${DOCKER_IMAGE_TAG}\nimage: ${DOCKER_IMAGE_NAME}";
    //    ImageDetails imageDetails = ImageDetails.builder().tag("Tag").name("Image").domainName("domain").build();
    doAnswer(invocation -> {
      Map values = invocation.getArgument(1, Map.class);
      values.put(ServiceOverride, singletonList(yamlFileContent));
      return null;
    })
        .when(applicationManifestUtils)
        .populateValuesFilesFromAppManifest(anyMap(), anyMap());
    ContainerServiceParams serviceParams = ContainerServiceParams.builder().build();

    List<String> files = helmDeployState.getValuesYamlOverrides(context, serviceParams, null, emptyMap());
    assertThat(files.get(0)).isEqualTo("tag: ${DOCKER_IMAGE_TAG}\nimage: ${DOCKER_IMAGE_NAME}");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetValuesYamlOverridesWithImageDetails() {
    String yamlFileContent = "tag: ${DOCKER_IMAGE_TAG}\nimage: ${DOCKER_IMAGE_NAME}";
    ImageDetails imageDetails = ImageDetails.builder().tag("Tag").name("Image").domainName("domain").build();
    doAnswer(invocation -> {
      Map values = invocation.getArgument(1, Map.class);
      values.put(ServiceOverride, singletonList(yamlFileContent));
      return null;
    })
        .when(applicationManifestUtils)
        .populateValuesFilesFromAppManifest(anyMap(), anyMap());
    ContainerServiceParams serviceParams = ContainerServiceParams.builder().build();

    List<String> files = helmDeployState.getValuesYamlOverrides(context, serviceParams, imageDetails, emptyMap());
    assertThat(files.get(0)).isEqualTo("tag: Tag\nimage: domain/Image");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetValuesYamlOverridesWithDomainAlreadyInFileContent() {
    String yamlFileContent = "tag: ${DOCKER_IMAGE_TAG}\nimage: domain/${DOCKER_IMAGE_NAME}";
    ImageDetails imageDetails = ImageDetails.builder().tag("Tag").name("Image").domainName("domain").build();
    doAnswer(invocation -> {
      Map values = invocation.getArgument(1, Map.class);
      values.put(ServiceOverride, singletonList(yamlFileContent));
      return null;
    })
        .when(applicationManifestUtils)
        .populateValuesFilesFromAppManifest(anyMap(), anyMap());
    ContainerServiceParams serviceParams = ContainerServiceParams.builder().build();

    List<String> files = helmDeployState.getValuesYamlOverrides(context, serviceParams, imageDetails, emptyMap());
    assertThat(files.get(0)).isEqualTo("tag: Tag\nimage: domain/Image");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetPreviousReleaseVersionFromInvalidResponse() throws Exception {
    GitConfig gitConfig = GitConfig.builder().build();
    KubernetesClusterConfig k8sClusterConfig = KubernetesClusterConfig.builder().build();
    doReturn(new DelegateResponseData() {}).when(delegateService).executeTaskV2(any(DelegateTask.class));

    String helmV2ExpectedMessage = "Make sure that the helm client and tiller is installed";
    testGetPreviousReleaseVersionInvalidResponse(HelmVersion.V2, null, mock(SettingValue.class), helmV2ExpectedMessage);
    String helmV3ExpectedMessage = "Make sure Helm 3 is installed";
    testGetPreviousReleaseVersionInvalidResponse(HelmVersion.V3, null, k8sClusterConfig, helmV3ExpectedMessage);
    String gitConfigExpectedMessage = "and delegate has git connectivity";
    testGetPreviousReleaseVersionInvalidResponse(HelmVersion.V3, gitConfig, k8sClusterConfig, gitConfigExpectedMessage);
    String useKubernetesDelegateConfigExpectedMessage = "and correct delegate name is selected in the cloud provider";
    k8sClusterConfig.setUseKubernetesDelegate(true);
    testGetPreviousReleaseVersionInvalidResponse(
        HelmVersion.V3, gitConfig, k8sClusterConfig, useKubernetesDelegateConfigExpectedMessage);
  }

  private void testGetPreviousReleaseVersionInvalidResponse(
      HelmVersion version, GitConfig gitConfig, SettingValue settingValue, String expectedMessage) throws Exception {
    ContainerServiceParams params =
        ContainerServiceParams.builder()
            .settingAttribute(SettingAttribute.Builder.aSettingAttribute().withValue(settingValue).build().toDTO())
            .build();
    assertThatThrownBy(()
                           -> helmDeployState.getPreviousReleaseVersion(context, app, RELEASE_NAME, params, gitConfig,
                               emptyList(), "", version, 0, HelmDeployStateExecutionData.builder(), null, null))
        .hasMessageContaining(expectedMessage);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetSteadyStateTimeout() {
    helmDeployState.setSteadyStateTimeout(999);
    assertThat(helmDeployState.getSteadyStateTimeout()).isEqualTo(999);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateActivityWithBuildOrchestrationWorkflowType() {
    doAnswer(invocation -> invocation.getArgument(0, Activity.class)).when(activityService).save(any(Activity.class));
    on(stateExecutionInstance).set("orchestrationWorkflowType", OrchestrationWorkflowType.BUILD);
    Activity activity = helmDeployState.createActivity(context, emptyList());
    assertThat(activity.getEnvironmentId()).isEqualTo(GLOBAL_ENV_ID);
    assertThat(activity.getEnvironmentName()).isEqualTo(GLOBAL_ENV_ID);
    assertThat(activity.getEnvironmentType()).isEqualTo(EnvironmentType.ALL);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateActivityWithInstanceElementDetails() {
    doAnswer(invocation -> invocation.getArgument(0, Activity.class)).when(activityService).save(any(Activity.class));
    ServiceTemplateElement serviceTemplateElement =
        ServiceTemplateElement.Builder.aServiceTemplateElement()
            .withUuid("templateId")
            .withName("serviceTemplateElement")
            .withServiceElement(ServiceElement.builder().uuid("serviceId").name("serviceName").build())
            .build();
    InstanceElement instanceElement = Builder.anInstanceElement()
                                          .uuid("instanceId")
                                          .serviceTemplateElement(serviceTemplateElement)
                                          .host(HostElement.builder().hostName(HOST_NAME).build())
                                          .build();
    stateExecutionInstance.getContextElements().add(instanceElement);
    Activity activity = helmDeployState.createActivity(context, emptyList());
    assertThat(activity.getServiceTemplateId()).isEqualTo("templateId");
    assertThat(activity.getServiceTemplateName()).isEqualTo("serviceTemplateElement");
    assertThat(activity.getServiceId()).isEqualTo("serviceId");
    assertThat(activity.getServiceName()).isEqualTo("serviceName");
    assertThat(activity.getServiceInstanceId()).isEqualTo("instanceId");
    assertThat(activity.getHostName()).isEqualTo(HOST_NAME);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testSaveHelmReleaseInfoToSweepingOutput() {
    ExecutionContextImpl contextSpy = spy(context);
    final HelmReleaseInfoElement helmReleaseInfoElement = HelmReleaseInfoElement.builder().releaseName("test").build();

    ArgumentCaptor<SweepingOutputInstance> instanceCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    helmDeployState.saveHelmReleaseInfoToSweepingOutput(contextSpy, helmReleaseInfoElement);
    verify(contextSpy).prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.PHASE);
    verify(sweepingOutputService).save(instanceCaptor.capture());
    assertThat(instanceCaptor.getValue().getName()).isEqualTo(HelmReleaseInfoElement.SWEEPING_OUTPUT_NAME);
    assertThat(((HelmReleaseInfoElement) instanceCaptor.getValue().getValue()).getReleaseName()).isEqualTo("test");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteWithHelmCommandFlags() {
    when(serviceResourceService.getHelmChartSpecification(APP_ID, SERVICE_ID))
        .thenReturn(HelmChartSpecification.builder()
                        .chartName(CHART_NAME)
                        .chartUrl(CHART_URL)
                        .chartVersion(CHART_VERSION)
                        .build());
    when(serviceTemplateHelper.fetchServiceTemplateId(any())).thenReturn(SERVICE_TEMPLATE_ID);
    when(applicationManifestService.getAppManifest(any(), any(), any(), any()))
        .thenReturn(ApplicationManifest.builder()
                        .helmCommandFlag(HELM_COMMAND_FLAG)
                        .storeType(HelmChartRepo)
                        .helmChartConfig(HelmChartConfig.builder()
                                             .chartName(CHART_NAME)
                                             .chartUrl(CHART_URL)
                                             .chartVersion(CHART_VERSION)
                                             .build())
                        .build());

    ExecutionContextImpl contextSpy = spy(context);
    doAnswer(t -> t.getArguments()[0]).when(contextSpy).renderExpression(anyString(), any(StateExecutionContext.class));
    ExecutionResponse executionResponse = helmDeployState.execute(contextSpy);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();

    verifyDelegateSelectorInDelegateTaskParams(delegateTask);
    HelmInstallCommandRequest helmInstallCommandRequest =
        (HelmInstallCommandRequest) delegateTask.getData().getParameters()[0];
    Map<HelmSubCommandType, String> flagsValueMap = helmInstallCommandRequest.getHelmCommandFlag().getValueMap();
    assertThat(flagsValueMap)
        .containsKeys(HelmSubCommand.INSTALL.getSubCommandType(), HelmSubCommand.UPGRADE.getSubCommandType());
    assertThat(flagsValueMap).containsValues("--debug2", "--debug2");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testSaveInstanceInfoToSweepingOutputDontSkipVerification() {
    on(helmDeployState).set("sweepingOutputService", sweepingOutputService);
    helmDeployState.saveInstanceInfoToSweepingOutput(context, asList(anInstanceElement().dockerId("dockerId").build()),
        asList(InstanceDetails.builder().hostName("hostName").newInstance(true).build(),
            InstanceDetails.builder().hostName("hostName").newInstance(false).build()));

    ArgumentCaptor<SweepingOutputInstance> argumentCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(sweepingOutputService, times(1)).save(argumentCaptor.capture());

    InstanceInfoVariables instanceInfoVariables = (InstanceInfoVariables) argumentCaptor.getValue().getValue();
    assertThat(instanceInfoVariables.isSkipVerification()).isEqualTo(false);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testSaveInstanceInfoToSweepingOutputSkipVerification() {
    on(helmDeployState).set("sweepingOutputService", sweepingOutputService);
    helmDeployState.saveInstanceInfoToSweepingOutput(context, asList(anInstanceElement().dockerId("dockerId").build()),
        asList(InstanceDetails.builder().hostName("hostName").newInstance(false).build(),
            InstanceDetails.builder().hostName("hostName").newInstance(false).build()));

    ArgumentCaptor<SweepingOutputInstance> argumentCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(sweepingOutputService, times(1)).save(argumentCaptor.capture());

    InstanceInfoVariables instanceInfoVariables = (InstanceInfoVariables) argumentCaptor.getValue().getValue();
    assertThat(instanceInfoVariables.isSkipVerification()).isEqualTo(true);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteCustomManifestFetchTask() throws InterruptedException {
    CustomManifestValuesFetchParams mockParams =
        CustomManifestValuesFetchParams.builder()
            .fetchFilesList(singletonList(CustomManifestFetchConfig.builder().key("value").build()))
            .build();
    CustomSourceConfig customSourceConfig = CustomSourceConfig.builder()
                                                .script("test script")
                                                .delegateSelectors(singletonList("delegate1"))
                                                .path("path/manifest")
                                                .build();
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = ImmutableMap.of(K8sValuesLocation.Service,
        ApplicationManifest.builder().customSourceConfig(customSourceConfig).storeType(CUSTOM).build());
    DirectKubernetesInfrastructureMapping infrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder().build();
    infrastructureMapping.setUuid(INFRA_MAPPING_ID);
    String serviceTemplateId = "serviceTemplateId";

    doReturn(true).when(featureFlagService).isEnabled(FeatureName.CUSTOM_MANIFEST, null);
    doReturn(true).when(applicationManifestUtils).isCustomManifest(context);
    doReturn(singleton("rendered-delegate1"))
        .when(k8sStateHelper)
        .getRenderedAndTrimmedSelectors(context, customSourceConfig.getDelegateSelectors());
    doReturn(infrastructureMapping).when(k8sStateHelper).fetchContainerInfrastructureMapping(context);
    doReturn(Activity.builder().uuid(ACTIVITY_ID).build()).when(activityService).save(any(Activity.class));
    doReturn(appManifestMap).when(applicationManifestUtils).getApplicationManifests(context, AppManifestKind.VALUES);
    doReturn(mockParams)
        .when(applicationManifestUtils)
        .createCustomManifestValuesFetchParams(context, appManifestMap, VALUES_YAML_KEY);
    doReturn(serviceTemplateId).when(serviceTemplateHelper).fetchServiceTemplateId(infrastructureMapping);

    helmDeployState.executeInternal(context);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(1)).queueTaskV2(captor.capture());
    DelegateTask queuedTask = captor.getValue();
    assertThat(queuedTask.isSelectionLogsTrackingEnabled())
        .isEqualTo(helmDeployState.isSelectionLogsTrackingForTasksEnabled());
    assertThat(queuedTask.getSetupAbstractions().get(Cd1SetupFields.SERVICE_TEMPLATE_ID_FIELD))
        .isEqualTo(serviceTemplateId);
    assertThat(queuedTask.getData()).isNotNull();
    assertThat(queuedTask.getData().getParameters()).isNotEmpty();

    CustomManifestValuesFetchParams parameter =
        (CustomManifestValuesFetchParams) queuedTask.getData().getParameters()[0];
    assertThat(parameter.getDelegateSelectors()).contains("rendered-delegate1");
    assertThat(parameter.getCommandUnitName()).isEqualTo("Fetch Files");
    assertThat(parameter.getFetchFilesList()).hasSize(1);
    assertThat(parameter.getFetchFilesList().get(0).getKey()).isEqualTo("value");
    assertThat(parameter.getCustomManifestSource().getScript()).isEqualTo("test script");
    assertThat(parameter.getCustomManifestSource().getFilePaths()).contains("path/manifest");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteCustomManifestFetchTaskFfOffFail() throws InterruptedException {
    doReturn(false).when(featureFlagService).isEnabled(FeatureName.CUSTOM_MANIFEST, null);
    doReturn(true).when(applicationManifestUtils).isCustomManifest(context);
    assertThatThrownBy(() -> helmDeployState.executeInternal(context))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Custom manifest can not be used with feature flag off");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteCustomManifestFetchTaskNoScriptFail() throws InterruptedException {
    CustomSourceConfig customSourceConfig = CustomSourceConfig.builder().build();
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = ImmutableMap.of(K8sValuesLocation.Service,
        ApplicationManifest.builder().customSourceConfig(customSourceConfig).storeType(CUSTOM).build());

    doReturn(true).when(featureFlagService).isEnabled(FeatureName.CUSTOM_MANIFEST, null);
    doReturn(true).when(applicationManifestUtils).isCustomManifest(context);
    doReturn(appManifestMap).when(applicationManifestUtils).getApplicationManifests(context, AppManifestKind.VALUES);

    assertThatThrownBy(() -> helmDeployState.executeInternal(context))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Script can not be null for custom manifest source");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForCustomManifestFetchTask() throws InterruptedException {
    HelmDeployState spyDeployState = spy(helmDeployState);

    HelmDeployStateExecutionData stateExecutionData = (HelmDeployStateExecutionData) context.getStateExecutionData();
    stateExecutionData.setCurrentTaskType(TaskType.CUSTOM_MANIFEST_FETCH_TASK);
    ApplicationManifest appManifest =
        ApplicationManifest.builder()
            .pollForChanges(true)
            .storeType(CUSTOM)
            .customSourceConfig(CustomSourceConfig.builder().path("path").script("test script").build())
            .build();
    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = singletonMap(K8sValuesLocation.Service, appManifest);
    CustomManifestValuesFetchResponse successfulFetchResponse =
        CustomManifestValuesFetchResponse.builder()
            .zippedManifestFileId("fileId")
            .valuesFilesContentMap(singletonMap("ServiceOverride", singletonList(CustomSourceFile.builder().build())))
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    stateExecutionData.setAppManifestMap(appManifestMap);
    Map<String, ResponseData> responseDataMap = ImmutableMap.of(ACTIVITY_ID, successfulFetchResponse);
    Map<K8sValuesLocation, Collection<String>> valuesMap =
        singletonMap(K8sValuesLocation.ServiceOverride, singletonList("values"));

    doReturn(appManifestMap)
        .when(applicationManifestUtils)
        .getOverrideApplicationManifests(context, AppManifestKind.VALUES);
    doReturn(appManifest)
        .when(applicationManifestService)
        .getAppManifest(app.getUuid(), null, serviceElement.getUuid(), AppManifestKind.K8S_MANIFEST);
    doReturn(true).when(featureFlagService).isEnabled(FeatureName.CUSTOM_MANIFEST, null);
    doReturn(valuesMap)
        .when(applicationManifestUtils)
        .getValuesFilesFromCustomFetchValuesResponse(context, appManifestMap, successfulFetchResponse, VALUES_YAML_KEY);
    ArgumentCaptor<DelegateTask> delegateTaskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    doReturn("taskId").when(delegateService).queueTaskV2(delegateTaskCaptor.capture());
    doReturn(false).when(applicationManifestUtils).isValuesInGit(appManifestMap);

    spyDeployState.handleAsyncResponse(context, responseDataMap);

    verify(spyDeployState, times(1)).executeHelmTask(any(ExecutionContext.class), any(), eq(appManifestMap), anyMap());
    assertThat(stateExecutionData.getValuesFiles()).isEqualTo(valuesMap);
    assertThat(stateExecutionData.getZippedManifestFileId()).isEqualTo("fileId");

    DelegateTask task = delegateTaskCaptor.getValue();
    assertThat(task.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(APP_ID);
    assertThat(task.getSetupAbstractions().get(Cd1SetupFields.ENV_ID_FIELD)).isEqualTo(ENV_ID);
    assertThat(task.getSetupAbstractions().get(Cd1SetupFields.ENV_TYPE_FIELD)).isEqualTo(ENV_PROD_FIELD);
    assertThat(task.getData().getTaskType()).isEqualTo(TaskType.HELM_COMMAND_TASK.name());
    assertThat(task.getData().isAsync()).isTrue();
    assertThat(task.getData().getTimeout()).isEqualTo(TimeUnit.MINUTES.toMillis(10));
    HelmCommandRequest helmCommandRequest = (HelmCommandRequest) task.getData().getParameters()[0];
    assertThat(helmCommandRequest.getRepoConfig().getCustomManifestSource().getFilePaths())
        .isEqualTo(singletonList("path"));
    assertThat(helmCommandRequest.getRepoConfig().getCustomManifestSource().getScript()).isEqualTo("test script");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseCustomManifestFetchTaskFail() throws InterruptedException {
    HelmDeployState spyDeployState = spy(helmDeployState);
    CustomManifestValuesFetchResponse failedValuesFetchResponse =
        CustomManifestValuesFetchResponse.builder().commandExecutionStatus(FAILURE).build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put(ACTIVITY_ID, failedValuesFetchResponse);
    HelmDeployStateExecutionData stateExecutionData = (HelmDeployStateExecutionData) context.getStateExecutionData();
    stateExecutionData.setCurrentTaskType(TaskType.CUSTOM_MANIFEST_FETCH_TASK);
    stateExecutionData.setActivityId(ACTIVITY_ID);

    doReturn(true).when(featureFlagService).isEnabled(FeatureName.CUSTOM_MANIFEST, null);

    ExecutionResponse executionResponse = spyDeployState.handleAsyncResponse(context, responseDataMap);

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    verify(activityService, times(1)).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.FAILED);
  }
}
