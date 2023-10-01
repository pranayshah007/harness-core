/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.template.resources.beans.NGTemplateConstants.TEMPLATE_INPUTS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.ng.core.template.refresh.NgManagerRefreshRequestDTO;
import io.harness.ng.core.template.refresh.NodeInfo;
import io.harness.ng.core.template.refresh.v2.InputsValidationResponse;
import io.harness.ng.core.template.refresh.v2.NodeErrorSummary;
import io.harness.ng.core.template.refresh.v2.TemplateNodeErrorSummary;
import io.harness.ng.core.template.refresh.v2.UnknownNodeErrorSummary;
import io.harness.ng.core.template.refresh.v2.ValidateInputsResponseDTO;
import io.harness.pms.merger.helpers.RuntimeInputsValidator;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.reconcile.remote.NgManagerReconcileClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntityGetResponse;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.resources.beans.yaml.NGTemplateConfig;
import io.harness.template.utils.NGTemplateFeatureFlagHelperService;
import io.harness.template.yaml.TemplateRefHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
@Singleton
public class InputsValidator {
  private static final int MAX_DEPTH = 10;
  private final Set<String> KEYS_TO_IGNORE = Set.of("service.serviceInputs", "environment.environmentInputs",
      "environment.serviceOverrideInputs", "codebase.repoName");
  @Inject private TemplateMergeServiceHelper templateMergeServiceHelper;
  @Inject private NGTemplateFeatureFlagHelperService featureFlagHelperService;
  @Inject private NgManagerReconcileClient ngManagerReconcileClient;

  public ValidateInputsResponseDTO validateInputsForTemplate(
      String accountId, String orgId, String projectId, TemplateEntityGetResponse templateEntityGetResponse) {
    TemplateEntity templateEntity = templateEntityGetResponse.getTemplateEntity();
    InputsValidationResponse inputsValidationResponse =
        validateInputsInternal(accountId, orgId, projectId, templateEntity.getYaml(), new HashMap<>(), false);

    TemplateNodeErrorSummary templateNodeErrorSummary =
        TemplateNodeErrorSummary.builder()
            .nodeInfo(
                NodeInfo.builder().identifier(templateEntity.getIdentifier()).name(templateEntity.getName()).build())
            .templateResponse(NGTemplateDtoMapper.writeTemplateResponseDto(templateEntityGetResponse))
            .childrenErrorNodes(inputsValidationResponse.getChildrenErrorNodes())
            .build();
    return ValidateInputsResponseDTO.builder()
        .validYaml(inputsValidationResponse.isValid())
        .nodeErrorSummary(templateNodeErrorSummary)
        .build();
  }

  public ValidateInputsResponseDTO validateInputsForYaml(
      String accountId, String orgId, String projectId, String yaml, boolean loadFromCache) {
    return validateInputsForYamlInternal(accountId, orgId, projectId, yaml, new HashMap<>(), loadFromCache);
  }

  public ValidateInputsResponseDTO validateInputsForYaml(String accountId, String orgId, String projectId, String yaml,
      Map<String, TemplateEntity> templateCacheMap, boolean loadFromCache) {
    return validateInputsForYamlInternal(accountId, orgId, projectId, yaml, templateCacheMap, loadFromCache);
  }

  private ValidateInputsResponseDTO validateInputsForYamlInternal(String accountId, String orgId, String projectId,
      String yaml, Map<String, TemplateEntity> templateCacheMap, boolean loadFromCache) {
    InputsValidationResponse inputsValidationResponse =
        validateInputsInternal(accountId, orgId, projectId, yaml, templateCacheMap, loadFromCache);

    NodeErrorSummary errorNodeSummary = UnknownNodeErrorSummary.builder()
                                            .nodeInfo(getRootNodeInfo(validateAndGetYamlNode(yaml)))
                                            .childrenErrorNodes(inputsValidationResponse.getChildrenErrorNodes())
                                            .build();
    return ValidateInputsResponseDTO.builder()
        .validYaml(inputsValidationResponse.isValid())
        .nodeErrorSummary(errorNodeSummary)
        .build();
  }

  private InputsValidationResponse validateInputsInternal(String accountId, String orgId, String projectId, String yaml,
      Map<String, TemplateEntity> templateCacheMap, boolean loadFromCache) {
    return validateInputsInternal(accountId, orgId, projectId, yaml, templateCacheMap, 0, loadFromCache);
  }

  private InputsValidationResponse validateInputsInternal(String accountId, String orgId, String projectId, String yaml,
      Map<String, TemplateEntity> templateCacheMap, int depth, boolean loadFromCache) {
    YamlNode yamlNode = validateAndGetYamlNode(yaml);
    InputsValidationResponse templateInputsValidationResponse =
        validateTemplateInputs(accountId, orgId, projectId, yamlNode, templateCacheMap, depth, loadFromCache);
    String resolvedTemplatesYaml = yaml;
    if (TemplateRefHelper.hasTemplateRef(yaml)) {
      Map<String, Object> resolvedTemplatesMap = templateMergeServiceHelper.mergeTemplateInputsInObject(
          accountId, orgId, projectId, yamlNode, templateCacheMap, 0, loadFromCache, false);
      resolvedTemplatesYaml = YamlUtils.writeYamlString(resolvedTemplatesMap);
    }
    InputsValidationResponse ngManagerInputsValidationResponse =
        NGRestUtils.getResponse(ngManagerReconcileClient.validateYaml(accountId, orgId, projectId,
            NgManagerRefreshRequestDTO.builder().yaml(yaml).resolvedTemplatesYaml(resolvedTemplatesYaml).build()));
    templateInputsValidationResponse.setValid(
        templateInputsValidationResponse.isValid() && ngManagerInputsValidationResponse.isValid());
    if (EmptyPredicate.isNotEmpty(ngManagerInputsValidationResponse.getChildrenErrorNodes())) {
      ngManagerInputsValidationResponse.getChildrenErrorNodes().forEach(
          templateInputsValidationResponse::addChildErrorNode);
    }
    return templateInputsValidationResponse;
  }

  private YamlNode validateAndGetYamlNode(String yaml) {
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
    return yamlNode;
  }

  private NodeInfo getRootNodeInfo(YamlNode yamlNode) {
    if (yamlNode == null) {
      return null;
    }

    List<YamlField> yamlFieldList = yamlNode.fields();
    if (EmptyPredicate.isNotEmpty(yamlFieldList)) {
      YamlNode rootYamlNode = yamlFieldList.get(0).getNode();
      return NodeInfo.builder()
          .identifier(rootYamlNode.getIdentifier())
          .name(rootYamlNode.getName())
          .localFqn(rootYamlNode.getFieldName())
          .build();
    }
    return null;
  }

  private InputsValidationResponse validateTemplateInputs(String accountId, String orgId, String projectId,
      YamlNode yamlNode, Map<String, TemplateEntity> templateCacheMap, int depth, boolean loadFromCache) {
    InputsValidationResponse inputsValidationResponse =
        InputsValidationResponse.builder().isValid(true).childrenErrorNodes(new ArrayList<>()).build();
    if (yamlNode.isObject()) {
      validateInputsInObject(
          accountId, orgId, projectId, yamlNode, templateCacheMap, depth, inputsValidationResponse, loadFromCache);
    } else if (yamlNode.isArray()) {
      validateInputsInArray(
          accountId, orgId, projectId, yamlNode, templateCacheMap, depth, inputsValidationResponse, loadFromCache);
    }
    return inputsValidationResponse;
  }

  private void validateInputsInObject(String accountId, String orgId, String projectId, YamlNode yamlNode,
      Map<String, TemplateEntity> templateCacheMap, int depth, InputsValidationResponse inputsValidationResponse,
      boolean loadFromCache) {
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
        validateTemplateInputs(accountId, orgId, projectId, currentYamlNode, templateCacheMap, depth,
            inputsValidationResponse, loadFromCache);
        depth--;
        continue;
      }

      if (value.isArray() && !YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(value)) {
        // Value -> Array
        validateInputsInArray(accountId, orgId, projectId, childYamlField.getNode(), templateCacheMap, depth,
            inputsValidationResponse, loadFromCache);
      } else if (value.isObject()) {
        // Value -> Object
        validateInputsInObject(accountId, orgId, projectId, childYamlField.getNode(), templateCacheMap, depth,
            inputsValidationResponse, loadFromCache);
      }
    }
  }

  private void validateInputsInArray(String accountId, String orgId, String projectId, YamlNode yamlNode,
      Map<String, TemplateEntity> templateCacheMap, int depth, InputsValidationResponse childrenNodeErrorSummary,
      boolean loadFromCache) {
    // Iterate over the array
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (arrayElement.isArray()) {
        // Value -> Array
        validateInputsInArray(accountId, orgId, projectId, arrayElement, templateCacheMap, depth,
            childrenNodeErrorSummary, loadFromCache);
      } else if (arrayElement.isObject()) {
        // Value -> Object
        validateInputsInObject(accountId, orgId, projectId, arrayElement, templateCacheMap, depth,
            childrenNodeErrorSummary, loadFromCache);
      }
    }
  }

  private void validateTemplateInputs(String accountId, String orgId, String projectId, YamlNode templateNode,
      Map<String, TemplateEntity> templateCacheMap, int depth, InputsValidationResponse inputsValidationResponse,
      boolean loadFromCache) {
    JsonNode templateNodeValue = templateNode.getCurrJsonNode();
    // Template YAML corresponding to the TemplateRef and Version Label
    TemplateEntityGetResponse templateEntityGetResponse = templateMergeServiceHelper.getLinkedTemplateEntity(
        accountId, orgId, projectId, templateNodeValue, templateCacheMap, loadFromCache);
    TemplateEntity templateEntity = templateEntityGetResponse.getTemplateEntity();
    String templateYaml = templateEntity.getYaml();

    // verify template inputs of child template.
    InputsValidationResponse childValidationResponse =
        validateInputsInternal(accountId, orgId, projectId, templateYaml, templateCacheMap, depth, loadFromCache);

    // if childrenErrorNodes are not empty, then current node is also an error node
    if (!childValidationResponse.isValid()) {
      inputsValidationResponse.setValid(false);
      inputsValidationResponse.addChildErrorNode(createTemplateErrorNode(
          templateNode, templateEntityGetResponse, childValidationResponse.getChildrenErrorNodes()));
      return;
    }

    // Generate the Template Spec from the Template YAML
    JsonNode templateSpec;
    try {
      NGTemplateConfig templateConfig = YamlUtils.read(templateYaml, NGTemplateConfig.class);
      templateSpec = templateConfig.getTemplateInfoConfig().getSpec();
    } catch (IOException e) {
      log.error("Could not read template yaml", e);
      throw new NGTemplateException("Could not read template yaml: " + e.getMessage());
    }

    // if no child node of template is invalid, then verify template inputs against the template.
    JsonNode templateInputs = templateNodeValue.get(TEMPLATE_INPUTS);
    if (!RuntimeInputsValidator.areInputsValidAgainstSourceNode(templateInputs, templateSpec, KEYS_TO_IGNORE)) {
      inputsValidationResponse.setValid(false);
    }
  }

  private NodeErrorSummary createTemplateErrorNode(YamlNode templateNode,
      TemplateEntityGetResponse templateEntityGetResponse, List<NodeErrorSummary> childrenErrorNodes) {
    YamlNode parentNode = templateNode.getParentNode();
    return TemplateNodeErrorSummary.builder()
        .nodeInfo(NodeInfo.builder()
                      .identifier(parentNode != null ? parentNode.getIdentifier() : null)
                      .name(parentNode != null ? parentNode.getName() : null)
                      .localFqn(YamlUtils.getFullyQualifiedName(templateNode))
                      .build())
        .templateResponse(NGTemplateDtoMapper.writeTemplateResponseDto(templateEntityGetResponse))
        .childrenErrorNodes(childrenErrorNodes)
        .build();
  }
}
