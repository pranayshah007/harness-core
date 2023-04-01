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
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.BRETT;

import static software.wings.api.CommandStateExecutionData.Builder.aCommandStateExecutionData;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.GcpKubernetesInfrastructureMapping.Builder.aGcpKubernetesInfrastructureMapping;
import static software.wings.beans.InstanceUnitType.COUNT;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.ServiceCommand.Builder.aServiceCommand;
import static software.wings.beans.container.Label.Builder.aLabel;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.service.intfc.ServiceTemplateService.EncryptedFieldComputeMode.OBTAIN_VALUE;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.WorkflowStandardParams.Builder.aWorkflowStandardParams;
import static software.wings.sm.states.KubernetesDeploy.KubernetesDeployBuilder.aKubernetesDeploy;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.CLUSTER_NAME;
import static software.wings.utils.WingsTestConstants.COMMAND_NAME;
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
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.expression.VariableResolverTracker;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.ContainerServiceElement;
import software.wings.api.ContextElementParamMapperFactory;
import software.wings.api.DeploymentType;
import software.wings.api.PhaseElement;
import software.wings.api.PhaseStepExecutionData;
import software.wings.api.ServiceElement;
import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.GcpConfig;
import software.wings.beans.InfraMappingSweepingOutput;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.command.CommandExecutionContext;
import software.wings.beans.command.CommandType;
import software.wings.beans.command.KubernetesResizeParams;
import software.wings.beans.command.ServiceCommand;
import software.wings.common.InfrastructureConstants;
import software.wings.common.VariableProcessor;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.delegatetasks.aws.AwsCommandHelper;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.container.ContainerMasterUrlHelper;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.infra.GoogleKubernetesEngine;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.ContainerService;
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
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.google.common.collect.Lists;
import dev.morphia.Key;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by brett on 3/10/17
 */
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class KubernetesDeployTest extends WingsBaseTest {
  private static final String KUBERNETES_CONTROLLER_NAME = "kubernetes-rc-name.1";
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
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private MainConfiguration configuration;
  @Mock private PortalConfig portalConfig;
  @Mock private ArtifactService artifactService;
  @Mock private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Mock private VariableProcessor variableProcessor;
  @Mock private ManagerExpressionEvaluator evaluator;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private AwsCommandHelper mockAwsCommandHelper;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Mock private ContainerMasterUrlHelper containerMasterUrlHelper;
  @Mock private ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;

  @InjectMocks
  private KubernetesDeploy kubernetesDeploy = aKubernetesDeploy(STATE_NAME)
                                                  .withCommandName(COMMAND_NAME)
                                                  .withInstanceCount("1")
                                                  .withInstanceUnitType(COUNT)
                                                  .build();
  @InjectMocks KubernetesDeployRollback kubernetesDeployRollback = new KubernetesDeployRollback(STATE_NAME);

  @Mock private ContainerService containerService;

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
                                 .lookupLabels(asList(aLabel().withName("foo").withValue("bar").build()))
                                 .build())
          .addStateExecutionData(new PhaseStepExecutionData())
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
  private Environment env = anEnvironment().appId(APP_ID).uuid(ENV_ID).name(ENV_NAME).build();
  private Service service = Service.builder().appId(APP_ID).uuid(SERVICE_ID).name(SERVICE_NAME).build();
  private SettingAttribute computeProvider =
      aSettingAttribute()
          .withValue(GcpConfig.builder().serviceAccountKeyFileContent("keyFileContent".toCharArray()).build())
          .build();
  private ExecutionContextImpl context;

  /**
   * Set up.
   */
  @Before
  public void setup() throws IllegalAccessException {
    when(appService.get(APP_ID)).thenReturn(app);
    when(appService.getApplicationWithDefaults(APP_ID)).thenReturn(app);
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(service);
    when(artifactStreamServiceBindingService.listArtifactStreamIds(SERVICE_ID)).thenReturn(new ArrayList<>());
    when(environmentService.get(APP_ID, ENV_ID, false)).thenReturn(env);
    when(sweepingOutputService.find(any())).thenReturn(sweepingOutputInstance);

    ServiceCommand serviceCommand =
        aServiceCommand()
            .withCommand(aCommand().withCommandType(CommandType.RESIZE).withName(COMMAND_NAME).build())
            .build();
    when(serviceResourceService.getCommandByName(APP_ID, SERVICE_ID, ENV_ID, COMMAND_NAME)).thenReturn(serviceCommand);

    WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService =
        new WorkflowStandardParamsExtensionService(appService, null, artifactService, environmentService,
            artifactStreamServiceBindingService, null, featureFlagService);
    on(kubernetesDeploy).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(kubernetesDeployRollback).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);

    ContextElementParamMapperFactory contextElementParamMapperFactory =
        new ContextElementParamMapperFactory(subdomainUrlHelper, workflowExecutionService, artifactService, null, null,
            featureFlagService, null, workflowStandardParamsExtensionService);

    InfrastructureMapping infrastructureMapping = aGcpKubernetesInfrastructureMapping()
                                                      .withUuid(INFRA_MAPPING_ID)
                                                      .withClusterName(CLUSTER_NAME)
                                                      .withComputeProviderSettingId(COMPUTE_PROVIDER_ID)
                                                      .withDeploymentType(DeploymentType.KUBERNETES.name())
                                                      .build();
    infrastructureMapping.setInfrastructureDefinitionId(INFRA_DEFINITION_ID);

    InfrastructureDefinition infrastructureDefinition =
        InfrastructureDefinition.builder()
            .deploymentType(DeploymentType.HELM)
            .uuid(INFRA_DEFINITION_ID)
            .infrastructure(
                GoogleKubernetesEngine.builder().cloudProviderId(COMPUTE_PROVIDER_ID).clusterName(CLUSTER_NAME).build())
            .build();

    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID)).thenReturn(infrastructureMapping);
    when(infrastructureDefinitionService.get(APP_ID, INFRA_DEFINITION_ID)).thenReturn(infrastructureDefinition);

    Activity activity = Activity.builder().build();
    activity.setUuid(ACTIVITY_ID);
    when(activityService.save(any(Activity.class))).thenReturn(activity);

    when(settingsService.get(COMPUTE_PROVIDER_ID)).thenReturn(computeProvider);

    when(serviceTemplateService.getTemplateRefKeysByService(APP_ID, SERVICE_ID, ENV_ID))
        .thenReturn(singletonList(new Key<>(ServiceTemplate.class, "serviceTemplate", TEMPLATE_ID)));

    when(serviceTemplateService.get(APP_ID, TEMPLATE_ID)).thenReturn(aServiceTemplate().withUuid(TEMPLATE_ID).build());
    when(serviceTemplateService.computeServiceVariables(APP_ID, ENV_ID, TEMPLATE_ID, null, OBTAIN_VALUE))
        .thenReturn(emptyList());
    when(secretManager.getEncryptionDetails(any(), anyString(), anyString())).thenReturn(Collections.emptyList());
    FieldUtils.writeField(kubernetesDeploy, "secretManager", secretManager, true);
    when(workflowExecutionService.getExecutionDetails(anyString(), anyString(), anyBoolean(), anyBoolean()))
        .thenReturn(WorkflowExecution.builder().build());

    context = new ExecutionContextImpl(stateExecutionInstance);
    on(context).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    on(context).set("contextElementParamMapperFactory", contextElementParamMapperFactory);

    when(delegateProxyFactory.getV2(eq(ContainerService.class), any(SyncTaskContext.class)))
        .thenReturn(containerService);
    when(configuration.getPortal()).thenReturn(portalConfig);
    when(portalConfig.getUrl()).thenReturn("http://www.url.com");
    when(artifactService.get(any())).thenReturn(anArtifact().build());
    when(variableProcessor.getVariables(any(), any())).thenReturn(emptyMap());
    when(evaluator.substitute(anyString(), anyMap(), any(VariableResolverTracker.class), anyString()))
        .thenAnswer(i -> i.getArguments()[0]);
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    doReturn(null).when(mockAwsCommandHelper).getAwsConfigTagsFromContext(any());
    when(subdomainUrlHelper.getPortalBaseUrl(any())).thenReturn("baseUrl");
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(anyString(), any());

    kubernetesDeployRollback.setCommandName(COMMAND_NAME);
    kubernetesDeployRollback.setRollback(true);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldExecute() {
    on(context).set("sweepingOutputService", sweepingOutputService);
    on(context).set("infrastructureMappingService", infrastructureMappingService);
    on(context).set("infrastructureDefinitionService", infrastructureDefinitionService);
    on(context).set("serviceResourceService", serviceResourceService);
    on(context).set("serviceTemplateService", serviceTemplateService);
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("evaluator", evaluator);
    on(context).set("featureFlagService", featureFlagService);
    on(context).set("settingsService", settingsService);
    on(kubernetesDeploy).set("containerDeploymentManagerHelper", containerDeploymentManagerHelper);

    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.KUBERNETES);
    when(containerDeploymentManagerHelper.getContainerServiceParams(any(), any(), any()))
        .thenReturn(ContainerServiceParams.builder().build());
    when(containerMasterUrlHelper.fetchMasterUrl(
             any(ContainerServiceParams.class), any(ContainerInfrastructureMapping.class)))
        .thenReturn(MASTER_URL);

    ExecutionResponse response = kubernetesDeploy.execute(context);
    assertThat(response).isNotNull().hasFieldOrPropertyWithValue("async", true);
    assertThat(response.getCorrelationIds()).isNotNull().hasSize(1);
    verify(activityService).save(any(Activity.class));
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    CommandExecutionContext executionContext = (CommandExecutionContext) delegateTask.getData().getParameters()[1];
    KubernetesResizeParams params = (KubernetesResizeParams) executionContext.getContainerResizeParams();
    assertThat(params.getInstanceCount()).isEqualTo(1);
    assertThat(params.getContainerServiceName()).isEqualTo(KUBERNETES_CONTROLLER_NAME);
    assertThat(params.getNamespace()).isEqualTo("default");
    assertThat(params.getMaxInstances()).isEqualTo(10);
    assertThat(params.getResizeStrategy()).isEqualTo(RESIZE_NEW_FIRST);
    assertThat(params.getClusterName()).isEqualTo(CLUSTER_NAME);
    assertThat(params.getMasterUrl()).isEqualTo(MASTER_URL);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldExecuteAsync() {
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("evaluator", evaluator);
    Map<String, ResponseData> notifyResponse = new HashMap<>();
    notifyResponse.put("key", CommandExecutionResult.builder().status(CommandExecutionStatus.SUCCESS).build());

    stateExecutionInstance.getStateExecutionMap().put(
        stateExecutionInstance.getDisplayName(), aCommandStateExecutionData().build());

    ExecutionResponse response = kubernetesDeploy.handleAsyncResponse(context, notifyResponse);
    assertThat(response)
        .isNotNull()
        .hasFieldOrPropertyWithValue("async", false)
        .hasFieldOrPropertyWithValue("executionStatus", ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldExecuteAsyncWithOldRControllerWithNoInstance() {
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("evaluator", evaluator);
    Map<String, ResponseData> notifyResponse = new HashMap<>();
    notifyResponse.put("key", CommandExecutionResult.builder().status(CommandExecutionStatus.SUCCESS).build());
    stateExecutionInstance.getStateExecutionMap().put(
        stateExecutionInstance.getDisplayName(), aCommandStateExecutionData().build());

    ExecutionResponse response = kubernetesDeploy.handleAsyncResponse(context, notifyResponse);
    assertThat(response)
        .isNotNull()
        .hasFieldOrPropertyWithValue("async", false)
        .hasFieldOrPropertyWithValue("executionStatus", ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testKubernetesRollbackDeployTest() {
    on(context).set("sweepingOutputService", sweepingOutputService);
    on(context).set("serviceResourceService", serviceResourceService);
    on(context).set("serviceTemplateService", serviceTemplateService);
    on(context).set("infrastructureMappingService", infrastructureMappingService);
    on(context).set("infrastructureDefinitionService", infrastructureDefinitionService);
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("featureFlagService", featureFlagService);
    on(context).set("evaluator", evaluator);
    on(context).set("settingsService", settingsService);

    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(DeploymentType.KUBERNETES);

    ExecutionResponse response = kubernetesDeployRollback.execute(context);
    assertThat(response).isNotNull();
    assertThat(response.getStateExecutionData().getErrorMsg()).isEqualTo("No context found for rollback. Skipping.");
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SKIPPED);
  }
}
