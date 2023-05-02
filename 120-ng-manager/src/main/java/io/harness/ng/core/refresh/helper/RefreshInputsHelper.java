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
import io.harness.ng.core.refresh.bean.EntityRefreshContext;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.yaml.CDYamlUtils;
import io.harness.persistence.PersistentEntity;
import io.harness.pms.merger.helpers.YamlRefreshHelper;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlNodeUtils;
import io.harness.pms.yaml.YamlUtils;

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
  @Inject EnvironmentRefreshHelper environmentRefreshHelper;

  public String refreshInputs(
      String accountId, String orgId, String projectId, String yaml, String resolvedTemplatesYaml) {
    if (isEmpty(yaml)) {
      throw new InvalidRequestException("Yaml to be validated cannot be empty.");
    }

    YamlNode yamlNode = getYamlNode(yaml);
    YamlNode resolvedTemplatesYamlNode = isEmpty(resolvedTemplatesYaml) ? null : getYamlNode(resolvedTemplatesYaml);

    Map<String, PersistentEntity> cacheMap = new HashMap<>();
    if (!yamlNode.isObject()) {
      log.error("Root node expected to be an object but was of type: " + yamlNode.getType());
      throw new InvalidRequestException("Root node to be refreshed should be an object.");
    }
    Map<String, Object> refreshInputsMap = refreshInputsInObject(yamlNode,
        EntityRefreshContext.builder()
            .accountId(accountId)
            .orgId(orgId)
            .projectId(projectId)
            .cacheMap(cacheMap)
            .resolvedTemplatesYamlNode(resolvedTemplatesYamlNode)
            .build());
    return CDYamlUtils.writeYamlString(refreshInputsMap);
  }

  private YamlNode getYamlNode(String yaml) {
    YamlNode yamlNode;
    try {
      // Parsing the YAML to get the YamlNode
      yamlNode = YamlUtils.readTree(yaml).getNode();
    } catch (IOException e) {
      log.error("Could not convert yaml to JsonNode. Yaml:\n" + yaml, e);
      throw new InvalidRequestException("Could not convert yaml to JsonNode: " + e.getMessage());
    }
    return yamlNode;
  }

  private Map<String, Object> refreshInputsInObject(YamlNode yamlNode, EntityRefreshContext context) {
    Map<String, Object> resObjectMap = new LinkedHashMap<>();
    for (YamlField childYamlField : yamlNode.fields()) {
      String fieldName = childYamlField.getName();
      YamlNode currentYamlNode = childYamlField.getNode();
      JsonNode value = currentYamlNode.getCurrJsonNode();

      // If Template is present, validate the Template Inputs
      if (serviceEntityService.isServiceField(fieldName, value)) {
        value = refreshServiceInputs(currentYamlNode, context);
      } else if (environmentRefreshHelper.isEnvironmentField(fieldName, value)) {
        value = environmentRefreshHelper.refreshEnvironmentInputs(currentYamlNode, context);
      }

      if (value.isValueNode() || YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(value)) {
        // Value -> LeafNode
        resObjectMap.put(fieldName, value);
      } else if (value.isArray()) {
        // Value -> Array
        resObjectMap.put(fieldName, refreshInputsInArray(childYamlField.getNode(), context));
      } else {
        // Value -> Object
        resObjectMap.put(fieldName, refreshInputsInObject(childYamlField.getNode(), context));
      }
    }
    return resObjectMap;
  }

  private List<Object> refreshInputsInArray(YamlNode yamlNode, EntityRefreshContext context) {
    List<Object> resList = new ArrayList<>();
    // Iterate over the array
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (yamlNode.getCurrJsonNode().isValueNode()) {
        // Value -> LeafNode
        resList.add(arrayElement);
      } else if (arrayElement.isArray()) {
        // Value -> Array
        resList.add(refreshInputsInArray(arrayElement, context));
      } else {
        // Value -> Object
        resList.add(refreshInputsInObject(arrayElement, context));
      }
    }
    return resList;
  }

  private JsonNode refreshServiceInputs(YamlNode entityNode, EntityRefreshContext context) {
    ObjectNode serviceNodeValue = (ObjectNode) entityNode.getCurrJsonNode();
    String serviceRef = serviceNodeValue.get(YamlTypes.SERVICE_REF) != null
        ? serviceNodeValue.get(YamlTypes.SERVICE_REF).asText()
        : null;

    // use from stage
    if (EmptyPredicate.isEmpty(serviceRef)) {
      serviceNodeValue.remove(YamlTypes.SERVICE_INPUTS);
      return serviceNodeValue;
    }
    if (NGExpressionUtils.isRuntimeField(serviceRef)) {
      serviceNodeValue.put(YamlTypes.SERVICE_INPUTS, "<+input>");
      return serviceNodeValue;
    } else if (NGExpressionUtils.isExpressionField(serviceRef)) {
      return serviceNodeValue;
    }

    ServiceEntity serviceEntity = entityFetchHelper.getService(
        context.getAccountId(), context.getOrgId(), context.getProjectId(), serviceRef, context.getCacheMap());

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
    String linkedServiceInputsYaml = CDYamlUtils.writeYamlString(serviceInputsNode);
    JsonNode refreshedJsonNode =
        YamlRefreshHelper.refreshYamlFromSourceYaml(linkedServiceInputsYaml, serviceRuntimeInputYaml);
    serviceNodeValue.set(YamlTypes.SERVICE_INPUTS, refreshedJsonNode.get(YamlTypes.SERVICE_INPUTS));
    return serviceNodeValue;
  }
}
