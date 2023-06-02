/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.ssca;

import static io.harness.pms.expression.ExpressionResolverUtils.resolveStringParameter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.ssca.beans.OrchestrationStepEnvVariables;
import io.harness.ssca.beans.OrchestrationStepSecretVariables;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.beans.attestation.AttestationType;
import io.harness.ssca.beans.attestation.CosignAttestation;
import io.harness.ssca.beans.source.ImageSbomSource;
import io.harness.ssca.beans.source.SbomSourceType;
import io.harness.ssca.beans.tools.syft.SyftSbomOrchestration;
import io.harness.ssca.cd.beans.orchestration.CdSscaOrchestrationSpecParameters;
import io.harness.ssca.client.SSCAServiceUtils;
import io.harness.ssca.execution.orchestration.SscaOrchestrationStepPluginUtils;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.yaml.core.variables.SecretNGVariable;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.SSCA)
public class SscaOrchestrationPluginHelper {
  @Inject private SSCAServiceUtils sscaServiceUtils;

  public static Map<String, SecretNGVariable> getSscaOrchestrationSecretVars(
      CdSscaOrchestrationSpecParameters stepInfo) {
    if (stepInfo.getAttestation() != null && AttestationType.COSIGN.equals(stepInfo.getAttestation().getType())) {
      CosignAttestation cosignAttestation = (CosignAttestation) stepInfo.getAttestation().getAttestationSpec();
      OrchestrationStepSecretVariables secretVariables = OrchestrationStepSecretVariables.builder()
                                                             .attestationPrivateKey(cosignAttestation.getPrivateKey())
                                                             .cosignPassword(cosignAttestation.getPassword())
                                                             .build();
      return SscaOrchestrationStepPluginUtils.getSscaOrchestrationSecretVars(secretVariables);
    }
    return new HashMap<>();
  }

  public Map<String, String> getSscaOrchestrationEnvVariables(
      CdSscaOrchestrationSpecParameters stepInfo, String identifier, Ambiance ambiance) {
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
    Map<String, String> envVars = SscaOrchestrationStepPluginUtils.getSScaOrchestrationStepEnvVariables(envVariables);
    envVars.putAll(sscaServiceUtils.getSSCAServiceEnvVariables(AmbianceUtils.getAccountId(ambiance),
        AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance)));
    return envVars;
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
