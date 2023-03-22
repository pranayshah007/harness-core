/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.execution.plugin;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.callback.DelegateCallbackToken;
import io.harness.product.ci.engine.proto.StepContext;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.utils.PluginUtils;
import io.harness.steps.plugin.PluginStep;

import com.google.inject.Inject;
import java.util.Map;
import java.util.function.Supplier;

@OwnedBy(HarnessTeam.SSCA)
public class PluginStepSerializer {
  @Inject PluginExecutionConfigHelper pluginExecutionConfigHelper;

  public UnitStep serializeStepWithStepParameters(PluginStep pluginStep, Integer port, String callbackId, String logKey,
      String identifier, long timeout, String accountId, String stepName,
      Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier) {
    if (port == null) {
      throw new ContainerStepExecutionException("Port can not be null");
    }

    if (callbackId == null) {
      throw new ContainerStepExecutionException("callbackId can not be null");
    }

    StepContext stepContext = StepContext.newBuilder().setExecutionTimeoutSecs(timeout).build();
    Map<String, String> envVarMap = PluginUtils.getPluginCompatibleEnvVariables(pluginStep, identifier);
    io.harness.product.ci.engine.proto.PluginStep pStep =
        io.harness.product.ci.engine.proto.PluginStep.newBuilder()
            .setContainerPort(port)
            .setImage(pluginExecutionConfigHelper.getPluginImage(pluginStep).getImage())
            .addAllEntrypoint(pluginExecutionConfigHelper.getPluginImage(pluginStep).getEntrypoint())
            .setContext(stepContext)
            .putAllEnvironment(envVarMap)
            .build();

    return UnitStep.newBuilder()
        .setAccountId(accountId)
        .setContainerPort(port)
        .setId(identifier)
        .setTaskId(callbackId)
        .setCallbackToken(delegateCallbackTokenSupplier.get().getToken())
        .setDisplayName(stepName)
        .setPlugin(pStep)
        .setLogKey(logKey)
        .build();
  }
}
