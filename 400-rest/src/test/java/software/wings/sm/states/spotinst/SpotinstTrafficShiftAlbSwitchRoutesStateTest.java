/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.spotinst;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.spotinst.model.SpotInstConstants.SPOTINST_SERVICE_ALB_SETUP_SWEEPING_OUTPUT_NAME;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.AwsAmiInfrastructureMapping.Builder.anAwsAmiInfrastructureMapping;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.spotinst.model.ElastiGroup;

import software.wings.WingsBaseTest;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.beans.Activity;
import software.wings.beans.AwsConfig;
import software.wings.beans.SpotInstConfig;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionData;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class SpotinstTrafficShiftAlbSwitchRoutesStateTest extends WingsBaseTest {
  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecute() {
    SpotinstTrafficShiftAlbSwitchRoutesState state = spy(SpotinstTrafficShiftAlbSwitchRoutesState.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(state).set("delegateService", mockDelegateService);
    ActivityService mockActivityService = mock(ActivityService.class);
    on(state).set("activityService", mockActivityService);
    SpotInstStateHelper mockSpotinstStateHelper = mock(SpotInstStateHelper.class);
    on(state).set("spotinstStateHelper", mockSpotinstStateHelper);
    StateExecutionService stateExecutionService = mock(StateExecutionService.class);
    on(state).set("stateExecutionService", stateExecutionService);
    DelegateTaskMigrationHelper mockDelegateTaskMigrationHelper = mock(DelegateTaskMigrationHelper.class);
    on(state).set("delegateTaskMigrationHelper", mockDelegateTaskMigrationHelper);
    SpotinstTrafficShiftDataBag dataBag =
        SpotinstTrafficShiftDataBag.builder()
            .app(anApplication().uuid(APP_ID).build())
            .env(anEnvironment().uuid(ENV_ID).build())
            .infrastructureMapping(
                anAwsAmiInfrastructureMapping().withUuid(INFRA_MAPPING_ID).withSpotinstElastiGroupJson("json").build())
            .awsConfig(AwsConfig.builder().build())
            .awsEncryptedDataDetails(emptyList())
            .spotinstConfig(SpotInstConfig.builder().build())
            .spotinstEncryptedDataDetails(emptyList())
            .build();
    doReturn(dataBag).when(mockSpotinstStateHelper).getDataBag(any());
    PhaseElement mockPhaseElement = mock(PhaseElement.class);
    ExecutionContext mockContext = mock(ExecutionContext.class);
    doReturn(mockPhaseElement).when(mockContext).getContextElement(any(), any());
    doReturn(ServiceElement.builder().uuid(SERVICE_ID).build()).when(mockPhaseElement).getServiceElement();
    doReturn(SpotinstTrafficShiftAlbSetupElement.builder()
                 .oldElastiGroupOriginalConfig(ElastiGroup.builder().id("oldId").name("oldName").build())
                 .newElastiGroupOriginalConfig(ElastiGroup.builder().id("newId").name("newName").build())
                 .timeoutIntervalInMin(10)
                 .build())
        .when(mockSpotinstStateHelper)
        .getSetupElementFromSweepingOutput(mockContext, SPOTINST_SERVICE_ALB_SETUP_SWEEPING_OUTPUT_NAME);
    doReturn(Activity.builder().uuid(ACTIVITY_ID).build())
        .when(mockSpotinstStateHelper)
        .createActivity(any(), any(), any(), any(), any(), anyList());
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(any(), any());
    doReturn(DelegateTask.builder().description("desc").build())
        .when(mockSpotinstStateHelper)
        .getDelegateTask(any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(true));
    ExecutionResponse response = state.execute(mockContext);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    StateExecutionData stateExecutionData = response.getStateExecutionData();
    assertThat(stateExecutionData instanceof SpotinstTrafficShiftAlbSwapRoutesExecutionData).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    SpotinstTrafficShiftAlbSwitchRoutesState state = spy(SpotinstTrafficShiftAlbSwitchRoutesState.class);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(state).set("delegateService", mockDelegateService);
    ActivityService mockActivityService = mock(ActivityService.class);
    on(state).set("activityService", mockActivityService);
    SpotInstStateHelper mockSpotinstStateHelper = mock(SpotInstStateHelper.class);
    on(state).set("spotinstStateHelper", mockSpotinstStateHelper);
    on(state).set("sweepingOutputService", mock(SweepingOutputService.class));
    ExecutionContext mockContext = mock(ExecutionContext.class);
    doReturn(SweepingOutputInstance.builder()).when(mockContext).prepareSweepingOutputBuilder(Scope.WORKFLOW);
    doReturn("some-string").when(mockContext).appendStateExecutionId(any());
    SpotinstTrafficShiftAlbSwapRoutesExecutionData data =
        SpotinstTrafficShiftAlbSwapRoutesExecutionData.builder().build();
    doReturn(data).when(mockContext).getStateExecutionData();
    SpotInstTaskExecutionResponse delegateResponse =
        SpotInstTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
    ExecutionResponse response = state.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, delegateResponse));
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testValidateFields() {
    SpotinstTrafficShiftAlbSwitchRoutesState state = spy(SpotinstTrafficShiftAlbSwitchRoutesState.class);
    Map<String, String> fieldsMap = state.validateFields();
    assertThat(fieldsMap).isNotNull();
    assertThat(fieldsMap.size()).isEqualTo(1);
  }
}
