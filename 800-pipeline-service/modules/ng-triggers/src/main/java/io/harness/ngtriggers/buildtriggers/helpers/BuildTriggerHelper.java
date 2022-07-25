/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.jackson.JsonNodeUtils;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.source.NGTriggerSpecV2;
import io.harness.ngtriggers.beans.source.artifact.BuildAware;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.inputset.InputSetErrorResponseDTOPMS;
import io.harness.pms.inputset.InputSetErrorWrapperDTOPMS;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.pipeline.TemplatesResolvedPipelineResponseDTO;
import io.harness.pms.yaml.YamlUtils;
import io.harness.polling.contracts.AcrPayload;
import io.harness.polling.contracts.ArtifactoryRegistryPayload;
import io.harness.polling.contracts.BuildInfo;
import io.harness.polling.contracts.DockerHubPayload;
import io.harness.polling.contracts.EcrPayload;
import io.harness.polling.contracts.GcrPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.contracts.PollingResponse;
import io.harness.remote.client.NGRestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.apache.logging.log4j.util.Strings;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(PIPELINE)
public class BuildTriggerHelper {
  private PipelineServiceClient pipelineServiceClient;

  public Optional<String> fetchPipelineForTrigger(NGTriggerEntity ngTriggerEntity) {
    PMSPipelineResponseDTO response = NGRestUtils.getResponse(pipelineServiceClient.getPipelineByIdentifier(
        ngTriggerEntity.getTargetIdentifier(), ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(),
        ngTriggerEntity.getProjectIdentifier(), null, null, false));

    return response != null ? Optional.of(response.getYamlPipeline()) : Optional.empty();
  }

  public Optional<String> fetchResolvedTemplatesPipelineForTrigger(NGTriggerEntity ngTriggerEntity) {
    TemplatesResolvedPipelineResponseDTO response =
        NGRestUtils.getResponse(pipelineServiceClient.getResolvedTemplatesPipelineByIdentifier(
            ngTriggerEntity.getTargetIdentifier(), ngTriggerEntity.getAccountId(), ngTriggerEntity.getOrgIdentifier(),
            ngTriggerEntity.getProjectIdentifier(), null, null, false));

    return response != null ? Optional.of(response.getResolvedTemplatesPipelineYaml()) : Optional.empty();
  }

  public Map<String, JsonNode> fetchTriggerBuildSpecMap(NGTriggerEntity ngTriggerEntity) throws IOException {
    JsonNode jsonNode = YamlUtils.readTree(ngTriggerEntity.getYaml()).getNode().getCurrJsonNode();
    return JsonNodeUtils.getMap(jsonNode.get("trigger").get("source"), "spec");
  }

  public Map<String, Object> generateFinalMapWithBuildSpecFromPipeline(
      String pipeline, String stageRef, String buildRef, List<String> fqnDisplayStrs) {
    YamlConfig yamlConfig = new YamlConfig(pipeline);

    fqnDisplayStrs = fqnDisplayStrs.stream()
                         .map(str -> str.replace("STAGE_REF", stageRef).replace("BUILD_REF", buildRef))
                         .collect(toList());

    Map<String, Object> fqnToValueMap = new HashMap<>();
    for (Map.Entry<FQN, Object> entry : yamlConfig.getFqnToValueMap().entrySet()) {
      String key = fqnDisplayStrs.stream()
                       .filter(str -> entry.getKey().display().toLowerCase().startsWith(str.toLowerCase()))
                       .findFirst()
                       .orElse(null);
      if (key == null) {
        continue;
      }

      FQN mapKey = entry.getKey();
      String display = mapKey.display();
      fqnToValueMap.put(display.substring(key.length() + 1, display.length() - 1), entry.getValue());
    }

    return fqnToValueMap;
  }

  public void validateBuildType(BuildTriggerOpsData buildTriggerOpsData) {
    EngineExpressionEvaluator engineExpressionEvaluator = new EngineExpressionEvaluator(null);
    TextNode typeFromPipeline = (TextNode) buildTriggerOpsData.getPipelineBuildSpecMap().get("type");
    TextNode typeFromTrigger =
        (TextNode) engineExpressionEvaluator.evaluateExpression("type", buildTriggerOpsData.getTriggerSpecMap());
    if (typeFromTrigger == null) {
      throw new InvalidRequestException(
          "Type filed is not present in Trigger Spec. Its needed for Artifact/Manifest Triggers");
    }

    if (!typeFromPipeline.asText().equals(typeFromTrigger.asText())) {
      throw new InvalidRequestException(new StringBuilder(128)
                                            .append("Artifact/Manifest Type in Trigger: ")
                                            .append(typeFromTrigger.asText())
                                            .append(", does not match with one in Pipeline: ")
                                            .append(typeFromPipeline.asText())
                                            .toString());
    }
  }

  public String fetchBuildType(Map<String, Object> buildTriggerSpecMap) {
    EngineExpressionEvaluator engineExpressionEvaluator = new EngineExpressionEvaluator(null);
    return ((TextNode) engineExpressionEvaluator.evaluateExpression("type", buildTriggerSpecMap)).asText();
  }

  public Map<String, Object> convertMapForExprEvaluation(Map<String, JsonNode> triggerSpecMap) {
    Map<String, Object> map = new HashMap<>();
    triggerSpecMap.forEach((k, v) -> map.put(k, v));
    return map;
  }

  public BuildTriggerOpsData generateBuildTriggerOpsDataForManifest(TriggerDetails triggerDetails, String pipelineYml)
      throws Exception {
    Map<String, JsonNode> triggerManifestSpecMap = fetchTriggerBuildSpecMap(triggerDetails.getNgTriggerEntity());

    String stageRef = triggerManifestSpecMap.get("stageIdentifier").asText();
    String buildRef = triggerManifestSpecMap.get("manifestRef").asText();
    List<String> keys = Arrays.asList(
        "pipeline.stages.stage[identifier:STAGE_REF].spec.serviceConfig.serviceDefinition.spec.manifests.manifest[identifier:BUILD_REF]",
        "pipeline.stages.stage[identifier:STAGE_REF].spec.serviceConfig.stageOverrides.manifests.manifest[identifier:BUILD_REF]",
        "pipeline.stages.PARALLEL.stage[identifier:STAGE_REF].spec.serviceConfig.serviceDefinition.spec.manifests.manifest[identifier:BUILD_REF]",
        "pipeline.stages.PARALLEL.stage[identifier:STAGE_REF].spec.serviceConfig.stageOverrides.manifests.manifest[identifier:BUILD_REF]");

    Map<String, Object> pipelineBuildSpecMap =
        generateFinalMapWithBuildSpecFromPipeline(pipelineYml, stageRef, buildRef, keys);

    Map<String, Object> manifestTriggerSpecMap = convertMapForExprEvaluation(triggerManifestSpecMap);
    return BuildTriggerOpsData.builder()
        .pipelineBuildSpecMap(pipelineBuildSpecMap)
        .triggerSpecMap(manifestTriggerSpecMap)
        .triggerDetails(triggerDetails)
        .build();
  }

  public BuildTriggerOpsData generateBuildTriggerOpsDataForArtifact(TriggerDetails triggerDetails, String pipelineYml)
      throws Exception {
    Map<String, JsonNode> triggerArtifactSpecMap = fetchTriggerBuildSpecMap(triggerDetails.getNgTriggerEntity());

    String stageRef = triggerArtifactSpecMap.get("stageIdentifier").asText();
    String buildRef = triggerArtifactSpecMap.get("artifactRef").asText();

    List<String> keys = new ArrayList<>();

    if (buildRef.equals("primary")) {
      keys.add(
          "pipeline.stages.stage[identifier:STAGE_REF].spec.serviceConfig.serviceDefinition.spec.artifacts.primary");
      keys.add("pipeline.stages.stage[identifier:STAGE_REF].spec.serviceConfig.stageOverrides.artifacts.primary");
      keys.add(
          "pipeline.stages.parallel.stage[identifier:STAGE_REF].spec.serviceConfig.serviceDefinition.spec.artifacts.primary");
      keys.add(
          "pipeline.stages.parallel.stage[identifier:STAGE_REF].spec.serviceConfig.stageOverrides.artifacts.primary");
    } else {
      keys.add(
          "pipeline.stages.stage[identifier:STAGE_REF].spec.serviceConfig.serviceDefinition.spec.artifacts.sidecars.sidecar[identifier:BUILD_REF]");
      keys.add(
          "pipeline.stages.stage[identifier:STAGE_REF].spec.serviceConfig.stageOverrides.artifacts.sidecars.sidecar[identifier:BUILD_REF]");
      keys.add(
          "pipeline.stages.parallel.stage[identifier:STAGE_REF].spec.serviceConfig.serviceDefinition.spec.artifacts.sidecars.sidecar[identifier:BUILD_REF]");
      keys.add(
          "pipeline.stages.parallel.stage[identifier:STAGE_REF].spec.serviceConfig.stageOverrides.artifacts.sidecars.sidecar[identifier:BUILD_REF]");
    }
    Map<String, Object> pipelineBuildSpecMap =
        generateFinalMapWithBuildSpecFromPipeline(pipelineYml, stageRef, buildRef, keys);

    Map<String, Object> manifestTriggerSpecMap = convertMapForExprEvaluation(triggerArtifactSpecMap);
    return BuildTriggerOpsData.builder()
        .pipelineBuildSpecMap(pipelineBuildSpecMap)
        .triggerSpecMap(manifestTriggerSpecMap)
        .triggerDetails(triggerDetails)
        .build();
  }

  public String fetchStoreTypeForHelm(BuildTriggerOpsData buildTriggerOpsData) {
    EngineExpressionEvaluator engineExpressionEvaluator = new EngineExpressionEvaluator(null);
    Object evaluateExpression =
        engineExpressionEvaluator.evaluateExpression("spec.store.type", buildTriggerOpsData.getTriggerSpecMap());
    if (evaluateExpression == null) {
      return null;
    }
    return ((TextNode) engineExpressionEvaluator.evaluateExpression(
                "spec.store.type", buildTriggerOpsData.getTriggerSpecMap()))
        .asText();
  }

  public String fetchValueFromJsonNode(String path, Map<String, Object> map) {
    EngineExpressionEvaluator engineExpressionEvaluator = new EngineExpressionEvaluator(null);
    Object evaluateExpression = engineExpressionEvaluator.evaluateExpression(path, map);
    if (evaluateExpression == null) {
      return EMPTY;
    }

    return ((TextNode) engineExpressionEvaluator.evaluateExpression(path, map)).asText();
  }

  public void validatePollingItemForArtifact(PollingItem pollingItem) {
    String error = checkFiledValueError("ConnectorRef", pollingItem.getPollingPayloadData().getConnectorRef());
    if (isNotBlank(error)) {
      throw new InvalidRequestException(error);
    }

    PollingPayloadData pollingPayloadData = pollingItem.getPollingPayloadData();
    if (pollingPayloadData.hasGcrPayload()) {
      validatePollingItemForGcr(pollingItem);
    } else if (pollingPayloadData.hasDockerHubPayload()) {
      validatePollingItemForDockerRegistry(pollingItem);
    } else if (pollingPayloadData.hasEcrPayload()) {
      validatePollingItemForEcr(pollingItem);
    } else if (pollingPayloadData.hasArtifactoryRegistryPayload()) {
      validatePollingItemForArtifactory(pollingItem);
    } else if (pollingPayloadData.hasAcrPayload()) {
      validatePollingItemForAcr(pollingItem);
    } else {
      throw new InvalidRequestException("Invalid Polling Type");
    }
  }

  private void validatePollingItemForGcr(PollingItem pollingItem) {
    GcrPayload gcrPayload = pollingItem.getPollingPayloadData().getGcrPayload();
    String error = checkFiledValueError("imagePath", gcrPayload.getImagePath());
    if (isNotBlank(error)) {
      throw new InvalidRequestException(error);
    }

    error = checkFiledValueError("registryHostname", gcrPayload.getRegistryHostname());
    if (isNotBlank(error)) {
      throw new InvalidRequestException(error);
    }
  }

  private void validatePollingItemForDockerRegistry(PollingItem pollingItem) {
    DockerHubPayload dockerHubPayload = pollingItem.getPollingPayloadData().getDockerHubPayload();
    String error = checkFiledValueError("imagePath", dockerHubPayload.getImagePath());
    if (isNotBlank(error)) {
      throw new InvalidRequestException(error);
    }
  }

  private void validatePollingItemForArtifactory(PollingItem pollingItem) {
    ArtifactoryRegistryPayload artifactoryRegistryPayload =
        pollingItem.getPollingPayloadData().getArtifactoryRegistryPayload();
    String error = checkFiledValueError("repository", artifactoryRegistryPayload.getRepository());
    if (isNotBlank(error)) {
      throw new InvalidRequestException(error);
    }

    error = checkFiledValueError("repositoryFormat", artifactoryRegistryPayload.getRepositoryFormat());
    if (isNotBlank(error)) {
      throw new InvalidRequestException(error);
    }

    String repositoryFormat = artifactoryRegistryPayload.getRepositoryFormat();
    if (repositoryFormat.equals("generic")) {
      error = checkFiledValueError("artifactDirectory", artifactoryRegistryPayload.getArtifactDirectory());
      if (isNotBlank(error)) {
        throw new InvalidRequestException(error);
      }
    } else {
      error = checkFiledValueError("artifactPath", artifactoryRegistryPayload.getArtifactPath());
      if (isNotBlank(error)) {
        throw new InvalidRequestException(error);
      }
    }
  }

  private void validatePollingItemForEcr(PollingItem pollingItem) {
    EcrPayload ecrPayload = pollingItem.getPollingPayloadData().getEcrPayload();
    String error = checkFiledValueError("region", ecrPayload.getRegion());
    if (isNotBlank(error)) {
      throw new InvalidRequestException(error);
    }

    error = checkFiledValueError("imagePath", ecrPayload.getImagePath());
    if (isNotBlank(error)) {
      throw new InvalidRequestException(error);
    }
  }

  private void validatePollingItemForAcr(PollingItem pollingItem) {
    AcrPayload acrPayload = pollingItem.getPollingPayloadData().getAcrPayload();

    String error = checkFiledValueError("subscriptionId", acrPayload.getSubscriptionId());
    if (isNotBlank(error)) {
      throw new InvalidRequestException(error);
    }

    error = checkFiledValueError("registry", acrPayload.getRegistry());
    if (isNotBlank(error)) {
      throw new InvalidRequestException(error);
    }

    error = checkFiledValueError("repository", acrPayload.getRepository());
    if (isNotBlank(error)) {
      throw new InvalidRequestException(error);
    }
  }

  public void validatePollingItemForHelmChart(PollingItem pollingItem) {
    String error = checkFiledValueError("ConnectorRef", pollingItem.getPollingPayloadData().getConnectorRef());
    if (isNotBlank(error)) {
      throw new InvalidRequestException(error);
    }

    if (pollingItem.getPollingPayloadData().hasHttpHelmPayload()) {
      error =
          checkFiledValueError("ChartName", pollingItem.getPollingPayloadData().getHttpHelmPayload().getChartName());
      if (isNotBlank(error)) {
        throw new InvalidRequestException(error);
      }

      error = checkFiledValueError(
          "helmVersion", pollingItem.getPollingPayloadData().getHttpHelmPayload().getHelmVersion().name());
      if (isNotBlank(error)) {
        throw new InvalidRequestException(error);
      }
    } else if (pollingItem.getPollingPayloadData().hasS3HelmPayload()) {
      error = checkFiledValueError("ChartName", pollingItem.getPollingPayloadData().getS3HelmPayload().getChartName());
      if (isNotBlank(error)) {
        throw new InvalidRequestException(error);
      }

      error = checkFiledValueError(
          "helmVersion", pollingItem.getPollingPayloadData().getS3HelmPayload().getHelmVersion().name());
      if (isNotBlank(error)) {
        throw new InvalidRequestException(error);
      }
    } else if (pollingItem.getPollingPayloadData().hasGcsHelmPayload()) {
      error = checkFiledValueError("ChartName", pollingItem.getPollingPayloadData().getGcsHelmPayload().getChartName());
      if (isNotBlank(error)) {
        throw new InvalidRequestException(error);
      }

      error = checkFiledValueError(
          "helmVersion", pollingItem.getPollingPayloadData().getGcsHelmPayload().getHelmVersion().name());
      if (isNotBlank(error)) {
        throw new InvalidRequestException(error);
      }
    } else {
      throw new InvalidRequestException("Store Type is not supported for HelmChart Trigger");
    }
  }

  public String checkFiledValueError(String fieldName, String fieldValue) {
    if (isBlank(fieldValue)) {
      return String.format("%s can not be blank. Needs to have concrete value", fieldName);
    } else if ("<+input>".equals(fieldValue)) {
      return String.format("%s can not be Runtime input in Trigger. Needs to have concrete value", fieldValue);
    } else {
      return EMPTY;
    }
  }

  public String generatePollingDescriptor(PollingResponse pollingResponse) {
    StringBuilder builder = new StringBuilder(1024);

    builder.append("AccountId: ").append(pollingResponse.getAccountId());

    if (pollingResponse.getSignaturesCount() > 0) {
      builder.append(", Signatures: [");
      for (int i = 0; i < pollingResponse.getSignaturesCount(); i++) {
        builder.append(pollingResponse.getSignatures(i)).append("  ");
      }
      builder.append("], ");
    }

    if (pollingResponse.hasBuildInfo()) {
      BuildInfo buildInfo = pollingResponse.getBuildInfo();

      builder.append(", BuildInfo Name: ").append(buildInfo.getName());
      builder.append(", Version: [");
      for (int i = 0; i < buildInfo.getVersionsCount(); i++) {
        builder.append(buildInfo.getVersions(i)).append("  ");
      }
      builder.append(']');
    }

    return builder.toString();
  }

  public String validateAndFetchFromJsonNode(BuildTriggerOpsData buildTriggerOpsData, String key) {
    String fieldName = buildTriggerOpsData.getPipelineBuildSpecMap().containsKey(key)
        ? ((JsonNode) buildTriggerOpsData.getPipelineBuildSpecMap().get(key)).asText()
        : Strings.EMPTY;
    if (isBlank(fieldName) || "<+input>".equals(fieldName)) {
      fieldName = fetchValueFromJsonNode(key, buildTriggerOpsData.getTriggerSpecMap());
    }
    return fieldName;
  }

  public void verifyStageAndBuildRef(TriggerDetails triggerDetails, String fieldName) {
    NGTriggerSpecV2 spec = triggerDetails.getNgTriggerConfigV2().getSource().getSpec();
    if (!BuildAware.class.isAssignableFrom(spec.getClass())) {
      return;
    }

    BuildAware buildAware = (BuildAware) spec;
    StringBuilder msg = new StringBuilder(128);
    boolean validationFailed = false;
    if (isBlank(buildAware.fetchStageRef())) {
      msg.append("stageIdentifier can not be blank/missing. ");
      validationFailed = true;
    }
    if (isBlank(buildAware.fetchbuildRef())) {
      msg.append(fieldName).append(" can not be blank/missing. ");
      validationFailed = true;
    }

    if (validationFailed) {
      throw new InvalidArgumentsException(msg.toString());
    }
  }

  public Map<String, Map<String, String>> generateErrorMap(InputSetErrorWrapperDTOPMS inputSetErrorWrapperDTOPMS) {
    Map<String, Map<String, String>> errorMap = new HashMap<>();

    if (inputSetErrorWrapperDTOPMS == null) {
      return errorMap;
    }
    for (Map.Entry<String, InputSetErrorResponseDTOPMS> entry :
        inputSetErrorWrapperDTOPMS.getUuidToErrorResponseMap().entrySet()) {
      Map<String, String> innerMap = new HashMap<>();
      InputSetErrorResponseDTOPMS inputSetErrorResponseDTOPMS = entry.getValue();
      if (isNotEmpty(inputSetErrorResponseDTOPMS.getErrors())) {
        inputSetErrorResponseDTOPMS.getErrors().forEach(inputSetErrorDTOPMS -> {
          innerMap.put("fieldName", inputSetErrorDTOPMS.getFieldName());
          innerMap.put("message", inputSetErrorDTOPMS.getMessage());
        });
      }

      errorMap.put(entry.getKey(), innerMap);
    }
    return errorMap;
  }
}
