/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.cd.execution.enforcement;

import static io.harness.pms.expression.ExpressionResolverUtils.resolveStringParameter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.beans.attestation.AttestationType;
import io.harness.ssca.beans.attestation.verify.CosignVerifyAttestation;
import io.harness.ssca.beans.enforcement.EnforcementStepEnvVariables;
import io.harness.ssca.beans.enforcement.EnforcementStepSecretVariables;
import io.harness.ssca.beans.source.ImageSbomSource;
import io.harness.ssca.beans.source.SbomSourceType;
import io.harness.ssca.beans.store.HarnessStore;
import io.harness.ssca.beans.store.StoreType;
import io.harness.ssca.cd.beans.enforcement.CdSscaEnforcementSpecParameters;
import io.harness.ssca.client.SSCAServiceUtils;
import io.harness.ssca.execution.enforcement.SscaEnforcementStepPluginUtils;
import io.harness.yaml.core.variables.SecretNGVariable;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.SSCA)
public class CdSscaEnforcementPluginHelper {
  @Inject private SSCAServiceUtils sscaServiceUtils;

  public Map<String, String> getSscaEnforcementStepEnvVariables(
      CdSscaEnforcementSpecParameters specParameters, String identifier, Ambiance ambiance) {
    String sbomSource = null;
    if (SbomSourceType.IMAGE.equals(specParameters.getSource().getType())) {
      sbomSource = resolveStringParameter("source", SscaConstants.CD_SSCA_ENFORCEMENT, identifier,
          ((ImageSbomSource) specParameters.getSource().getSbomSourceSpec()).getImage(), true);
    }

    String policyFile = null;
    if (specParameters.getPolicy().getStore() != null
        && StoreType.HARNESS.equals(specParameters.getPolicy().getStore().getType())) {
      policyFile = resolveStringParameter("file", SscaConstants.CD_SSCA_ENFORCEMENT, identifier,
          ((HarnessStore) specParameters.getPolicy().getStore().getStoreSpec()).getFile(), true);
    }

    String runtimeId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    Map<String, String> envVars =
        SscaEnforcementStepPluginUtils.getSscaEnforcementStepEnvVariables(EnforcementStepEnvVariables.builder()
                                                                              .stepExecutionId(runtimeId)
                                                                              .sbomSource(sbomSource)
                                                                              .harnessPolicyFileId(policyFile)
                                                                              .build());
    envVars.putAll(sscaServiceUtils.getSSCAServiceEnvVariables(AmbianceUtils.getAccountId(ambiance),
        AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance)));
    return envVars;
  }

  public static Map<String, SecretNGVariable> getSscaEnforcementSecretVariables(
      CdSscaEnforcementSpecParameters specParameters, String identifier) {
    if (specParameters.getVerifyAttestation() != null
        && AttestationType.COSIGN.equals(specParameters.getVerifyAttestation().getType())) {
      CosignVerifyAttestation verifyAttestation =
          (CosignVerifyAttestation) specParameters.getVerifyAttestation().getVerifyAttestationSpec();
      String cosignPublicKey = resolveStringParameter(
          "publicKey", SscaConstants.CD_SSCA_ENFORCEMENT, identifier, verifyAttestation.getPublicKey(), true);
      return SscaEnforcementStepPluginUtils.getSscaEnforcementSecretVars(
          EnforcementStepSecretVariables.builder().cosignPublicKey(cosignPublicKey).build());
    }
    return new HashMap<>();
  }
}
