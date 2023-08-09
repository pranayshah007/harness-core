/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.outcomes.LiteEnginePodDetailsOutcome.POD_DETAILS_OUTCOME;
import static io.harness.beans.steps.stepinfo.InitializeStepInfo.CALLBACK_IDS;
import static io.harness.beans.steps.stepinfo.InitializeStepInfo.LOG_KEYS;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODE_BASE_CONNECTOR_REF;
import static io.harness.beans.sweepingoutputs.ContainerPortDetails.PORT_DETAILS;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.beans.outcomes.VmDetailsOutcome;
import io.harness.beans.steps.stepinfo.BackgroundStepInfo;
import io.harness.beans.sweepingoutputs.CodeBaseConnectorRefSweepingOutput;
import io.harness.beans.sweepingoutputs.ContainerPortDetails;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StepLogKeyDetails;
import io.harness.beans.sweepingoutputs.StepTaskDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.serializer.BackgroundStepProtobufSerializer;
import io.harness.ci.serializer.vm.VmStepSerializer;
import io.harness.delegate.beans.ci.vm.steps.VmBackgroundStep;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.repositories.CIStageOutputRepository;
import io.harness.rule.Owner;
import io.harness.vm.VmExecuteStepUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CI)
public class BackgroundStepTest extends CIExecutionTestBase {
  private static final String STEP_ID = "backgroundStepId";
  private static final String callbackId = UUID.randomUUID().toString();

  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock private CIDelegateTaskExecutor ciDelegateTaskExecutor;
  @Mock private BackgroundStepProtobufSerializer backgroundStepProtobufSerializer;
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock private OutcomeService outcomeService;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private VmStepSerializer vmStepSerializer;
  @Mock private VmExecuteStepUtils vmExecuteStepUtils;
  @Mock protected CIFeatureFlagService featureFlagService;
  @Mock protected CIStageOutputRepository ciStageOutputRepository;
  @InjectMocks BackgroundStep backgroundStep;
  private Ambiance ambiance;
  private BackgroundStepInfo stepInfo;
  private StepElementParameters stepElementParameters;
  private StepInputPackage stepInputPackage;
  private StepTaskDetails stepTaskDetails;
  private ContainerPortDetails containerPortDetails;
  private CodeBaseConnectorRefSweepingOutput codeBaseConnectorRefSweepingOutput;

  @Before
  public void setUp() {
    HashMap<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "accountId");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "projectId");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "orgId");
    ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipelineId").setRunSequence(1).build())
            .putAllSetupAbstractions(setupAbstractions)
            .addLevels(Level.newBuilder()
                           .setRuntimeId("runtimeId")
                           .setIdentifier(STEP_ID)
                           .setOriginalIdentifier(STEP_ID)
                           .setRetryIndex(1)
                           .build())
            .build();

    stepInfo = BackgroundStepInfo.builder()
                   .identifier(STEP_ID)
                   .command(ParameterField.<String>builder().expressionValue("ls").build())
                   .image(ParameterField.<String>builder().expressionValue("alpine").build())
                   .entrypoint(ParameterField.createValueField(Arrays.asList("git", "status")))
                   .build();

    stepElementParameters = StepElementParameters.builder().name("name").spec(stepInfo).build();
    stepInputPackage = StepInputPackage.builder().build();
    Map<String, String> callbackIds = new HashMap<>();
    callbackIds.put(STEP_ID, callbackId);
    stepTaskDetails = StepTaskDetails.builder().taskIds(callbackIds).build();

    Map<String, List<Integer>> portDetails = new HashMap<>();
    portDetails.put(STEP_ID, asList(10));
    containerPortDetails = ContainerPortDetails.builder().portDetails(portDetails).build();

    codeBaseConnectorRefSweepingOutput =
        CodeBaseConnectorRefSweepingOutput.builder().codeBaseConnectorRef("codeBaseConnectorRef").build();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldExecuteAsync() {
    Map<String, List<String>> logKeys = new HashMap<>();
    String key =
        "accountId:accountId/orgId:orgId/projectId:projectId/pipelineId:pipelineId/runSequence:1/level0:backgroundStepId_1";
    StepLogKeyDetails stepLogKeyDetails = StepLogKeyDetails.builder().logKeys(logKeys).build();
    LiteEnginePodDetailsOutcome liteEnginePodDetailsOutcome =
        LiteEnginePodDetailsOutcome.builder().ipAddress("122.32.433.43").build();

    RefObject refObject1 = RefObjectUtils.getSweepingOutputRefObject(CALLBACK_IDS);
    RefObject refObject2 = RefObjectUtils.getSweepingOutputRefObject(LOG_KEYS);
    RefObject refObject3 = RefObjectUtils.getSweepingOutputRefObject(PORT_DETAILS);
    RefObject refObject4 = RefObjectUtils.getSweepingOutputRefObject(CODE_BASE_CONNECTOR_REF);

    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(K8StageInfraDetails.builder()
                                    .podName("podName")
                                    .infrastructure(K8sDirectInfraYaml.builder()
                                                        .spec(K8sDirectInfraYamlSpec.builder()
                                                                  .connectorRef(ParameterField.createValueField("fd"))
                                                                  .build())
                                                        .build())
                                    .containerNames(new ArrayList<>())
                                    .build())
                        .build());

    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());

    when(executionSweepingOutputResolver.resolve(eq(ambiance), eq(refObject1))).thenReturn(stepTaskDetails);
    when(executionSweepingOutputResolver.resolve(eq(ambiance), eq(refObject2))).thenReturn(stepLogKeyDetails);
    when(executionSweepingOutputResolver.resolve(eq(ambiance), eq(refObject3))).thenReturn(containerPortDetails);
    when(executionSweepingOutputResolver.resolveOptional(eq(ambiance), eq(refObject4)))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(codeBaseConnectorRefSweepingOutput).build());

    when(outcomeService.resolve(ambiance, RefObjectUtils.getOutcomeRefObject(POD_DETAILS_OUTCOME)))
        .thenReturn(liteEnginePodDetailsOutcome);
    when(ciExecutionServiceConfig.isLocal()).thenReturn(false);

    when(ciDelegateTaskExecutor.queueParkedDelegateTask(any(), anyLong(), any())).thenReturn(callbackId);
    when(ciDelegateTaskExecutor.queueTask(any(), any(), any(), any(), eq(false), any())).thenReturn(callbackId);

    when(backgroundStepProtobufSerializer.serializeStepWithStepParameters(
             any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(UnitStep.newBuilder().build());

    AsyncExecutableResponse asyncExecutableResponse =
        backgroundStep.executeAsync(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(asyncExecutableResponse)
        .isEqualTo(AsyncExecutableResponse.newBuilder()
                       .addCallbackIds(callbackId)
                       .addCallbackIds(callbackId)
                       .addLogKeys(key)
                       .build());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldExecuteAsyncVm() {
    Map<String, List<String>> logKeys = new HashMap<>();
    String key =
        "accountId:accountId/orgId:orgId/projectId:projectId/pipelineId:pipelineId/runSequence:1/level0:backgroundStepId_1";
    logKeys.put(STEP_ID, Collections.singletonList(key));

    RefObject refObject = RefObjectUtils.getSweepingOutputRefObject(CODE_BASE_CONNECTOR_REF);

    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(VmStageInfraDetails.builder().build()).build());
    when(executionSweepingOutputResolver.resolveOptional(eq(ambiance), eq(refObject)))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(codeBaseConnectorRefSweepingOutput).build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());
    when(outcomeService.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(VmDetailsOutcome.VM_DETAILS_OUTCOME)))
        .thenReturn(OptionalOutcome.builder()
                        .found(true)
                        .outcome(VmDetailsOutcome.builder().ipAddress("1.1.1.1").delegateId("test").build())
                        .build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(VmStageInfraDetails.builder().build()).build());

    when(vmExecuteStepUtils.isBareMetalUsed(any())).thenReturn(false);
    when(vmStepSerializer.serialize(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(VmBackgroundStep.builder().build());
    when(ciDelegateTaskExecutor.queueTask(any(), any(), any(), any(), eq(false), any())).thenReturn(callbackId);

    AsyncExecutableResponse asyncExecutableResponse =
        backgroundStep.executeAsync(ambiance, stepElementParameters, stepInputPackage, null);
    assertThat(asyncExecutableResponse)
        .isEqualTo(AsyncExecutableResponse.newBuilder().addCallbackIds(callbackId).addLogKeys(key).build());
  }
}
