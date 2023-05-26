/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.OrchestrationWorkflowType.BLUE_GREEN;
import static io.harness.beans.OrchestrationWorkflowType.CANARY;
import static io.harness.rule.OwnerRule.ANIL;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.AwsAmiInfrastructureMapping.Builder.anAwsAmiInfrastructureMapping;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.InstanceUnitType.PERCENTAGE;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.service.impl.aws.model.AwsConstants.AMI_ALB_SETUP_SWEEPING_OUTPUT_NAME;
import static software.wings.service.impl.aws.model.AwsConstants.BASE_DELAY_ACCOUNT_VARIABLE;
import static software.wings.service.impl.aws.model.AwsConstants.MAX_BACKOFF_ACCOUNT_VARIABLE;
import static software.wings.service.impl.aws.model.AwsConstants.MAX_ERROR_RETRY_ACCOUNT_VARIABLE;
import static software.wings.service.impl.aws.model.AwsConstants.THROTTLED_BASE_DELAY_ACCOUNT_VARIABLE;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.SweepingOutputInstance.SweepingOutputInstanceBuilder;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.task.aws.LbDetailsForAlbTrafficShift;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.AmiServiceTrafficShiftAlbSetupElement;
import software.wings.api.AwsAmiDeployStateExecutionData;
import software.wings.api.InstanceElement;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.beans.Activity;
import software.wings.beans.AmazonClientSDKDefaultBackoffStrategy;
import software.wings.beans.Application;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.AwsConfig;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.AmiArtifactStream;
import software.wings.beans.command.AmiCommandUnit;
import software.wings.beans.command.Command;
import software.wings.beans.command.ServiceCommand;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.aws.model.AwsAmiPreDeploymentData;
import software.wings.service.impl.aws.model.AwsAmiServiceDeployResponse;
import software.wings.service.impl.aws.model.AwsAmiServiceTrafficShiftAlbDeployRequest;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionData;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.spotinst.SpotInstStateHelper;

import com.amazonaws.services.ec2.model.Instance;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

@OwnedBy(CDP)
public class AwsAmiServiceTrafficShiftAlbDeployStateTest extends WingsBaseTest {
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private ActivityService activityService;
  @Mock private DelegateService delegateService;
  @Mock private InfrastructureMappingService mockInfrastructureMappingService;
  @Mock private LogService logService;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private AwsStateHelper awsStateHelper;
  @Mock private SettingsService mockSettingsService;
  @Mock private SecretManager mockSecretManager;
  @Spy @InjectMocks private AwsAmiServiceStateHelper awsAmiServiceHelper;
  @Mock private SpotInstStateHelper spotinstStateHelper;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @InjectMocks
  private final AwsAmiServiceTrafficShiftAlbDeployState state =
      spy(new AwsAmiServiceTrafficShiftAlbDeployState("deploy-state"));

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteSuccess() {
    ExecutionContextImpl mockContext = initializeMockSetup(true);
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(any(), any());
    ExecutionResponse response = state.execute(mockContext);
    verifyDelegateTaskCreationResult(response);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteFailure() {
    ExecutionContextImpl mockContext = initializeMockSetup(false);
    ExecutionResponse response = state.execute(mockContext);
    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    ExecutionContextImpl mockContext = initializeMockSetup(true);
    AwsAmiServiceDeployResponse amiServiceDeployResponse =
        AwsAmiServiceDeployResponse.builder()
            .delegateMetaInfo(DelegateMetaInfo.builder().build())
            .instancesAdded(Collections.singletonList(new Instance()))
            .build();
    ExecutionResponse response =
        state.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, amiServiceDeployResponse));
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testHandleAsyncFailureResponse() {
    ExecutionContextImpl mockContext = initializeMockSetup(true);
    AwsAmiServiceDeployResponse amiServiceDeployResponse =
        AwsAmiServiceDeployResponse.builder()
            .delegateMetaInfo(DelegateMetaInfo.builder().build())
            .instancesAdded(Collections.singletonList(new Instance()))
            .build();
    doAnswer(invocation -> { throw new Exception(); })
        .when(state.awsAmiServiceHelper)
        .populateAlbTrafficShiftSetupData(any());
    ExecutionResponse response =
        state.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, amiServiceDeployResponse));
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
  }

  @NotNull
  private ExecutionContextImpl initializeMockSetup(boolean isSuccess) {
    state.setInstanceUnitType(PERCENTAGE);
    state.setInstanceCountExpr("100");

    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    when(mockContext.renderExpression(any())).thenAnswer((Answer<String>) invocation -> {
      Object[] args = invocation.getArguments();
      return (String) args[0];
    });
    doReturn(CANARY).doReturn(BLUE_GREEN).when(mockContext).getOrchestrationWorkflowType();

    on(state).set("serviceResourceService", serviceResourceService);
    on(state).set("artifactStreamService", artifactStreamService);
    on(state).set("activityService", activityService);
    on(state).set("delegateService", delegateService);
    on(state).set("logService", logService);
    on(state).set("sweepingOutputService", sweepingOutputService);
    on(state).set("awsStateHelper", awsStateHelper);
    on(state).set("awsAmiServiceHelper", awsAmiServiceHelper);
    on(state).set("spotinstStateHelper", spotinstStateHelper);
    on(state).set("stateExecutionService", stateExecutionService);
    on(state).set("featureFlagService", featureFlagService);

    LbDetailsForAlbTrafficShift lbDetails = LbDetailsForAlbTrafficShift.builder()
                                                .loadBalancerName("lbName")
                                                .loadBalancerArn("lbArn")
                                                .listenerPort("port")
                                                .listenerArn("listArn")
                                                .useSpecificRule(true)
                                                .ruleArn("ruleArn")
                                                .prodTargetGroupName("prodTarget")
                                                .prodTargetGroupArn("prodTargetArn")
                                                .stageTargetGroupName("stageTarget")
                                                .stageTargetGroupArn("stageTargetArn")
                                                .build();

    AmiServiceTrafficShiftAlbSetupElement setupElement =
        AmiServiceTrafficShiftAlbSetupElement.builder()
            .newAutoScalingGroupName("newAsg")
            .oldAutoScalingGroupName("oldAsg")
            .baseScalingPolicyJSONs(Collections.singletonList("policyJson"))
            .minInstances(1)
            .maxInstances(4)
            .desiredInstances(2)
            .autoScalingSteadyStateTimeout(10)
            .commandName("COMMAND_NAME")
            .oldAsgNames(Collections.emptyList())
            .preDeploymentData(AwsAmiPreDeploymentData.builder().build())
            .detailsWithTargetGroups(Collections.singletonList(lbDetails))
            .build();
    doReturn(setupElement)
        .when(awsAmiServiceHelper)
        .getSetupElementFromSweepingOutput(mockContext, AMI_ALB_SETUP_SWEEPING_OUTPUT_NAME);

    PhaseElement phaseElement =
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build()).build();
    doReturn(phaseElement).when(mockContext).getContextElement(any(), any());
    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    doReturn(EmbeddedUser.builder().email("user@harness.io").name("user").build()).when(mockParams).getCurrentUser();
    doReturn(mockParams).when(mockContext).getContextElement(any());
    String revision = "ami-1234";
    Artifact artifact = anArtifact().withRevision(revision).build();
    doReturn(artifact).when(mockContext).getDefaultArtifactForService(any());
    Application application = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
    doReturn(application).when(workflowStandardParamsExtensionService).getApp(any());
    Environment environment = anEnvironment().uuid(ENV_ID).name(ENV_NAME).build();
    doReturn(environment).when(workflowStandardParamsExtensionService).getEnv(mockParams);
    Service service = Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build();
    doReturn(service).when(serviceResourceService).getWithDetails(any(), any());
    doReturn("10").when(mockContext).renderExpression(eq(BASE_DELAY_ACCOUNT_VARIABLE));
    doReturn("10").when(mockContext).renderExpression(eq(THROTTLED_BASE_DELAY_ACCOUNT_VARIABLE));
    doReturn("10").when(mockContext).renderExpression(eq(MAX_BACKOFF_ACCOUNT_VARIABLE));
    doReturn("10").when(mockContext).renderExpression(eq(MAX_ERROR_RETRY_ACCOUNT_VARIABLE));
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

    doReturn(AmiArtifactStream.builder().build()).when(artifactStreamService).get(any());
    doReturn(Activity.builder().uuid(ACTIVITY_ID).commandUnits(singletonList(new AmiCommandUnit())).build())
        .when(activityService)
        .save(any());
    doReturn(ServiceCommand.Builder.aServiceCommand()
                 .withName("ASG_COMMAND_NAME")
                 .withCommand(Command.Builder.aCommand().build())
                 .build())
        .when(serviceResourceService)
        .getCommandByName(any(), any(), any(), any());
    doReturn(emptyList()).when(serviceResourceService).getFlattenCommandUnitList(any(), any(), any(), any());

    SweepingOutputInstanceBuilder instanceBuilder = SweepingOutputInstance.builder();
    doReturn(instanceBuilder).when(mockContext).prepareSweepingOutputBuilder(any());
    doReturn("ExecutionId").when(mockContext).appendStateExecutionId(any());
    doReturn(SweepingOutputInstance.builder().build()).when(sweepingOutputService).save(any());

    InstanceElement instanceElement = new InstanceElement();
    instanceElement.setNewInstance(true);
    instanceElement.setUuid("id");
    instanceElement.setHostName("ec2-instance");
    doReturn(singletonList(instanceElement)).when(awsStateHelper).generateInstanceElements(any(), any(), any());
    doReturn(false).when(featureFlagService).isEnabled(any(), any());

    if (!isSuccess) {
      doAnswer(invocation -> { throw new Exception(); }).when(delegateService).queueTaskV2(any());
    }

    AwsAmiDeployStateExecutionData awsAmiDeployStateExecutionData = AwsAmiDeployStateExecutionData.builder().build();
    doReturn(awsAmiDeployStateExecutionData).when(mockContext).getStateExecutionData();
    doReturn(Activity.builder().uuid(ACTIVITY_ID).commandUnits(singletonList(new AmiCommandUnit())).build())
        .when(state.activityService)
        .get(any(), any());
    return mockContext;
  }

  private void verifyDelegateTaskCreationResult(ExecutionResponse response) {
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(state.delegateService, times(1)).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(delegateTask.getData().getParameters()[0] instanceof AwsAmiServiceTrafficShiftAlbDeployRequest).isTrue();
    AwsAmiServiceTrafficShiftAlbDeployRequest params =
        (AwsAmiServiceTrafficShiftAlbDeployRequest) delegateTask.getData().getParameters()[0];
    AmazonClientSDKDefaultBackoffStrategy sdkDefaultBackoffStrategy = AmazonClientSDKDefaultBackoffStrategy.builder()
                                                                          .baseDelayInMs(10)
                                                                          .throttledBaseDelayInMs(10)
                                                                          .maxBackoffInMs(10)
                                                                          .maxErrorRetry(10)
                                                                          .build();
    assertThat(params.getAwsConfig().getAmazonClientSDKDefaultBackoffStrategy()).isEqualTo(sdkDefaultBackoffStrategy);

    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    StateExecutionData stateExecutionData = response.getStateExecutionData();
    assertThat(stateExecutionData instanceof AwsAmiDeployStateExecutionData).isTrue();
    AwsAmiDeployStateExecutionData executionData = (AwsAmiDeployStateExecutionData) stateExecutionData;
    assertThat(executionData.getNewAutoScalingGroupName()).isEqualTo("newAsg");
    assertThat(executionData.getOldAutoScalingGroupName()).isEqualTo("oldAsg");
    assertThat(executionData.getMaxInstances()).isEqualTo(4);
    assertThat(executionData.getNewInstanceData().get(0).getDesiredCount()).isEqualTo(2);
  }
}
