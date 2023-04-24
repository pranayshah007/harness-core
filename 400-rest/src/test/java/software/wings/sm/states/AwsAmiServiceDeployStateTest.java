/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.EnvironmentType.PROD;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.OrchestrationWorkflowType.BLUE_GREEN;
import static io.harness.beans.OrchestrationWorkflowType.CANARY;
import static io.harness.delegate.beans.pcf.ResizeStrategy.RESIZE_NEW_FIRST;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.AwsAmiInfrastructureMapping.Builder.anAwsAmiInfrastructureMapping;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.InstanceUnitType.COUNT;
import static software.wings.beans.InstanceUnitType.PERCENTAGE;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.CommandType.ENABLE;
import static software.wings.beans.command.ServiceCommand.Builder.aServiceCommand;
import static software.wings.beans.infrastructure.Host.Builder.aHost;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.service.impl.aws.model.AwsAmiPreDeploymentData.DEFAULT_DESIRED_COUNT;
import static software.wings.service.impl.aws.model.AwsConstants.AMI_SERVICE_SETUP_SWEEPING_OUTPUT_NAME;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_SOURCE_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.HOST_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_TEMPLATE_ID;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static jersey.repackaged.com.google.common.collect.Maps.newHashMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.deployment.InstanceDetails;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.AmiServiceSetupElement;
import software.wings.api.AwsAmiDeployStateExecutionData;
import software.wings.api.ContainerServiceData;
import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementListParam;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.instancedetails.InstanceInfoVariables;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.AwsConfig;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.AmiArtifactStream;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.command.AmiCommandUnit;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.infrastructure.Host;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.model.AwsAmiPreDeploymentData;
import software.wings.service.impl.aws.model.AwsAmiResizeData;
import software.wings.service.impl.aws.model.AwsAmiServiceDeployRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceDeployResponse;
import software.wings.service.impl.servicetemplates.ServiceTemplateHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.aws.manager.AwsAsgHelperServiceManager;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.amazonaws.services.ec2.model.Instance;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import dev.morphia.Key;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@OwnedBy(CDP)
public class AwsAmiServiceDeployStateTest extends WingsBaseTest {
  @Mock private AwsHelperService mockAwsHelperService;
  @Mock private SettingsService mockSettingsService;
  @Mock private ServiceResourceService mockServiceResourceService;
  @Mock private ServiceTemplateService mockServiceTemplateService;
  @Mock private InfrastructureMappingService mockInfrastructureMappingService;
  @Mock private ArtifactStreamService mockArtifactStreamService;
  @Mock private SecretManager mockSecretManager;
  @Mock private EncryptionService mockEncryptionService;
  @Mock private ActivityService mockActivityService;
  @Mock private DelegateService mockDelegateService;
  @Mock private LogService mockLogService;
  @Mock private SweepingOutputService mockSweepingOutputService;
  @Mock private HostService mockHostService;
  @Mock private AwsUtils mockAwsUtils;
  @Mock private AwsAsgHelperServiceManager mockAwsAsgHelperServiceManager;
  @Mock private ServiceTemplateHelper mockServiceTemplateHelper;
  @Mock private AwsStateHelper mockAwsStateHelper;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private AwsAmiServiceStateHelper awsAmiServiceStateHelper;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private FeatureFlagService mockFeatureFlagService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @InjectMocks private AwsAmiServiceDeployState state = new AwsAmiServiceDeployState("stateName");

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecute() {
    state.setInstanceUnitType(PERCENTAGE);
    state.setInstanceCount("100");
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    when(mockContext.renderExpression(any())).thenAnswer(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        return (String) args[0];
      }
    });
    doReturn(CANARY).doReturn(BLUE_GREEN).when(mockContext).getOrchestrationWorkflowType();
    PhaseElement phaseElement =
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build()).build();
    String asg1 = "foo__1";
    String asg3 = "foo__3";

    AmiServiceSetupElement serviceSetupElement =
        AmiServiceSetupElement.builder()
            .blueGreen(false)
            .resizeStrategy(RESIZE_NEW_FIRST)
            .oldAsgNames(Lists.newArrayList(asg1))
            .oldAutoScalingGroupName(asg1)
            .preDeploymentData(AwsAmiPreDeploymentData.builder().desiredCapacity(1).oldAsgName(asg1).build())
            .autoScalingSteadyStateTimeout(10)
            .newAutoScalingGroupName(asg3)
            .minInstances(0)
            .desiredInstances(2)
            .maxInstances(2)
            .build();
    doReturn(phaseElement).when(mockContext).getContextElement(any(), any());
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    doReturn(EmbeddedUser.builder().email("user@harness.io").name("user").build()).when(mockParams).getCurrentUser();
    doReturn(serviceSetupElement)
        .when(awsAmiServiceStateHelper)
        .getSetupElementFromSweepingOutput(mockContext, AMI_SERVICE_SETUP_SWEEPING_OUTPUT_NAME);
    doReturn(mockParams).when(mockContext).getContextElement(any());
    Environment environment = anEnvironment().uuid(ENV_ID).environmentType(PROD).name(ENV_NAME).build();
    doReturn(environment).when(workflowStandardParamsExtensionService).getEnv(mockParams);
    doReturn(environment).when(mockContext).fetchRequiredEnvironment();
    Application application = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
    doReturn(application).when(workflowStandardParamsExtensionService).getApp(mockParams);
    Service service = Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build();
    doReturn(service).when(mockServiceResourceService).getWithDetails(any(), any());
    doReturn(false).when(mockFeatureFlagService).isEnabled(any(), any());
    doReturn(
        Arrays.asList(
            InstanceInfoVariables.builder()
                .instanceDetails(Arrays.asList(InstanceDetails.builder()
                                                   .hostName("h1")
                                                   .aws(InstanceDetails.AWS.builder().instanceId("instanceId1").build())
                                                   .newInstance(true)
                                                   .build()))
                .build(),
            InstanceInfoVariables.builder()
                .instanceDetails(Arrays.asList(InstanceDetails.builder()
                                                   .hostName("h2")
                                                   .aws(InstanceDetails.AWS.builder().instanceId("instanceId2").build())
                                                   .newInstance(true)
                                                   .build()))
                .build()))
        .when(sweepingOutputService)
        .findSweepingOutputsWithNamePrefix(any(), any());
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(any(), any());

    String revision = "ami-1234";
    Artifact artifact = anArtifact().withRevision(revision).build();
    doReturn(artifact).when(mockContext).getDefaultArtifactForService(any());
    ArtifactStream artifactStream =
        AmiArtifactStream.builder().uuid(ARTIFACT_STREAM_ID).sourceName(ARTIFACT_SOURCE_NAME).build();
    doReturn(artifactStream).when(mockArtifactStreamService).get(any());
    Command command = aCommand().withName("Ami-Command").withCommandType(ENABLE).build();
    ServiceCommand serviceCommand = aServiceCommand().withCommand(command).build();
    doReturn(serviceCommand).when(mockServiceResourceService).getCommandByName(any(), any(), any(), any());
    AmiCommandUnit commandUnit = new AmiCommandUnit();
    commandUnit.setName("Ami-Command-Unit");
    List<CommandUnit> commandUnits = singletonList(commandUnit);
    doReturn(commandUnits).when(mockServiceResourceService).getFlattenCommandUnitList(any(), any(), any(), any());
    Activity activity = Activity.builder().uuid(ACTIVITY_ID).appId(APP_ID).build();
    doReturn(activity).when(mockActivityService).save(any());
    String classicLb = "classicLb";
    String targetGroup = "targetGp";
    String baseAsg = "baseAsg";
    List<String> stageLbs = Arrays.asList("Stage_LB1", "Stage_LB2");
    List<String> stageTgs = Arrays.asList("Stage_TG1", "Stage_TG2");
    AwsAmiInfrastructureMapping infrastructureMapping = anAwsAmiInfrastructureMapping()
                                                            .withUuid(INFRA_MAPPING_ID)
                                                            .withEnvId(ENV_ID)
                                                            .withRegion("us-east-1")
                                                            .withClassicLoadBalancers(singletonList(classicLb))
                                                            .withTargetGroupArns(singletonList(targetGroup))
                                                            .withStageClassicLoadBalancers(stageLbs)
                                                            .withStageTargetGroupArns(stageTgs)
                                                            .withAutoScalingGroupName(baseAsg)
                                                            .build();
    doReturn(infrastructureMapping).when(mockInfrastructureMappingService).get(any(), any());
    SettingAttribute cloudProvider = aSettingAttribute().withValue(AwsConfig.builder().build()).build();
    doReturn(cloudProvider).when(mockSettingsService).get(any());
    doReturn(emptyList()).when(mockSecretManager).getEncryptionDetails(any(), any(), any());
    doReturn(ImmutableMap.of(asg1, 1, asg3, 0))
        .when(mockAwsAsgHelperServiceManager)
        .getDesiredCapacitiesOfAsgs(any(), anyList(), any(), anyList(), any());
    doReturn(0).when(mockAwsStateHelper).fetchRequiredAsgCapacity(anyMap(), any());
    ExecutionResponse response = state.execute(mockContext);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(mockDelegateService, times(1)).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(1).isEqualTo(delegateTask.getData().getParameters().length);
    assertThat(delegateTask.getData().getParameters()[0] instanceof AwsAmiServiceDeployRequest).isTrue();
    AwsAmiServiceDeployRequest params = (AwsAmiServiceDeployRequest) delegateTask.getData().getParameters()[0];
    assertThat(params).isNotNull();
    assertThat(params.getNewAutoScalingGroupName()).isEqualTo(asg3);
    assertThat(params.getNewAsgFinalDesiredCount()).isEqualTo(2);
    List<AwsAmiResizeData> asgDesiredCounts = params.getAsgDesiredCounts();
    assertThat(asgDesiredCounts).isNotNull();
    assertThat(asgDesiredCounts.size()).isEqualTo(1);
    assertThat(asgDesiredCounts.get(0).getAsgName()).isEqualTo(asg1);
    assertThat(asgDesiredCounts.get(0).getDesiredCount()).isEqualTo(0);
    assertThat(params.getInfraMappingClassisLbs().size()).isEqualTo(1);
    assertThat(params.getInfraMappingClassisLbs().get(0)).isEqualTo(classicLb);
    assertThat(params.getInfraMappingTargetGroupArns().size()).isEqualTo(1);
    assertThat(params.getInfraMappingTargetGroupArns().get(0)).isEqualTo(targetGroup);
    assertThat(params.getExistingInstanceIds()).containsOnly("instanceId1", "instanceId2");

    // BG
    doReturn(mockParams).when(mockContext).getContextElement(any());
    response = state.execute(mockContext);
    captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(mockDelegateService, times(2)).queueTaskV2(captor.capture());
    delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(1).isEqualTo(delegateTask.getData().getParameters().length);
    assertThat(delegateTask.getData().getParameters()[0] instanceof AwsAmiServiceDeployRequest).isTrue();
    params = (AwsAmiServiceDeployRequest) delegateTask.getData().getParameters()[0];
    assertThat(params.getInfraMappingTargetGroupArns().size()).isEqualTo(2);
    assertThat(params.getInfraMappingTargetGroupArns()).containsAll(stageTgs);

    assertThat(params.getInfraMappingClassisLbs().size()).isEqualTo(2);
    assertThat(params.getInfraMappingClassisLbs()).containsAll(stageLbs);

    ArgumentCaptor<List> gpNamesCaptor = ArgumentCaptor.forClass(List.class);
    verify(mockAwsAsgHelperServiceManager, times(2))
        .getDesiredCapacitiesOfAsgs(any(), any(), any(), gpNamesCaptor.capture(), any());
    List gpNames = gpNamesCaptor.getValue();
    assertThat(gpNames).isNotEmpty();
    assertThat(gpNames.size()).isEqualTo(1);
    assertThat(gpNames).containsExactly(asg3);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testExecuteException() {
    doReturn(AmiServiceSetupElement.builder().build())
        .when(awsAmiServiceStateHelper)
        .getSetupElementFromSweepingOutput(null, AMI_SERVICE_SETUP_SWEEPING_OUTPUT_NAME);
    assertThatThrownBy(() -> state.execute(null)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    AwsAmiDeployStateExecutionData stateData =
        AwsAmiDeployStateExecutionData.builder()
            .newInstanceData(
                singletonList(ContainerServiceData.builder().name("foo__1").desiredCount(1).previousCount(0).build()))
            .build();
    doReturn(stateData).when(mockContext).getStateExecutionData();
    doReturn(APP_ID).when(mockContext).getAppId();
    AwsAmiServiceDeployResponse delegateResponse =
        AwsAmiServiceDeployResponse.builder()
            .executionStatus(SUCCESS)
            .instancesAdded(singletonList(new Instance()
                                              .withInstanceId("i-1234")
                                              .withPublicDnsName("public.dns")
                                              .withPrivateDnsName("private.dns")))
            .build();
    Activity activity = Activity.builder().uuid(ACTIVITY_ID).appId(APP_ID).build();
    doReturn(activity).when(mockActivityService).get(any(), any());
    AmiServiceSetupElement serviceSetupElement = AmiServiceSetupElement.builder().build();
    PhaseElement phaseElement =
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build()).build();
    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    doReturn(serviceSetupElement)
        .when(awsAmiServiceStateHelper)
        .getSetupElementFromSweepingOutput(mockContext, AMI_SERVICE_SETUP_SWEEPING_OUTPUT_NAME);
    doReturn(mockParams).when(mockContext).getContextElement(any());
    doReturn(phaseElement).when(mockContext).getContextElement(any(), any());
    Application application = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
    doReturn(application).when(workflowStandardParamsExtensionService).getApp(mockParams);
    doReturn(true).when(mockLogService).batchedSaveCommandUnitLogs(any(), any(), any());
    AwsAmiInfrastructureMapping infrastructureMapping =
        anAwsAmiInfrastructureMapping().withUuid(INFRA_MAPPING_ID).withEnvId(ENV_ID).withRegion("us-east-1").build();
    doReturn(infrastructureMapping).when(mockInfrastructureMappingService).get(any(), any());
    Environment environment = anEnvironment().uuid(ENV_ID).name(ENV_NAME).build();
    doReturn(environment).when(workflowStandardParamsExtensionService).getEnv(mockParams);
    Service service = Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build();
    doReturn(service).when(mockServiceResourceService).getWithDetails(any(), any());
    Key<ServiceTemplate> serviceTemplateKey = new Key<>(ServiceTemplate.class, "collection", "id");
    doReturn(singletonList(serviceTemplateKey))
        .when(mockServiceTemplateService)
        .getTemplateRefKeysByService(any(), any(), any());
    String revision = "ami-1234";
    Artifact artifact = anArtifact().withRevision(revision).build();
    doReturn(artifact).when(mockContext).getDefaultArtifactForService(any());
    doReturn(SERVICE_TEMPLATE_ID).when(mockServiceTemplateHelper).fetchServiceTemplateId(any());
    Map<String, Object> contextMap = newHashMap();
    doReturn(contextMap).when(mockContext).asMap();
    doReturn("hostName").when(mockAwsHelperService).getHostnameFromConvention(anyMap(), any());
    Host host = aHost().withUuid(HOST_ID).build();
    doReturn(host).when(mockHostService).saveHost(any());
    doReturn(null).when(sweepingOutputService).save(any());
    doReturn(SweepingOutputInstance.builder()).when(mockContext).prepareSweepingOutputBuilder(any());
    doReturn("").when(mockContext).appendStateExecutionId(any());
    ExecutionResponse response = state.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, delegateResponse));
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    List<ContextElement> contextElements = response.getContextElements();
    assertThat(contextElements).isNotNull();
    assertThat(contextElements.size()).isEqualTo(1);
    ContextElement contextElement = contextElements.get(0);
    assertThat(contextElement instanceof InstanceElementListParam).isTrue();
    InstanceElementListParam listParam = (InstanceElementListParam) contextElement;
    List<InstanceElement> instanceElements = listParam.getInstanceElements();
    assertThat(instanceElements).isNotNull();
    assertThat(instanceElements.size()).isEqualTo(1);
    assertThat(instanceElements.get(0).getUuid()).isEqualTo("i-1234");
    assertThat(instanceElements.get(0).getDisplayName()).isEqualTo("public.dns");
    assertThat(instanceElements.get(0).getHostName()).isEqualTo("hostName");

    // Exception Scenario
    doThrow(new InvalidRequestException("Failed")).when(mockInfrastructureMappingService).get(any(), any());
    doReturn(mockParams).when(mockContext).getContextElement(any());
    response = state.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, delegateResponse));
    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(response.getStateExecutionData().getStatus()).isEqualTo(FAILED);
    assertThat(response.getErrorMessage()).isEqualTo("Invalid request: Failed");
    assertThat(response.getStateExecutionData().getErrorMsg()).isEqualTo("Invalid request: Failed");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testAwsAmiPreDeploymentData() {
    AwsAmiPreDeploymentData awsAmiPreDeploymentData = AwsAmiPreDeploymentData.builder().build();

    assertThat(awsAmiPreDeploymentData.getPreDeploymentDesiredCapacity()).isEqualTo(DEFAULT_DESIRED_COUNT);
    assertThat(awsAmiPreDeploymentData.getPreDeploymentMinCapacity()).isEqualTo(0);
    assertThat(awsAmiPreDeploymentData.getPreDeploymenyScalingPolicyJSON()).isNotNull();
    assertThat(awsAmiPreDeploymentData.getPreDeploymenyScalingPolicyJSON()).isEmpty();

    awsAmiPreDeploymentData.setDesiredCapacity(2);
    awsAmiPreDeploymentData.setMinCapacity(1);
    awsAmiPreDeploymentData.setOldAsgName("asg");
    awsAmiPreDeploymentData.setScalingPolicyJSON(Arrays.asList("json"));

    assertThat(awsAmiPreDeploymentData.getPreDeploymentDesiredCapacity()).isEqualTo(2);
    assertThat(awsAmiPreDeploymentData.getPreDeploymentMinCapacity()).isEqualTo(1);
    assertThat(awsAmiPreDeploymentData.getPreDeploymenyScalingPolicyJSON().size()).isEqualTo(1);
    assertThat(awsAmiPreDeploymentData.getPreDeploymenyScalingPolicyJSON()).containsExactly("json");
    assertThat(awsAmiPreDeploymentData.getOldAsgName()).isEqualTo("asg");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetNewDesiredCounts() {
    // int instancesToBeAdded, String  oldAsgName, Map<String, Integer> existingDesiredCapacities
    String asgName = "asg";
    AwsAmiResizeData newDesiredCounts = state.getNewDesiredCounts(2, asgName, singletonMap(asgName, 4), false);
    assertThat(newDesiredCounts.getAsgName()).isEqualTo(asgName);
    assertThat(newDesiredCounts.getDesiredCount()).isEqualTo(2);

    newDesiredCounts = state.getNewDesiredCounts(2, asgName, singletonMap(asgName, 4), true);
    assertThat(newDesiredCounts.getAsgName()).isEqualTo(asgName);
    assertThat(newDesiredCounts.getDesiredCount()).isEqualTo(0);

    newDesiredCounts = state.getNewDesiredCounts(4, asgName, singletonMap(asgName, 4), false);
    assertThat(newDesiredCounts.getAsgName()).isEqualTo(asgName);
    assertThat(newDesiredCounts.getDesiredCount()).isEqualTo(0);

    newDesiredCounts = state.getNewDesiredCounts(4, asgName, singletonMap(asgName, 2), false);
    assertThat(newDesiredCounts.getAsgName()).isEqualTo(asgName);
    assertThat(newDesiredCounts.getDesiredCount()).isEqualTo(0);

    newDesiredCounts = state.getNewDesiredCounts(4, asgName, emptyMap(), false);
    assertThat(newDesiredCounts.getAsgName()).isEqualTo(asgName);
    assertThat(newDesiredCounts.getDesiredCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testIsFinalDeployState() {
    AwsAmiServiceDeployState stateLocal = spy(new AwsAmiServiceDeployState("localState"));
    stateLocal.setInstanceUnitType(PERCENTAGE);
    assertThat(stateLocal.isFinalDeployState(110, AmiServiceSetupElement.builder().build())).isTrue();
    stateLocal.setInstanceUnitType(COUNT);
    assertThat(stateLocal.isFinalDeployState(2, AmiServiceSetupElement.builder().desiredInstances(1).build())).isTrue();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetTimeoutMillis() {
    doReturn(10).when(mockAwsStateHelper).getAmiStateTimeout(any());
    assertThat(state.getTimeoutMillis(mock(ExecutionContextImpl.class))).isEqualTo(10);
  }
}
