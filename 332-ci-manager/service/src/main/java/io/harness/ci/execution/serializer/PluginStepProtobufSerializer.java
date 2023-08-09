/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveJsonNodeMapParameter;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_STAGE_MACHINE;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_DEPTH_ATTRIBUTE;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_MANUAL_DEPTH;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_ENV_PREFIX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.yaml.extended.reports.JUnitTestReport;
import io.harness.beans.yaml.extended.reports.UnitTestReport;
import io.harness.beans.yaml.extended.reports.UnitTestReportType;
import io.harness.callback.DelegateCallbackToken;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.PluginStep;
import io.harness.product.ci.engine.proto.Report;
import io.harness.product.ci.engine.proto.StepContext;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Singleton
@OwnedBy(HarnessTeam.CI)
public class PluginStepProtobufSerializer implements ProtobufStepSerializer<PluginStepInfo> {
  @Inject private Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Inject private SerializerUtils serializerUtils;

  public UnitStep serializeStepWithStepParameters(PluginStepInfo pluginStepInfo, Integer port, String callbackId,
      String logKey, String identifier, ParameterField<Timeout> parameterFieldTimeout, String accountId,
      String stepName, ExecutionSource executionSource, String podName, Ambiance ambiance) {
    if (callbackId == null) {
      throw new CIStageExecutionException("CallbackId can not be null");
    }

    if (port == null) {
      throw new CIStageExecutionException("Port can not be null");
    }

    long timeout = TimeoutUtils.getTimeoutInSeconds(parameterFieldTimeout, pluginStepInfo.getDefaultTimeout());
    StepContext stepContext = StepContext.newBuilder().setExecutionTimeoutSecs(timeout).build();

    Map<String, JsonNode> settings =
        resolveJsonNodeMapParameter("settings", "Plugin", identifier, pluginStepInfo.getSettings(), false);
    Map<String, String> envVarMap = new HashMap<>();
    if (executionSource != null && executionSource.getType() == ExecutionSource.Type.MANUAL
        && identifier.equals(GIT_CLONE_STEP_ID) && settings != null) {
      resolveGitCloneDepth(settings, executionSource);
    }
    if (!isEmpty(settings)) {
      for (Map.Entry<String, JsonNode> entry : settings.entrySet()) {
        String key = PLUGIN_ENV_PREFIX + entry.getKey().toUpperCase();
        envVarMap.put(key, SerializerUtils.convertJsonNodeToString(entry.getKey(), entry.getValue()));
      }
    }
    envVarMap.put(DRONE_STAGE_MACHINE, podName);
    Map<String, String> statusEnvVars = serializerUtils.getStepStatusEnvVars(ambiance);
    envVarMap.putAll(statusEnvVars);
    PluginStep.Builder builder = PluginStep.newBuilder();

    UnitTestReport reports = pluginStepInfo.getReports().getValue();
    if (reports != null) {
      if (reports.getType() == UnitTestReportType.JUNIT) {
        JUnitTestReport junitTestReport = (JUnitTestReport) reports.getSpec();
        List<String> resolvedReport =
            RunTimeInputHandler.resolveListParameter("paths", "run", identifier, junitTestReport.getPaths(), false);

        Report report = Report.newBuilder().setType(Report.Type.JUNIT).addAllPaths(resolvedReport).build();
        builder.addReports(report);
      }
    }

    PluginStep pluginStep =
        builder.setContainerPort(port)
            .setImage(RunTimeInputHandler.resolveStringParameter(
                "Image", "Plugin", identifier, pluginStepInfo.getImage(), true))
            .addAllEntrypoint(Optional.ofNullable(pluginStepInfo.getEntrypoint().getValue()).orElse(emptyList()))
            .putAllEnvironment(envVarMap)
            .setContext(stepContext)
            .build();

    return UnitStep.newBuilder()
        .setId(identifier)
        .setTaskId(callbackId)
        .setAccountId(accountId)
        .setContainerPort(port)
        .setCallbackToken(delegateCallbackTokenSupplier.get().getToken())
        .setDisplayName(stepName)
        .setPlugin(pluginStep)
        .setLogKey(logKey)
        .build();
  }

  private void resolveGitCloneDepth(Map<String, JsonNode> settings, ExecutionSource executionSource) {
    ManualExecutionSource manualExecutionSource = (ManualExecutionSource) executionSource;
    if (isNotEmpty(manualExecutionSource.getBranch()) || isNotEmpty(manualExecutionSource.getTag())) {
      if (!settings.containsKey(GIT_CLONE_DEPTH_ATTRIBUTE)) {
        settings.put(GIT_CLONE_DEPTH_ATTRIBUTE, JsonNodeFactory.instance.textNode(GIT_CLONE_MANUAL_DEPTH.toString()));
      }
      if (settings.get(GIT_CLONE_DEPTH_ATTRIBUTE).asText().equals("0")) {
        settings.remove(GIT_CLONE_DEPTH_ATTRIBUTE);
      }
    }
  }
}
