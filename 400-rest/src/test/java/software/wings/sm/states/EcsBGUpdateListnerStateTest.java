/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.api.DeploymentType.ECS;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.EcsInfrastructureMapping.Builder.anEcsInfrastructureMapping;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.CLUSTER_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.ContainerServiceElement;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.ecs.EcsBGSetupData;
import software.wings.api.ecs.EcsListenerUpdateStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.SettingAttribute;
import software.wings.helpers.ext.ecs.request.EcsListenerUpdateRequestConfigData;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class EcsBGUpdateListnerStateTest extends WingsBaseTest {
  @Mock private AppService mockAppService;
  @Mock private InfrastructureMappingService mockInfrastructureMappingService;
  @Mock private DelegateService mockDelegateService;
  @Mock private SecretManager mockSecretManager;
  @Mock private SettingsService mockSettingsService;
  @Mock private ActivityService mockActivityService;
  @Mock private EcsStateHelper mockEcsStateHelper;
  @Mock private LogService mockLogService;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @InjectMocks private EcsBGUpdateListnerState state = new EcsBGUpdateListnerState("stateName");
  @InjectMocks
  private EcsBGUpdateListnerRollbackState rollbackState = new EcsBGUpdateListnerRollbackState("rollbackState");

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecute() {
    state.setDownsizeOldService(true);
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    PhaseElement phaseElement = PhaseElement.builder()
                                    .deploymentType("ECS")
                                    .serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build())
                                    .build();
    doReturn(phaseElement).when(mockContext).getContextElement(any(), nullable(String.class));
    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    doReturn(mockParams).when(mockContext).getContextElement(any());
    Environment environment = anEnvironment().uuid(ENV_ID).name(ENV_NAME).build();
    doReturn(environment).when(workflowStandardParamsExtensionService).getEnv(mockParams);
    Application application = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
    doReturn(application).when(mockAppService).get(nullable(String.class));
    EcsInfrastructureMapping mapping = anEcsInfrastructureMapping()
                                           .withUuid(INFRA_MAPPING_ID)
                                           .withClusterName(CLUSTER_NAME)
                                           .withRegion("us-east-1")
                                           .withVpcId("vpc-id")
                                           .withAssignPublicIp(true)
                                           .withLaunchType("Ec2")
                                           .build();
    doReturn(mapping).when(mockInfrastructureMappingService).get(nullable(String.class), nullable(String.class));
    doReturn(INFRA_MAPPING_ID).when(mockContext).fetchInfraMappingId();
    doReturn(ContainerServiceElement.builder()
                 .infraMappingId(INFRA_MAPPING_ID)
                 .deploymentType(ECS)
                 .clusterName(CLUSTER_NAME)
                 .newEcsServiceName("EcsSvc__2")
                 .ecsRegion("us-east-1")
                 .targetGroupForNewService("TgtNew")
                 .targetGroupForExistingService("TgtOld")
                 .ecsBGSetupData(EcsBGSetupData.builder()
                                     .prodEcsListener("ProdLArn")
                                     .stageEcsListener("StageLArn")
                                     .downsizedServiceName("EcsSvc__1")
                                     .build())
                 .build())
        .doReturn(null)
        .when(mockEcsStateHelper)
        .getSetupElementFromSweepingOutput(any(), anyBoolean());
    Activity activity = Activity.builder().uuid(ACTIVITY_ID).build();
    doReturn(activity)
        .when(mockEcsStateHelper)
        .createActivity(any(), nullable(String.class), nullable(String.class), any(), any());
    SettingAttribute cloudProvider = aSettingAttribute().withValue(AwsConfig.builder().build()).build();
    doReturn(cloudProvider).when(mockSettingsService).get(nullable(String.class));
    doReturn(emptyList())
        .when(mockSecretManager)
        .getEncryptionDetails(any(), nullable(String.class), nullable(String.class));
    ExecutionResponse response = state.execute(mockContext);
    ArgumentCaptor<EcsListenerUpdateRequestConfigData> captor =
        ArgumentCaptor.forClass(EcsListenerUpdateRequestConfigData.class);
    verify(mockEcsStateHelper)
        .queueDelegateTaskForEcsListenerUpdate(any(), any(), any(), any(), nullable(String.class), any(),
            nullable(String.class), captor.capture(), anyList(), anyInt(), eq(true), nullable(String.class));
    EcsListenerUpdateRequestConfigData config = captor.getValue();
    assertThat(config).isNotNull();
    assertThat(config.getProdListenerArn()).isEqualTo("ProdLArn");
    assertThat(config.getStageListenerArn()).isEqualTo("StageLArn");
    assertThat(config.getServiceName()).isEqualTo("EcsSvc__2");
    assertThat(config.getClusterName()).isEqualTo(CLUSTER_NAME);
    assertThat(config.getRegion()).isEqualTo("us-east-1");
    assertThat(config.getServiceNameDownsized()).isEqualTo("EcsSvc__1");
    assertThat(config.isDownsizeOldService()).isEqualTo(true);
    assertThat(config.getTargetGroupForNewService()).isEqualTo("TgtNew");
    assertThat(config.getTargetGroupForExistingService()).isEqualTo("TgtOld");

    response = rollbackState.execute(mockContext);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SKIPPED);
    assertThat(response.getStateExecutionData().getErrorMsg()).isEqualTo("No container setup element found. Skipping.");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    EcsCommandExecutionResponse delegateResponse =
        EcsCommandExecutionResponse.builder().commandExecutionStatus(SUCCESS).build();
    EcsListenerUpdateStateExecutionData data = EcsListenerUpdateStateExecutionData.builder().build();
    doReturn(data).when(mockContext).getStateExecutionData();
    state.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, delegateResponse));
    assertThat(data.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testGetEcsListenerUpdateRequestConfigData() {
    ContainerServiceElement element =
        ContainerServiceElement.builder()
            .clusterName(CLUSTER_NAME)
            .ecsBGSetupData(
                EcsBGSetupData.builder().downsizedServiceName(SERVICE_NAME).downsizedServiceCount(100).build())
            .targetGroupForNewService("TARGET_GROUP")
            .build();
    ExecutionContext context = mock(ExecutionContextImpl.class);
    EcsListenerUpdateRequestConfigData configData =
        rollbackState.getEcsListenerUpdateRequestConfigData(element, context);
    assertThat(configData.isRollback()).isTrue();
    assertThat(configData.getServiceNameDownsized()).isEqualTo(SERVICE_NAME);
    assertThat(configData.getServiceCountDownsized()).isEqualTo(100);
    assertThat(configData.getTargetGroupForNewService()).isEqualTo("TARGET_GROUP");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteThrowInvalidRequestException() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doThrow(new NullPointerException("test")).when(mockContext).getContextElement(any());
    state.execute(mockContext);
    assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> state.execute(mockContext));
  }

  @Test(expected = WingsException.class)
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseThrowWingsException() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    Map<String, ResponseData> delegateResponse = new HashMap<>();
    EcsCommandExecutionResponse ecsCommandExecutionResponse = mock(EcsCommandExecutionResponse.class);
    delegateResponse.put("test", ecsCommandExecutionResponse);
    doThrow(new InvalidRequestException("test")).when(ecsCommandExecutionResponse).getCommandExecutionStatus();
    state.handleAsyncResponse(mockContext, delegateResponse);
    assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> state.execute(mockContext));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseThrowInvalidRequestException() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    Map<String, ResponseData> delegateResponse = new HashMap<>();
    EcsCommandExecutionResponse ecsCommandExecutionResponse = mock(EcsCommandExecutionResponse.class);
    delegateResponse.put("test", ecsCommandExecutionResponse);
    doThrow(new NullPointerException("test")).when(ecsCommandExecutionResponse).getCommandExecutionStatus();
    state.handleAsyncResponse(mockContext, delegateResponse);
    assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> state.execute(mockContext));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetTimeoutMillis() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doReturn(10).when(mockEcsStateHelper).getEcsStateTimeoutFromContext(any(), anyBoolean());
    assertThat(state.getTimeoutMillis(mockContext)).isEqualTo(10);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testisDownsizeOldService() {
    state.setDownsizeOldService(false);
    assertThat(state.isDownsizeOldService()).isFalse();
    state.setDownsizeOldService(true);
    assertThat(state.isDownsizeOldService()).isTrue();
  }
}
