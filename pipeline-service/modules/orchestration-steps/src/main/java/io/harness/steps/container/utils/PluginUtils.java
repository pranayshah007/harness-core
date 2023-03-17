/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static io.harness.pms.expression.ExpressionResolverUtils.resolveStringParameter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ssca.beans.OrchestrationStepEnvVariables;
import io.harness.ssca.beans.OrchestrationStepSecretVariables;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.beans.source.ImageSbomSource;
import io.harness.ssca.beans.source.SbomSourceType;
import io.harness.ssca.beans.tools.syft.SyftSbomOrchestration;
import io.harness.ssca.cd.beans.stepinfo.CdSscaOrchestrationSpecParameters;
import io.harness.ssca.execution.SscaOrchestrationStepPluginUtils;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.plugin.PluginStep;
import io.harness.yaml.core.variables.SecretNGVariable;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.SSCA)
@UtilityClass
public class PluginUtils {
  public Map<String, String> getPluginCompatibleEnvVariables(PluginStep step, String identifier) {
    switch (step.getType()) {
      case CD_SSCA_ORCHESTRATION:
        return getSscaOrchestrationEnvVariables((CdSscaOrchestrationSpecParameters) step, identifier);
      default:
        return new HashMap<>();
    }
  }

  public Map<String, SecretNGVariable> getPluginCompatibleSecretVars(PluginStep step) {
    switch (step.getType()) {
      case CD_SSCA_ORCHESTRATION:
        return getSscaOrchestrationSecretVars((CdSscaOrchestrationSpecParameters) step);
      default:
        return new HashMap<>();
    }
  }

  public static Map<String, SecretNGVariable> getSscaOrchestrationSecretVars(
      CdSscaOrchestrationSpecParameters stepInfo) {
    if (stepInfo.getAttestation() != null && stepInfo.getAttestation().getPrivateKey() != null) {
      OrchestrationStepSecretVariables secretVariables =
          OrchestrationStepSecretVariables.builder()
              .attestationPrivateKey(stepInfo.getAttestation().getPrivateKey())
              .build();
      return SscaOrchestrationStepPluginUtils.getSscaOrchestrationSecretVars(secretVariables);
    }
    return new HashMap<>();
  }

  public Map<String, String> getSscaOrchestrationEnvVariables(
      CdSscaOrchestrationSpecParameters stepInfo, String identifier) {
    String tool = stepInfo.getTool().getType().toString();
    String format = getFormat(stepInfo);
    String sbomSource = null;
    if (stepInfo.getSource().getType().equals(SbomSourceType.IMAGE)) {
      sbomSource = resolveStringParameter("source", SscaConstants.SSCA_ORCHESTRATION_STEP, identifier,
          ((ImageSbomSource) stepInfo.getSource().getSbomSourceSpec()).getImage(), true);
    }

    OrchestrationStepEnvVariables envVariables = OrchestrationStepEnvVariables.builder()
                                                     .sbomGenerationTool(tool)
                                                     .sbomGenerationFormat(format)
                                                     .sbomSource(sbomSource)
                                                     .build();
    return SscaOrchestrationStepPluginUtils.getSScaOrchestrationStepEnvVariables(envVariables);
  }

  private static String getFormat(CdSscaOrchestrationSpecParameters stepInfo) {
    switch (stepInfo.getTool().getType()) {
      case SYFT:
        return ((SyftSbomOrchestration) stepInfo.getTool().getSbomOrchestrationSpec()).getFormat().toString();
      default:
        throw new ContainerStepExecutionException(
            String.format("Unsupported tool type: %s", stepInfo.getTool().getType()));
    }
  }
}
