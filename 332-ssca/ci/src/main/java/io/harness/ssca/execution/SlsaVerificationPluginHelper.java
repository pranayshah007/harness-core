/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.trimLeadingCharacter;
import static org.springframework.util.StringUtils.trimTrailingCharacter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.slsa.beans.verification.source.SlsaDockerSourceSpec;
import io.harness.slsa.beans.verification.source.SlsaGcrSourceSpec;
import io.harness.slsa.beans.verification.source.SlsaVerificationSourceType;
import io.harness.slsa.beans.verification.verify.CosignSlsaVerifyAttestation;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.beans.attestation.AttestationType;
import io.harness.ssca.beans.stepinfo.SlsaVerificationStepInfo;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.SSCA)
public class SlsaVerificationPluginHelper {
  private static final String PLUGIN_REPO = "PLUGIN_REPO";
  private static final String COSIGN_PUBLIC_KEY = "COSIGN_PUBLIC_KEY";
  private static final String PLUGIN_TAG = "PLUGIN_TAG";
  private static final String PLUGIN_DIGEST = "PLUGIN_DIGEST";
  public static final String PLUGIN_TYPE = "PLUGIN_TYPE";
  public static final String STEP_EXECUTION_ID = "STEP_EXECUTION_ID";
  private static final String PLUGIN_REGISTRY_TYPE = "PLUGIN_REGISTRY_TYPE";
  private static final String PLUGIN_REGISTRY = "PLUGIN_REGISTRY";

  public Map<String, String> getSlsaVerificationStepEnvVariables(
      SlsaVerificationStepInfo slsaVerificationStepInfo, String identifier, Ambiance ambiance) {
    Map<String, String> envMap = new HashMap<>();
    if (slsaVerificationStepInfo.getSource() != null && slsaVerificationStepInfo.getSource().getType() != null) {
      envMap.put(PLUGIN_REGISTRY_TYPE, slsaVerificationStepInfo.getSource().getType().getRegistryType());
    }
    if (slsaVerificationStepInfo.getSource() != null
        && slsaVerificationStepInfo.getSource().getType() == SlsaVerificationSourceType.DOCKER) {
      populateDockerEnvVariables(slsaVerificationStepInfo, identifier, envMap);
    }
    if (slsaVerificationStepInfo.getSource() != null
        && slsaVerificationStepInfo.getSource().getType() == SlsaVerificationSourceType.GCR) {
      populateGcrEnvVariables(slsaVerificationStepInfo, identifier, envMap);
    }
    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    envMap.put(STEP_EXECUTION_ID, stepExecutionId);
    envMap.put(PLUGIN_TYPE, "verify");
    return envMap;
  }

  private void populateGcrEnvVariables(
      SlsaVerificationStepInfo slsaVerificationStepInfo, String identifier, Map<String, String> envMap) {
    SlsaGcrSourceSpec spec = (SlsaGcrSourceSpec) slsaVerificationStepInfo.getSource().getSpec();
    String host = resolveStringParameter("host", SscaConstants.SLSA_VERIFICATION, identifier, spec.getHost(), true);
    String projectID =
        resolveStringParameter("projectID", SscaConstants.SLSA_VERIFICATION, identifier, spec.getProject_id(), true);
    String registry = null;
    if (isNotEmpty(host) && isNotEmpty(projectID)) {
      registry = format("%s/%s", trimTrailingCharacter(host, '/'), trimLeadingCharacter(projectID, '/'));
    }
    envMap.put(PLUGIN_REGISTRY, registry);
    envMap.put(PLUGIN_REPO,
        resolveStringParameter("imageName", SscaConstants.SLSA_VERIFICATION, identifier, spec.getImage_name(), true));
    envMap.put(
        PLUGIN_TAG, resolveStringParameter("tag", SscaConstants.SLSA_VERIFICATION, identifier, spec.getTag(), true));
  }

  private void populateDockerEnvVariables(
      SlsaVerificationStepInfo slsaVerificationStepInfo, String identifier, Map<String, String> envMap) {
    SlsaDockerSourceSpec spec = (SlsaDockerSourceSpec) slsaVerificationStepInfo.getSource().getSpec();
    envMap.put(PLUGIN_REPO,
        resolveStringParameter("image_path", SscaConstants.SLSA_VERIFICATION, identifier, spec.getImage_path(), true));
    envMap.put(
        PLUGIN_TAG, resolveStringParameter("tag", SscaConstants.SLSA_VERIFICATION, identifier, spec.getTag(), true));
  }

  public Map<String, SecretNGVariable> getSlsaVerificationStepSecretVariables(
      SlsaVerificationStepInfo stepInfo, String stepIdentifier) {
    Map<String, SecretNGVariable> secretEnvMap = new HashMap<>();
    if (stepInfo.getSlsaVerifyAttestation() != null
        && AttestationType.COSIGN.equals(stepInfo.getSlsaVerifyAttestation().getType())) {
      CosignSlsaVerifyAttestation attestation =
          (CosignSlsaVerifyAttestation) stepInfo.getSlsaVerifyAttestation().getSlsaVerifyAttestationSpec();

      String cosignPublicKey = resolveStringParameter(
          "public_key", SscaConstants.SLSA_VERIFICATION, stepIdentifier, attestation.getPublicKey(), true);
      SecretRefData secretRefData = SecretRefHelper.createSecretRef(cosignPublicKey);
      secretEnvMap.put(COSIGN_PUBLIC_KEY,
          SecretNGVariable.builder()
              .type(NGVariableType.SECRET)
              .value(ParameterField.createValueField(secretRefData))
              .name(COSIGN_PUBLIC_KEY)
              .build());
    }
    return secretEnvMap;
  }
}
