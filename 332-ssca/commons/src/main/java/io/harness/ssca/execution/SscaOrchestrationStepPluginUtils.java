/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.pms.yaml.ParameterField;
import io.harness.ssca.beans.OrchestrationStepEnvVariables;
import io.harness.ssca.beans.OrchestrationStepSecretVariables;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.SSCA)
@UtilityClass
public class SscaOrchestrationStepPluginUtils {
  public static final String PLUGIN_TOOL = "PLUGIN_TOOL";
  public static final String PLUGIN_FORMAT = "PLUGIN_FORMAT";
  public static final String PLUGIN_SBOMSOURCE = "PLUGIN_SBOMSOURCE";
  public static final String PLUGIN_TYPE = "PLUGIN_TYPE";
  public static final String PLUGIN_SBOMDESTINATION = "PLUGIN_SBOMDESTINATION";
  public static final String SKIP_NORMALISATION = "SKIP_NORMALISATION";
  public static final String ATTESTATION_PRIVATE_KEY = "COSIGN_PASSWORD";

  public Map<String, String> getSScaOrchestrationStepEnvVariables(OrchestrationStepEnvVariables envVariables) {
    Map<String, String> envMap = new HashMap<>();
    envMap.put(PLUGIN_TOOL, envVariables.getSbomGenerationTool());
    envMap.put(PLUGIN_FORMAT, envVariables.getSbomGenerationFormat());
    envMap.put(PLUGIN_SBOMSOURCE, envVariables.getSbomSource());
    envMap.put(PLUGIN_TYPE, "Orchestrate");
    envMap.put(PLUGIN_SBOMDESTINATION, "harness/sbom");
    envMap.put(SKIP_NORMALISATION, "true");
    return envMap;
  }

  public Map<String, SecretNGVariable> getSscaOrchestrationSecretVars(
      OrchestrationStepSecretVariables secretVariables) {
    Map<String, SecretNGVariable> secretNGVariableMap = new HashMap<>();
    SecretRefData secretRefData = SecretRefHelper.createSecretRef(secretVariables.getAttestationPrivateKey());
    secretNGVariableMap.put(ATTESTATION_PRIVATE_KEY,
        SecretNGVariable.builder()
            .type(NGVariableType.SECRET)
            .value(ParameterField.createValueField(secretRefData))
            .name(ATTESTATION_PRIVATE_KEY)
            .build());
    return secretNGVariableMap;
  }
}
