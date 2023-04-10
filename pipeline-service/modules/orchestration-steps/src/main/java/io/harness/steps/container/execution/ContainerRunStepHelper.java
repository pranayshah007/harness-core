/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.execution;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.CIShellType;
import io.harness.callback.DelegateCallbackToken;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.expression.ExpressionResolverUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.product.ci.engine.proto.RunStep;
import io.harness.product.ci.engine.proto.ShellType;
import io.harness.product.ci.engine.proto.StepContext;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.plugin.PluginStepSerializer;
import io.harness.steps.container.utils.ContainerStepResolverUtils;
import io.harness.steps.plugin.ContainerStepInfo;
import io.harness.steps.plugin.ContainerStepSpec;
import io.harness.steps.plugin.PluginStep;
import io.harness.yaml.core.variables.OutputNGVariable;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerRunStepHelper {
  @Inject ContainerDelegateTaskHelper containerDelegateTaskHelper;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Inject OutcomeService outcomeService;
  @Inject ContainerExecutionConfig containerExecutionConfig;
  @Inject PluginStepSerializer pluginStepSerializer;

  @Inject private ContainerStepBaseHelper containerStepBaseHelper;

  public UnitStep serialiseStep(Ambiance ambiance, ContainerStepSpec containerStepInfo, String accountId, String logKey,
      long timeout, String parkedTaskId) {
    String identifier = containerStepInfo.getIdentifier();
    Integer port = containerStepBaseHelper.getPort(ambiance, identifier);
    switch (containerStepInfo.getType()) {
      case RUN_CONTAINER:
        return serializeStepWithStepParameters((ContainerStepInfo) containerStepInfo, port, parkedTaskId, logKey,
            identifier, accountId, containerStepInfo.getName(), timeout);
      case CD_SSCA_ORCHESTRATION:
        return pluginStepSerializer.serializeStepWithStepParameters((PluginStep) containerStepInfo, port, parkedTaskId,
            logKey, identifier, timeout, accountId, containerStepInfo.getName(), delegateCallbackTokenSupplier,
            ambiance);
      default:
        throw new ContainerStepExecutionException("Step serialization not handled");
    }
  }

  private UnitStep serializeStepWithStepParameters(ContainerStepInfo runStepInfo, Integer port, String callbackId,
      String logKey, String identifier, String accountId, String stepName, long timeout) {
    if (callbackId == null) {
      throw new ContainerStepExecutionException("CallbackId can not be null");
    }
    if (port == null) {
      throw new ContainerStepExecutionException("Port can not be null");
    }

    RunStep.Builder runStepBuilder = RunStep.newBuilder();
    runStepBuilder.setCommand(
        ExpressionResolverUtils.resolveStringParameter("Command", "Run", identifier, runStepInfo.getCommand(), true));

    runStepBuilder.setContainerPort(port);
    Map<String, String> envvars = ExpressionResolverUtils.resolveMapParameter(
        "envVariables", "Run", identifier, runStepInfo.getEnvVariables(), false);
    if (!isEmpty(envvars)) {
      runStepBuilder.putAllEnvironment(envvars);
    }

    if (isNotEmpty(runStepInfo.getOutputVariables().getValue())) {
      List<String> outputVarNames = runStepInfo.getOutputVariables()
                                        .getValue()
                                        .stream()
                                        .map(OutputNGVariable::getName)
                                        .collect(Collectors.toList());
      runStepBuilder.addAllEnvVarOutputs(outputVarNames);
    }

    runStepBuilder.setContext(StepContext.newBuilder().setExecutionTimeoutSecs(timeout).build());

    CIShellType shellType = ContainerStepResolverUtils.resolveShellType(runStepInfo.getShell());
    return getUnitStep(port, callbackId, logKey, identifier, accountId, stepName, runStepBuilder, shellType,
        delegateCallbackTokenSupplier);
  }

  private UnitStep getUnitStep(Integer port, String callbackId, String logKey, String identifier, String accountId,
      String stepName, RunStep.Builder runStepBuilder, CIShellType shellType,
      Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier) {
    ShellType protoShellType = ShellType.SH;
    if (shellType == CIShellType.BASH) {
      protoShellType = ShellType.BASH;
    } else if (shellType == CIShellType.PWSH) {
      protoShellType = ShellType.PWSH;
    } else if (shellType == CIShellType.POWERSHELL) {
      protoShellType = ShellType.POWERSHELL;
    } else if (shellType == CIShellType.PYTHON) {
      protoShellType = ShellType.PYTHON;
    }

    runStepBuilder.setShellType(protoShellType);

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
