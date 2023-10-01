/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.template.resources.beans.NGTemplateConstants.TEMPLATE_INPUTS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.ng.core.template.RefreshResponseDTO;
import io.harness.ng.core.template.refresh.NgManagerRefreshRequestDTO;
import io.harness.pms.merger.helpers.YamlRefreshHelper;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.reconcile.remote.NgManagerReconcileClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntityGetResponse;
import io.harness.template.resources.beans.yaml.NGTemplateConfig;
import io.harness.template.utils.NGTemplateFeatureFlagHelperService;
import io.harness.template.yaml.TemplateRefHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(CDC)

/**
 * Class containing methods related to the refreshTemplateInputs API only
 * Aim -> to have the refresh code together in a separate class rather than in the TemplateMergeServiceImpl
 */
public class TemplateInputsRefreshHelper {
  @Inject private TemplateMergeServiceHelper templateMergeServiceHelper;
  @Inject private NGTemplateFeatureFlagHelperService featureFlagHelperService;
  @Inject private NgManagerReconcileClient ngManagerReconcileClient;
  // Returns the refreshed YAML when a YAML String is passed.
  public String refreshTemplates(String accountId, String orgId, String projectId, String yaml, boolean loadFromCache) {
    // Case -> empty YAML, cannot refresh
    if (isEmpty(yaml)) {
      throw new NGTemplateException("Yaml to be refreshed cannot be empty.");
    }

    YamlNode yamlNode;
    try {
      // Parsing the YAML to get the YamlNode
      yamlNode = YamlUtils.readTree(yaml).getNode();
    } catch (IOException e) {
      log.error("Could not convert yaml to JsonNode. Yaml:\n" + yaml, e);
      throw new NGTemplateException("Could not convert yaml to JsonNode: " + e.getMessage());
    }

    // TemplateCache Map
    Map<String, TemplateEntity> templateCacheMap = new HashMap<>();

    // refreshedTemplateInputsMap -> Key,Value pairs of the YAML with Refreshed Template Inputs
    Map<String, Object> refreshedTemplateInputsMap =
        getRefreshedTemplateInputsMap(accountId, orgId, projectId, yamlNode, templateCacheMap, loadFromCache);

    // Returning the Refreshed YAML corresponding to the ResMap
    String inputsRefreshYaml = YamlUtils.writeYamlString(refreshedTemplateInputsMap);
    String resolvedTemplatesYaml = inputsRefreshYaml;
    if (TemplateRefHelper.hasTemplateRef(yaml)) {
      Map<String, Object> resolvedTemplatesMap = templateMergeServiceHelper.mergeTemplateInputsInObject(
          accountId, orgId, projectId, yamlNode, templateCacheMap, 0, loadFromCache, false);
      resolvedTemplatesYaml = YamlUtils.writeYamlString(resolvedTemplatesMap);
    }
    RefreshResponseDTO ngManagerRefreshResponseDto =
        NGRestUtils.getResponse(ngManagerReconcileClient.refreshYaml(accountId, orgId, projectId,
            NgManagerRefreshRequestDTO.builder()
                .yaml(inputsRefreshYaml)
                .resolvedTemplatesYaml(resolvedTemplatesYaml)
                .build()));
    inputsRefreshYaml = ngManagerRefreshResponseDto.getRefreshedYaml();
    return inputsRefreshYaml;
  }

  // Gets the Updated ResMap -> Key,Value pairs of the YAML with Refreshed Template Inputs
  private Map<String, Object> getRefreshedTemplateInputsMap(String accountId, String orgId, String projectId,
      YamlNode yamlNode, Map<String, TemplateEntity> templateCacheMap, boolean loadFromCache) {
    Map<String, Object> resMap = new LinkedHashMap<>();

    // Iterating over the YAML fields to go to all the Templates Present
    for (YamlField childYamlField : yamlNode.fields()) {
      String fieldName = childYamlField.getName();
      JsonNode value = childYamlField.getNode().getCurrJsonNode();

      // If Template is present, Refresh the Template Inputs
      if (templateMergeServiceHelper.isTemplatePresent(fieldName, value)) {
        // Updated JsonNode with Refreshed TemplateInputs
        value = getUpdatedTemplateValue(accountId, orgId, projectId, value, templateCacheMap, loadFromCache);
      }

      if (value.isValueNode() || YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(value)) {
        // Value -> LeafNode
        resMap.put(fieldName, value);
      } else if (value.isArray()) {
        // Value -> Array
        resMap.put(fieldName,
            getRefreshedTemplateInputsInArray(
                accountId, orgId, projectId, childYamlField.getNode(), templateCacheMap, loadFromCache));
      } else {
        // Value -> Object
        resMap.put(fieldName,
            getRefreshedTemplateInputsMap(
                accountId, orgId, projectId, childYamlField.getNode(), templateCacheMap, loadFromCache));
      }
    }
    return resMap;
  }

  // Gets the ResMap if the yamlNode is of the type Array
  private List<Object> getRefreshedTemplateInputsInArray(String accountId, String orgId, String projectId,
      YamlNode yamlNode, Map<String, TemplateEntity> templateCacheMap, boolean loadFromCache) {
    List<Object> arrayList = new ArrayList<>();

    // Iterate over the array
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (yamlNode.getCurrJsonNode().isValueNode()) {
        // Value -> LeafNode
        arrayList.add(arrayElement);
      } else if (arrayElement.isArray()) {
        // Value -> Array
        arrayList.add(getRefreshedTemplateInputsInArray(
            accountId, orgId, projectId, arrayElement, templateCacheMap, loadFromCache));
      } else {
        // Value -> Object
        arrayList.add(
            getRefreshedTemplateInputsMap(accountId, orgId, projectId, arrayElement, templateCacheMap, loadFromCache));
      }
    }
    return arrayList;
  }

  // Gets the Updated Template Input values
  private JsonNode getUpdatedTemplateValue(String accountId, String orgId, String projectId, JsonNode TemplateNodeValue,
      Map<String, TemplateEntity> templateCacheMap, boolean loadFromCache) {
    // Template Inputs linked to the YAML
    JsonNode templateInputs = TemplateNodeValue.get(TEMPLATE_INPUTS);

    // Template YAML corresponding to the TemplateRef and Version Label
    TemplateEntityGetResponse templateEntityGetResponse = templateMergeServiceHelper.getLinkedTemplateEntity(
        accountId, orgId, projectId, TemplateNodeValue, templateCacheMap, loadFromCache);
    TemplateEntity templateEntity = templateEntityGetResponse.getTemplateEntity();
    String templateYaml = templateEntity.getYaml();

    // Generate the Template Spec from the Template YAML
    JsonNode templateSpec;
    try {
      NGTemplateConfig templateConfig = YamlUtils.read(templateYaml, NGTemplateConfig.class);
      templateSpec = templateConfig.getTemplateInfoConfig().getSpec();
    } catch (IOException e) {
      log.error("Could not read template yaml", e);
      throw new NGTemplateException("Could not read template yaml: " + e.getMessage());
    }

    // refreshed json node
    JsonNode refreshedJsonNode = YamlRefreshHelper.refreshNodeFromSourceNode(templateInputs, templateSpec);

    ObjectNode updatedValue = (ObjectNode) TemplateNodeValue;

    if (refreshedJsonNode == null) {
      // CASE -> When Template does not contain any runtime inputs
      updatedValue.remove(TEMPLATE_INPUTS);
    } else {
      // Inserting the Updated Value of TemplateInputs corresponding to the TemplateInputs field
      updatedValue.set(TEMPLATE_INPUTS, refreshedJsonNode);
    }

    // Returning the Refreshed Template Inputs Value
    return updatedValue;
  }
}
