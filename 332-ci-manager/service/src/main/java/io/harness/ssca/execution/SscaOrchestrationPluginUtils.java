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
import io.harness.exception.ngexception.CIStageExecutionUserException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.ssca.beans.OrchestrationStepEnvVariables;
import io.harness.ssca.beans.OrchestrationStepSecretVariables;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.beans.attestation.AttestationType;
import io.harness.ssca.beans.attestation.CosignAttestation;
import io.harness.ssca.beans.source.ImageSbomSource;
import io.harness.ssca.beans.source.SbomSourceType;
import io.harness.ssca.beans.stepinfo.SscaOrchestrationStepInfo;
import io.harness.ssca.beans.tools.syft.SyftSbomOrchestration;
import io.harness.ssca.client.SSCAServiceUtils;
import io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils;
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
  @Inject private SSCAServiceUtils sscaServiceUtils;

  public Map<String, String> getSscaOrchestrationStepEnvVariables(
      SscaOrchestrationStepInfo stepInfo, String identifier, Ambiance ambiance) {
    String tool = stepInfo.getTool().getType().toString();
    String format = getFormat(stepInfo);

    String sbomSource = null;
    if (stepInfo.getSource().getType().equals(SbomSourceType.IMAGE)) {
      sbomSource = resolveStringParameter("source", SscaConstants.SSCA_ORCHESTRATION_STEP, identifier,
          ((ImageSbomSource) stepInfo.getSource().getSbomSourceSpec()).getImage(), true);
    }

    String runtimeId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    OrchestrationStepEnvVariables envVariables =
        OrchestrationStepEnvVariables.builder()
            .sbomGenerationTool(tool)
            .sbomGenerationFormat(format)
            .sbomSource(sbomSource)
            .sscaCoreUrl(sscaServiceUtils.getSscaServiceConfig().getHttpClientConfig().getBaseUrl())
            .stepExecutionId(runtimeId)
            .stepIdentifier(identifier)
            .build();
    return SscaOrchestrationStepPluginUtils.getSScaOrchestrationStepEnvVariables(envVariables);
  }

  public static Map<String, SecretNGVariable> getSscaOrchestrationSecretVars(SscaOrchestrationStepInfo stepInfo) {
    Map<String, SecretNGVariable> secretNGVariableMap = new HashMap<>();
    if (stepInfo.getAttestation() != null && AttestationType.COSIGN.equals(stepInfo.getAttestation().getType())) {
      CosignAttestation cosignAttestation = (CosignAttestation) stepInfo.getAttestation().getAttestationSpec();
      OrchestrationStepSecretVariables secretVariables = OrchestrationStepSecretVariables.builder()
                                                             .attestationPrivateKey(cosignAttestation.getPrivateKey())
                                                             .cosignPassword(cosignAttestation.getPassword())
                                                             .build();
      return SscaOrchestrationStepPluginUtils.getSscaOrchestrationSecretVars(secretVariables);
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
