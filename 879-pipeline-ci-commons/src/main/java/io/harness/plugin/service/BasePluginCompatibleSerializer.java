/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plugin.service;

import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_ARTIFACT_FILE_VALUE;

import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.serializer.ProtobufStepSerializer;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.PluginStep;
import io.harness.product.ci.engine.proto.StepContext;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;

public abstract class BasePluginCompatibleSerializer implements ProtobufStepSerializer<PluginCompatibleStep> {
  @Inject PluginService pluginService;

  public UnitStep serializeStepWithStepParameters(PluginCompatibleStep pluginCompatibleStep, Integer port,
      String callbackId, String logKey, String identifier, ParameterField<Timeout> parameterFieldTimeout,
      String accountId, String stepName, OSType os, Ambiance ambiance) {
    if (port == null) {
      throw new CIStageExecutionException("Port can not be null");
    }

    if (callbackId == null) {
      throw new CIStageExecutionException("callbackId can not be null");
    }

    long timeout = TimeoutUtils.getTimeoutInSeconds(parameterFieldTimeout, pluginCompatibleStep.getDefaultTimeout());
    List<String> outputVarNames = getOutputVariables(pluginCompatibleStep);

    StepContext stepContext = StepContext.newBuilder().setExecutionTimeoutSecs(timeout).build();
    Map<String, String> envVarMap = pluginService.getPluginCompatibleEnvVariables(
        pluginCompatibleStep, identifier, timeout, ambiance, StageInfraDetails.Type.K8, true, true);
    PluginStep pluginStep = PluginStep.newBuilder()
                                .setContainerPort(port)
                                .setImage(getImageName(pluginCompatibleStep, accountId))
                                .addAllEntrypoint(getEntryPoint(pluginCompatibleStep, accountId, os))
                                .setContext(stepContext)
                                .addAllEnvVarOutputs(outputVarNames)
                                .putAllEnvironment(envVarMap)
                                .setArtifactFilePath(PLUGIN_ARTIFACT_FILE_VALUE)
                                .build();

    return UnitStep.newBuilder()
        .setAccountId(accountId)
        .setContainerPort(port)
        .setId(identifier)
        .setTaskId(callbackId)
        .setCallbackToken(getDelegateCallbackToken())
        .setDisplayName(stepName)
        .setPlugin(pluginStep)
        .setLogKey(logKey)
        .build();
  }

  public abstract String getImageName(PluginCompatibleStep pluginCompatibleStep, String accountId);

  public abstract List<String> getOutputVariables(PluginCompatibleStep pluginCompatibleStep);

  public abstract List<String> getEntryPoint(PluginCompatibleStep pluginCompatibleStep, String accountId, OSType os);

  public abstract String getDelegateCallbackToken();
}
