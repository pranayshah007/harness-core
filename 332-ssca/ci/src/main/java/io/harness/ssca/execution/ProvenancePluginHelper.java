/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveListParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_JSON_KEY;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.trimLeadingCharacter;
import static org.springframework.util.StringUtils.trimTrailingCharacter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.ProvenanceArtifact;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.steps.outcome.CIStepArtifactOutcome;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.JsonUtils;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.beans.attestation.AttestationType;
import io.harness.ssca.beans.attestation.v1.CosignAttestationV1;
import io.harness.ssca.beans.provenance.DockerSourceSpec;
import io.harness.ssca.beans.provenance.GcrSourceSpec;
import io.harness.ssca.beans.provenance.ProvenanceSourceType;
import io.harness.ssca.beans.stepinfo.ProvenanceStepInfo;
import io.harness.ssca.execution.provenance.ProvenanceStepUtils;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.SSCA)
public class ProvenancePluginHelper {
  @Inject private OutcomeService outcomeService;
  protected static final String PLUGIN_REPO = "PLUGIN_REPO";
  protected static final String PLUGIN_REGISTRY = "PLUGIN_REGISTRY";
  protected static final String COSIGN_PRIVATE_KEY = "COSIGN_PRIVATE_KEY";
  protected static final String COSIGN_PASSWORD = "COSIGN_PASSWORD";
  protected static final String PLUGIN_TAGS = "PLUGIN_TAGS";
  protected static final String PLUGIN_DIGESTS = "PLUGIN_DIGESTS";
  protected static final String PROVENANCE_PREDICATE = "PROVENANCE_PREDICATE";
  protected static final String PLUGIN_TYPE = "PLUGIN_TYPE";
  protected static final String STEP_EXECUTION_ID = "STEP_EXECUTION_ID";
  protected static final String DOCKER_USERNAME = "DOCKER_USERNAME";
  protected static final String DOCKER_PASSW = "DOCKER_PASSWORD";
  protected static final String DOCKER_REGISTRY = "DOCKER_REGISTRY";
  protected static final String PLUGIN_REGISTRY_TYPE = "PLUGIN_REGISTRY_TYPE";

  public Map<String, String> getProvenanceStepEnvVariables(
      ProvenanceStepInfo provenanceStepInfo, String identifier, Ambiance ambiance) {
    Map<String, String> envMap = new HashMap<>();
    if (provenanceStepInfo.getSource() != null && provenanceStepInfo.getSource().getType() != null) {
      envMap.put(PLUGIN_REGISTRY_TYPE, provenanceStepInfo.getSource().getType().getRegistryType());
    }
    if (provenanceStepInfo.getSource() != null
        && provenanceStepInfo.getSource().getType() == ProvenanceSourceType.DOCKER) {
      populateDockerEnvVariables(provenanceStepInfo, identifier, envMap);
    }
    if (provenanceStepInfo.getSource() != null
        && provenanceStepInfo.getSource().getType() == ProvenanceSourceType.GCR) {
      populateGcrEnvVariables(provenanceStepInfo, identifier, envMap);
    }
    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    envMap.put(STEP_EXECUTION_ID, stepExecutionId);
    envMap.put(PLUGIN_TYPE, "attest");
    envMap.put(PLUGIN_DIGESTS, "");
    envMap.put(PROVENANCE_PREDICATE, "");
    return envMap;
  }

  private void populateDockerEnvVariables(
      ProvenanceStepInfo provenanceStepInfo, String identifier, Map<String, String> envMap) {
    DockerSourceSpec spec = (DockerSourceSpec) provenanceStepInfo.getSource().getSpec();
    envMap.put(
        PLUGIN_REPO, resolveStringParameter("repo", SscaConstants.SLSA_PROVENANCE, identifier, spec.getRepo(), true));
    envMap.put(PLUGIN_TAGS,
        listToStringSlice(
            resolveListParameter("tags", SscaConstants.SLSA_PROVENANCE, identifier, spec.getTags(), true)));
  }

  private void populateGcrEnvVariables(
      ProvenanceStepInfo provenanceStepInfo, String identifier, Map<String, String> envMap) {
    GcrSourceSpec spec = (GcrSourceSpec) provenanceStepInfo.getSource().getSpec();
    String host = resolveStringParameter("host", SscaConstants.SLSA_PROVENANCE, identifier, spec.getHost(), true);
    String projectID =
        resolveStringParameter("projectID", SscaConstants.SLSA_PROVENANCE, identifier, spec.getProjectID(), true);
    String registry = null;
    if (isNotEmpty(host) && isNotEmpty(projectID)) {
      registry = format("%s/%s", trimTrailingCharacter(host, '/'), trimLeadingCharacter(projectID, '/'));
    }
    envMap.put(PLUGIN_REGISTRY, registry);
    envMap.put(PLUGIN_REPO,
        resolveStringParameter("imageName", SscaConstants.SLSA_PROVENANCE, identifier, spec.getImageName(), true));
    envMap.put(PLUGIN_TAGS,
        listToStringSlice(
            resolveListParameter("tags", SscaConstants.SLSA_PROVENANCE, identifier, spec.getTags(), true)));
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

  public Map<String, String> getProvenanceStepEnvVariablesAtRuntime(
      ProvenanceStepInfo provenanceStepInfo, String identifier, Ambiance ambiance) {
    String previousStepIdentifier = identifier.replace("_" + ProvenanceStepUtils.PROVENANCE_STEP, "");
    OptionalOutcome outcomeOpt = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject("artifact_" + previousStepIdentifier));
    if (!outcomeOpt.isFound() || !(outcomeOpt.getOutcome() instanceof CIStepArtifactOutcome)) {
      throw new CIStageExecutionException(
          "Could not find artifact details for previous build and push step with complete identifier: "
          + previousStepIdentifier);
    }
    Outcome outcome = outcomeOpt.getOutcome();
    CIStepArtifactOutcome ciStepArtifactOutcome = (CIStepArtifactOutcome) outcome;
    if (ciStepArtifactOutcome.getStepArtifacts() == null
        || EmptyPredicate.isEmpty(ciStepArtifactOutcome.getStepArtifacts().getProvenanceArtifacts())
        || ciStepArtifactOutcome.getStepArtifacts().getProvenanceArtifacts().get(0) == null) {
      throw new CIStageExecutionException(
          "Could not find provenance file for previous build and push step with complete identifier: "
          + previousStepIdentifier);
    }
    if (EmptyPredicate.isEmpty(ciStepArtifactOutcome.getStepArtifacts().getPublishedImageArtifacts())) {
      throw new CIStageExecutionException(
          "Could not find published image for previous build and push step with complete identifier: "
          + previousStepIdentifier);
    }
    List<PublishedImageArtifact> imageArtifacts = ciStepArtifactOutcome.getStepArtifacts().getPublishedImageArtifacts();
    ProvenanceArtifact provenanceArtifact = ciStepArtifactOutcome.getStepArtifacts().getProvenanceArtifacts().get(0);
    Map<String, String> envMap = new HashMap<>();
    if (provenanceStepInfo.getSource() != null && provenanceStepInfo.getSource().getType() != null) {
      envMap.put(PLUGIN_REGISTRY_TYPE, provenanceStepInfo.getSource().getType().getRegistryType());
    }
    if (provenanceStepInfo.getSource() != null
        && provenanceStepInfo.getSource().getType() == ProvenanceSourceType.DOCKER) {
      populateDockerEnvVariables(provenanceStepInfo, identifier, envMap);
    }
    if (provenanceStepInfo.getSource() != null
        && provenanceStepInfo.getSource().getType() == ProvenanceSourceType.GCR) {
      populateGcrEnvVariables(provenanceStepInfo, identifier, envMap);
    }
    List<String> digests = imageArtifacts.stream().map(PublishedImageArtifact::getDigest).collect(Collectors.toList());
    List<String> tags = imageArtifacts.stream().map(PublishedImageArtifact::getTag).collect(Collectors.toList());
    envMap.put(PLUGIN_DIGESTS, listToStringSlice(digests));
    envMap.put(PLUGIN_TAGS, listToStringSlice(tags));
    envMap.put(PROVENANCE_PREDICATE, JsonUtils.asJson(provenanceArtifact.getPredicate()));
    String stepExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    envMap.put(STEP_EXECUTION_ID, stepExecutionId);
    envMap.put(PLUGIN_TYPE, "attest");
    return envMap;
  }

  // converts list "value1", "value2" to string "value1,value2"
  private String listToStringSlice(List<String> stringList) {
    return String.join(",", stringList);
  }

  public static Map<EnvVariableEnum, String> getConnectorSecretEnvMap() {
    Map<EnvVariableEnum, String> map = new HashMap<>();
    map.put(EnvVariableEnum.DOCKER_USERNAME, DOCKER_USERNAME);
    map.put(EnvVariableEnum.DOCKER_PASSWORD, DOCKER_PASSW);
    map.put(EnvVariableEnum.DOCKER_REGISTRY, DOCKER_REGISTRY);
    map.put(EnvVariableEnum.GCP_KEY, PLUGIN_JSON_KEY);
    return map;
  }
}
