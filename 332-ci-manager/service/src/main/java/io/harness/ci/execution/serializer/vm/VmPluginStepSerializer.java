/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer.vm;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveJsonNodeMapParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameterV2;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_ARCHIVE_FORMAT;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_BACKEND;
import static io.harness.ci.buildstate.PluginSettingUtils.PLUGIN_BUCKET;
import static io.harness.ci.commonconstants.CIExecutionConstants.CACHE_ARCHIVE_TYPE_TAR;
import static io.harness.ci.commonconstants.CIExecutionConstants.CACHE_GCS_BACKEND;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_DEPTH_ATTRIBUTE;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_MANUAL_DEPTH;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_ENV_PREFIX;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_JSON_KEY;
import static io.harness.ci.commonconstants.CIExecutionConstants.RESTORE_CACHE_STEP_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.SAVE_CACHE_STEP_ID;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.CIRegistry;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.yaml.extended.reports.JUnitTestReport;
import io.harness.beans.yaml.extended.reports.UnitTestReportType;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.config.CICacheIntelligenceConfig;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.serializer.SerializerUtils;
import io.harness.ci.utils.CIStepInfoUtils;
import io.harness.ci.utils.HarnessImageUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.steps.VmJunitTestReport;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep.VmPluginStepBuilder;
import io.harness.delegate.beans.ci.vm.steps.VmRunStep;
import io.harness.delegate.beans.ci.vm.steps.VmRunStep.VmRunStepBuilder;
import io.harness.delegate.beans.ci.vm.steps.VmStepInfo;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
public class VmPluginStepSerializer {
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject ConnectorUtils connectorUtils;
  @Inject HarnessImageUtils harnessImageUtils;
  @Inject CIStepInfoUtils ciStepInfoUtils;

  public VmStepInfo serialize(PluginStepInfo pluginStepInfo, StageInfraDetails stageInfraDetails, String identifier,
      ParameterField<Timeout> parameterFieldTimeout, String stepName, Ambiance ambiance, List<CIRegistry> registries,
      ExecutionSource executionSource) {
    Map<String, JsonNode> settings =
        resolveJsonNodeMapParameter("settings", "Plugin", identifier, pluginStepInfo.getSettings(), false);
    Map<String, String> envVars = new HashMap<>();
    if (executionSource != null && executionSource.getType() == ExecutionSource.Type.MANUAL) {
      if (identifier.equals(GIT_CLONE_STEP_ID) && settings != null
          && !settings.containsKey(GIT_CLONE_DEPTH_ATTRIBUTE)) {
        ManualExecutionSource manualExecutionSource = (ManualExecutionSource) executionSource;
        if (isNotEmpty(manualExecutionSource.getBranch()) || isNotEmpty(manualExecutionSource.getTag())) {
          settings.put(GIT_CLONE_DEPTH_ATTRIBUTE, JsonNodeFactory.instance.textNode(GIT_CLONE_MANUAL_DEPTH.toString()));
        }
      }
    }
    if (!isEmpty(settings)) {
      for (Map.Entry<String, JsonNode> entry : settings.entrySet()) {
        String key = PLUGIN_ENV_PREFIX + entry.getKey().toUpperCase();
        envVars.put(key, SerializerUtils.convertJsonNodeToString(entry.getKey(), entry.getValue()));
      }
    }
    envVars.putAll(resolveMapParameterV2("envVars", "pluginStep", identifier, pluginStepInfo.getEnvVariables(), false));

    String image =
        RunTimeInputHandler.resolveStringParameter("Image", stepName, identifier, pluginStepInfo.getImage(), false);
    String connectorIdentifier;
    if (isNotEmpty(registries)) {
      connectorIdentifier = ciStepInfoUtils.resolveConnectorFromRegistries(registries, image).orElse(null);
    } else {
      connectorIdentifier = RunTimeInputHandler.resolveStringParameter(
          "connectorRef", stepName, identifier, pluginStepInfo.getConnectorRef(), false);
    }
    String uses =
        RunTimeInputHandler.resolveStringParameter("uses", stepName, identifier, pluginStepInfo.getUses(), false);
    long timeout = TimeoutUtils.getTimeoutInSeconds(parameterFieldTimeout, pluginStepInfo.getDefaultTimeout());

    if (isNotEmpty(image)) {
      if (isNotEmpty(uses)) {
        log.warn("Both image and uses are set for plugin step. Ignoring uses field");
      }
      return convertContainerStep(
          ambiance, identifier, image, connectorIdentifier, envVars, timeout, stageInfraDetails, pluginStepInfo);
    } else if (isNotEmpty(uses)) {
      if (stageInfraDetails.getType() != StageInfraDetails.Type.DLITE_VM) {
        throw new CIStageExecutionException(format("uses field is applicable only for cloud builds"));
      }
      return convertContainerlessStep(identifier, uses, envVars, timeout, pluginStepInfo);
    } else {
      throw new CIStageExecutionException("Either image or uses field needs to be set");
    }
  }

  private VmPluginStep convertContainerStep(Ambiance ambiance, String identifier, String image,
      String connectorIdentifier, Map<String, String> envVars, long timeout, StageInfraDetails stageInfraDetails,
      PluginStepInfo pluginStepInfo) {
    setEnvVariablesForHostedCachingSteps(stageInfraDetails, identifier, envVars);
    VmPluginStepBuilder pluginStepBuilder =
        VmPluginStep.builder().image(image).envVariables(envVars).timeoutSecs(timeout);

    // if the plugin type is git clone use default harnessImage Connector
    // else if the connector is given in plugin, use that.
    if (identifier.equals(GIT_CLONE_STEP_ID) && pluginStepInfo.isHarnessManagedImage()) {
      String gitImage = ciExecutionServiceConfig.getStepConfig().getVmImageConfig().getGitClone();
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      ConnectorDetails harnessInternalImageConnector =
          harnessImageUtils.getHarnessImageConnectorDetailsForVM(ngAccess, stageInfraDetails);
      image = IntegrationStageUtils.getFullyQualifiedImageName(gitImage, harnessInternalImageConnector);
      pluginStepBuilder.image(image);
      pluginStepBuilder.imageConnector(harnessInternalImageConnector);
    } else if (!StringUtils.isEmpty(image) && !StringUtils.isEmpty(connectorIdentifier)) {
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorIdentifier);
      pluginStepBuilder.image(image);
      pluginStepBuilder.imageConnector(connectorDetails);
    }

    if (pluginStepInfo.getReports().getValue() != null) {
      if (pluginStepInfo.getReports().getValue().getType() == UnitTestReportType.JUNIT) {
        JUnitTestReport junitTestReport = (JUnitTestReport) pluginStepInfo.getReports().getValue().getSpec();
        List<String> resolvedReport = RunTimeInputHandler.resolveListParameter(
            "paths", pluginStepInfo.getName(), pluginStepInfo.getIdentifier(), junitTestReport.getPaths(), false);
        pluginStepBuilder.unitTestReport(VmJunitTestReport.builder().paths(resolvedReport).build());
      }
    }
    pluginStepBuilder.privileged(RunTimeInputHandler.resolveBooleanParameter(pluginStepInfo.getPrivileged(), false));
    return pluginStepBuilder.build();
  }

  private VmRunStep convertContainerlessStep(
      String identifier, String uses, Map<String, String> envVars, long timeout, PluginStepInfo pluginStepInfo) {
    VmRunStepBuilder stepBuilder = VmRunStep.builder()
                                       .entrypoint(Arrays.asList("plugin", "-kind", "harness", "-repo"))
                                       .command(uses)
                                       .envVariables(envVars)
                                       .timeoutSecs(timeout);
    if (pluginStepInfo.getReports().getValue() != null) {
      if (pluginStepInfo.getReports().getValue().getType() == UnitTestReportType.JUNIT) {
        JUnitTestReport junitTestReport = (JUnitTestReport) pluginStepInfo.getReports().getValue().getSpec();
        List<String> resolvedReport = RunTimeInputHandler.resolveListParameter(
            "paths", pluginStepInfo.getName(), identifier, junitTestReport.getPaths(), false);
        stepBuilder.unitTestReport(VmJunitTestReport.builder().paths(resolvedReport).build());
      }
    }
    return stepBuilder.build();
  }

  private void setEnvVariablesForHostedCachingSteps(
      StageInfraDetails stageInfraDetails, String identifier, Map<String, String> envVarMap) {
    if (stageInfraDetails != null && stageInfraDetails.getType() == StageInfraDetails.Type.DLITE_VM) {
      switch (identifier) {
        case SAVE_CACHE_STEP_ID:
        case RESTORE_CACHE_STEP_ID:
          CICacheIntelligenceConfig cacheIntelligenceConfig = ciExecutionServiceConfig.getCacheIntelligenceConfig();
          try {
            String cacheKeyString = new String(Files.readAllBytes(Paths.get(cacheIntelligenceConfig.getServiceKey())));
            envVarMap.put(PLUGIN_JSON_KEY, cacheKeyString);
          } catch (IOException e) {
            log.error("Cannot read storage key file for Cache Intelligence steps");
          }
          envVarMap.put(PLUGIN_BUCKET, cacheIntelligenceConfig.getBucket());
          envVarMap.put(PLUGIN_BACKEND, CACHE_GCS_BACKEND);
          envVarMap.put(PLUGIN_ARCHIVE_FORMAT, CACHE_ARCHIVE_TYPE_TAR);
          break;
        default:
          break;
      }
    }
  }
}
