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
import static io.harness.beans.OrchestrationWorkflowType.BASIC;
import static io.harness.beans.OrchestrationWorkflowType.BLUE_GREEN;
import static io.harness.beans.OrchestrationWorkflowType.CANARY;
import static io.harness.beans.OrchestrationWorkflowType.MULTI_SERVICE;
import static io.harness.beans.OrchestrationWorkflowType.ROLLING;
import static io.harness.delegate.beans.pcf.ResizeStrategy.RESIZE_NEW_FIRST;
import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.SATYAM;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.AwsAmiInfrastructureMapping.Builder.anAwsAmiInfrastructureMapping;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.CommandType.ENABLE;
import static software.wings.beans.command.ServiceCommand.Builder.aServiceCommand;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
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
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.AwsAmiSetupExecutionData;
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
import software.wings.beans.command.AmiCommandUnit;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.container.UserDataSpecification;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.aws.model.AwsAmiServiceSetupRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceSetupResponse;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry.SweepingOutputInquiryBuilder;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.spotinst.SpotInstStateHelper;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class AwsAmiServiceSetupTest extends WingsBaseTest {
  @Mock private SettingsService mockSettingsService;
  @Mock private ServiceResourceService mockServiceResourceService;
  @Mock private InfrastructureMappingService mockInfrastructureMappingService;
  @Mock private SecretManager mockSecretManager;
  @Mock private ActivityService mockActivityService;
  @Mock private LogService mockLogService;
  @Mock private DelegateService mockDelegateService;
  @Mock private SpotInstStateHelper mockSpotinstStateHelper;
  @Mock private SweepingOutputService mockSweepingOutputService;
  @Mock private AwsAmiServiceStateHelper mockAwsAmiServiceStateHelper;
  @Mock private AwsStateHelper awsStateHelper;
  @Mock private FeatureFlagService mockFeatureFlagService;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @InjectMocks private AwsAmiServiceSetup state = new AwsAmiServiceSetup("stateName");

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecute() {
    String asgName = "foo";
    state.setAutoScalingGroupName(asgName);
    state.setMaxInstances("2");
    state.setDesiredInstances("1");
    state.setMinInstances("0");
    state.setAutoScalingSteadyStateTimeout(10);
    state.setBlueGreen(false);
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doReturn(asgName).when(mockContext).renderExpression(any());
    PhaseElement phaseElement =
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build()).build();
    doReturn(phaseElement).when(mockContext).getContextElement(any(), any());
    doReturn(0)
        .doReturn(2)
        .doReturn(1)
        .doReturn(2)
        .doReturn(1)
        .when(mockSpotinstStateHelper)
        .renderCount(any(), any(), anyInt());
    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    doReturn(EmbeddedUser.builder().email("user@harness.io").name("user").build()).when(mockParams).getCurrentUser();
    doReturn(mockParams).when(mockContext).getContextElement(any());
    doReturn(false).when(mockFeatureFlagService).isEnabled(any(), any());
    String revision = "ami-1234";
    Artifact artifact = anArtifact().withRevision(revision).build();
    doReturn(artifact).when(mockContext).getDefaultArtifactForService(any());
    Application application = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
    doReturn(application).when(workflowStandardParamsExtensionService).getApp(mockParams);
    Environment environment = anEnvironment().uuid(ENV_ID).name(ENV_NAME).build();
    doReturn(environment).when(workflowStandardParamsExtensionService).getEnv(mockParams);
    Service service = Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build();
    doReturn(service).when(mockServiceResourceService).getWithDetails(any(), any());
    String classicLb = "classicLb";
    String targetGroup = "targetGp";
    String baseAsg = "baseAsg";
    AwsAmiInfrastructureMapping infrastructureMapping = anAwsAmiInfrastructureMapping()
                                                            .withUuid(INFRA_MAPPING_ID)
                                                            .withRegion("us-east-1")
                                                            .withClassicLoadBalancers(singletonList(classicLb))
                                                            .withTargetGroupArns(singletonList(targetGroup))
                                                            .withAutoScalingGroupName(baseAsg)
                                                            .build();
    doReturn(infrastructureMapping).when(mockInfrastructureMappingService).get(any(), any());
    SettingAttribute cloudProvider = aSettingAttribute().withValue(AwsConfig.builder().build()).build();
    doReturn(cloudProvider).when(mockSettingsService).get(any());
    doReturn(emptyList()).when(mockSecretManager).getEncryptionDetails(any(), any(), any());
    Command command = aCommand().withName("Ami-Command").withCommandType(ENABLE).build();
    ServiceCommand serviceCommand = aServiceCommand().withCommand(command).build();
    doReturn(serviceCommand).when(mockServiceResourceService).getCommandByName(any(), any(), any(), any());
    AmiCommandUnit commandUnit = new AmiCommandUnit();
    commandUnit.setName("Ami-Command-Unit");
    List<CommandUnit> commandUnits = singletonList(commandUnit);
    doReturn(commandUnits).when(mockServiceResourceService).getFlattenCommandUnitList(any(), any(), any(), any());
    Activity activity = Activity.builder().uuid(ACTIVITY_ID).appId(APP_ID).build();
    doReturn(activity).when(mockActivityService).save(any());
    doReturn(true).when(mockLogService).batchedSaveCommandUnitLogs(any(), any(), any());
    String userData = "userData";
    UserDataSpecification userDataSpecification = UserDataSpecification.builder().data(userData).build();
    doReturn(userDataSpecification).when(mockServiceResourceService).getUserDataSpecification(any(), any());
    doReturn("10").when(mockContext).renderExpression(eq(BASE_DELAY_ACCOUNT_VARIABLE));
    doReturn("10").when(mockContext).renderExpression(eq(THROTTLED_BASE_DELAY_ACCOUNT_VARIABLE));
    doReturn("10").when(mockContext).renderExpression(eq(MAX_BACKOFF_ACCOUNT_VARIABLE));
    doReturn("10").when(mockContext).renderExpression(eq(MAX_ERROR_RETRY_ACCOUNT_VARIABLE));
    AmazonClientSDKDefaultBackoffStrategy sdkDefaultBackoffStrategy = AmazonClientSDKDefaultBackoffStrategy.builder()
                                                                          .baseDelayInMs(10)
                                                                          .throttledBaseDelayInMs(10)
                                                                          .maxBackoffInMs(10)
                                                                          .maxErrorRetry(10)
                                                                          .build();
    ExecutionResponse response = state.execute(mockContext);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(mockDelegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(1).isEqualTo(delegateTask.getData().getParameters().length);
    assertThat(delegateTask.getData().getParameters()[0] instanceof AwsAmiServiceSetupRequest).isTrue();
    AwsAmiServiceSetupRequest params = (AwsAmiServiceSetupRequest) delegateTask.getData().getParameters()[0];
    assertThat(params.getInfraMappingClassisLbs().size()).isEqualTo(1);
    assertThat(params.getInfraMappingClassisLbs().get(0)).isEqualTo(classicLb);
    assertThat(params.getInfraMappingTargetGroupArns().size()).isEqualTo(1);
    assertThat(params.getInfraMappingTargetGroupArns().get(0)).isEqualTo(targetGroup);
    assertThat(params.getNewAsgNamePrefix()).isEqualTo(asgName);
    assertThat(params.getMinInstances()).isEqualTo(0);
    assertThat(params.getMaxInstances()).isEqualTo(2);
    assertThat(params.getDesiredInstances()).isEqualTo(1);
    assertThat(params.getArtifactRevision()).isEqualTo(revision);
    assertThat(params.getAwsConfig().getAmazonClientSDKDefaultBackoffStrategy()).isEqualTo(sdkDefaultBackoffStrategy);

    // ASG Name blank, should generate app.name__service.name__env.name
    state.setAutoScalingGroupName(null);
    response = state.execute(mockContext);
    captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(mockDelegateService, times(2)).queueTaskV2(captor.capture());
    delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(1).isEqualTo(delegateTask.getData().getParameters().length);
    assertThat(delegateTask.getData().getParameters()[0] instanceof AwsAmiServiceSetupRequest).isTrue();
    params = (AwsAmiServiceSetupRequest) delegateTask.getData().getParameters()[0];
    assertThat(params.getNewAsgNamePrefix())
        .isEqualTo(new StringBuilder(application.getName())
                       .append("__")
                       .append(service.getName())
                       .append("__")
                       .append(environment.getName())
                       .toString());
    state.setAutoScalingGroupName(asgName);

    // OrchestrationWorkflowType == BG
    state.setBlueGreen(true);
    doReturn(OrchestrationWorkflowType.BLUE_GREEN).when(mockContext).getOrchestrationWorkflowType();
    List<String> classicLBs = Arrays.asList("LB1", "LB2");
    List<String> stageTargetGroupArns = Arrays.asList("TG1", "TG2");
    infrastructureMapping.setStageClassicLoadBalancers(classicLBs);
    infrastructureMapping.setStageTargetGroupArns(stageTargetGroupArns);

    state.execute(mockContext);
    captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(mockDelegateService, times(3)).queueTaskV2(captor.capture());
    delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(1).isEqualTo(delegateTask.getData().getParameters().length);
    assertThat(delegateTask.getData().getParameters()[0] instanceof AwsAmiServiceSetupRequest).isTrue();
    params = (AwsAmiServiceSetupRequest) delegateTask.getData().getParameters()[0];
    assertThat(params.getInfraMappingClassisLbs()).containsAll(classicLBs);
    assertThat(params.getInfraMappingTargetGroupArns()).containsAll(stageTargetGroupArns);

    // Exception in execute
    doThrow(new InvalidRequestException("Failed")).when(mockDelegateService).queueTaskV2(any());
    response = state.execute(mockContext);
    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(response.getStateExecutionData().getStatus()).isEqualTo(FAILED);
    assertThat(response.getErrorMessage()).isEqualTo("Invalid request: Failed");
    assertThat(response.getStateExecutionData().getErrorMsg()).isEqualTo("Invalid request: Failed");

    // Artifact is NULL. Should Receive InvalidRequestException
    doReturn(null).when(mockContext).getDefaultArtifactForService(any());
    assertThatThrownBy(() -> state.execute(mockContext)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    AwsAmiSetupExecutionData stateData = AwsAmiSetupExecutionData.builder().build();
    doReturn(stateData).when(mockContext).getStateExecutionData();
    doReturn("test").when(mockAwsAmiServiceStateHelper).getSweepingOutputName(any(), any());
    doReturn(SweepingOutputInstance.builder())
        .doReturn(SweepingOutputInstance.builder())
        .when(mockContext)
        .prepareSweepingOutputBuilder(any());
    String newAsgName = "foo__2";

    SweepingOutputInquiryBuilder builder = SweepingOutputInquiry.builder();
    doReturn(builder).when(mockContext).prepareSweepingOutputInquiryBuilder();

    AwsAmiServiceSetupResponse delegateResponse = AwsAmiServiceSetupResponse.builder()
                                                      .executionStatus(SUCCESS)
                                                      .newAsgName(newAsgName)
                                                      .minInstances(0)
                                                      .maxInstances(2)
                                                      .desiredInstances(1)
                                                      .build();
    ExecutionResponse response = state.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, delegateResponse));
    verify(mockSweepingOutputService, times(1)).save(any());
    verify(awsStateHelper).populateAmiVariables(any(), any());
    assertThat(response).isNotNull();
    assertThat(response.getNotifyElements()).isNotNull();
    assertThat(response.getNotifyElements().size()).isEqualTo(0);

    assertThat(stateData.getDesiredInstances()).isEqualTo(1);
    assertThat(stateData.getMaxInstances()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseException() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);

    assertThatThrownBy(() -> state.handleAsyncResponse(mockContext, null)).isInstanceOf(InvalidRequestException.class);

    doThrow(new AccessDeniedException("failed", USER)).when(mockActivityService).updateStatus(any(), any(), any());
    assertThatThrownBy(() -> state.handleAsyncResponse(mockContext, emptyMap())).isInstanceOf(WingsException.class);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testIsBlueGreenWorkflow() {
    ExecutionContext execContext = mock(ExecutionContext.class);
    doReturn(BASIC)
        .doReturn(CANARY)
        .doReturn(ROLLING)
        .doReturn(MULTI_SERVICE)
        .doReturn(BLUE_GREEN)
        .when(execContext)
        .getOrchestrationWorkflowType();

    assertThat(state.isBlueGreenWorkflow(execContext)).isFalse();
    assertThat(state.isBlueGreenWorkflow(execContext)).isFalse();
    assertThat(state.isBlueGreenWorkflow(execContext)).isFalse();
    assertThat(state.isBlueGreenWorkflow(execContext)).isFalse();
    assertThat(state.isBlueGreenWorkflow(execContext)).isTrue();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetterSetters() {
    state.setBlueGreen(true);
    state.setAutoScalingGroupName("asg");
    state.setAutoScalingSteadyStateTimeout(1);
    state.setDesiredInstances("3");
    state.setMaxInstances("4");
    state.setMinInstances("0");
    state.setResizeStrategy(RESIZE_NEW_FIRST);
    state.setUseCurrentRunningCount(true);
    state.setCommandName("SETUP");

    assertThat(state.isBlueGreen()).isTrue();
    assertThat(state.getCommandName()).isEqualTo("SETUP");
    assertThat(state.getAutoScalingGroupName()).isEqualTo("asg");
    assertThat(state.getMaxInstances()).isEqualTo("4");
    assertThat(state.getMinInstances()).isEqualTo("0");
    assertThat(state.getDesiredInstances()).isEqualTo("3");
    assertThat(state.getResizeStrategy()).isEqualTo(RESIZE_NEW_FIRST);
    assertThat(state.isUseCurrentRunningCount()).isEqualTo(true);
    assertThat(state.getAutoScalingSteadyStateTimeout()).isEqualTo(1);
  }
}
