/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.execution;

import static io.harness.ci.commonconstants.ContainerExecutionConstants.LITE_ENGINE_PORT;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.TMP_PATH;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.callback.DelegateCallbackToken;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.k8s.CIK8ExecuteStepTaskParams;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.expression.ExpressionResolverUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.ExecuteStepRequest;
import io.harness.product.ci.engine.proto.RunStep;
import io.harness.product.ci.engine.proto.StepContext;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.yaml.core.variables.OutputNGVariable;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerV2RunStepHelper {
  @Inject ContainerDelegateTaskHelper containerDelegateTaskHelper;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Inject OutcomeService outcomeService;
  @Inject ContainerExecutionConfig containerExecutionConfig;

  @Inject private ContainerStepBaseHelper containerStepBaseHelper;

  public TaskData getRunStepTask(Ambiance ambiance, AbstractStepNode abstractStepNode, String accountId, String logKey,
      long timeout, String parkedTaskId, ParameterField<Map<String, String>> envVariables,
      ParameterField<List<OutputNGVariable>> outputNGVariableList) {
    UnitStep unitStep = serialiseStep(
        ambiance, abstractStepNode, accountId, logKey, timeout, parkedTaskId, envVariables, outputNGVariableList);
    LiteEnginePodDetailsOutcome liteEnginePodDetailsOutcome = (LiteEnginePodDetailsOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(LiteEnginePodDetailsOutcome.POD_DETAILS_OUTCOME));
    String ip = liteEnginePodDetailsOutcome.getIpAddress();

    ExecuteStepRequest executeStepRequest = ExecuteStepRequest.newBuilder()
                                                .setExecutionId(ambiance.getPlanExecutionId())
                                                .setStep(unitStep)
                                                .setTmpFilePath(TMP_PATH)
                                                .build();
    CIK8ExecuteStepTaskParams params =
        CIK8ExecuteStepTaskParams.builder()
            .ip(ip)
            .port(LITE_ENGINE_PORT)
            .serializedStep(executeStepRequest.toByteArray())
            .isLocal(containerExecutionConfig.isLocal())
            .delegateSvcEndpoint(containerExecutionConfig.getDelegateServiceEndpointVariableValue())
            .build();
    return containerDelegateTaskHelper.getDelegateTaskDataForExecuteStep(ambiance, timeout, params);
  }

  private UnitStep serialiseStep(Ambiance ambiance, AbstractStepNode abstractStepNode, String accountId, String logKey,
      long timeout, String parkedTaskId, ParameterField<Map<String, String>> envVariables,
      ParameterField<List<OutputNGVariable>> outputNGVariableList) {
    String identifier = abstractStepNode.getIdentifier();
    Integer port = containerStepBaseHelper.getPort(ambiance, identifier);
    return serializeStepWithStepParameters(port, parkedTaskId, logKey, identifier, accountId,
        abstractStepNode.getName(), timeout, envVariables, outputNGVariableList);
  }

  private UnitStep serializeStepWithStepParameters(Integer port, String callbackId, String logKey, String identifier,
      String accountId, String stepName, long timeout, ParameterField<Map<String, String>> envVariables,
      ParameterField<List<OutputNGVariable>> outputNGVariableList) {
    if (callbackId == null) {
      throw new ContainerStepExecutionException("CallbackId can not be null");
    }
    if (port == null) {
      throw new ContainerStepExecutionException("Port can not be null");
    }

    RunStep.Builder runStepBuilder = RunStep.newBuilder();

    runStepBuilder.setContainerPort(port);

    Map<String, String> envvars =
        ExpressionResolverUtils.resolveMapParameter("envVariables", "Run", identifier, envVariables, false);
    if (!isEmpty(envvars)) {
      runStepBuilder.putAllEnvironment(envvars);
    }

    if (isNotEmpty(outputNGVariableList.getValue())) {
      List<String> outputVarNames =
          outputNGVariableList.getValue().stream().map(OutputNGVariable::getName).collect(Collectors.toList());
      runStepBuilder.addAllEnvVarOutputs(outputVarNames);
    }

    runStepBuilder.setContext(StepContext.newBuilder().setExecutionTimeoutSecs(timeout).build());

    return getUnitStep(
        port, callbackId, logKey, identifier, accountId, stepName, runStepBuilder, delegateCallbackTokenSupplier);
  }

  private UnitStep getUnitStep(Integer port, String callbackId, String logKey, String identifier, String accountId,
      String stepName, RunStep.Builder runStepBuilder, Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier) {
    return UnitStep.newBuilder()
        .setAccountId(accountId)
        .setContainerPort(port)
        .setId(identifier)
        .setTaskId(callbackId)
        .setCallbackToken(delegateCallbackTokenSupplier.get().getToken())
        .setDisplayName(stepName)
        .setRun(runStepBuilder.build())
        .setLogKey(logKey)
        .build();
  }
}
