/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.expression.ExpressionResolverUtils;
import io.harness.ssca.cd.beans.enforcement.CdSscaEnforcementSpecParameters;
import io.harness.ssca.cd.beans.orchestration.CdSscaOrchestrationSpecParameters;
import io.harness.ssca.cd.execution.enforcement.CdSscaEnforcementPluginHelper;
import io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.ssca.SscaOrchestrationPluginHelper;
import io.harness.steps.plugin.ContainerStepType;
import io.harness.steps.plugin.PluginStep;
import io.harness.yaml.core.variables.SecretNGVariable;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.SSCA)
@Singleton
public class PluginUtils {
  @Inject SscaOrchestrationPluginHelper sscaOrchestrationPluginHelper;
  @Inject CdSscaEnforcementPluginHelper enforcementPluginHelper;

  public Map<String, String> getPluginCompatibleEnvVariables(PluginStep step, String identifier, Ambiance ambiance) {
    switch (step.getType()) {
      case CD_SSCA_ORCHESTRATION:
        return sscaOrchestrationPluginHelper.getSscaOrchestrationEnvVariables(
            (CdSscaOrchestrationSpecParameters) step, identifier, ambiance);
      case CD_SSCA_ENFORCEMENT:
        return enforcementPluginHelper.getSscaEnforcementStepEnvVariables(
            (CdSscaEnforcementSpecParameters) step, identifier, ambiance);
      default:
        return new HashMap<>();
    }
  }

  public Map<String, SecretNGVariable> getPluginCompatibleSecretVars(PluginStep step, String identifier) {
    switch (step.getType()) {
      case CD_SSCA_ORCHESTRATION:
        return SscaOrchestrationPluginHelper.getSscaOrchestrationSecretVars((CdSscaOrchestrationSpecParameters) step);
      case CD_SSCA_ENFORCEMENT:
        return CdSscaEnforcementPluginHelper.getSscaEnforcementSecretVariables(
            (CdSscaEnforcementSpecParameters) step, identifier);
      default:
        return new HashMap<>();
    }
  }

  public static String getConnectorRef(PluginStep pluginStep) {
    return ExpressionResolverUtils.resolveStringParameter("connectorRef", pluginStep.getType().toString(),
        pluginStep.getIdentifier(), pluginStep.getConnectorRef(), false);
  }

  public static Map<EnvVariableEnum, String> getConnectorSecretEnvMap(ContainerStepType containerStepType) {
    switch (containerStepType) {
      case CD_SSCA_ORCHESTRATION:
        return SscaOrchestrationStepPluginUtils.getConnectorSecretEnvMap();
      default:
        throw new ContainerStepExecutionException("Unhandled connector secret for step type: " + containerStepType);
    }
  }
}
