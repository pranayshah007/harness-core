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

import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.AwsAmiInfrastructureMapping.Builder.anAwsAmiInfrastructureMapping;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.delegate.task.spotinst.response.SpotinstTrafficShiftAlbDeployResponse;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.spotinst.model.ElastiGroup;

import software.wings.WingsBaseTest;
import software.wings.api.InstanceElement;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.beans.Activity;
import software.wings.beans.AwsConfig;
import software.wings.beans.SpotInstConfig;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionData;
import software.wings.sm.states.AwsStateHelper;

import com.amazonaws.services.ec2.model.Instance;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class SpotinstTrafficShiftAlbDeployStateTest extends WingsBaseTest {
  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecute() {
    SpotinstTrafficShiftAlbDeployState state = spy(SpotinstTrafficShiftAlbDeployState.class);
    AwsStateHelper mockAwsStateHelper = mock(AwsStateHelper.class);
    on(state).set("awsStateHelper", mockAwsStateHelper);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(state).set("delegateService", mockDelegateService);
    DelegateTaskMigrationHelper mockDelegateTaskMigrationHelper = mock(DelegateTaskMigrationHelper.class);
    on(state).set("delegateTaskMigrationHelper", mockDelegateTaskMigrationHelper);
    ActivityService mockActivityService = mock(ActivityService.class);
    on(state).set("activityService", mockActivityService);
    SpotInstStateHelper mockSpotinstStateHelper = mock(SpotInstStateHelper.class);
    on(state).set("spotinstStateHelper", mockSpotinstStateHelper);
    InfrastructureMappingService mockInfrastructureMappingService = mock(InfrastructureMappingService.class);
    StateExecutionService mockStateExecutionService = mock(StateExecutionService.class);
    on(state).set("stateExecutionService", mockStateExecutionService);
    on(state).set("infrastructureMappingService", mockInfrastructureMappingService);
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
    doNothing().when(mockStateExecutionService).appendDelegateTaskDetails(any(), any());
    doReturn(DelegateTask.builder().description("desc").build())
        .when(mockSpotinstStateHelper)
        .getDelegateTask(any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(true));
    doReturn(SpotinstTrafficShiftAlbSetupElement.builder()
                 .oldElastiGroupOriginalConfig(ElastiGroup.builder().id("oldId").name("oldName").build())
                 .newElastiGroupOriginalConfig(ElastiGroup.builder().id("newId").name("newName").build())
                 .timeoutIntervalInMin(10)
                 .build())
        .when(mockSpotinstStateHelper)
        .getSetupElementFromSweepingOutput(any(), any());

    doReturn(Activity.builder().uuid(ACTIVITY_ID).build())
        .when(mockSpotinstStateHelper)
        .createActivity(any(), any(), any(), any(), any(), anyList());
    ExecutionResponse response = state.execute(mockContext);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    StateExecutionData stateExecutionData = response.getStateExecutionData();
    assertThat(stateExecutionData instanceof SpotinstTrafficShiftAlbDeployExecutionData).isTrue();
    SpotinstTrafficShiftAlbDeployExecutionData data = (SpotinstTrafficShiftAlbDeployExecutionData) stateExecutionData;
    assertThat(data.getOldElastigroupOriginalConfig().getId()).isEqualTo("oldId");
    assertThat(data.getOldElastigroupOriginalConfig().getName()).isEqualTo("oldName");
    assertThat(data.getNewElastigroupOriginalConfig().getId()).isEqualTo("newId");
    assertThat(data.getNewElastigroupOriginalConfig().getName()).isEqualTo("newName");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    SpotinstTrafficShiftAlbDeployState state = spy(SpotinstTrafficShiftAlbDeployState.class);
    AwsStateHelper mockAwsStateHelper = mock(AwsStateHelper.class);
    on(state).set("awsStateHelper", mockAwsStateHelper);
    DelegateService mockDelegateService = mock(DelegateService.class);
    on(state).set("delegateService", mockDelegateService);
    ActivityService mockActivityService = mock(ActivityService.class);
    on(state).set("activityService", mockActivityService);
    SpotInstStateHelper mockSpotinstStateHelper = mock(SpotInstStateHelper.class);
    on(state).set("spotinstStateHelper", mockSpotinstStateHelper);
    InfrastructureMappingService mockInfrastructureMappingService = mock(InfrastructureMappingService.class);
    on(state).set("infrastructureMappingService", mockInfrastructureMappingService);
    SpotInstTaskExecutionResponse delegateResponse =
        SpotInstTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .delegateMetaInfo(DelegateMetaInfo.builder().build())
            .spotInstTaskResponse(SpotinstTrafficShiftAlbDeployResponse.builder()
                                      .ec2InstancesAdded(singletonList(new Instance().withInstanceId("i-new")))
                                      .ec2InstancesExisting(singletonList(new Instance().withInstanceId("i-old")))
                                      .build())
            .build();
    doReturn(anAwsAmiInfrastructureMapping().withUuid(INFRA_MAPPING_ID).withSpotinstElastiGroupJson("json").build())
        .when(mockInfrastructureMappingService)
        .get(anyString(), anyString());
    List<InstanceElement> elements = singletonList(anInstanceElement().uuid("inst-id").build());
    doReturn(elements).when(mockAwsStateHelper).generateInstanceElements(anyList(), any(), any());
    SpotinstTrafficShiftAlbDeployExecutionData data = SpotinstTrafficShiftAlbDeployExecutionData.builder().build();
    ExecutionContext mockContext = mock(ExecutionContext.class);
    doReturn(data).when(mockContext).getStateExecutionData();
    ExecutionResponse response = state.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, delegateResponse));
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
  }
}
