/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.template.beans.NGTemplateConstants.TEMPLATE_INPUTS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.pms.merger.helpers.RuntimeInputsValidator;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.template.beans.refresh.ErrorNodeSummary;
import io.harness.template.beans.refresh.NodeInfo;
import io.harness.template.beans.refresh.TemplateInfo;
import io.harness.template.beans.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.template.beans.yaml.NGTemplateConfig;
import io.harness.template.entity.TemplateEntity;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class TemplateInputsValidator {
  private static final int MAX_DEPTH = 10;
  @Inject private TemplateMergeServiceHelper templateMergeServiceHelper;

  public ValidateTemplateInputsResponseDTO validateNestedTemplateInputsForTemplates(
      String accountId, String orgId, String projectId, TemplateEntity templateEntity) {
    ErrorNodeSummary errorNodeSummary =
        ErrorNodeSummary.builder()
            .nodeInfo(
                NodeInfo.builder().identifier(templateEntity.getIdentifier()).name(templateEntity.getName()).build())
            .templateInfo(TemplateInfo.builder()
                              .templateIdentifier(templateEntity.getIdentifier())
                              .versionLabel(templateEntity.getVersionLabel())
                              .templateEntityType(templateEntity.getTemplateEntityType())
                              .build())
            .childrenErrorNodes(new ArrayList<>())
            .build();

    ValidateTemplateInputsResponseDTO validateTemplateInputsResponse =
        ValidateTemplateInputsResponseDTO.builder().validYaml(true).errorNodeSummary(errorNodeSummary).build();
    validateNestedTemplateInputsInternal(
        accountId, orgId, projectId, templateEntity.getYaml(), 0, new HashMap<>(), validateTemplateInputsResponse);
    return validateTemplateInputsResponse;
  }

  public ValidateTemplateInputsResponseDTO validateNestedTemplateInputsForGivenYaml(
      String accountId, String orgId, String projectId, String yaml) {
    ErrorNodeSummary errorNodeSummary = ErrorNodeSummary.builder().childrenErrorNodes(new ArrayList<>()).build();

    ValidateTemplateInputsResponseDTO validateTemplateInputsResponse =
        ValidateTemplateInputsResponseDTO.builder().validYaml(true).errorNodeSummary(errorNodeSummary).build();
    validateNestedTemplateInputsInternal(
        accountId, orgId, projectId, yaml, 0, new HashMap<>(), validateTemplateInputsResponse);
    return validateTemplateInputsResponse;
  }

  private void validateNestedTemplateInputsInternal(String accountId, String orgId, String projectId, String yaml,
      int depth, Map<String, TemplateEntity> templateCacheMap,
      ValidateTemplateInputsResponseDTO validateTemplateInputsResponse) {
    // Case -> empty YAML, cannot validate
    if (isEmpty(yaml)) {
      throw new NGTemplateException("Yaml to be validated cannot be empty.");
    }

    YamlNode yamlNode;
    try {
      // Parsing the YAML to get the YamlNode
      yamlNode = YamlUtils.readTree(yaml).getNode();
    } catch (IOException e) {
      log.error("Could not convert yaml to JsonNode. Yaml:\n" + yaml, e);
      throw new NGTemplateException("Could not convert yaml to JsonNode: " + e.getMessage());
    }

    // populates validateTemplateInputsResponse if any validation fails
    validateNestedTemplateInputsInObject(
        accountId, orgId, projectId, yamlNode, templateCacheMap, depth, validateTemplateInputsResponse);
  }

  private void validateNestedTemplateInputsInObject(String accountId, String orgId, String projectId, YamlNode yamlNode,
      Map<String, TemplateEntity> templateCacheMap, int depth,
      ValidateTemplateInputsResponseDTO validateTemplateInputsResponse) {
    // Iterating over the YAML fields to go to all the Templates Present
    for (YamlField childYamlField : yamlNode.fields()) {
      String fieldName = childYamlField.getName();
      YamlNode currentYamlNode = childYamlField.getNode();
      JsonNode value = currentYamlNode.getCurrJsonNode();

      // If Template is present, validate the Template Inputs
      if (templateMergeServiceHelper.isTemplatePresent(fieldName, value)) {
        depth++;
        if (depth >= MAX_DEPTH) {
          throw new InvalidRequestException("Exponentially growing template nesting. Aborting");
        }
        validateTemplateInputs(
            accountId, orgId, projectId, currentYamlNode, templateCacheMap, validateTemplateInputsResponse, depth);
        depth--;
        continue;
      }

      if (value.isArray() && !YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(value)) {
        // Value -> Array
        validateNestedTemplateInputsInArray(accountId, orgId, projectId, childYamlField.getNode(), templateCacheMap,
            depth, validateTemplateInputsResponse);
      } else if (value.isObject()) {
        // Value -> Object
        validateNestedTemplateInputsInObject(accountId, orgId, projectId, childYamlField.getNode(), templateCacheMap,
            depth, validateTemplateInputsResponse);
      }
    }
  }

  // Gets the ResMap if the yamlNode is of the type Array
  private void validateNestedTemplateInputsInArray(String accountId, String orgId, String projectId, YamlNode yamlNode,
      Map<String, TemplateEntity> templateCacheMap, int depth,
      ValidateTemplateInputsResponseDTO validateTemplateInputsResponse) {
    // Iterate over the array
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (arrayElement.isArray()) {
        // Value -> Array
        validateNestedTemplateInputsInArray(
            accountId, orgId, projectId, yamlNode, templateCacheMap, depth, validateTemplateInputsResponse);
      } else if (arrayElement.isObject()) {
        // Value -> Object
        validateNestedTemplateInputsInObject(
            accountId, orgId, projectId, arrayElement, templateCacheMap, depth, validateTemplateInputsResponse);
      }
    }
  }

  private void validateTemplateInputs(String accountId, String orgId, String projectId, YamlNode templateNode,
      Map<String, TemplateEntity> templateCacheMap, ValidateTemplateInputsResponseDTO validateTemplateInputsResponse,
      int depth) {
    JsonNode templateNodeValue = templateNode.getCurrJsonNode();
    // Template YAML corresponding to the TemplateRef and Version Label
    TemplateEntity templateEntity = templateMergeServiceHelper.getLinkedTemplateEntity(
        accountId, orgId, projectId, templateNodeValue, templateCacheMap);

    String templateYaml = templateEntity.getYaml();

    // verify template inputs of child template.
    ValidateTemplateInputsResponseDTO childrenNodeValidationResponse =
        getValidateTemplateInputsResponseDTO(templateNode, templateEntity);
    validateNestedTemplateInputsInternal(
        accountId, orgId, projectId, templateYaml, depth, templateCacheMap, childrenNodeValidationResponse);

    // if child template is invalid, add the invalid errorNode as childNode
    if (!childrenNodeValidationResponse.isValidYaml()) {
      validateTemplateInputsResponse.setValidYaml(false);
      validateTemplateInputsResponse.getErrorNodeSummary().addChildrenErrorNode(
          childrenNodeValidationResponse.getErrorNodeSummary());
      return;
    }

    // Generate the Template Spec from the Template YAML
    JsonNode templateSpec;
    try {
      NGTemplateConfig templateConfig = YamlPipelineUtils.read(templateYaml, NGTemplateConfig.class);
      templateSpec = templateConfig.getTemplateInfoConfig().getSpec();
    } catch (IOException e) {
      log.error("Could not read template yaml", e);
      throw new NGTemplateException("Could not read template yaml: " + e.getMessage());
    }

    // if no child node of template is invalid, then verify template inputs against the template.
    JsonNode templateInputs = templateNodeValue.get(TEMPLATE_INPUTS);
    if (!RuntimeInputsValidator.areInputsValidAgainstSourceNode(templateInputs, templateSpec)) {
      validateTemplateInputsResponse.setValidYaml(false);
    }
  }

  private ValidateTemplateInputsResponseDTO getValidateTemplateInputsResponseDTO(
      YamlNode templateNode, TemplateEntity templateEntity) {
    YamlNode parentNode = templateNode.getParentNode();
    ErrorNodeSummary childErrorNodeSummary =
        ErrorNodeSummary.builder()
            .nodeInfo(NodeInfo.builder()
                          .identifier(parentNode != null ? parentNode.getIdentifier() : null)
                          .name(parentNode != null ? parentNode.getName() : null)
                          .localFqn(YamlUtils.getFullyQualifiedName(templateNode))
                          .build())
            .templateInfo(TemplateInfo.builder()
                              .templateIdentifier(templateEntity.getIdentifier())
                              .templateEntityType(templateEntity.getTemplateEntityType())
                              .versionLabel(templateEntity.getVersionLabel())
                              .build())
            .childrenErrorNodes(new ArrayList<>())
            .build();
    return ValidateTemplateInputsResponseDTO.builder().validYaml(true).errorNodeSummary(childErrorNodeSummary).build();
  }
}
