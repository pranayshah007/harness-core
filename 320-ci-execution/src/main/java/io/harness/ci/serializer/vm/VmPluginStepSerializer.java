/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer.vm;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveJsonNodeMapParameter;
import static io.harness.common.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.common.CIExecutionConstants.PLUGIN_ENV_PREFIX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.reports.JUnitTestReport;
import io.harness.beans.yaml.extended.reports.UnitTestReportType;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.serializer.SerializerUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.steps.VmJunitTestReport;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep.VmPluginStepBuilder;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.stateutils.buildstate.ConnectorUtils;
import io.harness.util.HarnessImageUtils;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class VmPluginStepSerializer {
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject ConnectorUtils connectorUtils;
  @Inject HarnessImageUtils harnessImageUtils;

  public VmPluginStep serialize(PluginStepInfo pluginStepInfo, VmStageInfraDetails vmStageInfraDetails,
      String identifier, ParameterField<Timeout> parameterFieldTimeout, String stepName, Ambiance ambiance) {
    Map<String, JsonNode> settings =
        resolveJsonNodeMapParameter("settings", "Plugin", identifier, pluginStepInfo.getSettings(), false);
    Map<String, String> envVars = new HashMap<>();
    if (!isEmpty(settings)) {
      for (Map.Entry<String, JsonNode> entry : settings.entrySet()) {
        String key = PLUGIN_ENV_PREFIX + entry.getKey().toUpperCase();
        envVars.put(key, SerializerUtils.convertJsonNodeToString(entry.getKey(), entry.getValue()));
      }
    }
    if (!isEmpty(pluginStepInfo.getEnvVariables())) {
      envVars.putAll(pluginStepInfo.getEnvVariables());
    }

    String connectorIdentifier = RunTimeInputHandler.resolveStringParameter(
        "connectorRef", stepName, identifier, pluginStepInfo.getConnectorRef(), false);

    String image =
        RunTimeInputHandler.resolveStringParameter("Image", stepName, identifier, pluginStepInfo.getImage(), false);

    long timeout = TimeoutUtils.getTimeoutInSeconds(parameterFieldTimeout, pluginStepInfo.getDefaultTimeout());

    VmPluginStepBuilder pluginStepBuilder =
        VmPluginStep.builder().image(image).envVariables(envVars).timeoutSecs(timeout);

    // if the plugin type is git clone use default harnessImage Connector
    // else if the connector is given in plugin, use that.
    if (identifier.equals(GIT_CLONE_STEP_ID) && pluginStepInfo.isHarnessManagedImage()) {
      String gitImage = ciExecutionServiceConfig.getStepConfig().getVmImageConfig().getGitClone();
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      ConnectorDetails harnessInternalImageConnector =
          harnessImageUtils.getHarnessImageConnectorDetailsForVM(ngAccess, vmStageInfraDetails);
      image = IntegrationStageUtils.getFullyQualifiedImageName(gitImage, harnessInternalImageConnector);
      pluginStepBuilder.image(image);
      pluginStepBuilder.imageConnector(harnessInternalImageConnector);
    } else if (!StringUtils.isEmpty(image) && !StringUtils.isEmpty(connectorIdentifier)) {
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorIdentifier);
      pluginStepBuilder.imageConnector(connectorDetails);
    }

    if (pluginStepInfo.getReports().getValue() != null) {
      if (pluginStepInfo.getReports().getValue().getType() == UnitTestReportType.JUNIT) {
        JUnitTestReport junitTestReport = (JUnitTestReport) pluginStepInfo.getReports().getValue().getSpec();
        List<String> resolvedReport = junitTestReport.resolve(identifier, stepName);

        pluginStepBuilder.unitTestReport(VmJunitTestReport.builder().paths(resolvedReport).build());
      }
    }
    return pluginStepBuilder.build();
  }
}
