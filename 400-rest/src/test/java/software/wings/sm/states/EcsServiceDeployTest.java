/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.beans.FeatureName.TIMEOUT_FAILURE_SUPPORT;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.EcsInfrastructureMapping.Builder.anEcsInfrastructureMapping;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.InstanceUnitType.PERCENTAGE;
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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.ContainerServiceElement;
import software.wings.beans.Activity;
import software.wings.beans.AwsConfig;
import software.wings.beans.Service;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.ecs.request.EcsServiceDeployRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class EcsServiceDeployTest extends WingsBaseTest {
  @Mock private SecretManager mockSecretManager;
  @Mock private EcsStateHelper mockEcsStateHelper;
  @Mock private SettingsService mockSettingsService;
  @Mock private DelegateService mockDelegateService;
  @Mock private ActivityService mockActivityService;
  @Mock private ServiceResourceService mockServiceResourceService;
  @Mock private ServiceTemplateService mockServiceTemplateService;
  @Mock private InfrastructureMappingService mockInfrastructureMappingService;
  @Mock private ContainerDeploymentManagerHelper mockContainerDeploymentHelper;
  @Mock private FeatureFlagService mockFeatureFlagService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;

  @InjectMocks private EcsServiceDeploy state = new EcsServiceDeploy("stateName");

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecute() {
    state.setInstanceCount("20");
    state.setInstanceUnitType(PERCENTAGE);
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    when(mockContext.renderExpression(anyString())).thenAnswer(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        return (String) args[0];
      }
    });
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
    doReturn(bag).when(mockEcsStateHelper).prepareBagForEcsDeploy(any(), any(), any(), any(), any(), anyBoolean());
    Activity activity = Activity.builder().uuid(ACTIVITY_ID).build();
    doReturn(activity).when(mockEcsStateHelper).createActivity(any(), anyString(), anyString(), any(), any());
    doReturn(false).when(mockFeatureFlagService).isEnabled(any(), anyString());
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(anyString(), any());
    doReturn(DelegateTask.builder().description("desc").build())
        .when(mockEcsStateHelper)
        .createAndQueueDelegateTaskForEcsServiceDeploy(any(), any(), any(), any(), eq(true));
    ExecutionResponse response = state.execute(mockContext);
    ArgumentCaptor<EcsServiceDeployRequest> captor = ArgumentCaptor.forClass(EcsServiceDeployRequest.class);
    verify(mockEcsStateHelper)
        .createAndQueueDelegateTaskForEcsServiceDeploy(any(), captor.capture(), any(), any(), eq(true));
    verify(mockEcsStateHelper).createSweepingOutputForRollback(any(), any(), any(), any(), any(), eq(false));
    verify(mockFeatureFlagService).isEnabled(eq(TIMEOUT_FAILURE_SUPPORT), any());
    EcsServiceDeployRequest request = captor.getValue();
    assertThat(request).isNotNull();
    assertThat(request.getEcsResizeParams()).isNotNull();
    assertThat(request.getEcsResizeParams().getInstanceCount()).isEqualTo(20);
    assertThat(request.getEcsResizeParams().getDownsizeInstanceUnitType()).isEqualTo(PERCENTAGE);
    assertThat(request.getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(request.getCluster()).isEqualTo(CLUSTER_NAME);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    EcsCommandExecutionResponse executionResponse = EcsCommandExecutionResponse.builder().build();
    state.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, executionResponse));
    verify(mockEcsStateHelper)
        .handleDelegateResponseForEcsDeploy(any(), anyMap(), anyBoolean(), any(), anyBoolean(), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteNullDataBag() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doReturn(EcsDeployDataBag.builder().build())
        .when(mockEcsStateHelper)
        .prepareBagForEcsDeploy(any(), any(), any(), any(), any(), anyBoolean());
    ExecutionResponse response = state.execute(mockContext);
    assertThat(response.getExecutionStatus()).isEqualTo(SKIPPED);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteThrowWingsException() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doThrow(new WingsException("test"))
        .when(mockEcsStateHelper)
        .prepareBagForEcsDeploy(any(), any(), any(), any(), any(), anyBoolean());
    state.execute(mockContext);
    assertThatExceptionOfType(WingsException.class).isThrownBy(() -> state.execute(mockContext));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteThrowInvalidRequestException() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doThrow(new NullPointerException("test"))
        .when(mockEcsStateHelper)
        .prepareBagForEcsDeploy(any(), any(), any(), any(), any(), anyBoolean());
    state.execute(mockContext);
    assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> state.execute(mockContext));
  }

  @Test(expected = WingsException.class)
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseThrowWingsException() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    Map<String, ResponseData> delegateResponse = new HashMap<>();
    doThrow(new WingsException("test"))
        .when(mockEcsStateHelper)
        .handleDelegateResponseForEcsDeploy(any(), anyMap(), anyBoolean(), any(), anyBoolean(), any());
    state.handleAsyncResponse(mockContext, delegateResponse);
    assertThatExceptionOfType(WingsException.class).isThrownBy(() -> state.execute(mockContext));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseThrowInvalidRequestException() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    Map<String, ResponseData> delegateResponse = new HashMap<>();
    doThrow(new NullPointerException("test"))
        .when(mockEcsStateHelper)
        .handleDelegateResponseForEcsDeploy(any(), anyMap(), anyBoolean(), any(), anyBoolean(), any());
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
  public void testValidateFields() {
    state.setRollback(true);
    assertThat(state.validateFields()).isEmpty();

    state.setRollback(false);
    Map results = state.validateFields();
    assertThat(results.get("instanceCount")).isEqualTo("Instance count must not be blank");

    state.setRollback(false);
    state.setInstanceCount("2");
    assertThat(state.validateFields()).isEmpty();
  }
}
