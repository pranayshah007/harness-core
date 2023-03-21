/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.ngexception.CIStageExecutionUserException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.beans.source.ImageSbomSource;
import io.harness.ssca.beans.source.SbomSourceType;
import io.harness.ssca.beans.stepinfo.SscaOrchestrationStepInfo;
import io.harness.ssca.beans.tools.syft.SyftSbomOrchestration;
import io.harness.ssca.client.SSCAServiceUtils;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.SSCA)
public class SscaOrchestrationPluginUtils {
  public static final String PLUGIN_TOOL = "PLUGIN_TOOL";
  public static final String PLUGIN_FORMAT = "PLUGIN_FORMAT";
  public static final String PLUGIN_SBOMSOURCE = "PLUGIN_SBOMSOURCE";
  public static final String PLUGIN_TYPE = "PLUGIN_TYPE";
  public static final String PLUGIN_SBOMDESTINATION = "PLUGIN_SBOMDESTINATION";
  public static final String SKIP_NORMALISATION = "SKIP_NORMALISATION";
  public static final String ATTESTATION_PRIVATE_KEY = "COSIGN_PASSWORD";
  public static final String DOCKER_USERNAME = "DOCKER_USERNAME";
  public static final String DOCKER_PASSW = "DOCKER_PASSWORD";
  public static final String DOCKER_REGISTRY = "DOCKER_REGISTRY";
  public static final String SSCA_CORE_URL = "SSCS_Core_Url";
  public static final String STEP_EXECUTION_ID = "STEP_EXECUTION_ID";
  @Inject private SSCAServiceUtils sscaServiceUtils;

  public Map<String, String> getSscaOrchestrationStepEnvVariables(
      SscaOrchestrationStepInfo stepInfo, String identifier, Ambiance ambiance) {
    Map<String, String> envMap = new HashMap<>();

    String tool = stepInfo.getTool().getType().toString();
    envMap.put(PLUGIN_TOOL, tool);

    String format = getFormat(stepInfo);
    envMap.put(PLUGIN_FORMAT, format);

    if (stepInfo.getSource().getType().equals(SbomSourceType.IMAGE)) {
      String sbomSource = resolveStringParameter("source", SscaConstants.SSCA_ORCHESTRATION_STEP, identifier,
          ((ImageSbomSource) stepInfo.getSource().getSbomSourceSpec()).getImage(), true);
      envMap.put(PLUGIN_SBOMSOURCE, sbomSource);
    }

    String runtimeId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    envMap.put(STEP_EXECUTION_ID, runtimeId);

    envMap.put(PLUGIN_TYPE, "Orchestrate");
    envMap.put(PLUGIN_SBOMDESTINATION, "harness/sbom");
    envMap.put(SKIP_NORMALISATION, "true");
    envMap.put(SSCA_CORE_URL, sscaServiceUtils.getSscaServiceConfig().getBaseUrl());
    return envMap;
  }

  public static Map<EnvVariableEnum, String> getConnectorSecretEnvMap() {
    Map<EnvVariableEnum, String> map = new HashMap<>();
    map.put(EnvVariableEnum.DOCKER_USERNAME, DOCKER_USERNAME);
    map.put(EnvVariableEnum.DOCKER_PASSWORD, DOCKER_PASSW);
    map.put(EnvVariableEnum.DOCKER_REGISTRY, DOCKER_REGISTRY);
    return map;
  }

  public static Map<String, SecretNGVariable> getSscaOrchestrationSecretVars(SscaOrchestrationStepInfo stepInfo) {
    Map<String, SecretNGVariable> secretNGVariableMap = new HashMap<>();
    if (stepInfo.getAttestation() != null && stepInfo.getAttestation().getPrivateKey() != null) {
      SecretRefData secretRefData = SecretRefHelper.createSecretRef(stepInfo.getAttestation().getPrivateKey());
      secretNGVariableMap.put(ATTESTATION_PRIVATE_KEY,
          SecretNGVariable.builder()
              .type(NGVariableType.SECRET)
              .value(ParameterField.createValueField(secretRefData))
              .name(ATTESTATION_PRIVATE_KEY)
              .build());
    }
    return secretNGVariableMap;
  }

  private static String getFormat(SscaOrchestrationStepInfo stepInfo) {
    switch (stepInfo.getTool().getType()) {
      case SYFT:
        return ((SyftSbomOrchestration) stepInfo.getTool().getSbomOrchestrationSpec()).getFormat().toString();
      default:
        throw new CIStageExecutionUserException(
            String.format("Unsupported tool type: %s", stepInfo.getTool().getType()));
    }
  }
}
