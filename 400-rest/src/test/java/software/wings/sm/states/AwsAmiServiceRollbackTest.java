/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.EnvironmentType.PROD;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.delegate.beans.pcf.ResizeStrategy.RESIZE_NEW_FIRST;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.AwsAmiInfrastructureMapping.Builder.anAwsAmiInfrastructureMapping;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.CommandType.ENABLE;
import static software.wings.beans.command.ServiceCommand.Builder.aServiceCommand;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.service.impl.aws.model.AwsConstants.AMI_SERVICE_SETUP_SWEEPING_OUTPUT_NAME;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_SOURCE_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.WingsBaseTest;
import software.wings.api.AmiServiceDeployElement;
import software.wings.api.AmiServiceSetupElement;
import software.wings.api.AwsAmiDeployStateExecutionData;
import software.wings.api.ContainerServiceData;
import software.wings.api.InstanceElement;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.AwsConfig;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.AmiArtifactStream;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.command.AmiCommandUnit;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.ServiceCommand;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.aws.model.AwsAmiPreDeploymentData;
import software.wings.service.impl.aws.model.AwsAmiServiceDeployResponse;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

@OwnedBy(CDP)
public class AwsAmiServiceRollbackTest extends WingsBaseTest {
  @Mock private SweepingOutputService mockSweepingOutputService;
  @Mock private SecretManager mockSecretManager;
  @Mock private SettingsService mockSettingsService;
  @Mock private ServiceResourceService mockServiceResourceService;
  @Mock private AwsAmiServiceStateHelper mockAwsAmiServiceStateHelper;
  @Mock private InfrastructureMappingService mockInfrastructureMappingService;
  @Mock private ArtifactStreamService mockArtifactStreamService;
  @Mock private ActivityService mockActivityService;
  @Mock private DelegateService mockDelegateService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;

  @InjectMocks private AwsAmiServiceRollback state = new AwsAmiServiceRollback("stepName");

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInternalNoServiceSetupElement() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    ExecutionResponse executionResponse = state.executeInternal(mockContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SKIPPED);
    assertThat(executionResponse.getErrorMessage()).isEqualTo("No service setup element found. Skipping rollback.");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInternalNoServiceDeployElement() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doReturn(AmiServiceSetupElement.builder().build())
        .when(mockAwsAmiServiceStateHelper)
        .getSetupElementFromSweepingOutput(mockContext, AMI_SERVICE_SETUP_SWEEPING_OUTPUT_NAME);
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    ExecutionResponse executionResponse = state.executeInternal(mockContext);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SKIPPED);
    assertThat(executionResponse.getErrorMessage()).isEqualTo("No service deploy element found. Skipping rollback.");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInternal() {
    state.setRollbackAllPhasesAtOnce(true);
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    when(mockContext.renderExpression(any())).thenAnswer((Answer<String>) invocation -> {
      Object[] args = invocation.getArguments();
      return (String) args[0];
    });
    PhaseElement phaseElement =
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build()).build();
    String asg1 = "foo__1";
    String asg3 = "foo__3";

    AmiServiceSetupElement serviceSetupElement =
        AmiServiceSetupElement.builder()
            .blueGreen(false)
            .resizeStrategy(RESIZE_NEW_FIRST)
            .oldAsgNames(Lists.newArrayList(asg1))
            .oldAutoScalingGroupName(asg1)
            .preDeploymentData(AwsAmiPreDeploymentData.builder().desiredCapacity(1).oldAsgName(asg1).build())
            .autoScalingSteadyStateTimeout(10)
            .newAutoScalingGroupName(asg3)
            .minInstances(0)
            .desiredInstances(2)
            .maxInstances(2)
            .build();
    doReturn(phaseElement).when(mockContext).getContextElement(any(), any());
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    doReturn(false).when(featureFlagService).isEnabled(any(), any());
    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    List newInstanceData = new ArrayList();
    newInstanceData.add(ContainerServiceData.builder().name("target-name").build());
    doReturn(EmbeddedUser.builder().email("user@harness.io").name("user").build()).when(mockParams).getCurrentUser();
    doReturn(mockParams).when(mockContext).getContextElement(any());
    Environment environment = anEnvironment().uuid(ENV_ID).environmentType(PROD).name(ENV_NAME).build();
    doReturn(environment).when(workflowStandardParamsExtensionService).getEnv(mockParams);
    doReturn(environment).when(mockContext).fetchRequiredEnvironment();
    Application application = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
    doReturn(application).when(workflowStandardParamsExtensionService).getApp(mockParams);
    Service service = Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build();
    doReturn(service).when(mockServiceResourceService).getWithDetails(any(), any());
    doReturn(serviceSetupElement)
        .when(mockAwsAmiServiceStateHelper)
        .getSetupElementFromSweepingOutput(mockContext, AMI_SERVICE_SETUP_SWEEPING_OUTPUT_NAME);
    doReturn(
        AmiServiceDeployElement.builder().newInstanceData(newInstanceData).oldInstanceData(new ArrayList<>()).build())
        .when(mockContext)
        .getContextElement(ContextElementType.AMI_SERVICE_DEPLOY);
    String revision = "ami-1234";
    Artifact artifact = anArtifact().withRevision(revision).build();
    doReturn(artifact).when(mockContext).getDefaultArtifactForService(any());
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(any(), any());
    ArtifactStream artifactStream =
        AmiArtifactStream.builder().uuid(ARTIFACT_STREAM_ID).sourceName(ARTIFACT_SOURCE_NAME).build();
    doReturn(artifactStream).when(mockArtifactStreamService).get(any());
    Command command = aCommand().withName("Ami-Command").withCommandType(ENABLE).build();
    ServiceCommand serviceCommand = aServiceCommand().withCommand(command).build();
    doReturn(serviceCommand).when(mockServiceResourceService).getCommandByName(any(), any(), any(), any());
    AmiCommandUnit commandUnit = new AmiCommandUnit();
    commandUnit.setName("Ami-Command-Unit");
    List<CommandUnit> commandUnits = singletonList(commandUnit);
    doReturn(commandUnits).when(mockServiceResourceService).getFlattenCommandUnitList(any(), any(), any(), any());
    Activity activity = Activity.builder().uuid(ACTIVITY_ID).appId(APP_ID).build();
    doReturn(activity).when(mockActivityService).save(any());
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

    ExecutionResponse response = state.executeInternal(mockContext);
    verify(mockDelegateService, times(1)).queueTaskV2(any(DelegateTask.class));
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(((AwsAmiDeployStateExecutionData) response.getStateExecutionData()).isRollback()).isTrue();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleAsyncInternal() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    List<InstanceElement> result = state.handleAsyncInternal(AwsAmiServiceDeployResponse.builder().build(), mockContext,
        AmiServiceSetupElement.builder().build(), mock(ManagerExecutionLogCallback.class));
    assertThat(result.size()).isEqualTo(0);

    state.setRollbackAllPhasesAtOnce(true);
    doReturn(SweepingOutputInstance.builder())
        .when(mockContext)
        .prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW);
    state.handleAsyncInternal(AwsAmiServiceDeployResponse.builder().executionStatus(ExecutionStatus.SUCCESS).build(),
        mockContext, AmiServiceSetupElement.builder().build(), mock(ManagerExecutionLogCallback.class));
    assertThat(result.size()).isEqualTo(0);
    verify(mockSweepingOutputService, times(1)).save(any());
  }
}
