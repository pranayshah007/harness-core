/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.EnvironmentType.PROD;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.FeatureName.ECS_REGISTER_TASK_DEFINITION_TAGS;
import static io.harness.beans.FeatureName.ENABLE_ADDING_SERVICE_VARS_TO_ECS_SPEC;
import static io.harness.beans.FeatureName.FIXED_INSTANCE_ZERO_ALLOW;
import static io.harness.delegate.beans.pcf.ResizeStrategy.RESIZE_NEW_FIRST;
import static io.harness.exception.FailureType.TIMEOUT;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.JELENA;
import static io.harness.rule.OwnerRule.SAINATH;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.api.CommandStateExecutionData.Builder.aCommandStateExecutionData;
import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.beans.Activity.Type.Command;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.EcsInfrastructureMapping.Builder.anEcsInfrastructureMapping;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.command.CommandUnitDetails.CommandUnitType.AWS_ECS_SERVICE_SETUP;
import static software.wings.beans.command.EcsSetupParams.EcsSetupParamsBuilder.anEcsSetupParams;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.sm.InstanceStatusSummary.InstanceStatusSummaryBuilder.anInstanceStatusSummary;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.CLUSTER_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.util.Maps.newHashMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.container.ContainerInfo;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.deployment.InstanceDetails;
import io.harness.ecs.EcsContainerDetails;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.git.model.GitFile;
import io.harness.k8s.model.ImageDetails;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.api.CommandStateExecutionData;
import software.wings.api.ContainerRollbackRequestElement;
import software.wings.api.ContainerServiceData;
import software.wings.api.ContainerServiceElement;
import software.wings.api.HostElement;
import software.wings.api.InstanceElementListParam;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.ecs.EcsBGRoute53SetupStateExecutionData;
import software.wings.api.ecs.EcsSetupStateExecutionData;
import software.wings.api.instancedetails.InstanceInfoVariables;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.GitFileConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData;
import software.wings.beans.command.ContainerSetupParams;
import software.wings.beans.command.EcsResizeParams;
import software.wings.beans.command.EcsSetupParams;
import software.wings.beans.container.AwsAutoScalarConfig;
import software.wings.beans.container.ContainerDefinition;
import software.wings.beans.container.EcsContainerTask;
import software.wings.beans.container.EcsServiceSpecification;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.ecs.request.EcsBGListenerUpdateRequest;
import software.wings.helpers.ext.ecs.request.EcsDeployRollbackDataFetchRequest;
import software.wings.helpers.ext.ecs.request.EcsListenerUpdateRequestConfigData;
import software.wings.helpers.ext.ecs.request.EcsRunTaskDeployRequest;
import software.wings.helpers.ext.ecs.request.EcsServiceDeployRequest;
import software.wings.helpers.ext.ecs.request.EcsServiceSetupRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.helpers.ext.ecs.response.EcsDeployRollbackDataFetchResponse;
import software.wings.helpers.ext.ecs.response.EcsServiceDeployResponse;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry.SweepingOutputInquiryBuilder;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class EcsStateHelperTest extends CategoryTest {
  @Mock private FeatureFlagService featureFlagService;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;
  @Inject @InjectMocks private EcsStateHelper helper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(any(), any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testBuildContainerSetupParams() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    when(mockContext.renderExpression(any())).thenAnswer(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        return (String) args[0];
      }
    });
    EcsContainerTask containerTask = new EcsContainerTask();
    containerTask.setContainerDefinitions(
        singletonList(ContainerDefinition.builder().commands(singletonList("Command")).build()));
    containerTask.setAdvancedConfig("AdvancedConfig");
    EcsSetupStateConfig config =
        EcsSetupStateConfig.builder()
            .app(anApplication().uuid(APP_ID).name(APP_NAME).build())
            .env(anEnvironment().uuid(ENV_ID).name(ENV_NAME).build())
            .containerTask(containerTask)
            .serviceSteadyStateTimeout(10)
            .infrastructureMapping(anEcsInfrastructureMapping()
                                       .withUuid(INFRA_MAPPING_ID)
                                       .withRegion("us-east-1")
                                       .withVpcId("vpc-id")
                                       .withAssignPublicIp(true)
                                       .withLaunchType("Ec2")
                                       .build())
            .ecsServiceName("EcsService")
            .clusterName("EcsCluster")
            .blueGreen(true)
            .useRoute53DNSSwap(true)
            .serviceDiscoveryService1JSON("Json1")
            .serviceDiscoveryService2JSON("Json2")
            .parentRecordHostedZoneId("ParentZone")
            .parentRecordName("ParentRecord")
            .ecsServiceSpecification(EcsServiceSpecification.builder().serviceSpecJson("ServiceSpecJson").build())
            .awsAutoScalarConfigs(singletonList(AwsAutoScalarConfig.builder()
                                                    .scalableTargetJson("ScalbaleTarget")
                                                    .scalingPolicyForTarget("ScalablePolicy")
                                                    .build()))
            .build();
    boolean ecsRegisterTaskDefinitionTagsEnabled = true;
    when(featureFlagService.isEnabled(ECS_REGISTER_TASK_DEFINITION_TAGS, config.getApp().getAccountId()))
        .thenReturn(ecsRegisterTaskDefinitionTagsEnabled);
    ContainerSetupParams containerSetupParams = helper.buildContainerSetupParams(mockContext, config);
    assertThat(containerSetupParams).isNotNull();
    assertThat(containerSetupParams instanceof EcsSetupParams).isTrue();
    EcsSetupParams ecsSetupParams = (EcsSetupParams) containerSetupParams;
    assertThat(ecsSetupParams.getRegion()).isEqualTo("us-east-1");
    assertThat(ecsSetupParams.getVpcId()).isEqualTo("vpc-id");
    assertThat(ecsSetupParams.getLaunchType()).isEqualTo("Ec2");
    assertThat(ecsSetupParams.getEcsServiceSpecification().getServiceSpecJson()).isEqualTo("ServiceSpecJson");
    assertThat(ecsSetupParams.getNewAwsAutoScalarConfigList().size()).isEqualTo(1);
    assertThat(ecsSetupParams.getServiceDiscoveryService1JSON()).isEqualTo("Json1");
    assertThat(ecsSetupParams.getServiceDiscoveryService2JSON()).isEqualTo("Json2");
    assertThat(ecsSetupParams.getParentRecordHostedZoneId()).isEqualTo("ParentZone");
    assertThat(ecsSetupParams.getParentRecordName()).isEqualTo("ParentRecord");
    assertThat(ecsSetupParams.getClusterName()).isEqualTo("EcsCluster");
    assertThat(ecsSetupParams.isEcsRegisterTaskDefinitionTagsEnabled()).isEqualTo(ecsRegisterTaskDefinitionTagsEnabled);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCreateActivity() {
    ActivityService mockActivityService = mock(ActivityService.class);
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    Application application = anApplication().uuid(APP_ID).name(APP_NAME).build();
    Environment environment = anEnvironment().uuid(ENV_ID).name(ENV_NAME).build();
    doReturn(application).when(mockContext).fetchRequiredApp();
    doReturn(environment).when(mockContext).getEnv();
    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    doReturn(mockParams).when(mockContext).getContextElement(any());
    EmbeddedUser user = EmbeddedUser.builder().name("user").email("user@harness.io").build();
    doReturn(user).when(mockParams).getCurrentUser();
    helper.createActivity(mockContext, "CommandName", "StateType", AWS_ECS_SERVICE_SETUP, mockActivityService);
    ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
    verify(mockActivityService).save(captor.capture());
    Activity activity = captor.getValue();
    assertThat(activity).isNotNull();
    assertThat(activity.getAppId()).isEqualTo(APP_ID);
    assertThat(activity.getType()).isEqualTo(Command);
    assertThat(activity.getCommandName()).isEqualTo("CommandName");
    assertThat(activity.getStatus()).isEqualTo(RUNNING);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testQueueDelegateTaskForEcsListenerUpdate() throws InterruptedException {
    Application application = anApplication().uuid(APP_ID).name(APP_NAME).build();
    AwsConfig awsConfig = AwsConfig.builder().build();
    DelegateService mockService = mock(DelegateService.class);
    EcsInfrastructureMapping mapping = anEcsInfrastructureMapping()
                                           .withUuid(INFRA_MAPPING_ID)
                                           .withRegion("us-east-1")
                                           .withVpcId("vpc-id")
                                           .withAssignPublicIp(true)
                                           .withLaunchType("Ec2")
                                           .build();
    EcsListenerUpdateRequestConfigData configData = EcsListenerUpdateRequestConfigData.builder()
                                                        .prodListenerArn("ProdLArn")
                                                        .stageListenerArn("StageLArn")
                                                        .region("us-east-1")
                                                        .serviceName("ServiceName")
                                                        .downsizeOldService(true)
                                                        .targetGroupForNewService("NewTgt")
                                                        .targetGroupForExistingService("OldTgt")
                                                        .build();
    Environment environment = anEnvironment().environmentType(PROD).build();
    helper.queueDelegateTaskForEcsListenerUpdate(application, awsConfig, mockService, mapping, ACTIVITY_ID, environment,
        "CommandName", configData, emptyList(), 10, false, null);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(mockService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(2).isEqualTo(delegateTask.getData().getParameters().length);
    assertThat(delegateTask.getData().getParameters()[0] instanceof EcsBGListenerUpdateRequest).isTrue();
    EcsBGListenerUpdateRequest params = (EcsBGListenerUpdateRequest) delegateTask.getData().getParameters()[0];
    assertThat(params).isNotNull();
    assertThat(params.getProdListenerArn()).isEqualTo("ProdLArn");
    assertThat(params.getStageListenerArn()).isEqualTo("StageLArn");
    assertThat(params.getTargetGroupForExistingService()).isEqualTo("OldTgt");
    assertThat(params.getTargetGroupForNewService()).isEqualTo("NewTgt");
    assertThat(params.getCommandName()).isEqualTo("CommandName");
    assertThat(params.getServiceName()).isEqualTo("ServiceName");
    assertThat(params.getServiceSteadyStateTimeout()).isEqualTo(10);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testBuildContainerServiceElement() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    Mockito.when(featureFlagService.isEnabled(FIXED_INSTANCE_ZERO_ALLOW, mockContext.getAccountId())).thenReturn(true);
    when(mockContext.renderExpression(any())).thenAnswer(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        return (String) args[0];
      }
    });
    ContainerSetupCommandUnitExecutionData data = ContainerSetupCommandUnitExecutionData.builder()
                                                      .containerServiceName("ContainerServiceName")
                                                      .instanceCountForLatestVersion(2)
                                                      .build();
    ImageDetails details = ImageDetails.builder().name("imgName").tag("imgTag").build();
    Logger mockLogger = mock(Logger.class);
    CommandStateExecutionData executionData =
        aCommandStateExecutionData()
            .withServiceId(SERVICE_ID)
            .withClusterName(CLUSTER_NAME)
            .withContainerSetupParams(anEcsSetupParams()
                                          .withInfraMappingId(INFRA_MAPPING_ID)
                                          .withNewAwsAutoScalarConfigList(
                                              singletonList(AwsAutoScalarConfig.builder().resourceId("ResId").build()))
                                          .build())
            .build();
    doReturn(executionData).when(mockContext).getStateExecutionData();
    ContainerServiceElement element = helper.buildContainerServiceElement(
        mockContext, data, details, "3", "2", "fixedInstances", RESIZE_NEW_FIRST, 10);
    assertThat(element).isNotNull();
    assertThat(element.getUuid()).isEqualTo(SERVICE_ID);
    assertThat(element.getName()).isEqualTo("ContainerServiceName");
    assertThat(element.getImage()).isEqualTo("imgName:imgTag");
    assertThat(element.getClusterName()).isEqualTo(CLUSTER_NAME);
    assertThat(element.getNewServiceAutoScalarConfig().size()).isEqualTo(1);
    assertThat(element.getNewServiceAutoScalarConfig().get(0).getResourceId()).isEqualTo("ResId");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testPrepareBagForEcsSetUp() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    when(mockContext.renderExpression(any())).thenAnswer(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        return (String) args[0];
      }
    });
    ArtifactCollectionUtils mockUtils = mock(ArtifactCollectionUtils.class);
    ServiceResourceService mockService = mock(ServiceResourceService.class);
    InfrastructureMappingService mockMappingService = mock(InfrastructureMappingService.class);
    SettingsService mockSettingService = mock(SettingsService.class);
    SecretManager mockSecretManaer = mock(SecretManager.class);
    PhaseElement phaseElement =
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build()).build();
    doReturn(phaseElement).when(mockContext).getContextElement(any(), any());
    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    doReturn(mockParams).when(mockContext).getContextElement(any());
    Application app = anApplication().uuid(APP_ID).name(APP_NAME).build();
    Environment env = anEnvironment().uuid(ENV_ID).name(ENV_NAME).build();
    doReturn(app).when(workflowStandardParamsExtensionService).fetchRequiredApp(mockParams);
    doReturn(env).when(workflowStandardParamsExtensionService).getEnv(mockParams);
    Artifact artifact = anArtifact().build();
    doReturn(artifact).when(mockContext).getDefaultArtifactForService(any());
    ImageDetails details = ImageDetails.builder().name("imgName").tag("imgTag").build();
    doReturn(details).when(mockUtils).fetchContainerImageDetails(any(), any());
    Service service = Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build();
    doReturn(service).when(mockService).getWithDetails(any(), any());
    EcsContainerTask containerTask = new EcsContainerTask();
    containerTask.setUuid("ContainerTaskId");
    EcsInfrastructureMapping mapping = anEcsInfrastructureMapping()
                                           .withUuid(INFRA_MAPPING_ID)
                                           .withRegion("us-east-1")
                                           .withVpcId("vpc-id")
                                           .withAssignPublicIp(true)
                                           .withLaunchType("Ec2")
                                           .build();
    doReturn(mapping).when(mockMappingService).get(any(), any());
    SettingAttribute settingAttribute =
        aSettingAttribute().withUuid(SETTING_ID).withValue(AwsConfig.builder().build()).build();
    doReturn(settingAttribute).when(mockSettingService).get(any());
    doReturn(emptyList()).when(mockSecretManaer).getEncryptionDetails(any(), any(), any());
    EcsServiceSpecification specification = EcsServiceSpecification.builder().serviceId(SERVICE_ID).build();
    doReturn(specification).when(mockService).getEcsServiceSpecification(any(), any());
    EcsSetUpDataBag bag = helper.prepareBagForEcsSetUp(
        mockContext, 0, mockUtils, mockService, mockMappingService, mockSettingService, mockSecretManaer);
    assertThat(bag).isNotNull();
    assertThat(bag.getService()).isNotNull();
    assertThat(bag.getService().getUuid()).isEqualTo(SERVICE_ID);
    assertThat(bag.getEnvironment()).isNotNull();
    assertThat(bag.getEnvironment().getUuid()).isEqualTo(ENV_ID);
    assertThat(bag.getApplication()).isNotNull();
    assertThat(bag.getApplication().getUuid()).isEqualTo(APP_ID);
    assertThat(bag.getImageDetails()).isNotNull();
    assertThat(bag.getImageDetails().getName()).isEqualTo("imgName");
    assertThat(bag.getImageDetails().getTag()).isEqualTo("imgTag");
    assertThat(bag.getServiceSpecification()).isNotNull();
    assertThat(bag.getServiceSpecification().getServiceId()).isEqualTo(SERVICE_ID);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testRenderEcsSetupContextVariables() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    when(mockContext.renderExpression(any())).thenAnswer(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        return (String) args[0];
      }
    });

    doReturn(false)
        .doReturn(true)
        .when(featureFlagService)
        .isNotEnabled(eq(ENABLE_ADDING_SERVICE_VARS_TO_ECS_SPEC), any());
    Map<String, String> serviceVars = newHashMap("sk", "sv");
    Map<String, String> safeDisplayVars = newHashMap("dk", "dv");
    doReturn(serviceVars).when(mockContext).getServiceVariables();
    doReturn(safeDisplayVars).when(mockContext).getSafeDisplayServiceVariables();
    EcsSetupContextVariableHolder holder = helper.renderEcsSetupContextVariables(mockContext);
    assertThat(holder).isNotNull();
    assertThat(holder.getServiceVariables().size()).isEqualTo(1);
    assertThat(holder.getServiceVariables().get("sk")).isEqualTo("sv");
    assertThat(holder.getSafeDisplayServiceVariables().size()).isEqualTo(1);
    assertThat(holder.getSafeDisplayServiceVariables().get("dk")).isEqualTo("dv");

    holder = helper.renderEcsSetupContextVariables(mockContext);
    assertThat(holder.getServiceVariables()).isEmpty();
    assertThat(holder.getSafeDisplayServiceVariables()).isEmpty();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testPopulateFromDelegateResponse() {
    ContainerSetupCommandUnitExecutionData data = ContainerSetupCommandUnitExecutionData.builder()
                                                      .previousEcsServiceSnapshotJson("PrevJson")
                                                      .ecsServiceArn("EcsSvcArn")
                                                      .ecsRegion("us-east-1")
                                                      .targetGroupForNewService("TgtNew")
                                                      .targetGroupForExistingService("Tgtold")
                                                      .containerServiceName("ContainerServiceName")
                                                      .instanceCountForLatestVersion(2)
                                                      .ecsServiceToBeDownsized("Svc__1")
                                                      .parentRecordName("ParentRecord")
                                                      .parentRecordHostedZoneId("ParentZoneId")
                                                      .oldServiceDiscoveryArn("OldSDSArn")
                                                      .newServiceDiscoveryArn("NewSDSArn")
                                                      .useRoute53Swap(true)
                                                      .build();
    CommandStateExecutionData executionData = aCommandStateExecutionData()
                                                  .withContainerSetupParams(anEcsSetupParams()
                                                                                .withBlueGreen(true)
                                                                                .withProdListenerArn("ProdLArn")
                                                                                .withTargetGroupArn("TgtGpArn")
                                                                                .withTargetGroupArn2("TgtGpArn2")
                                                                                .build())
                                                  .build();
    ContainerServiceElement containerServiceElement = ContainerServiceElement.builder().build();
    helper.populateFromDelegateResponse(data, executionData, containerServiceElement);
    assertThat(containerServiceElement.getNewEcsServiceName()).isEqualTo("ContainerServiceName");
    assertThat(containerServiceElement.getTargetGroupForNewService()).isEqualTo("TgtNew");
    assertThat(containerServiceElement.getTargetGroupForExistingService()).isEqualTo("Tgtold");
    assertThat(containerServiceElement.getEcsRegion()).isEqualTo("us-east-1");
    assertThat(containerServiceElement.getEcsBGSetupData()).isNotNull();
    assertThat(containerServiceElement.getEcsBGSetupData().getProdEcsListener()).isEqualTo("ProdLArn");
    assertThat(containerServiceElement.getEcsBGSetupData().getEcsBGTargetGroup1()).isEqualTo("TgtGpArn");
    assertThat(containerServiceElement.getEcsBGSetupData().getEcsBGTargetGroup2()).isEqualTo("TgtGpArn2");
    assertThat(containerServiceElement.getEcsBGSetupData().getDownsizedServiceName()).isEqualTo("Svc__1");
    assertThat(containerServiceElement.getEcsBGSetupData().getParentRecordName()).isEqualTo("ParentRecord");
    assertThat(containerServiceElement.getEcsBGSetupData().getParentRecordHostedZoneId()).isEqualTo("ParentZoneId");
    assertThat(containerServiceElement.getEcsBGSetupData().getOldServiceDiscoveryArn()).isEqualTo("OldSDSArn");
    assertThat(containerServiceElement.getEcsBGSetupData().getNewServiceDiscoveryArn()).isEqualTo("NewSDSArn");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetStateExecutionData() {
    EcsSetUpDataBag bag = EcsSetUpDataBag.builder()
                              .service(Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build())
                              .application(anApplication().uuid(APP_ID).name(APP_NAME).build())
                              .ecsInfrastructureMapping(anEcsInfrastructureMapping()
                                                            .withUuid(INFRA_MAPPING_ID)
                                                            .withClusterName(CLUSTER_NAME)
                                                            .withRegion("us-east-1")
                                                            .withVpcId("vpc-id")
                                                            .withAssignPublicIp(true)
                                                            .withLaunchType("Ec2")
                                                            .build())
                              .build();
    CommandStateExecutionData data =
        helper.getStateExecutionData(bag, "CommandName", null, Activity.builder().uuid(ACTIVITY_ID).build());
    assertThat(data).isNotNull();
    assertThat(data.getAppId()).isEqualTo(APP_ID);
    assertThat(data.getCommandName()).isEqualTo("CommandName");
    assertThat(data.getServiceName()).isEqualTo(SERVICE_NAME);
    assertThat(data.getServiceId()).isEqualTo(SERVICE_ID);
    assertThat(data.getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(data.getClusterName()).isEqualTo(CLUSTER_NAME);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCreateAndQueueDelegateTaskForEcsServiceSetUp() {
    EcsSetUpDataBag bag = EcsSetUpDataBag.builder()
                              .service(Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build())
                              .application(anApplication().uuid(APP_ID).name(APP_NAME).build())
                              .environment(anEnvironment().uuid(ENV_ID).name(ENV_NAME).build())
                              .ecsInfrastructureMapping(anEcsInfrastructureMapping()
                                                            .withUuid(INFRA_MAPPING_ID)
                                                            .withClusterName(CLUSTER_NAME)
                                                            .withRegion("us-east-1")
                                                            .withVpcId("vpc-id")
                                                            .withAssignPublicIp(true)
                                                            .withLaunchType("Ec2")
                                                            .build())
                              .awsConfig(AwsConfig.builder().build())
                              .encryptedDataDetails(emptyList())
                              .build();
    DelegateService mockDelegateService = mock(DelegateService.class);
    helper.createAndQueueDelegateTaskForEcsServiceSetUp(EcsServiceSetupRequest.builder().build(), bag,
        Activity.builder().uuid(ACTIVITY_ID).build(), mockDelegateService, false);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(mockDelegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(2).isEqualTo(delegateTask.getData().getParameters().length);
    assertThat(delegateTask.getData().getParameters()[0] instanceof EcsServiceSetupRequest).isTrue();
    assertThat(delegateTask.getWaitId()).isEqualTo(ACTIVITY_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(APP_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.ENV_ID_FIELD)).isEqualTo(ENV_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD))
        .isEqualTo(INFRA_MAPPING_ID);
  }

  @Test
  @Owner(developers = JELENA)
  @Category(UnitTests.class)
  public void testCreateAndQueueDelegateTaskForEcsRunTaskDeploy() {
    final String accountId = "accountId";
    final String appUuid = "appUuid";
    final String appAppId = "appAppId";
    final String envUuid = "envUuid";
    final String ecsRunTaskFamilyName = "runTaskFamilyName";
    final String activityId = "activityId";
    EcsRunTaskDataBag bag = EcsRunTaskDataBag.builder()
                                .applicationAccountId(accountId)
                                .applicationUuid(appUuid)
                                .applicationAppId(appAppId)
                                .envUuid(envUuid)
                                .awsConfig(AwsConfig.builder().build())
                                .listTaskDefinitionJson(new ArrayList<String>())
                                .ecsRunTaskFamilyName(ecsRunTaskFamilyName)
                                .serviceSteadyStateTimeout(10L)
                                .skipSteadyStateCheck(true)
                                .build();

    InfrastructureMappingService infrastructureMappingService = mock(InfrastructureMappingService.class);
    doReturn(anEcsInfrastructureMapping()
                 .withUuid(INFRA_MAPPING_ID)
                 .withClusterName(CLUSTER_NAME)
                 .withRegion("us-east-1")
                 .withVpcId("vpc-id")
                 .withAssignPublicIp(true)
                 .withLaunchType("Ec2")
                 .build())
        .when(infrastructureMappingService)
        .get(any(), any());
    DelegateService mockDelegateService = mock(DelegateService.class);
    SecretManager mockSecretManager = mock(SecretManager.class);
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doReturn(anEnvironment().uuid(ENV_ID).name(ENV_NAME).build()).when(mockContext).fetchRequiredEnvironment();

    helper.createAndQueueDelegateTaskForEcsRunTaskDeploy(bag, infrastructureMappingService, mockSecretManager,
        anApplication().uuid(APP_ID).name(APP_NAME).build(), mockContext, EcsRunTaskDeployRequest.builder().build(),
        activityId, mockDelegateService, false);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(mockDelegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(2).isEqualTo(delegateTask.getData().getParameters().length);
    assertThat(delegateTask.getData().getParameters()[0] instanceof EcsRunTaskDeployRequest).isTrue();
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(appUuid);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.ENV_ID_FIELD)).isEqualTo(envUuid);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD))
        .isEqualTo(INFRA_MAPPING_ID);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testHandleDelegateResponseForEcsDeploy() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    CommandStateExecutionData executionData = aCommandStateExecutionData().build();
    doReturn(executionData).when(mockContext).getStateExecutionData();
    EcsCommandExecutionResponse delegateResponse =
        EcsCommandExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .ecsCommandResponse(
                EcsServiceDeployResponse.builder()
                    .containerInfos(singletonList(ContainerInfo.builder().containerId("ContId").ip("ip").build()))
                    .build())
            .build();
    ContainerDeploymentManagerHelper mockHelper = mock(ContainerDeploymentManagerHelper.class);
    doReturn(singletonList(
                 anInstanceStatusSummary()
                     .withInstanceElement(anInstanceElement()
                                              .dockerId("DockerId")
                                              .hostName("HostName")
                                              .host(HostElement.builder().build())
                                              .ecsContainerDetails(EcsContainerDetails.builder()
                                                                       .taskId("TASK_ID")
                                                                       .taskArn("TASK_ARN")
                                                                       .completeDockerId("COMPLETE_DOCKER_ID")
                                                                       .containerInstanceId("CONTAINER_INSTANCE_ID")
                                                                       .containerInstanceArn("CONTAINER_INSTANCE_ARN")
                                                                       .containerId("CONTAINER_ID")
                                                                       .ecsServiceName("ECS_SERVICE_NAME")
                                                                       .dockerId("DOCKER_ID")
                                                                       .build())
                                              .build())
                     .build()))
        .doReturn(emptyList())
        .when(mockHelper)
        .getInstanceStatusSummaries(any(), anyList());
    ActivityService mockService = mock(ActivityService.class);
    ArgumentCaptor<SweepingOutputInstance> sweepingOutputInstanceCaptor =
        ArgumentCaptor.forClass(SweepingOutputInstance.class);
    doReturn(null).when(sweepingOutputService).save(sweepingOutputInstanceCaptor.capture());
    doReturn("").when(mockContext).appendStateExecutionId(any());
    doReturn(SweepingOutputInstance.builder())
        .doReturn(SweepingOutputInstance.builder())
        .when(mockContext)
        .prepareSweepingOutputBuilder(any());
    PhaseElement phaseElement = PhaseElement.builder()
                                    .phaseName("Rollback Phase1")
                                    .phaseNameForRollback("Phase1")
                                    .serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build())
                                    .build();
    doReturn(phaseElement).when(mockContext).getContextElement(any(), any());
    ExecutionResponse response = helper.handleDelegateResponseForEcsDeploy(
        mockContext, ImmutableMap.of(ACTIVITY_ID, delegateResponse), false, mockService, false, mockHelper);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    List<ContextElement> contextElements = response.getContextElements();
    assertThat(contextElements).isNotNull();
    assertThat(contextElements.size()).isEqualTo(1);
    ContextElement contextElement = contextElements.get(0);
    assertThat(contextElement instanceof InstanceElementListParam).isTrue();
    InstanceElementListParam listParam = (InstanceElementListParam) contextElement;
    assertThat(listParam.getInstanceElements()).isNotNull();
    assertThat(listParam.getInstanceElements().size()).isEqualTo(1);
    assertThat(listParam.getInstanceElements().get(0).getHostName()).isEqualTo("HostName");
    assertThat(listParam.getInstanceElements().get(0).getDockerId()).isEqualTo("DockerId");
    assertThat(response.getFailureTypes()).isNull();

    InstanceDetails.AWS aws = ((InstanceInfoVariables) sweepingOutputInstanceCaptor.getValue().getValue())
                                  .getInstanceDetails()
                                  .get(0)
                                  .getAws();
    assertThat(aws.getTaskId()).isEqualTo("TASK_ID");
    assertThat(aws.getTaskArn()).isEqualTo("TASK_ARN");
    assertThat(aws.getCompleteDockerId()).isEqualTo("COMPLETE_DOCKER_ID");
    assertThat(aws.getContainerInstanceId()).isEqualTo("CONTAINER_INSTANCE_ID");
    assertThat(aws.getContainerInstanceArn()).isEqualTo("CONTAINER_INSTANCE_ARN");
    assertThat(aws.getContainerId()).isEqualTo("CONTAINER_ID");
    assertThat(aws.getEcsServiceName()).isEqualTo("ECS_SERVICE_NAME");
    assertThat(aws.getDockerId()).isEqualTo("DOCKER_ID");

    delegateResponse.getEcsCommandResponse().setTimeoutFailure(true);
    response = helper.handleDelegateResponseForEcsDeploy(
        mockContext, ImmutableMap.of(ACTIVITY_ID, delegateResponse), false, mockService, false, mockHelper);
    assertThat(response.getFailureTypes()).isEqualTo(TIMEOUT);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testHandleDelegateResponseForEcsDeploy_skipVerificationCheckIfNoNewContainers() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    CommandStateExecutionData executionData = aCommandStateExecutionData().build();
    doReturn(executionData).when(mockContext).getStateExecutionData();
    EcsCommandExecutionResponse delegateResponse =
        EcsCommandExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .ecsCommandResponse(EcsServiceDeployResponse.builder().containerInfos(null).build())
            .build();
    ContainerDeploymentManagerHelper mockHelper = mock(ContainerDeploymentManagerHelper.class);
    doReturn(emptyList()).when(mockHelper).getInstanceStatusSummaries(any(), anyList());
    ActivityService mockService = mock(ActivityService.class);
    ArgumentCaptor<SweepingOutputInstance> sweepingOutputInstanceCaptor =
        ArgumentCaptor.forClass(SweepingOutputInstance.class);
    doReturn(null).when(sweepingOutputService).save(sweepingOutputInstanceCaptor.capture());
    doReturn("").when(mockContext).appendStateExecutionId(any());
    doReturn(SweepingOutputInstance.builder())
        .doReturn(SweepingOutputInstance.builder())
        .when(mockContext)
        .prepareSweepingOutputBuilder(any());
    PhaseElement phaseElement = PhaseElement.builder()
                                    .phaseName("Rollback Phase1")
                                    .phaseNameForRollback("Phase1")
                                    .serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build())
                                    .build();
    doReturn(phaseElement).when(mockContext).getContextElement(any(), any());
    ExecutionResponse response = helper.handleDelegateResponseForEcsDeploy(
        mockContext, ImmutableMap.of(ACTIVITY_ID, delegateResponse), false, mockService, false, mockHelper);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    SweepingOutputInstance sweepingOutputInstance = sweepingOutputInstanceCaptor.getValue();
    assertThat(sweepingOutputInstance.getName()).isEqualTo("");
    assertThat(((InstanceInfoVariables) sweepingOutputInstance.getValue()).isSkipVerification()).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testCreateAndQueueDelegateTaskForEcsServiceDeploy() {
    EcsDeployDataBag bag =
        EcsDeployDataBag.builder()
            .service(Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build())
            .app(anApplication().uuid(APP_ID).name(APP_NAME).build())
            .env(anEnvironment().uuid(ENV_ID).name(ENV_NAME).build())
            .ecsInfrastructureMapping(anEcsInfrastructureMapping()
                                          .withUuid(INFRA_MAPPING_ID)
                                          .withClusterName(CLUSTER_NAME)
                                          .withRegion("us-east-1")
                                          .withVpcId("vpc-id")
                                          .withAssignPublicIp(true)
                                          .withLaunchType("Ec2")
                                          .build())
            .awsConfig(AwsConfig.builder().build())
            .encryptedDataDetails(emptyList())
            .containerElement(ContainerServiceElement.builder().serviceSteadyStateTimeout(10).build())
            .build();
    DelegateService mockDelegateService = mock(DelegateService.class);
    helper.createAndQueueDelegateTaskForEcsServiceDeploy(bag, EcsServiceDeployRequest.builder().build(),
        Activity.builder().uuid(ACTIVITY_ID).build(), mockDelegateService, false);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(mockDelegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(2).isEqualTo(delegateTask.getData().getParameters().length);
    assertThat(delegateTask.getData().getParameters()[0] instanceof EcsServiceDeployRequest).isTrue();
    assertThat(delegateTask.getWaitId()).isEqualTo(ACTIVITY_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(APP_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.ENV_ID_FIELD)).isEqualTo(ENV_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD))
        .isEqualTo(INFRA_MAPPING_ID);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetTimeout() {
    long timeout =
        helper.getTimeout(EcsDeployDataBag.builder()
                              .containerElement(ContainerServiceElement.builder().serviceSteadyStateTimeout(10).build())
                              .build());

    assertThat(timeout).isEqualTo(10);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testPrepareBagForEcsDeploy() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    when(mockContext.renderExpression(any())).thenAnswer(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        return (String) args[0];
      }
    });
    ServiceResourceService mockService = mock(ServiceResourceService.class);
    InfrastructureMappingService mockMappingService = mock(InfrastructureMappingService.class);
    SettingsService mockSettingService = mock(SettingsService.class);
    SecretManager mockSecretManaer = mock(SecretManager.class);
    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    doReturn(mockParams).when(mockContext).getContextElement(any());
    Application app = anApplication().uuid(APP_ID).name(APP_NAME).build();
    Environment env = anEnvironment().uuid(ENV_ID).name(ENV_NAME).build();
    doReturn(app).when(workflowStandardParamsExtensionService).fetchRequiredApp(mockParams);
    doReturn(env).when(workflowStandardParamsExtensionService).getEnv(mockParams);
    PhaseElement phaseElement = PhaseElement.builder()
                                    .phaseName("Rollback Phase1")
                                    .phaseNameForRollback("Phase1")
                                    .serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build())
                                    .build();
    doReturn(phaseElement)
        .doReturn(phaseElement)
        .doReturn(phaseElement)
        .when(mockContext)
        .getContextElement(any(), any());
    Service service = Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build();
    doReturn(service).when(mockService).getWithDetails(any(), any());
    EcsInfrastructureMapping mapping = anEcsInfrastructureMapping()
                                           .withUuid(INFRA_MAPPING_ID)
                                           .withRegion("us-east-1")
                                           .withVpcId("vpc-id")
                                           .withAssignPublicIp(true)
                                           .withLaunchType("Ec2")
                                           .build();
    doReturn(mapping).when(mockMappingService).get(any(), any());
    SettingAttribute settingAttribute =
        aSettingAttribute().withUuid(SETTING_ID).withValue(AwsConfig.builder().build()).build();
    doReturn(settingAttribute).when(mockSettingService).get(any());
    doReturn(emptyList()).when(mockSecretManaer).getEncryptionDetails(any(), any(), any());
    SweepingOutputInquiryBuilder builder1 = SweepingOutputInquiry.builder();
    SweepingOutputInquiryBuilder builder2 = SweepingOutputInquiry.builder();
    doReturn(builder1).doReturn(builder2).when(mockContext).prepareSweepingOutputInquiryBuilder();
    doReturn(ContainerServiceElement.builder().build())
        .doReturn(ContainerRollbackRequestElement.builder().build())
        .when(sweepingOutputService)
        .findSweepingOutput(any());
    EcsDeployDataBag bag = helper.prepareBagForEcsDeploy(
        mockContext, mockService, mockMappingService, mockSettingService, mockSecretManaer, true);
    assertThat(bag).isNotNull();
    assertThat(bag.getService()).isNotNull();
    assertThat(bag.getService().getUuid()).isEqualTo(SERVICE_ID);
    assertThat(bag.getEnv()).isNotNull();
    assertThat(bag.getEnv().getUuid()).isEqualTo(ENV_ID);
    assertThat(bag.getApp()).isNotNull();
    assertThat(bag.getApp().getUuid()).isEqualTo(APP_ID);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testReverse() {
    EcsStateHelper helper = spy(new EcsStateHelper());
    List<ContainerServiceData> retVal = helper.reverse(singletonList(ContainerServiceData.builder()
                                                                         .name("foo")
                                                                         .desiredCount(1)
                                                                         .desiredTraffic(10)
                                                                         .previousCount(4)
                                                                         .previousTraffic(40)
                                                                         .build()));
    assertThat(retVal).isNotNull();
    assertThat(retVal.size()).isEqualTo(1);
    assertThat(retVal.get(0).getName()).isEqualTo("foo");
    assertThat(retVal.get(0).getPreviousCount()).isEqualTo(1);
    assertThat(retVal.get(0).getDesiredCount()).isEqualTo(4);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetTimeoutFromContext() {
    EcsStateHelper helper = spy(new EcsStateHelper());

    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doReturn(ContainerServiceElement.builder().serviceSteadyStateTimeout(10).build())
        .when(helper)
        .getSetupElementFromSweepingOutput(any(), anyBoolean());
    assertThat(helper.getEcsStateTimeoutFromContext(mockContext, true)).isEqualTo(900000);

    doReturn(ContainerServiceElement.builder().serviceSteadyStateTimeout(0).build())
        .when(helper)
        .getSetupElementFromSweepingOutput(any(), anyBoolean());
    assertThat(helper.getEcsStateTimeoutFromContext(mockContext, true)).isEqualTo(null);

    doReturn(ContainerServiceElement.builder().build())
        .when(helper)
        .getSetupElementFromSweepingOutput(any(), anyBoolean());
    assertThat(helper.getEcsStateTimeoutFromContext(mockContext, true)).isEqualTo(null);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetTimeoutMillis() {
    assertThat(helper.getTimeout(10)).isEqualTo(900000);
    assertThat(helper.getTimeout(35792)).isNull();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testCreateSweepingOutputForRollbackFailure() throws InterruptedException {
    EcsDeployDataBag bag =
        EcsDeployDataBag.builder()
            .service(Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build())
            .app(anApplication().uuid(APP_ID).name(APP_NAME).build())
            .env(anEnvironment().uuid(ENV_ID).name(ENV_NAME).build())
            .ecsInfrastructureMapping(anEcsInfrastructureMapping()
                                          .withUuid(INFRA_MAPPING_ID)
                                          .withClusterName(CLUSTER_NAME)
                                          .withRegion("us-east-1")
                                          .withVpcId("vpc-id")
                                          .withAssignPublicIp(true)
                                          .withLaunchType("Ec2")
                                          .build())
            .awsConfig(AwsConfig.builder().build())
            .encryptedDataDetails(emptyList())
            .containerElement(ContainerServiceElement.builder().serviceSteadyStateTimeout(10).build())
            .build();
    DelegateService mockDelegateService = mock(DelegateService.class);
    ExecutionContext mockContext = mock(ExecutionContext.class);
    Activity activity = Activity.builder().uuid(ACTIVITY_ID).build();
    EcsResizeParams ecsResizeParams = mock(EcsResizeParams.class);
    doThrow(new InterruptedException()).when(mockDelegateService).executeTaskV2(any());

    assertThatThrownBy(()
                           -> helper.createSweepingOutputForRollback(
                               bag, activity, mockDelegateService, ecsResizeParams, mockContext, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Failed to generate rollback information");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testCreateSweepingOutputForRollback() throws InterruptedException {
    EcsDeployDataBag bag =
        EcsDeployDataBag.builder()
            .service(Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build())
            .app(anApplication().uuid(APP_ID).name(APP_NAME).build())
            .env(anEnvironment().uuid(ENV_ID).name(ENV_NAME).build())
            .ecsInfrastructureMapping(anEcsInfrastructureMapping()
                                          .withUuid(INFRA_MAPPING_ID)
                                          .withClusterName(CLUSTER_NAME)
                                          .withRegion("us-east-1")
                                          .withVpcId("vpc-id")
                                          .withAssignPublicIp(true)
                                          .withLaunchType("Ec2")
                                          .build())
            .awsConfig(AwsConfig.builder().build())
            .encryptedDataDetails(emptyList())
            .containerElement(ContainerServiceElement.builder().serviceSteadyStateTimeout(10).build())
            .build();
    DelegateService mockDelegateService = mock(DelegateService.class);
    ExecutionContext mockContext = mock(ExecutionContext.class);
    Activity activity = Activity.builder().uuid(ACTIVITY_ID).build();
    EcsResizeParams ecsResizeParams = mock(EcsResizeParams.class);

    doReturn(EcsCommandExecutionResponse.builder()
                 .ecsCommandResponse(EcsDeployRollbackDataFetchResponse.builder().build())
                 .build())
        .when(mockDelegateService)
        .executeTaskV2(any());

    doNothing().when(sweepingOutputService).ensure(any());
    doReturn("").when(mockContext).appendStateExecutionId(any());
    doReturn(SweepingOutputInstance.builder())
        .doReturn(SweepingOutputInstance.builder())
        .when(mockContext)
        .prepareSweepingOutputBuilder(any());
    PhaseElement phaseElement = PhaseElement.builder()
                                    .phaseName("Rollback Phase1")
                                    .phaseNameForRollback("Phase1")
                                    .serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build())
                                    .build();
    doReturn(phaseElement).when(mockContext).getContextElement(any(), any());

    helper.createSweepingOutputForRollback(bag, activity, mockDelegateService, ecsResizeParams, mockContext, false);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(mockDelegateService).executeTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();

    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(2).isEqualTo(delegateTask.getData().getParameters().length);
    assertThat(delegateTask.getData().getParameters()[0] instanceof EcsDeployRollbackDataFetchRequest).isTrue();
    assertThat(delegateTask.getWaitId()).isEqualTo(ACTIVITY_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(APP_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.ENV_ID_FIELD)).isEqualTo(ENV_ID);
    assertThat(delegateTask.getSetupAbstractions().get(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD))
        .isEqualTo(INFRA_MAPPING_ID);

    verify(sweepingOutputService).ensure(any());
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testSetUpRemoteContainerTaskAndServiceSpecForEcsRoute53IfRequired() {
    // case remote service spec path null, remote task def path null
    GitFileConfig gitFileConfig = GitFileConfig.builder().serviceSpecFilePath(null).taskSpecFilePath(null).build();
    StoreType storeType = StoreType.CUSTOM;
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().gitFileConfig(gitFileConfig).storeType(storeType).build();
    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    applicationManifestMap.put(K8sValuesLocation.ServiceOverride, applicationManifest);
    EcsBGRoute53SetupStateExecutionData ecsBGRoute53SetupStateExecutionData =
        EcsBGRoute53SetupStateExecutionData.builder().applicationManifestMap(applicationManifestMap).build();
    EcsSetUpDataBag ecsSetUpDataBag = EcsSetUpDataBag.builder().build();
    ExecutionContext executionContext = mock(ExecutionContextImpl.class);
    doReturn(ecsBGRoute53SetupStateExecutionData).when(executionContext).getStateExecutionData();
    assertThatThrownBy(()
                           -> helper.setUpRemoteContainerTaskAndServiceSpecForEcsRoute53IfRequired(
                               executionContext, ecsSetUpDataBag, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Manifest does not contain the proper git file config");

    // case remote service spec path null, remote task def path not null
    gitFileConfig.setServiceSpecFilePath(null);
    gitFileConfig.setUseInlineServiceDefinition(true);
    gitFileConfig.setTaskSpecFilePath("taskPath");
    Map<String, GitFetchFilesResult> filesFromMultipleRepo = new HashMap<>();
    List<GitFile> gitFileList = new ArrayList<>();
    gitFileList.add(GitFile.builder().filePath("taskPath").fileContent("fileContent").build());
    GitFetchFilesResult gitFetchFilesResult = GitFetchFilesResult.builder().files(gitFileList).build();
    filesFromMultipleRepo.put("ServiceOverride", gitFetchFilesResult);
    GitFetchFilesFromMultipleRepoResult gitFetchFilesFromMultipleRepoResult =
        GitFetchFilesFromMultipleRepoResult.builder().filesFromMultipleRepo(filesFromMultipleRepo).build();
    ecsBGRoute53SetupStateExecutionData.setFetchFilesResult(gitFetchFilesFromMultipleRepoResult);
    applicationManifest.setGitFileConfig(gitFileConfig);
    ecsSetUpDataBag.setService(Service.builder().uuid("uuid").build());
    ecsSetUpDataBag.setApplication(Application.Builder.anApplication().appId("appId").build());

    Exception exceptionCaught = null;
    try {
      helper.setUpRemoteContainerTaskAndServiceSpecForEcsRoute53IfRequired(executionContext, ecsSetUpDataBag, null);
    } catch (Exception e) {
      exceptionCaught = e;
    }

    assertThat(exceptionCaught).isNull();

    // case remote service spec path not null, remote task def path not null
    gitFileConfig.setServiceSpecFilePath("servicePath");
    gitFileConfig.setUseInlineServiceDefinition(false);
    gitFileConfig.setTaskSpecFilePath("taskPath");
    filesFromMultipleRepo = new HashMap<>();
    gitFileList = new ArrayList<>();
    gitFileList.add(GitFile.builder().filePath("taskPath").fileContent("fileContent").build());
    gitFileList.add(GitFile.builder().filePath("servicePath").fileContent("fileContent").build());
    gitFetchFilesResult = GitFetchFilesResult.builder().files(gitFileList).build();
    filesFromMultipleRepo.put("ServiceOverride", gitFetchFilesResult);
    gitFetchFilesFromMultipleRepoResult =
        GitFetchFilesFromMultipleRepoResult.builder().filesFromMultipleRepo(filesFromMultipleRepo).build();
    ecsBGRoute53SetupStateExecutionData.setFetchFilesResult(gitFetchFilesFromMultipleRepoResult);
    applicationManifest.setGitFileConfig(gitFileConfig);
    ecsSetUpDataBag.setService(Service.builder().uuid("uuid").build());
    ecsSetUpDataBag.setApplication(Application.Builder.anApplication().appId("appId").build());

    exceptionCaught = null;
    try {
      helper.setUpRemoteContainerTaskAndServiceSpecForEcsRoute53IfRequired(executionContext, ecsSetUpDataBag, null);
    } catch (Exception e) {
      exceptionCaught = e;
    }

    assertThat(exceptionCaught).isNull();
  }

  @Test
  @Owner(developers = SAINATH)
  @Category(UnitTests.class)
  public void testSetUpRemoteContainerTaskAndServiceSpecIfRequired() {
    // case remote service spec path null, remote task def path null
    GitFileConfig gitFileConfig = GitFileConfig.builder().serviceSpecFilePath(null).taskSpecFilePath(null).build();
    StoreType storeType = StoreType.CUSTOM;
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().gitFileConfig(gitFileConfig).storeType(storeType).build();
    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    applicationManifestMap.put(K8sValuesLocation.ServiceOverride, applicationManifest);
    EcsSetupStateExecutionData ecsSetupStateExecutionData =
        EcsSetupStateExecutionData.builder().applicationManifestMap(applicationManifestMap).build();
    EcsSetUpDataBag ecsSetUpDataBag = EcsSetUpDataBag.builder().build();
    ExecutionContext executionContext = mock(ExecutionContextImpl.class);
    doReturn(ecsSetupStateExecutionData).when(executionContext).getStateExecutionData();
    assertThatThrownBy(
        () -> helper.setUpRemoteContainerTaskAndServiceSpecIfRequired(executionContext, ecsSetUpDataBag, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Manifest does not contain the proper git file config");

    // case remote service spec path null, remote task def path not null
    gitFileConfig.setServiceSpecFilePath(null);
    gitFileConfig.setUseInlineServiceDefinition(true);
    gitFileConfig.setTaskSpecFilePath("taskPath");
    Map<String, GitFetchFilesResult> filesFromMultipleRepo = new HashMap<>();
    List<GitFile> gitFileList = new ArrayList<>();
    gitFileList.add(GitFile.builder().filePath("taskPath").fileContent("fileContent").build());
    GitFetchFilesResult gitFetchFilesResult = GitFetchFilesResult.builder().files(gitFileList).build();
    filesFromMultipleRepo.put("ServiceOverride", gitFetchFilesResult);
    GitFetchFilesFromMultipleRepoResult gitFetchFilesFromMultipleRepoResult =
        GitFetchFilesFromMultipleRepoResult.builder().filesFromMultipleRepo(filesFromMultipleRepo).build();
    ecsSetupStateExecutionData.setFetchFilesResult(gitFetchFilesFromMultipleRepoResult);
    applicationManifest.setGitFileConfig(gitFileConfig);
    ecsSetUpDataBag.setService(Service.builder().uuid("uuid").build());
    ecsSetUpDataBag.setApplication(Application.Builder.anApplication().appId("appId").build());

    Exception exceptionCaught = null;
    try {
      helper.setUpRemoteContainerTaskAndServiceSpecIfRequired(executionContext, ecsSetUpDataBag, null);
    } catch (Exception e) {
      exceptionCaught = e;
    }

    assertThat(exceptionCaught).isNull();

    // case remote service spec path not null, remote task def path not null
    gitFileConfig.setServiceSpecFilePath("servicePath");
    gitFileConfig.setUseInlineServiceDefinition(false);
    gitFileConfig.setTaskSpecFilePath("taskPath");
    filesFromMultipleRepo = new HashMap<>();
    gitFileList = new ArrayList<>();
    gitFileList.add(GitFile.builder().filePath("taskPath").fileContent("fileContent").build());
    gitFileList.add(GitFile.builder().filePath("servicePath").fileContent("fileContent").build());
    gitFetchFilesResult = GitFetchFilesResult.builder().files(gitFileList).build();
    filesFromMultipleRepo.put("ServiceOverride", gitFetchFilesResult);
    gitFetchFilesFromMultipleRepoResult =
        GitFetchFilesFromMultipleRepoResult.builder().filesFromMultipleRepo(filesFromMultipleRepo).build();
    ecsSetupStateExecutionData.setFetchFilesResult(gitFetchFilesFromMultipleRepoResult);
    applicationManifest.setGitFileConfig(gitFileConfig);
    ecsSetUpDataBag.setService(Service.builder().uuid("uuid").build());
    ecsSetUpDataBag.setApplication(Application.Builder.anApplication().appId("appId").build());

    exceptionCaught = null;
    try {
      helper.setUpRemoteContainerTaskAndServiceSpecIfRequired(executionContext, ecsSetUpDataBag, null);
    } catch (Exception e) {
      exceptionCaught = e;
    }

    assertThat(exceptionCaught).isNull();
  }
}
