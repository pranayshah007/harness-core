/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.entities.Instance;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.serializer.JsonUtils;
import io.harness.ssca.beans.SLSAVerificationSummary;
import io.harness.ssca.entities.ArtifactEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.util.Iterator;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PipelineServiceImpl implements PipelineService {
  @Inject PipelineServiceClient pipelineServiceClient;

  private static String IDENTIFIER = "identifier";
  private static String STEP_TYPE = "stepType";

  private static String EXECUTION_GRAPH = "executionGraph";
  private static String SLSA_VERIFICATION_STEP_ID = "SlsaVerification";
  private static String NODE_MAP = "nodeMap";
  private static String OUTCOMES = "outcomes";

  private static String PIPELINE_EXECUTION_SUMMARY = "pipelineExecutionSummary";
  private static String PIPELINE_NAME = "name";
  private static String RUN_SEQUENCE = "runSequence";
  private static String EXECUTION_TRIGGER_INFO = "executionTriggerInfo";
  private static String TRIGGER_TYPE = "triggerType";

  private static String STEP_ARTIFACTS = "stepArtifacts";
  private static String PROVENANCE_ARTIFACTS = "provenanceArtifacts";
  private static String POLICY_OUTPUT = "policyOutput";
  private static String STATUS = "status";

  private static String PUBLISHED_IMAGES = "publishedImageArtifacts";
  private static String IMAGE_NAME = "imageName";
  private static String TAG = "tag";
  @Override
  public JsonNode getPmsExecutionSummary(ArtifactEntity artifact) {
    JsonNode rootNode = null;
    try {
      Object pmsExecutionSummary = NGRestUtils.getResponse(pipelineServiceClient.getExecutionDetailV2(
          artifact.getPipelineExecutionId(), artifact.getAccountId(), artifact.getOrgId(), artifact.getProjectId()));
      rootNode = JsonUtils.asTree(pmsExecutionSummary);
    } catch (Exception e) {
      log.error(String.format("PMS Request Failed. Exception: %s", e));
    }
    return rootNode;
  }

  @Override
  public JsonNode getPmsExecutionSummary(Instance instance) {
    JsonNode rootNode = null;
    try {
      Object pmsExecutionSummary = NGRestUtils.getResponse(pipelineServiceClient.getExecutionDetailV2(
          instance.getLastPipelineExecutionId(), instance.getAccountIdentifier(), instance.getOrgIdentifier(),
          instance.getProjectIdentifier(), instance.getStageSetupId()));
      rootNode = JsonUtils.asTree(pmsExecutionSummary);
    } catch (Exception e) {
      log.error(String.format("PMS Request Failed. Exception: %s", e));
    }
    return rootNode;
  }

  @Override
  public String getPipelineName(JsonNode pmsExecutionSummaryJsonNode) {
    return getNodeValue(parseField(pmsExecutionSummaryJsonNode, PIPELINE_EXECUTION_SUMMARY, PIPELINE_NAME));
  }

  @Override
  public String getPipelineExecutionSequenceId(JsonNode pmsExecutionSummaryJsonNode) {
    return getNodeValue(parseField(pmsExecutionSummaryJsonNode, PIPELINE_EXECUTION_SUMMARY, RUN_SEQUENCE));
  }

  @Override
  public String getPipelineExecutionTriggerType(JsonNode pmsExecutionSummaryJsonNode) {
    return getNodeValue(
        parseField(pmsExecutionSummaryJsonNode, PIPELINE_EXECUTION_SUMMARY, EXECUTION_TRIGGER_INFO, TRIGGER_TYPE));
  }

  public SLSAVerificationSummary getSlsaVerificationSummary(
      JsonNode pmsExecutionSummaryJsonNode, ArtifactEntity artifact) {
    SLSAVerificationSummary.SLSAVerificationSummaryBuilder slsaVerificationSummaryBuilder =
        SLSAVerificationSummary.builder();
    if (pmsExecutionSummaryJsonNode == null) {
      return slsaVerificationSummaryBuilder.build();
    }
    try {
      JsonNode executionNodeList = parseField(pmsExecutionSummaryJsonNode, EXECUTION_GRAPH, NODE_MAP);
      if (Objects.isNull(executionNodeList)) {
        return slsaVerificationSummaryBuilder.build();
      }
      for (JsonNode node : executionNodeList) {
        if (SLSA_VERIFICATION_STEP_ID.equals(getNodeValue(node.get(STEP_TYPE)))
            && getNodeValue(node.get(IDENTIFIER)) != null) {
          String fqnSlsaStepIdentifier = getFqnSlsaStepIdentifier(node);
          if (fqnSlsaStepIdentifier != null && isCorrelated(fqnSlsaStepIdentifier, node, artifact)) {
            JsonNode provenanceArtifactList =
                parseField(node, OUTCOMES, fqnSlsaStepIdentifier, STEP_ARTIFACTS, PROVENANCE_ARTIFACTS);
            JsonNode provenanceArtifact =
                Objects.nonNull(provenanceArtifactList) ? provenanceArtifactList.get(0) : null;
            slsaVerificationSummaryBuilder
                .slsaPolicyOutcomeStatus(getNodeValue(parseField(node, OUTCOMES, POLICY_OUTPUT, STATUS)))
                .provenanceArtifact(provenanceArtifact);
            break;
          }
        }
      }
    } catch (Exception e) {
      log.error(String.format("Failed to extract SLSA Verification Data. Exception: %s", e));
    }
    return slsaVerificationSummaryBuilder.build();
  }

  private String getFqnSlsaStepIdentifier(JsonNode node) {
    JsonNode outcomes = parseField(node, OUTCOMES);
    if (outcomes == null) {
      return null;
    }
    Iterator<String> fields = outcomes.fieldNames();
    String identifier = getNodeValue(parseField(node, IDENTIFIER));
    while (fields.hasNext()) {
      String field = fields.next();
      if (field.startsWith("artifact") && field.endsWith(identifier)) {
        return field;
      }
    }
    return null;
  }

  private boolean isCorrelated(String fqnIdentifier, JsonNode node, ArtifactEntity artifact) {
    JsonNode publishedImageArtifacts = parseField(node, OUTCOMES, fqnIdentifier, STEP_ARTIFACTS, PUBLISHED_IMAGES);
    if (publishedImageArtifacts == null) {
      return false;
    }
    for (JsonNode artifactNode : publishedImageArtifacts) {
      if (artifact.getName().equals(getNodeValue(parseField(artifactNode, IMAGE_NAME)))
          && artifact.getTag().equals(getNodeValue(parseField(artifactNode, TAG)))) {
        return true;
      }
    }
    return false;
  }

  public static JsonNode parseField(JsonNode rootNode, String... path) {
    for (String field : path) {
      if (rootNode == null) {
        return null;
      } else {
        rootNode = rootNode.get(field);
      }
    }
    return rootNode;
  }

  public static String getNodeValue(JsonNode node) {
    if (Objects.isNull(node)) {
      return null;
    }
    return node.asText();
  }
}
