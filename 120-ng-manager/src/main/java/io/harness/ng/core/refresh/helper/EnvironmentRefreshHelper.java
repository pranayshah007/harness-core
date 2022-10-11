/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.refresh.helper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.refresh.bean.EntityRefreshContext;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.RuntimeInputsValidator;
import io.harness.pms.merger.helpers.YamlRefreshHelper;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlNodeUtils;
import io.harness.pms.yaml.YamlUtils;
import io.harness.template.beans.refresh.v2.InputsValidationResponse;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class EnvironmentRefreshHelper {
  EnvironmentService environmentService;
  InfrastructureEntityService infrastructureEntityService;
  private static final String STAGES_KEY = "stages";
  private static final String DUMMY_NODE = "dummy";

  public boolean isEnvironmentField(String fieldName, JsonNode envValue) {
    return YamlTypes.ENVIRONMENT_YAML.equals(fieldName) && envValue.isObject()
        && (envValue.get(YamlTypes.ENVIRONMENT_REF) != null || envValue.get(YamlTypes.INFRASTRUCTURE_DEFS) != null);
  }

  public void validateEnvironmentInputs(
      YamlNode entityNode, EntityRefreshContext context, InputsValidationResponse errorNodeSummary) {
    JsonNode envJsonNode = entityNode.getCurrJsonNode();
    JsonNode envRefJsonNode = envJsonNode.get(YamlTypes.ENVIRONMENT_REF);
    String envRefValue;
    ObjectMapper mapper = new ObjectMapper();
    JsonNode infraDefsNode = envJsonNode.get(YamlTypes.INFRASTRUCTURE_DEFS);
    if (envRefJsonNode != null) {
      envRefValue = envRefJsonNode.asText();
      JsonNode envInputsNode = envJsonNode.get(YamlTypes.ENVIRONMENT_INPUTS);
      if (NGExpressionUtils.isRuntimeOrExpressionField(envRefValue)) {
        if (!isNodeValueRuntime(envInputsNode) || !isNodeValueRuntime(infraDefsNode)) {
          errorNodeSummary.setValid(false);
        }
        return;
      }

      // If envInputs not valid, no need to check infraDefs inputs
      if (!validateEnvInputs(context, errorNodeSummary, envRefValue, mapper, envInputsNode)) {
        return;
      }

      validateInfraDefsInput(context, errorNodeSummary, envRefValue, mapper, infraDefsNode);
    } else {
      if (infraDefsNode != null) {
        YamlNode envNodeInResolvedTemplatesYaml =
            getEnvNodeFromResolvedTemplatesYaml(entityNode, context.getResolvedTemplatesYamlNode());
        if (envNodeInResolvedTemplatesYaml == null) {
          log.warn("Env node in Resolved templates yaml is null");
          return;
        }
        JsonNode infraDefsNodeInResolvedTemplatesYaml =
            envNodeInResolvedTemplatesYaml.getField(YamlTypes.INFRASTRUCTURE_DEFS).getNode().getCurrJsonNode();
        if (infraDefsNodeInResolvedTemplatesYaml == null) {
          log.warn("Something wrong happened finding infraDefinitions node in resolved templates yaml");
          return;
        }
        if (checkIfInfraDefsToBeValidated(infraDefsNode, infraDefsNodeInResolvedTemplatesYaml, mapper)) {
          JsonNode envRefNode =
              envNodeInResolvedTemplatesYaml.getField(YamlTypes.ENVIRONMENT_REF).getNode().getCurrJsonNode();
          if (envRefNode == null) {
            log.warn("Skipping because couldn't find envRef value");
            return;
          }
          envRefValue = envRefNode.asText();
          validateInfraDefsInput(context, errorNodeSummary, envRefValue, mapper, infraDefsNode);
        }
      }
    }
  }

  public JsonNode refreshEnvironmentInputs(YamlNode entityNode, EntityRefreshContext context) {
    ObjectNode envObjectNode = (ObjectNode) entityNode.getCurrJsonNode();
    JsonNode envRefJsonNode = envObjectNode.get(YamlTypes.ENVIRONMENT_REF);
    String envRefValue;
    ObjectMapper mapper = new ObjectMapper();
    JsonNode infraDefsNode = envObjectNode.get(YamlTypes.INFRASTRUCTURE_DEFS);
    if (envRefJsonNode != null) {
      envRefValue = envRefJsonNode.asText();
      JsonNode envInputsNode = envObjectNode.get(YamlTypes.ENVIRONMENT_INPUTS);
      if (NGExpressionUtils.isRuntimeOrExpressionField(envRefValue)) {
        if (!isNodeValueRuntime(envObjectNode)) {
          envObjectNode.put(YamlTypes.ENVIRONMENT_INPUTS, "<+input>");
        }
        if (!isNodeValueRuntime(infraDefsNode)) {
          envObjectNode.put(YamlTypes.INFRASTRUCTURE_DEFS, "<+input>");
        }
        return envObjectNode;
      }

      refreshEnvInputs(context, envRefValue, mapper, envObjectNode, envInputsNode);
      refreshInfraDefsInput(context, envRefValue, mapper, envObjectNode, infraDefsNode);
    } else {
      if (infraDefsNode != null) {
        YamlNode envNodeInResolvedTemplatesYaml =
            getEnvNodeFromResolvedTemplatesYaml(entityNode, context.getResolvedTemplatesYamlNode());
        if (envNodeInResolvedTemplatesYaml == null) {
          log.warn("Env node in Resolved templates yaml is null");
          return envObjectNode;
        }
        JsonNode infraDefsNodeInResolvedTemplatesYaml =
            envNodeInResolvedTemplatesYaml.getField(YamlTypes.INFRASTRUCTURE_DEFS).getNode().getCurrJsonNode();
        if (infraDefsNodeInResolvedTemplatesYaml == null) {
          log.warn("Something wrong happened finding infraDefinitions node in resolved templates yaml");
          return envObjectNode;
        }
        if (checkIfInfraDefsToBeValidated(infraDefsNode, infraDefsNodeInResolvedTemplatesYaml, mapper)) {
          JsonNode envRefNode =
              envNodeInResolvedTemplatesYaml.getField(YamlTypes.ENVIRONMENT_REF).getNode().getCurrJsonNode();
          if (envRefNode == null) {
            log.warn("Skipping because couldn't find envRef value");
            return envObjectNode;
          }
          envRefValue = envRefNode.asText();
          refreshInfraDefsInput(context, envRefValue, mapper, envObjectNode, infraDefsNode);
        }
      }
    }
    return envObjectNode;
  }

  private void refreshInfraDefsInput(EntityRefreshContext context, String envRefValue, ObjectMapper mapper,
      ObjectNode envObjectNode, JsonNode infraDefsNode) {
    if (infraDefsNode == null) {
      return;
    }
    if (NGExpressionUtils.isRuntimeOrExpressionField(infraDefsNode.asText())) {
      return;
    }
    List<String> infraDefIdentifiers = collectAllInfraIdentifiers(infraDefsNode);
    if (EmptyPredicate.isNotEmpty(infraDefIdentifiers)) {
      String infraInputs = infrastructureEntityService.createInfrastructureInputsFromYamlV2(
          context.getAccountId(), context.getOrgId(), context.getProjectId(), envRefValue, infraDefIdentifiers, false);
      if (EmptyPredicate.isEmpty(infraInputs)) {
        envObjectNode.remove(YamlTypes.INFRASTRUCTURE_DEFS);
        return;
      }

      ObjectNode linkedInfraDefs = mapper.createObjectNode();
      linkedInfraDefs.set(YamlTypes.INFRASTRUCTURE_DEFS, infraDefsNode);

      // Adding dummy node because down the line we create yamlConfig which doesn't work if root is an array.
      JsonNode dummyLinkedInfraDefs = addDummyRootToJsonNode(linkedInfraDefs, mapper);
      JsonNode infraInputsNode = readTree(infraInputs);
      JsonNode dummyInfraInputsNode = addDummyRootToJsonNode(infraInputsNode, mapper);

      JsonNode refreshedJsonNode =
          YamlRefreshHelper.refreshYamlFromSourceYaml(YamlPipelineUtils.writeYamlString(dummyLinkedInfraDefs),
              YamlPipelineUtils.writeYamlString(dummyInfraInputsNode));
      if (refreshedJsonNode != null) {
        envObjectNode.setAll((ObjectNode) refreshedJsonNode.get(DUMMY_NODE));
      }
    }
  }

  private void refreshEnvInputs(EntityRefreshContext context, String envRefValue, ObjectMapper mapper,
      ObjectNode envObjectNode, JsonNode linkedEnvInputsValue) {
    String envInputsYaml = environmentService.createEnvironmentInputsYaml(
        context.getAccountId(), context.getOrgId(), context.getProjectId(), envRefValue);
    if (EmptyPredicate.isEmpty(envInputsYaml)) {
      envObjectNode.remove(YamlTypes.ENVIRONMENT_INPUTS);
      return;
    }

    ObjectNode linkedEnvInputs = mapper.createObjectNode();
    linkedEnvInputs.set(YamlTypes.ENVIRONMENT_INPUTS, linkedEnvInputsValue);
    String linkedEnvInputsYaml = YamlPipelineUtils.writeYamlString(linkedEnvInputs);
    JsonNode refreshedJsonNode = YamlRefreshHelper.refreshYamlFromSourceYaml(linkedEnvInputsYaml, envInputsYaml);
    envObjectNode.set(YamlTypes.ENVIRONMENT_INPUTS, refreshedJsonNode.get(YamlTypes.ENVIRONMENT_INPUTS));
  }

  private void validateInfraDefsInput(EntityRefreshContext context, InputsValidationResponse errorNodeSummary,
      String envRefValue, ObjectMapper mapper, JsonNode infraDefsNode) {
    if (infraDefsNode == null) {
      return;
    }
    if (NGExpressionUtils.isRuntimeOrExpressionField(infraDefsNode.asText())) {
      return;
    }
    List<String> infraDefIdentifiers = collectAllInfraIdentifiers(infraDefsNode);
    if (EmptyPredicate.isNotEmpty(infraDefIdentifiers)) {
      String infraInputs = infrastructureEntityService.createInfrastructureInputsFromYamlV2(
          context.getAccountId(), context.getOrgId(), context.getProjectId(), envRefValue, infraDefIdentifiers, false);
      if (EmptyPredicate.isEmpty(infraInputs)) {
        errorNodeSummary.setValid(false);
        return;
      }

      ObjectNode linkedInfraDefs = mapper.createObjectNode();
      linkedInfraDefs.set(YamlTypes.INFRASTRUCTURE_DEFS, infraDefsNode);

      // Adding dummy node because down the line we create yamlConfig which doesn't work if root is an array.
      JsonNode dummyLinkedInfraDefs = addDummyRootToJsonNode(linkedInfraDefs, mapper);
      JsonNode infraInputsNode = readTree(infraInputs);
      JsonNode dummyInfraInputsNode = addDummyRootToJsonNode(infraInputsNode, mapper);

      if (!RuntimeInputsValidator.validateInputsAgainstSourceNode(
              YamlPipelineUtils.writeYamlString(dummyLinkedInfraDefs),
              YamlPipelineUtils.writeYamlString(dummyInfraInputsNode))) {
        errorNodeSummary.setValid(false);
      }
    }
  }

  private JsonNode readTree(String yaml) {
    try {
      YamlField yamlField = YamlUtils.readTree(yaml);
      return yamlField.getNode().getCurrJsonNode();
    } catch (IOException e) {
      log.error("Error while converting yaml to jsonNode");
      throw new InvalidRequestException("Exception occurred while converting yaml to jsonNode");
    }
  }

  private boolean validateEnvInputs(EntityRefreshContext context, InputsValidationResponse errorNodeSummary,
      String envRefValue, ObjectMapper mapper, JsonNode envInputsNode) {
    String envInputsYaml = environmentService.createEnvironmentInputsYaml(
        context.getAccountId(), context.getOrgId(), context.getProjectId(), envRefValue);
    if (EmptyPredicate.isEmpty(envInputsYaml)) {
      if (envInputsNode != null) {
        errorNodeSummary.setValid(false);
        return false;
      }
      return true;
    }

    ObjectNode linkedEnvInputs = mapper.createObjectNode();
    linkedEnvInputs.set(YamlTypes.ENVIRONMENT_INPUTS, envInputsNode);
    String linkedEnvInputsYaml = YamlPipelineUtils.writeYamlString(linkedEnvInputs);
    if (!RuntimeInputsValidator.validateInputsAgainstSourceNode(linkedEnvInputsYaml, envInputsYaml)) {
      errorNodeSummary.setValid(false);
      return false;
    }
    return true;
  }

  private boolean checkIfInfraDefsToBeValidated(
      JsonNode infraNode, JsonNode resolvedTemplatesInfraNode, ObjectMapper objectMapper) {
    ObjectNode newInfraNode = objectMapper.createObjectNode();
    newInfraNode.set(YamlTypes.INFRASTRUCTURE_DEFS, infraNode);
    ObjectNode envNode = objectMapper.createObjectNode();
    envNode.set(YamlTypes.ENVIRONMENT_YAML, newInfraNode);
    ObjectNode newResolvedTemplatesInfraNode = objectMapper.createObjectNode();
    newResolvedTemplatesInfraNode.set(YamlTypes.INFRASTRUCTURE_DEFS, resolvedTemplatesInfraNode);
    ObjectNode envNodeForResolvedTemplates = objectMapper.createObjectNode();
    envNodeForResolvedTemplates.set(YamlTypes.ENVIRONMENT_YAML, newResolvedTemplatesInfraNode);
    // Passing envNode instead of infraNode in YamlConfig because it requires root field to be an object.
    YamlConfig envNodeYamlConfig = new YamlConfig(envNode);
    YamlConfig resolvedTemplatesEnvNodeYamlConfig = new YamlConfig(envNodeForResolvedTemplates);

    Set<String> envNodeKeySet =
        envNodeYamlConfig.getFqnToValueMap().keySet().stream().map(FQN::getExpressionFqn).collect(Collectors.toSet());
    Set<String> envNodeInResolvedTemplatesYamlKeySet = resolvedTemplatesEnvNodeYamlConfig.getFqnToValueMap()
                                                           .keySet()
                                                           .stream()
                                                           .map(FQN::getExpressionFqn)
                                                           .collect(Collectors.toSet());
    return envNodeKeySet.containsAll(envNodeInResolvedTemplatesYamlKeySet);
  }

  private List<String> collectAllInfraIdentifiers(JsonNode infraDefsNode) {
    List<String> infraDefs = new ArrayList<>();
    if (infraDefsNode.isArray()) {
      for (JsonNode arrayElement : infraDefsNode) {
        JsonNode identifier = arrayElement.get(YamlTypes.IDENTIFIER);
        if (identifier != null) {
          infraDefs.add(identifier.asText());
        }
      }
    }
    return infraDefs;
  }

  private YamlNode getEnvNodeFromResolvedTemplatesYaml(YamlNode entityNode, YamlNode resolvedTemplatesYamlNode) {
    String stageIdentifier = getStageIdentifierForGivenEnvironmentField(entityNode);
    if (stageIdentifier == null) {
      log.warn("Stage not found, returning null");
      return null;
    }
    YamlNode stageYamlNodeInResolvedTemplatesYaml =
        findStageWithGivenIdentifier(resolvedTemplatesYamlNode, stageIdentifier);
    return YamlNodeUtils.goToPathUsingFqn(stageYamlNodeInResolvedTemplatesYaml, "spec.environment");
  }

  boolean isNodeValueRuntime(JsonNode jsonNode) {
    if (jsonNode == null) {
      return false;
    }
    return !(jsonNode.isObject()
        || (jsonNode.isValueNode() && !NGExpressionUtils.matchesInputSetPattern(jsonNode.asText())));
  }

  private String getStageIdentifierForGivenEnvironmentField(YamlNode entityNode) {
    if (entityNode == null) {
      return null;
    }
    if (YamlTypes.STAGE.equals(entityNode.getFieldName())) {
      return entityNode.getIdentifier();
    }
    return getStageIdentifierForGivenEnvironmentField(entityNode.getParentNode());
  }

  public YamlNode findStageWithGivenIdentifier(YamlNode resolvedTemplatesYamlNode, String stageIdentifier) {
    if (resolvedTemplatesYamlNode == null) {
      return null;
    }
    YamlNode stagesYamlNode = YamlNodeUtils.findFirstNodeMatchingFieldName(resolvedTemplatesYamlNode, STAGES_KEY);
    if (stagesYamlNode == null || stagesYamlNode.isObject()) {
      return null;
    }

    return findStageNodeWithGivenStageIdentifier(stageIdentifier, stagesYamlNode);
  }

  private YamlNode findStageNodeWithGivenStageIdentifier(String stageIdentifier, YamlNode stagesYamlNode) {
    for (YamlNode stageNode : stagesYamlNode.asArray()) {
      YamlNode requiredStageNode = null;
      if (stageNode.getField(YamlTypes.STAGE) != null
          && stageIdentifier.equals(stageNode.getField(YamlTypes.STAGE).getNode().getIdentifier())) {
        requiredStageNode = stageNode.getField(YamlTypes.STAGE).getNode();
      } else if (stageNode.getField(YAMLFieldNameConstants.PARALLEL) != null) {
        requiredStageNode = findStageNodeWithGivenStageIdentifier(
            stageIdentifier, stageNode.getField(YAMLFieldNameConstants.PARALLEL).getNode());
      }
      if (requiredStageNode != null) {
        return requiredStageNode;
      }
    }
    return null;
  }

  private JsonNode addDummyRootToJsonNode(JsonNode node, ObjectMapper mapper) {
    ObjectNode dummyObjectNode = mapper.createObjectNode();
    dummyObjectNode.set(DUMMY_NODE, node);
    return dummyObjectNode;
  }
}
