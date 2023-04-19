/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.spotinst;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.delegate.beans.pcf.ResizeStrategy.RESIZE_NEW_FIRST;
import static io.harness.rule.OwnerRule.SATYAM;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.beans.AmiDeploymentType.SPOTINST;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.AwsAmiInfrastructureMapping.Builder.anAwsAmiInfrastructureMapping;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.InstanceUnitType.PERCENTAGE;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.spotinst.request.SpotInstDeployTaskParameters;
import io.harness.delegate.task.spotinst.request.SpotInstSetupTaskParameters;
import io.harness.delegate.task.spotinst.request.SpotInstTaskParameters;
import io.harness.delegate.task.spotinst.response.SpotInstDeployTaskResponse;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;

import software.wings.WingsBaseTest;
import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementListParam;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.service.impl.spotinst.SpotInstCommandRequest;
import software.wings.service.impl.spotinst.SpotInstCommandRequest.SpotInstCommandRequestBuilder;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionData;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.AwsStateHelper;
import software.wings.utils.WingsTestConstants;

import com.amazonaws.services.ec2.model.Instance;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDP)
public class SpotInstDeployStateTest extends WingsBaseTest {
  @Mock private AppService mockAppService;
  @Mock private InfrastructureMappingService mockInfrastructureMappingService;
  @Mock private DelegateService mockDelegateService;
  @Mock private SettingsService mockSettingsService;
  @Mock private ActivityService mockActivityService;
  @Mock private SpotInstStateHelper mockSpotinstStateHelper;
  @Spy private AwsStateHelper mockAwsStateHelper;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @InjectMocks SpotInstDeployState state = new SpotInstDeployState("stateName");

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecute() {
    state.setInstanceUnitType(PERCENTAGE);
    state.setInstanceCount(100);
    state.setDownsizeInstanceUnitType(PERCENTAGE);
    state.setDownsizeInstanceCount(0);
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doReturn(INFRA_MAPPING_ID).when(mockContext).fetchInfraMappingId();
    Environment env = anEnvironment().uuid(ENV_ID).build();
    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    doReturn(mockParams).when(mockContext).getContextElement(any());
    doReturn(env).when(workflowStandardParamsExtensionService).getEnv(mockParams);
    Application application = anApplication().appId(APP_ID).accountId(ACCOUNT_ID).uuid(APP_ID).build();
    doReturn(application).when(mockAppService).get(any());
    AwsAmiInfrastructureMapping infrastructureMapping =
        anAwsAmiInfrastructureMapping().withAmiDeploymentType(SPOTINST).withRegion("us-east-1").build();
    doReturn(infrastructureMapping).when(mockInfrastructureMappingService).get(any(), any());
    SpotInstSetupContextElement element =
        SpotInstSetupContextElement.builder()
            .isBlueGreen(false)
            .resizeStrategy(RESIZE_NEW_FIRST)
            .newElastiGroupOriginalConfig(
                ElastiGroup.builder()
                    .id("newId")
                    .name("foo__2")
                    .capacity(ElastiGroupCapacity.builder().minimum(1).maximum(4).target(2).build())
                    .build())
            .oldElastiGroupOriginalConfig(
                ElastiGroup.builder()
                    .id("oldId")
                    .name("foo__1")
                    .capacity(ElastiGroupCapacity.builder().minimum(1).maximum(4).target(2).build())
                    .build())
            .infraMappingId(INFRA_MAPPING_ID)
            .serviceId(SERVICE_ID)
            .appId(APP_ID)
            .envId(ENV_ID)
            .commandRequest(
                SpotInstCommandRequest.builder()
                    .spotInstTaskParameters(SpotInstSetupTaskParameters.builder().timeoutIntervalInMin(10).build())
                    .build())
            .build();
    doReturn(element).when(mockSpotinstStateHelper).getSetupElementFromSweepingOutput(any(), any());
    DelegateTask task = DelegateTask.builder().description("desc").build();
    doReturn(task)
        .when(mockSpotinstStateHelper)
        .getDelegateTask(any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(true));
    Activity activity = Activity.builder().uuid(ACTIVITY_ID).build();
    doReturn(activity).when(mockSpotinstStateHelper).createActivity(any(), any(), any(), any(), any(), anyList());
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(any(), any());
    SpotInstCommandRequestBuilder builder = SpotInstCommandRequest.builder();
    doReturn(builder).when(mockSpotinstStateHelper).generateSpotInstCommandRequest(any(), any());
    ExecutionResponse response = state.execute(mockContext);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    StateExecutionData stateExecutionData = response.getStateExecutionData();
    assertThat(stateExecutionData).isNotNull();
    assertThat(stateExecutionData instanceof SpotInstDeployStateExecutionData).isTrue();
    SpotInstDeployStateExecutionData deployData = (SpotInstDeployStateExecutionData) stateExecutionData;
    SpotInstCommandRequest spotinstCommandRequest = deployData.getSpotinstCommandRequest();
    assertThat(spotinstCommandRequest).isNotNull();
    SpotInstTaskParameters spotInstTaskParameters = spotinstCommandRequest.getSpotInstTaskParameters();
    assertThat(spotInstTaskParameters).isNotNull();
    assertThat(spotInstTaskParameters instanceof SpotInstDeployTaskParameters).isTrue();
    SpotInstDeployTaskParameters deployParams = (SpotInstDeployTaskParameters) spotInstTaskParameters;
    assertThat(deployParams.getNewElastiGroupWithUpdatedCapacity().getCapacity().getTarget()).isEqualTo(2);
    assertThat(deployParams.getOldElastiGroupWithUpdatedCapacity().getCapacity().getTarget()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    SpotInstTaskExecutionResponse delegateResponse =
        SpotInstTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .spotInstTaskResponse(SpotInstDeployTaskResponse.builder()
                                      .ec2InstancesAdded(singletonList(new Instance().withInstanceId("id-new")))
                                      .ec2InstancesExisting(singletonList(new Instance().withInstanceId("id-old")))
                                      .build())
            .build();
    SpotInstDeployStateExecutionData data = SpotInstDeployStateExecutionData.builder().build();
    doReturn(data).when(mockContext).getStateExecutionData();
    AwsAmiInfrastructureMapping infrastructureMapping =
        anAwsAmiInfrastructureMapping().withAmiDeploymentType(SPOTINST).withRegion("us-east-1").build();
    doReturn(infrastructureMapping).when(mockInfrastructureMappingService).get(any(), any());
    String newId = "new-uuid";
    String oldId = "old-uuid";
    doReturn(singletonList(anInstanceElement().uuid(newId).build()))
        .doReturn(singletonList(anInstanceElement().uuid(oldId).build()))
        .when(mockAwsStateHelper)
        .generateInstanceElements(anyList(), any(), any());
    doReturn(SweepingOutputInstance.builder())
        .when(mockContext)
        .prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW);
    doReturn(WingsTestConstants.STATE_EXECUTION_ID).when(mockContext).appendStateExecutionId(any());
    ExecutionResponse response = state.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, delegateResponse));
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    List<ContextElement> contextElements = response.getContextElements();
    assertThat(contextElements).isNotNull();
    assertThat(contextElements.size()).isEqualTo(1);
    ContextElement contextElement = contextElements.get(0);
    assertThat(contextElement instanceof InstanceElementListParam).isTrue();
    InstanceElementListParam param = (InstanceElementListParam) contextElement;
    assertThat(param.getInstanceElements().get(0).getUuid()).isEqualTo(newId);
    assertThat(param.getInstanceElements().get(0).isNewInstance()).isTrue();
    assertThat(param.getInstanceElements().get(1).getUuid()).isEqualTo(oldId);
    assertThat(param.getInstanceElements().get(1).isNewInstance()).isFalse();

    ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
    verify(mockSpotinstStateHelper, times(1))
        .saveInstanceInfoToSweepingOutput(any(ExecutionContext.class), captor.capture());
    assertThat(captor.getValue()).hasSize(2);
    assertThat(captor.getValue().stream().map(InstanceElement.class ::cast).count()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testValidateFields() {
    SpotInstDeployState stateLocal = new SpotInstDeployState("stateName_2");
    Map<String, String> fieldMap = stateLocal.validateFields();
    assertThat(fieldMap).isNotNull();
    assertThat(fieldMap.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetDownsizeUpdateCount() {
    SpotInstDeployState stateLocal = new SpotInstDeployState("stateName_" + System.currentTimeMillis());
    stateLocal.setDownsizeInstanceCount(100);
    stateLocal.setDownsizeInstanceUnitType(PERCENTAGE);
    ElastiGroupCapacity elastiGroupCapacity = ElastiGroupCapacity.builder().target(2).maximum(3).minimum(1).build();
    SpotInstSetupContextElement setupContextElement =
        SpotInstSetupContextElement.builder()
            .oldElastiGroupOriginalConfig(ElastiGroup.builder().capacity(elastiGroupCapacity).build())
            .build();

    Integer downsizeUpdateCount =
        stateLocal.getDownsizeUpdateCount(4, setupContextElement, OrchestrationWorkflowType.BLUE_GREEN);
    assertThat(downsizeUpdateCount).isEqualTo(2);
  }
}
