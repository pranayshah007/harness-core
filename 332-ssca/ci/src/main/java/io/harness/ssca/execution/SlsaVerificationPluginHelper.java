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
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.slsa.beans.verification.source.SlsaDockerSourceSpec;
import io.harness.slsa.beans.verification.source.SlsaVerificationSourceType;
import io.harness.slsa.beans.verification.verify.CosignSlsaVerifyAttestation;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.beans.attestation.AttestationType;
import io.harness.ssca.beans.attestation.v1.CosignAttestationV1;
import io.harness.ssca.beans.stepinfo.SlsaVerificationStepInfo;
import io.harness.ssca.execution.enforcement.SscaEnforcementStepPluginUtils;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

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

  public Map<String, String> getSlsaVerificationStepEnvVariables(
      SlsaVerificationStepInfo slsaVerificationStepInfo, String identifier, Ambiance ambiance) {
    Map<String, String> envMap = new HashMap<>();
    if (slsaVerificationStepInfo.getSource() != null
        && slsaVerificationStepInfo.getSource().getType() == SlsaVerificationSourceType.DOCKER) {
      SlsaDockerSourceSpec spec = (SlsaDockerSourceSpec) slsaVerificationStepInfo.getSource().getSpec();
      envMap.put(PLUGIN_REPO,
          resolveStringParameter(
              "image_path", SscaConstants.SLSA_VERIFICATION, identifier, spec.getImage_path(), true));
      envMap.put(
          PLUGIN_TAG, resolveStringParameter("tag", SscaConstants.SLSA_VERIFICATION, identifier, spec.getTag(), true));
    }
    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    envMap.put(STEP_EXECUTION_ID, stepExecutionId);
    envMap.put(PLUGIN_TYPE, "verify");
    return envMap;
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
