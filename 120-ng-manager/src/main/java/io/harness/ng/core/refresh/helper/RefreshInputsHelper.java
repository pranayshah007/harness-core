/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.refresh.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.persistence.PersistentEntity;
import io.harness.pms.merger.helpers.YamlRefreshHelper;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlNodeUtils;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class RefreshInputsHelper {
  @Inject ServiceEntityService serviceEntityService;
  @Inject EntityFetchHelper entityFetchHelper;

  public String refreshInputs(String accountId, String orgId, String projectId, String yaml) {
    if (isEmpty(yaml)) {
      throw new InvalidRequestException("Yaml to be validated cannot be empty.");
    }

    YamlNode yamlNode;
    try {
      // Parsing the YAML to get the YamlNode
      yamlNode = YamlUtils.readTree(yaml).getNode();
    } catch (IOException e) {
      log.error("Could not convert yaml to JsonNode. Yaml:\n" + yaml, e);
      throw new InvalidRequestException("Could not convert yaml to JsonNode: " + e.getMessage());
    }

    Map<String, PersistentEntity> cacheMap = new HashMap<>();
    if (!yamlNode.isObject()) {
      log.error("Root node expected to be an object but was of type: " + yamlNode.getType());
      throw new InvalidRequestException("Root node to be refreshed should be an object.");
    }
    Map<String, Object> refreshInputsMap = refreshInputsInObject(accountId, orgId, projectId, yamlNode, cacheMap);
    return YamlPipelineUtils.writeYamlString(refreshInputsMap);
  }

  private Map<String, Object> refreshInputsInObject(
      String accountId, String orgId, String projectId, YamlNode yamlNode, Map<String, PersistentEntity> cacheMap) {
    Map<String, Object> resObjectMap = new LinkedHashMap<>();
    for (YamlField childYamlField : yamlNode.fields()) {
      String fieldName = childYamlField.getName();
      YamlNode currentYamlNode = childYamlField.getNode();
      JsonNode value = currentYamlNode.getCurrJsonNode();

      // If Template is present, validate the Template Inputs
      if (serviceEntityService.isServiceField(fieldName, value)) {
        value = refreshServiceInputs(accountId, orgId, projectId, currentYamlNode, cacheMap);
      }

      if (value.isValueNode() || YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(value)) {
        // Value -> LeafNode
        resObjectMap.put(fieldName, value);
      } else if (value.isArray()) {
        // Value -> Array
        resObjectMap.put(
            fieldName, refreshInputsInArray(accountId, orgId, projectId, childYamlField.getNode(), cacheMap));
      } else {
        // Value -> Object
        resObjectMap.put(
            fieldName, refreshInputsInObject(accountId, orgId, projectId, childYamlField.getNode(), cacheMap));
      }
    }
    return resObjectMap;
  }

  private List<Object> refreshInputsInArray(String accountId, String orgId, String projectId, YamlNode yamlNode,
      Map<String, PersistentEntity> templateCacheMap) {
    List<Object> resList = new ArrayList<>();
    // Iterate over the array
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (yamlNode.getCurrJsonNode().isValueNode()) {
        // Value -> LeafNode
        resList.add(arrayElement);
      } else if (arrayElement.isArray()) {
        // Value -> Array
        resList.add(refreshInputsInArray(accountId, orgId, projectId, arrayElement, templateCacheMap));
      } else {
        // Value -> Object
        resList.add(refreshInputsInObject(accountId, orgId, projectId, arrayElement, templateCacheMap));
      }
    }
    return resList;
  }

  private JsonNode refreshServiceInputs(
      String accountId, String orgId, String projectId, YamlNode entityNode, Map<String, PersistentEntity> cacheMap) {
    ObjectNode serviceNodeValue = (ObjectNode) entityNode.getCurrJsonNode();
    String serviceRef = serviceNodeValue.get(YamlTypes.SERVICE_REF).asText();
    if (NGExpressionUtils.isRuntimeOrExpressionField(serviceRef)) {
      serviceNodeValue.put(YamlTypes.SERVICE_INPUTS, "<+input>");
      return serviceNodeValue;
    }

    ServiceEntity serviceEntity = entityFetchHelper.getService(accountId, orgId, projectId, serviceRef, cacheMap);

    JsonNode serviceInputs = serviceNodeValue.get(YamlTypes.SERVICE_INPUTS);
    String serviceYaml = serviceEntity.fetchNonEmptyYaml();
    YamlNode primaryArtifactRefNode = YamlNodeUtils.goToPathUsingFqn(
        entityNode, "serviceInputs.serviceDefinition.spec.artifacts.primary.primaryArtifactRef");
    String serviceRuntimeInputYaml = serviceEntityService.createServiceInputsYamlGivenPrimaryArtifactRef(
        serviceYaml, serviceRef, primaryArtifactRefNode == null ? null : primaryArtifactRefNode.asText());
    if (EmptyPredicate.isEmpty(serviceRuntimeInputYaml)) {
      serviceNodeValue.remove(YamlTypes.SERVICE_INPUTS);
      return serviceNodeValue;
    }

    ObjectMapper mapper = new ObjectMapper();
    ObjectNode serviceInputsNode = mapper.createObjectNode();
    serviceInputsNode.set(YamlTypes.SERVICE_INPUTS, serviceInputs);
    String linkedServiceInputsYaml = YamlPipelineUtils.writeYamlString(serviceInputsNode);
    JsonNode refreshedJsonNode =
        YamlRefreshHelper.refreshYamlFromSourceYaml(linkedServiceInputsYaml, serviceRuntimeInputYaml);
    serviceNodeValue.set(YamlTypes.SERVICE_INPUTS, refreshedJsonNode.get(YamlTypes.SERVICE_INPUTS));
    return serviceNodeValue;
  }
}
