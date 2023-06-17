/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.api.CommandStateExecutionData.Builder.aCommandStateExecutionData;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.EcsInfrastructureMapping.Builder.anEcsInfrastructureMapping;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.command.EcsSetupParams.EcsSetupParamsBuilder.anEcsSetupParams;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.beans.DelegateTask;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.SweepingOutputInstance.SweepingOutputInstanceBuilder;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.model.ImageDetails;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.CommandStateExecutionData;
import software.wings.api.ContainerServiceElement;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.beans.Activity;
import software.wings.beans.AwsConfig;
import software.wings.beans.Service;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData;
import software.wings.beans.command.EcsSetupParams;
import software.wings.helpers.ext.ecs.request.EcsBGServiceSetupRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.helpers.ext.ecs.response.EcsServiceSetupResponse;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.utils.ApplicationManifestUtils;

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

public class EcsBlueGreenServiceSetupTest extends WingsBaseTest {
  @Mock private EcsStateHelper mockEcsStateHelper;
  @Mock private SecretManager mockSecretManager;
  @Mock private ActivityService mockActivityService;
  @Mock private AppService appService;
  @Mock private FeatureFlagService mockFeatureFlagService;
  @Mock private SettingsService mockSettingsService;
  @Mock private DelegateService mockDelegateService;
  @Mock private ArtifactCollectionUtils mockArtifactCollectionUtils;
  @Mock private ServiceResourceService mockServiceResourceService;
  @Mock private InfrastructureMappingService mockInfrastructureMappingService;
  @Mock private SweepingOutputService mockSweepingOutputService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private ApplicationManifestUtils applicationManifestUtils;

  @InjectMocks private EcsBlueGreenServiceSetup state = new EcsBlueGreenServiceSetup("stateName");

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecute() {
    state.setEcsServiceName("EcsSvc");
    state.setRoleArn("RoleArn");
    state.setLoadBalancerName("LbName");
    state.setProdListenerArn("ProdLArn");
    state.setStageListenerArn("StageLArn");
    state.setTargetPort("8080");
    state.setStageListenerPort("8181");
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    when(mockContext.renderExpression(nullable(String.class))).thenAnswer(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        return (String) args[0];
      }
    });
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
    doReturn(bag).when(mockEcsStateHelper).prepareBagForEcsSetUp(any(), anyInt(), any(), any(), any(), any(), any());
    Activity activity = Activity.builder().uuid(ACTIVITY_ID).build();
    doReturn(activity)
        .when(mockEcsStateHelper)
        .createActivity(any(), nullable(String.class), nullable(String.class), any(), any());
    EcsSetupParams params =
        anEcsSetupParams().withBlueGreen(false).withServiceName("EcsSvc").withClusterName(CLUSTER_NAME).build();
    doReturn(params).when(mockEcsStateHelper).buildContainerSetupParams(any(), any());
    CommandStateExecutionData executionData = aCommandStateExecutionData().build();
    doReturn(executionData)
        .when(mockEcsStateHelper)
        .getStateExecutionData(any(), nullable(String.class), any(), any(Activity.class));
    EcsSetupContextVariableHolder holder = EcsSetupContextVariableHolder.builder().build();
    doReturn(holder).when(mockEcsStateHelper).renderEcsSetupContextVariables(any());
    doReturn(DelegateTask.builder().uuid("DEL_TASK_ID").description("desc").build())
        .when(mockEcsStateHelper)
        .createAndQueueDelegateTaskForEcsServiceSetUp(any(), any(), nullable(String.class), any(), eq(true));
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(nullable(String.class), any());
    ExecutionResponse response = state.execute(mockContext);
    ArgumentCaptor<EcsSetupStateConfig> captor = ArgumentCaptor.forClass(EcsSetupStateConfig.class);
    verify(mockEcsStateHelper).buildContainerSetupParams(any(), captor.capture());
    EcsSetupStateConfig config = captor.getValue();
    assertThat(config).isNotNull();
    assertThat(config.getServiceName()).isEqualTo(SERVICE_NAME);
    assertThat(config.getApp()).isNotNull();
    assertThat(config.getApp().getUuid()).isEqualTo(APP_ID);
    assertThat(config.getService()).isNotNull();
    assertThat(config.getService().getUuid()).isEqualTo(SERVICE_ID);
    assertThat(config.getClusterName()).isEqualTo(CLUSTER_NAME);
    assertThat(config.getEcsServiceName()).isEqualTo("EcsSvc");
    assertThat(config.getLoadBalancerName()).isEqualTo("LbName");
    assertThat(config.getRoleArn()).isEqualTo("RoleArn");
    assertThat(config.getProdListenerArn()).isEqualTo("ProdLArn");
    assertThat(config.getStageListenerArn()).isEqualTo("StageLArn");
    assertThat(config.getTargetPort()).isEqualTo("8080");
    assertThat(config.getStageListenerPort()).isEqualTo("8181");
    ArgumentCaptor<EcsBGServiceSetupRequest> captor2 = ArgumentCaptor.forClass(EcsBGServiceSetupRequest.class);
    verify(mockEcsStateHelper)
        .createAndQueueDelegateTaskForEcsServiceSetUp(captor2.capture(), any(), any(String.class), any(), eq(true));
    EcsBGServiceSetupRequest request = captor2.getValue();
    assertThat(request).isNotNull();
    assertThat(request.getEcsSetupParams()).isNotNull();
    assertThat(request.getEcsSetupParams().getClusterName()).isEqualTo(CLUSTER_NAME);
    assertThat(request.getEcsSetupParams().getServiceName()).isEqualTo("EcsSvc");
    assertThat(request.getCluster()).isEqualTo(CLUSTER_NAME);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    EcsCommandExecutionResponse delegateResponse =
        EcsCommandExecutionResponse.builder()
            .commandExecutionStatus(SUCCESS)
            .ecsCommandResponse(EcsServiceSetupResponse.builder()
                                    .isBlueGreen(false)
                                    .setupData(ContainerSetupCommandUnitExecutionData.builder()
                                                   .containerServiceName("ContainerServiceName")
                                                   .instanceCountForLatestVersion(2)
                                                   .build())
                                    .build())
            .build();
    CommandStateExecutionData executionData = aCommandStateExecutionData().build();
    doReturn(executionData).when(mockContext).getStateExecutionData();
    PhaseElement phaseElement =
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build()).build();
    doReturn(phaseElement).when(mockContext).getContextElement(any(), nullable(String.class));
    Artifact artifact = anArtifact().withRevision("rev").build();
    doReturn(artifact).when(mockContext).getDefaultArtifactForService(nullable(String.class));
    ImageDetails details = ImageDetails.builder().name("imgName").tag("imgTag").build();
    doReturn(details).when(mockArtifactCollectionUtils).fetchContainerImageDetails(any(), nullable(String.class));
    ContainerServiceElement containerServiceElement = ContainerServiceElement.builder().build();
    doReturn(containerServiceElement)
        .when(mockEcsStateHelper)
        .buildContainerServiceElement(any(), any(), any(), nullable(String.class), nullable(String.class),
            nullable(String.class), any(), anyInt());
    SweepingOutputInstanceBuilder builder = SweepingOutputInstance.builder();
    doReturn(builder).when(mockContext).prepareSweepingOutputBuilder(any());
    doReturn("foo").when(mockEcsStateHelper).getSweepingOutputName(any(), anyBoolean(), nullable(String.class));
    doReturn(null).when(mockSweepingOutputService).save(any());
    ExecutionResponse response = state.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, delegateResponse));
    verify(mockEcsStateHelper)
        .buildContainerServiceElement(any(), any(), any(), nullable(String.class), nullable(String.class),
            nullable(String.class), any(), anyInt());
    verify(mockEcsStateHelper).populateFromDelegateResponse(any(), any(), any());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testValidateFields() {
    EcsBlueGreenServiceSetup state = spy(new EcsBlueGreenServiceSetup("ECS Service Setup Test"));
    state.setDesiredInstanceCount("runningInstances");
    Map<String, String> fieldsMap = state.validateFields();
    assertThat(fieldsMap).isNotNull();
    assertThat(fieldsMap.size()).isEqualTo(4);

    state.setDesiredInstanceCount("fixedInstances");
    fieldsMap = state.validateFields();
    assertThat(fieldsMap).isNotNull();
    assertThat(fieldsMap.size()).isEqualTo(3);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteThrowWingsException() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doThrow(new WingsException("test"))
        .when(mockEcsStateHelper)
        .prepareBagForEcsSetUp(any(), anyInt(), any(), any(), any(), any(), any());
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
        .prepareBagForEcsSetUp(any(), anyInt(), any(), any(), any(), any(), any());
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
    doThrow(new WingsException("test")).when(ecsCommandExecutionResponse).getCommandExecutionStatus();
    state.handleAsyncResponse(mockContext, delegateResponse);
    assertThatExceptionOfType(WingsException.class).isThrownBy(() -> state.execute(mockContext));
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
    state.setServiceSteadyStateTimeout(0);
    assertThat(state.getTimeoutMillis()).isNull();

    doReturn(600000).when(mockEcsStateHelper).getTimeout(10);
    state.setServiceSteadyStateTimeout(10);
    assertThat(state.getTimeoutMillis()).isEqualTo(10 * 60 * 1000);

    doReturn(null).when(mockEcsStateHelper).getTimeout(35792);
    state.setServiceSteadyStateTimeout(35792);
    assertThat(state.getTimeoutMillis()).isNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleAbortEvent() {
    EcsBlueGreenServiceSetup ecsBlueGreenServiceSetup = spy(state);
    ecsBlueGreenServiceSetup.handleAbortEvent(mock(ExecutionContextImpl.class));
    verify(ecsBlueGreenServiceSetup, times(1)).handleAbortEvent(any(ExecutionContext.class));
  }
}
