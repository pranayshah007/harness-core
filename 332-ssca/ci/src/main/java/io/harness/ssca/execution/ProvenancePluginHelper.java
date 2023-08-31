/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveListParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.pms.yaml.ParameterField;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.beans.attestation.AttestationType;
import io.harness.ssca.beans.attestation.v1.CosignAttestationV1;
import io.harness.ssca.beans.provenance.DockerSourceSpec;
import io.harness.ssca.beans.provenance.ProvenanceSourceType;
import io.harness.ssca.beans.stepinfo.ProvenanceStepInfo;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.SSCA)
public class ProvenancePluginHelper {
  private static final String PLUGIN_REPO = "PLUGIN_REPO";
  private static final String COSIGN_PRIVATE_KEY = "COSIGN_PRIVATE_KEY";
  private static final String COSIGN_PASSWORD = "COSIGN_PASSWORD";
  private static final String PLUGIN_TAGS = "PLUGIN_TAGS";
  private static final String PLUGIN_DIGESTS = "PLUGIN_DIGESTS";
  private static final String PROVENANCE_PREDICATE = "PROVENANCE_PREDICATE";

  public Map<String, String> getProvenanceStepEnvVariables(ProvenanceStepInfo provenanceStepInfo, String identifier) {
    Map<String, String> envMap = new HashMap<>();
    if (provenanceStepInfo.getSource() != null
        && provenanceStepInfo.getSource().getType() == ProvenanceSourceType.DOCKER) {
      DockerSourceSpec spec = (DockerSourceSpec) provenanceStepInfo.getSource().getSpec();
      envMap.put(
          PLUGIN_REPO, resolveStringParameter("repo", SscaConstants.SLSA_PROVENANCE, identifier, spec.getRepo(), true));
      envMap.put(PLUGIN_TAGS,
          listToStringSlice(
              resolveListParameter("tags", SscaConstants.SLSA_PROVENANCE, identifier, spec.getTags(), true)));
      envMap.put(PLUGIN_DIGESTS, "");
      envMap.put(PROVENANCE_PREDICATE, "");
    }
    return envMap;
  }

  public Map<String, SecretNGVariable> getProvenanceStepSecretVariables(ProvenanceStepInfo stepInfo) {
    Map<String, SecretNGVariable> secretEnvMap = new HashMap<>();
    if (stepInfo.getAttestation() != null && AttestationType.COSIGN.equals(stepInfo.getAttestation().getType())) {
      CosignAttestationV1 attestationSpecV1 = (CosignAttestationV1) stepInfo.getAttestation().getSpec();
      if (EmptyPredicate.isNotEmpty(attestationSpecV1.getPrivate_key())) {
        SecretRefData secretRefData = SecretRefHelper.createSecretRef(attestationSpecV1.getPrivate_key());
        secretEnvMap.put(COSIGN_PRIVATE_KEY,
            SecretNGVariable.builder()
                .type(NGVariableType.SECRET)
                .value(ParameterField.createValueField(secretRefData))
                .name(COSIGN_PRIVATE_KEY)
                .build());
      }
      if (EmptyPredicate.isNotEmpty(attestationSpecV1.getPassword())) {
        SecretRefData secretRefData = SecretRefHelper.createSecretRef(attestationSpecV1.getPassword());
        secretEnvMap.put(COSIGN_PASSWORD,
            SecretNGVariable.builder()
                .type(NGVariableType.SECRET)
                .value(ParameterField.createValueField(secretRefData))
                .name(COSIGN_PASSWORD)
                .build());
      }
    }
    return secretEnvMap;
  }

  // converts list "value1", "value2" to string "value1,value2"
  private String listToStringSlice(List<String> stringList) {
    return String.join(",", stringList);
  }
}
