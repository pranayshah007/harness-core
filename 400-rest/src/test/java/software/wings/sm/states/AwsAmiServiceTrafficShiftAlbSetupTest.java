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
import static io.harness.rule.OwnerRule.ANIL;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.AwsAmiInfrastructureMapping.Builder.anAwsAmiInfrastructureMapping;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.service.impl.aws.model.AwsConstants.AMI_ALB_SETUP_SWEEPING_OUTPUT_NAME;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
import io.harness.beans.ExecutionStatus;
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
import software.wings.api.AwsAmiSetupExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.AwsConfig;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.Service;
import software.wings.beans.command.AmiCommandUnit;
import software.wings.beans.command.Command;
import software.wings.beans.command.ServiceCommand;
import software.wings.service.impl.aws.model.AwsAmiServiceTrafficShiftAlbSetupRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceTrafficShiftAlbSetupResponse;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.states.spotinst.SpotInstStateHelper;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

@OwnedBy(CDP)
public class AwsAmiServiceTrafficShiftAlbSetupTest extends WingsBaseTest {
  @Mock private DelegateService delegateService;
  @Mock private ActivityService activityService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private SpotInstStateHelper spotInstStateHelper;
  @Mock private AwsAmiServiceStateHelper awsAmiServiceHelper;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private AwsStateHelper awsStateHelper;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @Captor private ArgumentCaptor<SweepingOutputInstance> sweepingOutputInstanceArgumentCaptor;

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteSuccess() {
    AwsAmiServiceTrafficShiftAlbSetup state = spy(new AwsAmiServiceTrafficShiftAlbSetup("Setup Test"));
    DeploymentExecutionContext mockContext = mock(DeploymentExecutionContext.class);
    initializeMockSetup(state, mockContext, true);
    ExecutionResponse response = state.execute(mockContext);
    verifyTestResult(state, response);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteFailure() {
    AwsAmiServiceTrafficShiftAlbSetup state = spy(new AwsAmiServiceTrafficShiftAlbSetup("Setup Test"));
    DeploymentExecutionContext mockContext = mock(DeploymentExecutionContext.class);
    initializeMockSetup(state, mockContext, false);
    ExecutionResponse response = state.execute(mockContext);
    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testValidateFields() {
    AwsAmiServiceTrafficShiftAlbSetup state = spy(new AwsAmiServiceTrafficShiftAlbSetup("Setup Test"));
    Map<String, String> fieldsMap = state.validateFields();
    assertThat(fieldsMap).isNotNull();
    assertThat(fieldsMap.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    AwsAmiServiceTrafficShiftAlbSetup state = spy(new AwsAmiServiceTrafficShiftAlbSetup("Setup Test"));
    DeploymentExecutionContext mockContext = mock(DeploymentExecutionContext.class);
    AwsAmiSetupExecutionData awsAmiExecutionData = AwsAmiSetupExecutionData.builder().activityId(ACTIVITY_ID).build();
    List<LbDetailsForAlbTrafficShift> lbDetailsWithTargetGroups =
        singletonList(LbDetailsForAlbTrafficShift.builder()
                          .loadBalancerName("lbName")
                          .loadBalancerArn("lbArn")
                          .listenerArn("listArn")
                          .listenerPort("8080")
                          .useSpecificRule(true)
                          .ruleArn("ruleArn")
                          .prodTargetGroupName("prodTgtName")
                          .prodTargetGroupArn("prodTgtArn")
                          .stageTargetGroupName("stageTgtName")
                          .stageTargetGroupArn("stageTgtArn")
                          .build());

    SweepingOutputInstanceBuilder sweepingOutputInstanceBuilder = SweepingOutputInstance.builder();
    doReturn("test").when(awsAmiServiceHelper).getSweepingOutputName(mockContext, AMI_ALB_SETUP_SWEEPING_OUTPUT_NAME);
    doReturn(sweepingOutputInstanceBuilder)
        .when(mockContext)
        .prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW);

    AwsAmiServiceTrafficShiftAlbSetupResponse delegateResponse =
        initializeMockSetupForAsyncResponse(state, mockContext, lbDetailsWithTargetGroups, awsAmiExecutionData);
    ExecutionResponse response = state.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, delegateResponse));
    verifyAwsAmiTrafficShiftSetupAsyncResponse(awsAmiExecutionData, lbDetailsWithTargetGroups, response);
    verify(sweepingOutputService, times(1)).save(sweepingOutputInstanceArgumentCaptor.capture());
    verify(awsStateHelper, times(1)).populateAmiVariables(any(), any());
    AmiServiceTrafficShiftAlbSetupElement contextElement =
        (AmiServiceTrafficShiftAlbSetupElement) sweepingOutputInstanceArgumentCaptor.getValue().getValue();
    assertThat(contextElement).isNotNull();
    assertThat(contextElement instanceof AmiServiceTrafficShiftAlbSetupElement).isTrue();
    AmiServiceTrafficShiftAlbSetupElement setupElement = contextElement;

    assertThat(setupElement.getMinInstances()).isEqualTo(1);
    assertThat(setupElement.getDesiredInstances()).isEqualTo(2);
    assertThat(setupElement.getMaxInstances()).isEqualTo(4);
    assertThat(setupElement.getOldAutoScalingGroupName()).isEqualTo("ASG_1");
    assertThat(setupElement.getNewAutoScalingGroupName()).isEqualTo("ASG_2");
    assertThat(setupElement.getDetailsWithTargetGroups()).isEqualTo(lbDetailsWithTargetGroups);
  }

  private void initializeMockSetup(
      AwsAmiServiceTrafficShiftAlbSetup state, DeploymentExecutionContext mockContext, boolean isSuccess) {
    state.setMinInstancesExpr("0");
    state.setMaxInstancesExpr("1");
    state.setTargetInstancesExpr("1");
    state.setAutoScalingSteadyStateTimeout("10");
    state.setUseCurrentRunningCount(true);
    state.setLbDetails(singletonList(LbDetailsForAlbTrafficShift.builder()
                                         .loadBalancerName("lbName")
                                         .loadBalancerArn("lbArn")
                                         .listenerArn("listArn")
                                         .listenerPort("8080")
                                         .useSpecificRule(true)
                                         .ruleArn("ruleArn")
                                         .build()));
    on(state).set("activityService", activityService);
    on(state).set("serviceResourceService", serviceResourceService);
    on(state).set("spotinstStateHelper", spotInstStateHelper);
    on(state).set("delegateService", delegateService);
    on(state).set("awsAmiServiceHelper", awsAmiServiceHelper);
    on(state).set("awsStateHelper", awsStateHelper);
    on(state).set("stateExecutionService", stateExecutionService);
    on(state).set("featureFlagService", featureFlagService);
    on(state).set("delegateTaskMigrationHelper", delegateTaskMigrationHelper);

    when(mockContext.renderExpression(anyString())).thenAnswer((Answer<String>) invocation -> {
      Object[] args = invocation.getArguments();
      return (String) args[0];
    });

    AwsAmiTrafficShiftAlbData trafficShiftAlbData =
        AwsAmiTrafficShiftAlbData.builder()
            .artifact(anArtifact().withUuid(ARTIFACT_ID).build())
            .app(anApplication().uuid(APP_ID).build())
            .service(Service.builder().build())
            .env(anEnvironment().uuid(ENV_ID).build())
            .awsConfig(AwsConfig.builder().build())
            .infrastructureMapping(
                anAwsAmiInfrastructureMapping().withUuid(INFRA_MAPPING_ID).withSpotinstElastiGroupJson("json").build())
            .awsEncryptedDataDetails(emptyList())
            .region("region")
            .serviceId("serviceId")
            .currentUser(EmbeddedUser.builder().build())
            .build();
    doReturn(trafficShiftAlbData).when(awsAmiServiceHelper).populateAlbTrafficShiftSetupData(mockContext);
    doReturn(Activity.builder().uuid(ACTIVITY_ID).commandUnits(singletonList(new AmiCommandUnit())).build())
        .when(activityService)
        .save(any());
    doReturn(ServiceCommand.Builder.aServiceCommand()
                 .withName("Aws Ami Setup")
                 .withCommand(Command.Builder.aCommand().build())
                 .build())
        .when(serviceResourceService)
        .getCommandByName(any(), any(), any(), any());
    doReturn(emptyList()).when(serviceResourceService).getFlattenCommandUnitList(any(), any(), any(), any());
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(anyString(), any());
    doReturn(false).when(featureFlagService).isEnabled(any(), anyString());
    if (!isSuccess) {
      doAnswer(invocation -> { throw new Exception(); }).when(delegateService).queueTaskV2(any());
    }
  }

  private void verifyTestResult(AwsAmiServiceTrafficShiftAlbSetup state, ExecutionResponse response) {
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(1)).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(delegateTask.getData().getParameters()[0] instanceof AwsAmiServiceTrafficShiftAlbSetupRequest).isTrue();

    assertThat(response).isNotNull();
    assertThat(response.getCorrelationIds().size()).isEqualTo(1);
    assertThat(response.getCorrelationIds().get(0)).isEqualTo(ACTIVITY_ID);
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getStateExecutionData()).isNotNull();
    assertThat(response.getStateExecutionData() instanceof AwsAmiSetupExecutionData).isTrue();
    AwsAmiSetupExecutionData data = (AwsAmiSetupExecutionData) response.getStateExecutionData();
    assertThat(data.getActivityId()).isEqualTo(ACTIVITY_ID);
  }

  private AwsAmiServiceTrafficShiftAlbSetupResponse initializeMockSetupForAsyncResponse(
      AwsAmiServiceTrafficShiftAlbSetup state, DeploymentExecutionContext mockContext,
      List<LbDetailsForAlbTrafficShift> lbDetailsWithTargetGroups, AwsAmiSetupExecutionData awsAmiExecutionData) {
    on(state).set("useCurrentRunningCount", true);
    on(state).set("autoScalingGroupName", "asg");
    on(state).set("awsAmiServiceHelper", awsAmiServiceHelper);
    on(state).set("sweepingOutputService", sweepingOutputService);

    ActivityService mockActivityService = mock(ActivityService.class);
    SpotInstStateHelper mockSpotinstStateHelper = mock(SpotInstStateHelper.class);
    on(state).set("activityService", mockActivityService);
    on(state).set("spotinstStateHelper", mockSpotinstStateHelper);
    on(state).set("featureFlagService", featureFlagService);
    on(state).set("awsStateHelper", awsStateHelper);

    doReturn(10).when(mockSpotinstStateHelper).renderCount(anyString(), any(), anyInt());

    AwsAmiServiceTrafficShiftAlbSetupResponse delegateResponse =
        AwsAmiServiceTrafficShiftAlbSetupResponse.builder()
            .delegateMetaInfo(DelegateMetaInfo.builder().build())
            .executionStatus(ExecutionStatus.SUCCESS)
            .lbDetailsWithTargetGroups(lbDetailsWithTargetGroups)
            .newAsgName("ASG_2")
            .lastDeployedAsgName("ASG_1")
            .minInstances(1)
            .desiredInstances(2)
            .maxInstances(4)
            .harnessRevision(5)
            .build();

    doReturn(awsAmiExecutionData).when(mockContext).getStateExecutionData();
    doReturn(false).when(featureFlagService).isEnabled(any(), anyString());

    when(mockContext.renderExpression(anyString())).thenAnswer((Answer<String>) invocation -> {
      Object[] args = invocation.getArguments();
      return (String) args[0];
    });
    return delegateResponse;
  }

  private void verifyAwsAmiTrafficShiftSetupAsyncResponse(AwsAmiSetupExecutionData awsAmiExecutionData,
      List<LbDetailsForAlbTrafficShift> lbDetailsWithTargetGroups, ExecutionResponse response) {
    assertThat(awsAmiExecutionData.getOldAutoScalingGroupName()).isEqualTo("ASG_1");
    assertThat(awsAmiExecutionData.getNewAutoScalingGroupName()).isEqualTo("ASG_2");
    assertThat(awsAmiExecutionData.getNewVersion()).isEqualTo(5);

    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);

    List<ContextElement> contextElements = response.getContextElements();
    assertThat(contextElements).isNotNull();
    assertThat(contextElements.size()).isEqualTo(0);
  }
}
