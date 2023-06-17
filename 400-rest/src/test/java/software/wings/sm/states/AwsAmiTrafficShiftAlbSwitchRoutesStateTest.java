/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SKIPPED;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.task.aws.LbDetailsForAlbTrafficShift;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.AmiServiceTrafficShiftAlbSetupElement;
import software.wings.api.AwsAmiTrafficShiftAlbStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.AwsConfig;
import software.wings.beans.Service;
import software.wings.beans.command.SpotinstDummyCommandUnit;
import software.wings.service.impl.aws.model.AwsAmiPreDeploymentData;
import software.wings.service.impl.aws.model.AwsAmiSwitchRoutesResponse;
import software.wings.service.impl.aws.model.AwsAmiTrafficShiftAlbSwitchRouteRequest;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.states.spotinst.SpotInstStateHelper;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class AwsAmiTrafficShiftAlbSwitchRoutesStateTest extends WingsBaseTest {
  @Mock private SpotInstStateHelper spotinstStateHelper;
  @Mock private AwsAmiServiceStateHelper awsAmiServiceHelper;
  @Mock private DelegateService delegateService;
  @Mock private ActivityService activityService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;

  private final List<LbDetailsForAlbTrafficShift> lbDetails = getLbDetailsForAlbTrafficShifts();
  @InjectMocks
  private final AwsAmiTrafficShiftAlbSwitchRoutesState switchRoutesState =
      new AwsAmiTrafficShiftAlbSwitchRoutesState("switch-route-state");
  @InjectMocks
  private final AwsAmiRollbackTrafficShiftAlbSwitchRoutesState switchRouteRollbackState =
      new AwsAmiRollbackTrafficShiftAlbSwitchRoutesState("switch-route-rollback-state");

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteExecute() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRoutesState, true, true);
    ExecutionResponse response = switchRoutesState.execute(mockContext);
    verifyDelegateTaskCreationResult(response, false);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteExecuteFailure() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRoutesState, false, true);
    ExecutionResponse response = switchRoutesState.execute(mockContext);
    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteExecuteSetupElementFailure() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRoutesState, false, false);
    doAnswer(invocation -> { throw new NullPointerException(); })
        .when(awsAmiServiceHelper)
        .getSetupElementFromSweepingOutput(mockContext, AMI_ALB_SETUP_SWEEPING_OUTPUT_NAME);
    ExecutionResponse response = switchRoutesState.execute(mockContext);
    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteHandleAsyncResponse() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRoutesState, true, true);
    AwsAmiSwitchRoutesResponse amiServiceDeployResponse = AwsAmiSwitchRoutesResponse.builder()
                                                              .executionStatus(SUCCESS)
                                                              .delegateMetaInfo(DelegateMetaInfo.builder().build())
                                                              .build();
    ExecutionResponse response =
        switchRoutesState.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, amiServiceDeployResponse));
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testValidateFields() {
    initializeMockSetup(switchRoutesState, true, true);
    Map<String, String> fieldsMap = switchRoutesState.validateFields();
    assertThat(fieldsMap).isNotNull();
    assertThat(fieldsMap.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteRollBackExecute() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRouteRollbackState, true, true);
    ExecutionResponse response = switchRouteRollbackState.execute(mockContext);
    verifyDelegateTaskCreationResult(response, true);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteRollBackSetupElementFailure() {
    ExecutionContextImpl mockContext = initializeMockSetup(switchRouteRollbackState, true, false);
    ExecutionResponse response = switchRouteRollbackState.execute(mockContext);
    assertThat(response.getExecutionStatus()).isEqualTo(SKIPPED);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwitchRouteRollBackValidateFields() {
    initializeMockSetup(switchRouteRollbackState, true, true);
    Map<String, String> fieldsMap = switchRouteRollbackState.validateFields();
    assertThat(fieldsMap).isNotNull();
    assertThat(fieldsMap.size()).isEqualTo(0);
  }

  private ExecutionContextImpl initializeMockSetup(
      AwsAmiTrafficShiftAlbSwitchRoutesState routeState, boolean isSuccess, boolean contextElement) {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    routeState.setDownsizeOldAsg(true);
    on(routeState).set("spotinstStateHelper", spotinstStateHelper);
    on(routeState).set("awsAmiServiceHelper", awsAmiServiceHelper);
    on(routeState).set("delegateService", delegateService);
    on(routeState).set("activityService", activityService);
    on(routeState).set("featureFlagService", featureFlagService);

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
    doReturn(false).when(featureFlagService).isEnabled(any(), anyString());

    if (contextElement) {
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
              .detailsWithTargetGroups(lbDetails)
              .build();
      doReturn(setupElement)
          .when(awsAmiServiceHelper)
          .getSetupElementFromSweepingOutput(mockContext, AMI_ALB_SETUP_SWEEPING_OUTPUT_NAME);
    }

    AwsAmiTrafficShiftAlbStateExecutionData awsAmiTrafficShiftAlbStateExecutionData =
        AwsAmiTrafficShiftAlbStateExecutionData.builder().build();
    doReturn(awsAmiTrafficShiftAlbStateExecutionData).when(mockContext).getStateExecutionData();

    doNothing().when(spotinstStateHelper).saveInstanceInfoToSweepingOutput(eq(mockContext), anyInt());

    Activity activity = Activity.builder()
                            .uuid(ACTIVITY_ID)
                            .commandUnits(Collections.singletonList(new SpotinstDummyCommandUnit()))
                            .build();
    doReturn(activity)
        .when(spotinstStateHelper)
        .createActivity(eq(mockContext), eq(null), anyString(), anyString(), any(), any());
    if (!isSuccess) {
      doAnswer(invocation -> { throw new Exception(); }).when(delegateService).queueTaskV2(any());
    }
    return mockContext;
  }

  private void verifyDelegateTaskCreationResult(ExecutionResponse response, boolean isRollback) {
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());

    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(1).isEqualTo(delegateTask.getData().getParameters().length);
    assertThat(delegateTask.getData().getParameters()[0] instanceof AwsAmiTrafficShiftAlbSwitchRouteRequest).isTrue();
    AwsAmiTrafficShiftAlbSwitchRouteRequest params =
        (AwsAmiTrafficShiftAlbSwitchRouteRequest) delegateTask.getData().getParameters()[0];

    assertThat(params.getOldAsgName()).isEqualTo("oldAsg");
    assertThat(params.getNewAsgName()).isEqualTo("newAsg");
    assertThat(params.isDownscaleOldAsg()).isEqualTo(true);
    assertThat(params.isRollback()).isEqualTo(isRollback);
    assertThat(params.getLbDetails()).isEqualTo(lbDetails);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @NotNull
  private List<LbDetailsForAlbTrafficShift> getLbDetailsForAlbTrafficShifts() {
    return Collections.singletonList(LbDetailsForAlbTrafficShift.builder()
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
                                         .build());
  }
}
