/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.customDeployment.helper;

import static io.harness.common.EntityYamlRootNames.INFRASTRUCTURE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.CONNECTORS;
import static io.harness.ng.core.template.TemplateEntityConstants.CUSTOM_DEPLOYMENT_ROOT_FIELD;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.customDeployment.CustomDeploymentVariableProperties;
import io.harness.ng.core.customDeployment.CustomDeploymentVariableResponseDTO;
import io.harness.ng.core.customDeployment.CustomDeploymentYamlRequestDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Singleton;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class CustomDeploymentYamlHelperImpl implements CustomDeploymentYamlHelper {
  @Override
  public String getVariables(String yaml) {
    ObjectNode templateInfra = getInfra(yaml);
    JsonNode templateVariables = templateInfra.get(YAMLFieldNameConstants.VARIABLES);
    if (isNull(templateVariables) || isEmpty(templateVariables.toString())) {
      log.error("Template yaml provided does not have variables in it.");
      throw new InvalidRequestException("Template yaml provided does not have variables in it.");
    }
    return YamlPipelineUtils.writeYamlString(templateVariables);
  }

  @Override
  public String getConnectors(String yaml) {
    ObjectNode templateInfra = getInfra(yaml);
    JsonNode templateConnectors = templateInfra.get(YAMLFieldNameConstants.CONNECTOR_REFS);
    if (isNull(templateConnectors) || isEmpty(templateConnectors.toString())) {
      log.error("Template yaml provided does not have infrastructure connectors in it.");
      throw new InvalidRequestException("Template yaml provided does not have infrastructure connectors in it.");
    }
    return YamlPipelineUtils.writeYamlString(templateConnectors);
  }

  @Override
  public CustomDeploymentVariableResponseDTO getVariablesFromYaml(
      CustomDeploymentYamlRequestDTO customDeploymentYamlRequestDTO) {
    try {
      YamlField uuidInjectedYaml = YamlUtils.injectUuidWithLeafUuid(customDeploymentYamlRequestDTO.getEntityYaml());
      Map<String, CustomDeploymentVariableProperties> uuIdToFQNMap = getUuidToFQNMapForLeafNodes(
          uuidInjectedYaml.getNode().getField(TemplateEntityType.CUSTOM_DEPLOYMENT_TEMPLATE.getRootYamlName()));
      return CustomDeploymentVariableResponseDTO.builder()
          .yaml(YamlUtils.writeYamlString(uuidInjectedYaml))
          .metadataMap(uuIdToFQNMap)
          .build();
    } catch (Exception e) {
      log.error("Template yaml provided does not have valid expression variables ", e);
      throw new InvalidRequestException("Template yaml provided does not have valid expression variables ", e);
    }
  }

  @Override
  public List<EntityDetailProtoDTO> getReferencesFromYaml(
      String accountId, String orgId, String projectId, String entityYaml) {
    List<EntityDetailProtoDTO> referredEntities = new ArrayList<>();
    Stack<String> path = new Stack<>();
    try {
      YamlField yaml = YamlUtils.getTopRootFieldInYaml(entityYaml);
      populateReferredEntitiesListForLeafNodes(accountId, orgId, projectId, referredEntities, yaml, path);
    } catch (Exception e) {
      log.error("Template yaml provided does not have valid entity references ", e);
      throw new InvalidRequestException("Template yaml provided does not have valid entity references ", e);
    }
    return referredEntities;
  }

  private static Map<String, CustomDeploymentVariableProperties> getUuidToFQNMapForLeafNodes(
      YamlField uuidInjectedYaml) {
    Map<String, CustomDeploymentVariableProperties> uuidToFQNMap = new HashMap<>();
    Stack<String> path = new Stack<>();
    populateUuidToFQNMapForLeafNodes(uuidToFQNMap, uuidInjectedYaml, path);
    return uuidToFQNMap;
  }

  private static void populateUuidToFQNMapForLeafNodes(
      Map<String, CustomDeploymentVariableProperties> uuidToFQNMap, YamlField yamlField, Stack<String> path) {
    path.push(yamlField.getName());
    if (yamlField.getNode().isArray()) {
      populateUuidToFQNMapForLeafNodesInArray(uuidToFQNMap, yamlField.getNode(), path);
    } else if (yamlField.getNode().isObject()) {
      populateUuidToFQNMapForLeafNodesInObject(uuidToFQNMap, yamlField.getNode(), path);
    }
    path.pop();
  }

  private static void populateUuidToFQNMapForLeafNodesInObject(
      Map<String, CustomDeploymentVariableProperties> uuidToFQNMap, YamlNode yamlNode, Stack<String> path) {
    for (YamlField field : yamlNode.fields()) {
      if (field.getNode().getCurrJsonNode().isValueNode()) {
        if (Arrays.asList(YamlNode.IDENTIFIER_FIELD_NAME, YamlNode.UUID_FIELD_NAME, YamlNode.TYPE_FIELD_NAME)
                .contains(field.getName())) {
          continue;
        }
        uuidToFQNMap.put(field.getNode().asText(), getFQNFromPath(path, field.getName()));
      } else {
        populateUuidToFQNMapForLeafNodes(uuidToFQNMap, field, path);
      }
    }
  }

  private static void populateUuidToFQNMapForLeafNodesInArray(
      Map<String, CustomDeploymentVariableProperties> uuidToFQNMap, YamlNode yamlNode, Stack<String> path) {
    if (YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(yamlNode.getCurrJsonNode())) {
      return;
    }
    for (YamlNode arrayElement : yamlNode.asArray()) {
      /*
       * For nodes such as variables where only value field is associated with name, key.
       */
      if (EmptyPredicate.isEmpty(arrayElement.getIdentifier())
          && EmptyPredicate.isNotEmpty(arrayElement.getArrayUniqueIdentifier())) {
        String fieldName = "value";
        if (isNull(arrayElement.getField(fieldName))) {
          if (isNull(arrayElement.getField("ref"))) {
            continue;
          }
          fieldName = "ref";
        }
        uuidToFQNMap.put(arrayElement.getField(fieldName).getNode().asText(),
            getFQNFromPath(path, arrayElement.getArrayUniqueIdentifier()));
      } else if (EmptyPredicate.isNotEmpty(arrayElement.getIdentifier())) {
        path.push(arrayElement.getIdentifier());
        populateUuidToFQNMapForLeafNodesInObject(uuidToFQNMap, arrayElement, path);
        path.pop();
      }
    }
  }

  private static void populateReferredEntitiesListForLeafNodes(String accountId, String orgId, String projectId,
      List<EntityDetailProtoDTO> referredEntities, YamlField yamlField, Stack<String> path) {
    path.push(yamlField.getName());
    if (yamlField.getNode().isArray()) {
      populateReferredEntitiesListForLeafNodesInArray(
          accountId, orgId, projectId, referredEntities, yamlField.getNode(), path);
    } else if (yamlField.getNode().isObject()) {
      populateReferredEntitiesListForLeafNodesInObject(
          accountId, orgId, projectId, referredEntities, yamlField.getNode(), path);
    }
    path.pop();
  }

  private static void populateReferredEntitiesListForLeafNodesInObject(String accountId, String orgId, String projectId,
      List<EntityDetailProtoDTO> referredEntities, YamlNode yamlNode, Stack<String> path) {
    for (YamlField field : yamlNode.fields()) {
      if (!field.getNode().getCurrJsonNode().isValueNode()) {
        populateReferredEntitiesListForLeafNodes(accountId, orgId, projectId, referredEntities, field, path);
      }
    }
  }

  private static void populateReferredEntitiesListForLeafNodesInArray(String accountId, String orgId, String projectId,
      List<EntityDetailProtoDTO> referredEntities, YamlNode yamlNode, Stack<String> path) {
    if (YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(yamlNode.getCurrJsonNode())) {
      return;
    }
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (EmptyPredicate.isEmpty(arrayElement.getIdentifier())
          && EmptyPredicate.isNotEmpty(arrayElement.getArrayUniqueIdentifier())) {
        if (isNull(arrayElement.getField(YAMLFieldNameConstants.VALUE))
            || isNull(arrayElement.getField(YAMLFieldNameConstants.TYPE))) {
          continue;
        }
        if (arrayElement.getField(YAMLFieldNameConstants.TYPE).getNode().asText().equalsIgnoreCase("connector")) {
          String connectorRef = arrayElement.getField(YAMLFieldNameConstants.VALUE).getNode().asText();
          IdentifierRefProtoDTO.Builder identifierRefProtoDTO =
              IdentifierRefProtoDTO.newBuilder()
                  .setAccountIdentifier(StringValue.of(accountId))
                  .putAllMetadata(
                      Map.of("fqn", getFQNFromPath(path, arrayElement.getArrayUniqueIdentifier()).getFqn()));
          if (connectorRef.contains("account.")) {
            identifierRefProtoDTO.setScope(ScopeProtoEnum.ACCOUNT)
                .setIdentifier(StringValue.of(connectorRef.replace("account.", "")));
          } else if (connectorRef.contains("org.")) {
            identifierRefProtoDTO.setScope(ScopeProtoEnum.ORG)
                .setOrgIdentifier(StringValue.of(orgId))
                .setIdentifier(StringValue.of(connectorRef.replace("org.", "")));
          } else {
            identifierRefProtoDTO.setScope(ScopeProtoEnum.PROJECT)
                .setOrgIdentifier(StringValue.of(orgId))
                .setProjectIdentifier(StringValue.of(projectId))
                .setIdentifier(StringValue.of(connectorRef));
          }
          EntityDetailProtoDTO referredEntity = EntityDetailProtoDTO.newBuilder()
                                                    .setType(CONNECTORS)
                                                    .setIdentifierRef(identifierRefProtoDTO.build())
                                                    .build();
          referredEntities.add(referredEntity);
        }
      }
    }
  }

  private static CustomDeploymentVariableProperties getFQNFromPath(Stack<String> path, String fieldName) {
    StringBuilder fqnBuilder = new StringBuilder();
    path.stream().forEach(pathSring -> fqnBuilder.append(pathSring).append("."));
    fqnBuilder.append(fieldName);
    return CustomDeploymentVariableProperties.builder()
        .fqn(fqnBuilder.toString())
        .localName(
            fqnBuilder.toString().replace(CUSTOM_DEPLOYMENT_ROOT_FIELD + ".", "").replace(INFRASTRUCTURE, "infra"))
        .variableName(fieldName)
        .visible(true)
        .build();
  }

  private ObjectNode getInfra(String yaml) {
    try {
      if (isEmpty(yaml)) {
        throw new InvalidRequestException("Template yaml to create template inputs cannot be empty");
      }
      YamlField templateYamlField = YamlUtils.readTree(yaml).getNode().getField(YAMLFieldNameConstants.TEMPLATE);
      if (templateYamlField == null) {
        log.error("Yaml provided is not a template yaml. Yaml:\n" + yaml);
        throw new InvalidRequestException("Yaml provided is not a template yaml.");
      }
      ObjectNode templateNode = (ObjectNode) templateYamlField.getNode().getCurrJsonNode();
      JsonNode templateSpec = templateNode.get(YAMLFieldNameConstants.SPEC);
      if (isNull(templateSpec) || isEmpty(templateSpec.toString())) {
        log.error("Template yaml provided does not have spec in it.");
        throw new InvalidRequestException("Template yaml provided does not have spec in it.");
      }
      JsonNode templateInfra = templateSpec.get(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE);
      if (isNull(templateInfra) || isEmpty(templateInfra.toString())) {
        log.error("Template yaml provided does not have infrastructure in it.");
        throw new InvalidRequestException("Template yaml provided does not have infrastructure in it.");
      }
      return (ObjectNode) templateInfra;
    } catch (IOException e) {
      log.error("Error occurred while fetching template infrastructure " + e);
      throw new InvalidRequestException("Error occurred while fetching template infrastructure ", e);
    }
  }
}
