/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.pcf.ResizeStrategy.RESIZE_NEW_FIRST;
import static io.harness.rule.OwnerRule.BRETT;

import static software.wings.api.CommandStateExecutionData.Builder.aCommandStateExecutionData;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.GcpKubernetesInfrastructureMapping.Builder.aGcpKubernetesInfrastructureMapping;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.KubernetesSetupParams.KubernetesSetupParamsBuilder.aKubernetesSetupParams;
import static software.wings.beans.command.ServiceCommand.Builder.aServiceCommand;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.service.intfc.ServiceTemplateService.EncryptedFieldComputeMode.MASKED;
import static software.wings.service.intfc.ServiceTemplateService.EncryptedFieldComputeMode.OBTAIN_VALUE;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.WorkflowStandardParams.Builder.aWorkflowStandardParams;
import static software.wings.sm.states.ContainerServiceSetup.DEFAULT_MAX;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.CLUSTER_NAME;
import static software.wings.utils.WingsTestConstants.COMPUTE_PROVIDER_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.STATE_NAME;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.expression.VariableResolverTracker;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.model.ImageDetails;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.ContainerServiceElement;
import software.wings.api.ContainerServiceElement.ContainerServiceElementBuilder;
import software.wings.api.ContextElementParamMapperFactory;
import software.wings.api.DeploymentType;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.DockerConfig;
import software.wings.beans.Environment;
import software.wings.beans.InfraMappingSweepingOutput;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.beans.command.CommandExecutionContext;
import software.wings.beans.command.CommandType;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData;
import software.wings.beans.command.ContainerSetupParams;
import software.wings.beans.command.KubernetesSetupParams;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.container.ContainerDefinition;
import software.wings.beans.container.KubernetesContainerTask;
import software.wings.common.InfrastructureConstants;
import software.wings.common.VariableProcessor;
import software.wings.delegatetasks.aws.AwsCommandHelper;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.container.ContainerMasterUrlHelper;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.infra.GoogleKubernetesEngine;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import dev.morphia.Key;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class KubernetesSetupTest extends WingsBaseTest {
  private static final String KUBERNETES_CONTROLLER_NAME = "kubernetes-rc-name.1";
  private static final String BASE_URL = "https://env.harness.io/";
  private static final String PHASE_NAME = "phaseName";
  private static final String MASTER_URL = "http://example.com";

  @Mock private SettingsService settingsService;
  @Mock private DelegateService delegateService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private ActivityService activityService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private InfrastructureDefinitionService infrastructureDefinitionService;
  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;
  @Mock private ServiceTemplateService serviceTemplateService;
  @Mock private SecretManager secretManager;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private ArtifactService artifactService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Mock private VariableProcessor variableProcessor;
  @Mock private ManagerExpressionEvaluator evaluator;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private AwsCommandHelper mockAwsCommandHelper;
  @Mock private ArtifactCollectionUtils artifactCollectionUtils;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Mock private ContainerMasterUrlHelper containerMasterUrlHelper;
  @Mock private ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @Mock private StateExecutionService stateExecutionService;

  // gets initialized in setup.
  private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;
  private ContextElementParamMapperFactory contextElementParamMapperFactory;

  @InjectMocks private KubernetesSetup kubernetesSetup = new KubernetesSetup("name");

  @Mock private MainConfiguration configuration;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;

  private ExecutionContext context;

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
                                          .appId(APP_ID)
                                          .deploymentType(DeploymentType.KUBERNETES.name())
                                          .workflowExecutionId(WORKFLOW_EXECUTION_ID)
                                          .phaseName(PHASE_NAME)
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
                                 .name(KUBERNETES_CONTROLLER_NAME)
                                 .resizeStrategy(RESIZE_NEW_FIRST)
                                 .infraMappingId(INFRA_MAPPING_ID)
                                 .deploymentType(DeploymentType.KUBERNETES)
                                 .build())
          .addStateExecutionData(aCommandStateExecutionData().build())
          .build();

  private Application app = anApplication().uuid(APP_ID).name(APP_NAME).build();
  private Environment env = anEnvironment().appId(APP_ID).uuid(ENV_ID).name(ENV_NAME).build();
  private Service service = Service.builder()
                                .appId(APP_ID)
                                .uuid(SERVICE_ID)
                                .name(SERVICE_NAME)
                                .artifactStreamIds(singletonList(ARTIFACT_STREAM_ID))
                                .build();
  private Artifact artifact =
      anArtifact()
          .withArtifactSourceName("source")
          .withMetadata(new ArtifactMetadata(ImmutableMap.of(ArtifactMetadataKeys.buildNo, "bn")))
          .withArtifactStreamId(ARTIFACT_STREAM_ID)
          .build();
  private ArtifactStream artifactStream = DockerArtifactStream.builder().appId(APP_ID).imageName("imageName").build();

  private SettingAttribute dockerConfig = aSettingAttribute()
                                              .withValue(DockerConfig.builder()
                                                             .dockerRegistryUrl("url")
                                                             .password("pass".toCharArray())
                                                             .username("user")
                                                             .accountId(ACCOUNT_ID)
                                                             .build())
                                              .build();

  private List<ServiceVariable> serviceVariableList = asList(
      ServiceVariable.builder().type(ServiceVariableType.TEXT).name("VAR_1").value("value1".toCharArray()).build(),
      ServiceVariable.builder()
          .type(ServiceVariableType.ENCRYPTED_TEXT)
          .name("VAR_2")
          .value("value2".toCharArray())
          .build());

  private List<ServiceVariable> safeDisplayServiceVariableList = asList(
      ServiceVariable.builder().type(ServiceVariableType.TEXT).name("VAR_1").value("value1".toCharArray()).build(),
      ServiceVariable.builder()
          .type(ServiceVariableType.ENCRYPTED_TEXT)
          .name("VAR_2")
          .value("*******".toCharArray())
          .build());

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

  @Before
  public void setup() throws IllegalAccessException {
    when(appService.get(APP_ID)).thenReturn(app);
    when(appService.getApplicationWithDefaults(APP_ID)).thenReturn(app);
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(service);
    when(serviceResourceService.get(SERVICE_ID)).thenReturn(service);
    when(artifactStreamServiceBindingService.listArtifactStreamIds(APP_ID, SERVICE_ID))
        .thenReturn(singletonList(ARTIFACT_STREAM_ID));
    when(artifactStreamServiceBindingService.listArtifactStreamIds(SERVICE_ID))
        .thenReturn(singletonList(ARTIFACT_STREAM_ID));
    when(environmentService.get(APP_ID, ENV_ID, false)).thenReturn(env);
    when(sweepingOutputService.find(any())).thenReturn(sweepingOutputInstance);

    ServiceCommand serviceCommand =
        aServiceCommand()
            .withCommand(aCommand().withCommandType(CommandType.SETUP).withName("Setup Replication Controller").build())
            .build();
    when(serviceResourceService.getCommandByName(APP_ID, SERVICE_ID, ENV_ID, "Setup Replication Controller"))
        .thenReturn(serviceCommand);

    KubernetesContainerTask kubernetesContainerTask = new KubernetesContainerTask();
    ContainerDefinition containerDefinition = ContainerDefinition.builder().memory(256).cpu(1d).build();
    kubernetesContainerTask.setContainerDefinitions(Lists.newArrayList(containerDefinition));
    when(serviceResourceService.getContainerTaskByDeploymentType(APP_ID, SERVICE_ID, DeploymentType.KUBERNETES.name()))
        .thenReturn(kubernetesContainerTask);

    workflowStandardParamsExtensionService = new WorkflowStandardParamsExtensionService(appService, null,
        artifactService, environmentService, artifactStreamServiceBindingService, null, featureFlagService);
    on(kubernetesSetup).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);

    contextElementParamMapperFactory =
        new ContextElementParamMapperFactory(subdomainUrlHelper, workflowExecutionService, artifactService,
            artifactStreamService, null, featureFlagService, null, workflowStandardParamsExtensionService);

    when(artifactService.get(any())).thenReturn(artifact);
    when(artifactStreamService.get(any())).thenReturn(artifactStream);

    InfrastructureMapping infrastructureMapping = aGcpKubernetesInfrastructureMapping()
                                                      .withUuid(generateUuid())
                                                      .withClusterName(CLUSTER_NAME)
                                                      .withComputeProviderSettingId(COMPUTE_PROVIDER_ID)
                                                      .build();

    infrastructureMapping.setInfrastructureDefinitionId(INFRA_DEFINITION_ID);

    InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder()
            .uuid(INFRA_DEFINITION_ID)
            .deploymentType(DeploymentType.KUBERNETES)
            .infrastructure(
                GoogleKubernetesEngine.builder().cloudProviderId(COMPUTE_PROVIDER_ID).clusterName(CLUSTER_NAME).build())
            .build();

    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(infrastructureMapping);
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(infrastructureDefinition);

    Activity activity = Activity.builder().build();
    activity.setUuid(ACTIVITY_ID);
    when(activityService.save(any(Activity.class))).thenReturn(activity);

    when(settingsService.get(any())).thenReturn(dockerConfig);

    when(serviceTemplateService.getTemplateRefKeysByService(APP_ID, SERVICE_ID, ENV_ID))
        .thenReturn(singletonList(new Key<>(ServiceTemplate.class, "serviceTemplate", TEMPLATE_ID)));

    when(serviceTemplateService.get(APP_ID, TEMPLATE_ID)).thenReturn(aServiceTemplate().withUuid(TEMPLATE_ID).build());
    when(serviceTemplateService.computeServiceVariables(APP_ID, ENV_ID, TEMPLATE_ID, null, OBTAIN_VALUE))
        .thenReturn(serviceVariableList);
    when(serviceTemplateService.computeServiceVariables(APP_ID, ENV_ID, TEMPLATE_ID, null, MASKED))
        .thenReturn(safeDisplayServiceVariableList);
    when(secretManager.getEncryptionDetails(any(), anyString(), anyString())).thenReturn(Collections.emptyList());
    FieldUtils.writeField(kubernetesSetup, "secretManager", secretManager, true);
    when(workflowExecutionService.getExecutionDetails(anyString(), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(WorkflowExecution.builder().build());
    context = new ExecutionContextImpl(stateExecutionInstance);
    on(context).set("infrastructureMappingService", infrastructureMappingService);
    on(context).set("infrastructureDefinitionService", infrastructureDefinitionService);
    on(context).set("serviceResourceService", serviceResourceService);
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("sweepingOutputService", sweepingOutputService);
    on(context).set("evaluator", evaluator);
    on(context).set("settingsService", settingsService);
    on(context).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(context).set("contextElementParamMapperFactory", contextElementParamMapperFactory);
    when(variableProcessor.getVariables(any(), any())).thenReturn(emptyMap());
    when(evaluator.substitute(anyString(), anyMap(), any(VariableResolverTracker.class), anyString()))
        .thenAnswer(i -> i.getArguments()[0]);
    PortalConfig portalConfig = new PortalConfig();
    portalConfig.setUrl(BASE_URL);
    when(configuration.getPortal()).thenReturn(portalConfig);
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    doReturn(null).when(mockAwsCommandHelper).getAwsConfigTagsFromContext(any());
    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn("baseUrl");
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(anyString(), any());
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldExecute() {
    on(context).set("serviceTemplateService", serviceTemplateService);
    on(context).set("featureFlagService", featureFlagService);
    on(kubernetesSetup).set("containerDeploymentManagerHelper", containerDeploymentManagerHelper);

    when(artifactCollectionUtils.fetchContainerImageDetails(artifact, context.getWorkflowExecutionId()))
        .thenReturn(ImageDetails.builder().name(artifactStream.getSourceName()).tag(artifact.getBuildNo()).build());
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.KUBERNETES);

    when(containerDeploymentManagerHelper.getContainerServiceParams(any(), any(), any()))
        .thenReturn(ContainerServiceParams.builder().build());
    when(containerMasterUrlHelper.fetchMasterUrl(
             any(ContainerServiceParams.class), any(ContainerInfrastructureMapping.class)))
        .thenReturn(MASTER_URL);

    kubernetesSetup.execute(context);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();

    CommandExecutionContext executionContext = (CommandExecutionContext) delegateTask.getData().getParameters()[1];

    Map<String, String> serviceVariables = executionContext.getServiceVariables();
    assertThat(serviceVariables.size()).isEqualTo(2);
    assertThat(serviceVariables.get("VAR_1")).isEqualTo("value1");
    assertThat(serviceVariables.get("VAR_2")).isEqualTo("value2");

    Map<String, String> safeDisplayServiceVariables = executionContext.getSafeDisplayServiceVariables();
    assertThat(safeDisplayServiceVariables.size()).isEqualTo(2);
    assertThat(safeDisplayServiceVariables.get("VAR_1")).isEqualTo("value1");
    assertThat(safeDisplayServiceVariables.get("VAR_2")).isEqualTo("*******");

    ContainerSetupParams params = executionContext.getContainerSetupParams();
    assertThat(params.getAppName()).isEqualTo(APP_NAME);
    assertThat(params.getEnvName()).isEqualTo(ENV_NAME);
    assertThat(params.getServiceName()).isEqualTo(SERVICE_NAME);
    assertThat(params.getClusterName()).isEqualTo(CLUSTER_NAME);
    assertThat(((KubernetesSetupParams) params).getMasterUrl()).isEqualTo(MASTER_URL);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldTrimIngressYaml() {
    kubernetesSetup.setIngressYaml(null);
    assertThat(kubernetesSetup.getIngressYaml()).isNull();

    kubernetesSetup.setIngressYaml("one line");
    assertThat(kubernetesSetup.getIngressYaml()).isEqualTo("one line");

    kubernetesSetup.setIngressYaml("a\nb");
    assertThat(kubernetesSetup.getIngressYaml()).isEqualTo("a\nb");

    kubernetesSetup.setIngressYaml("a \nb");
    assertThat(kubernetesSetup.getIngressYaml()).isEqualTo("a\nb");

    kubernetesSetup.setIngressYaml("a    \n b   \n  c");
    assertThat(kubernetesSetup.getIngressYaml()).isEqualTo("a\n b\n  c");
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldTrimCustomMetricYaml() {
    kubernetesSetup.setCustomMetricYamlConfig(null);
    assertThat(kubernetesSetup.getCustomMetricYamlConfig()).isNull();

    kubernetesSetup.setCustomMetricYamlConfig("one line");
    assertThat(kubernetesSetup.getCustomMetricYamlConfig()).isEqualTo("one line");

    kubernetesSetup.setCustomMetricYamlConfig("a\nb");
    assertThat(kubernetesSetup.getCustomMetricYamlConfig()).isEqualTo("a\nb");

    kubernetesSetup.setCustomMetricYamlConfig("a \nb");
    assertThat(kubernetesSetup.getCustomMetricYamlConfig()).isEqualTo("a\nb");

    kubernetesSetup.setCustomMetricYamlConfig("a    \n b   \n  c");
    assertThat(kubernetesSetup.getCustomMetricYamlConfig()).isEqualTo("a\n b\n  c");
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldBuildContainerServiceElement() {
    ContainerServiceElement containerServiceElement = buildContainerServiceElement("10", "5", 0);

    assertThat(containerServiceElement.getName()).isEqualTo(KUBERNETES_CONTROLLER_NAME);
    assertThat(containerServiceElement.getMaxInstances()).isEqualTo(10);
    assertThat(containerServiceElement.getFixedInstances()).isEqualTo(5);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldBuildContainerServiceElementEmptyValues() {
    ContainerServiceElement containerServiceElement = buildContainerServiceElement(null, null, 0);

    assertThat(containerServiceElement.getName()).isEqualTo(KUBERNETES_CONTROLLER_NAME);
    assertThat(containerServiceElement.getMaxInstances()).isEqualTo(DEFAULT_MAX);
    assertThat(containerServiceElement.getFixedInstances()).isEqualTo(DEFAULT_MAX);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldBuildContainerServiceElementEmptyValuesEmptyFixed() {
    ContainerServiceElement containerServiceElement = buildContainerServiceElement("10", null, 0);

    assertThat(containerServiceElement.getName()).isEqualTo(KUBERNETES_CONTROLLER_NAME);
    assertThat(containerServiceElement.getMaxInstances()).isEqualTo(10);
    assertThat(containerServiceElement.getFixedInstances()).isEqualTo(10);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldBuildContainerServiceElementZero() {
    ContainerServiceElement containerServiceElement = buildContainerServiceElement("0", "0", 0);

    assertThat(containerServiceElement.getName()).isEqualTo(KUBERNETES_CONTROLLER_NAME);
    assertThat(containerServiceElement.getMaxInstances()).isEqualTo(DEFAULT_MAX);
    assertThat(containerServiceElement.getFixedInstances()).isEqualTo(DEFAULT_MAX);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldBuildContainerServiceElementMoreActive() {
    ContainerServiceElement containerServiceElement = buildContainerServiceElement("5", "5", 10);

    assertThat(containerServiceElement.getName()).isEqualTo(KUBERNETES_CONTROLLER_NAME);
    assertThat(containerServiceElement.getMaxInstances()).isEqualTo(10);
    assertThat(containerServiceElement.getFixedInstances()).isEqualTo(5);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldBuildContainerServiceElementFewerActive() {
    ContainerServiceElement containerServiceElement = buildContainerServiceElement("5", "5", 3);

    assertThat(containerServiceElement.getName()).isEqualTo(KUBERNETES_CONTROLLER_NAME);
    assertThat(containerServiceElement.getMaxInstances()).isEqualTo(3);
    assertThat(containerServiceElement.getFixedInstances()).isEqualTo(5);
  }

  private ContainerServiceElement buildContainerServiceElement(
      String maxInstances, String fixedInstances, int activeServiceCount) {
    KubernetesSetupParams setupParams = aKubernetesSetupParams().build();
    ContainerServiceElementBuilder serviceElementBuilder = ContainerServiceElement.builder()
                                                               .uuid(serviceElement.getUuid())
                                                               .clusterName(CLUSTER_NAME)
                                                               .namespace("default")
                                                               .name(KUBERNETES_CONTROLLER_NAME)
                                                               .resizeStrategy(RESIZE_NEW_FIRST)
                                                               .infraMappingId(INFRA_MAPPING_ID)
                                                               .deploymentType(DeploymentType.KUBERNETES);
    if (maxInstances != null) {
      serviceElementBuilder.maxInstances(Integer.valueOf(maxInstances));
    }
    if (fixedInstances != null) {
      serviceElementBuilder.maxInstances(Integer.valueOf(fixedInstances));
    }
    StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance()
            .displayName(STATE_NAME)
            .addContextElement(workflowStandardParams)
            .addContextElement(phaseElement)
            .addContextElement(serviceElementBuilder.build())
            .addStateExecutionData(aCommandStateExecutionData().withContainerSetupParams(setupParams).build())
            .build();
    ExecutionContext context = new ExecutionContextImpl(stateExecutionInstance);
    on(context).set("infrastructureMappingService", infrastructureMappingService);
    on(context).set("infrastructureDefinitionService", infrastructureDefinitionService);
    on(context).set("serviceResourceService", serviceResourceService);
    on(context).set("sweepingOutputService", sweepingOutputService);
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("evaluator", evaluator);
    on(context).set("featureFlagService", featureFlagService);
    on(context).set("settingsService", settingsService);
    on(context).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(context).set("contextElementParamMapperFactory", contextElementParamMapperFactory);

    CommandExecutionResult result =
        CommandExecutionResult.builder()
            .commandExecutionData(ContainerSetupCommandUnitExecutionData.builder()
                                      .containerServiceName(KUBERNETES_CONTROLLER_NAME)
                                      .activeServiceCounts(ImmutableList.of(
                                          new String[] {"old-service", Integer.toString(activeServiceCount)}))
                                      .build())
            .build();

    kubernetesSetup.setMaxInstances(maxInstances);
    kubernetesSetup.setFixedInstances(fixedInstances);
    return kubernetesSetup.buildContainerServiceElement(
        context, result, ExecutionStatus.SUCCESS, ImageDetails.builder().name("foo").tag("43").build());
  }
}
