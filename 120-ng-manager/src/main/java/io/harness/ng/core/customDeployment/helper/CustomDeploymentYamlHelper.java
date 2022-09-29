/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.customDeployment.helper;

import static io.harness.NGCommonEntityConstants.VERSION_LABEL_KEY;
import static io.harness.common.EntityYamlRootNames.INFRASTRUCTURE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.CONNECTORS;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.TEMPLATE;
import static io.harness.ng.core.template.TemplateEntityConstants.CUSTOM_DEPLOYMENT_ROOT_FIELD;
import static io.harness.template.yaml.TemplateRefHelper.TEMPLATE_REF;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.eventsframework.schemas.entity.TemplateReferenceProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.customDeployment.CustomDeploymentVariableProperties;
import io.harness.ng.core.customDeployment.CustomDeploymentVariableResponseDTO;
import io.harness.ng.core.customDeployment.CustomDeploymentYamlRequestDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.plancreator.customDeployment.StepTemplateRef;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.beans.TemplateResponseDTO;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class CustomDeploymentYamlHelper {
  private static final String ACCOUNT_IDENTIFIER = "account.";
  private static final String INPUT_STRING = "<+input>";
  private static final String ORG_IDENTIFIER = "org.";
  @JsonIgnore private final ObjectMapper jsonObjectMapper = new ObjectMapper();
  @Inject TemplateResourceClient templateResourceClient;

  public StepTemplateRef getStepTemplateRefFromYaml(String infrastructureYaml, String accountId) {
    YamlConfig yamlConfig = new YamlConfig(infrastructureYaml);
    JsonNode yamlMap = yamlConfig.getYamlMap();
    JsonNode infraDef = yamlMap.get("infrastructureDefinition");
    try {
      if (infraDef.isNull()) {
        log.error("Infra definition is null in yaml for account id :{}", accountId);
        throw new InvalidRequestException("Infra definition is null in yaml");
      }
      JsonNode spec = infraDef.get("spec");
      if (spec.isNull()) {
        log.error("spec is null in yaml for account id :{}", accountId);
        throw new InvalidRequestException("Infra definition spec is null in yaml");
      }
      JsonNode customDeploymentRef = spec.get("customDeploymentRef");
      if (customDeploymentRef.isNull()) {
        log.error("customDeploymentRef is null in yaml for account id :{}", accountId);
        throw new InvalidRequestException("customDeploymentRef is null in yaml");
      }
      StepTemplateRef stepTemplateRef = jsonObjectMapper.treeToValue(customDeploymentRef, StepTemplateRef.class);
      if (isEmpty(stepTemplateRef.getTemplateRef())) {
        log.error("templateRef is empty in yaml for account id :{}", accountId);
        throw new InvalidRequestException("templateRef is null in yaml");
      }
      return stepTemplateRef;
    } catch (Exception e) {
      log.error("Could not fetch the template reference from yaml for acc :{}: {}", accountId, e);
      throw new InvalidRequestException("Could not fetch the template reference from yaml " + e.getMessage());
    }
  }

  private Map<String, String> getTemplateVariables(String yaml) {
    try {
      ObjectNode templateInfra = getInfra(yaml);
      JsonNode templateVariables = templateInfra.get(YAMLFieldNameConstants.VARIABLES);
      Map<String, String> variables = new HashMap<>();
      if (isNull(templateVariables) || isEmpty(templateVariables.toString())) {
        return variables;
      }
      for (JsonNode variable : templateVariables) {
        if (isNull(variable) || isEmpty(variable.get("name").toString()) || isEmpty(variable.get("type").toString())) {
          throw new InvalidRequestException("Template yaml is not valid");
        }
        variables.put(variable.get("name").asText(), variable.get("type").asText());
      }
      return variables;
    } catch (Exception e) {
      log.error("Error occurred while fetching template " + e);
      throw new InvalidRequestException("Error occurred while fetching template ", e);
    }
  }

  private Map<String, String> getInfraVariables(InfrastructureEntity infraEntity) {
    YamlConfig yamlConfig = new YamlConfig(infraEntity.getYaml());
    JsonNode yamlMap = yamlConfig.getYamlMap();
    JsonNode infraDef = yamlMap.get("infrastructureDefinition");
    try {
      if (infraDef.isNull()) {
        log.error("Infra definition is null in yaml for account id :{}", infraEntity.getAccountId());
        throw new InvalidRequestException("Infra definition is null in yaml");
      }
      JsonNode spec = infraDef.get("spec");
      if (spec.isNull()) {
        log.error("spec is null in yaml for account id :{}", infraEntity.getAccountId());
        throw new InvalidRequestException("Infra definition spec is null in yaml");
      }
      JsonNode infraVariables = spec.get(YAMLFieldNameConstants.VARIABLES);
      Map<String, String> variables = new HashMap<>();
      if (isNull(infraVariables) || isEmpty(infraVariables.toString())) {
        return variables;
      }
      for (JsonNode variable : infraVariables) {
        if (variable.isNull() || isEmpty(variable.get("name").toString()) || isEmpty(variable.get("type").toString())) {
          throw new InvalidRequestException("Infrastructure yaml is not valid");
        }
        variables.put(variable.get("name").asText(), variable.get("type").asText());
      }
      return variables;
    } catch (Exception e) {
      log.error("Error occurred while parsing variables from infrastructure yaml " + e);
      throw new InvalidRequestException("Error occurred while parsing variables from infrastructure yaml ", e);
    }
  }

  public TemplateResponseDTO getScopedTemplateResponseDTO(
      String accRef, String orgRef, String projectRef, String templateRef, String versionLabel) {
    TemplateResponseDTO response;
    if (templateRef.contains(ACCOUNT_IDENTIFIER)) {
      response = NGRestUtils.getResponse(templateResourceClient.get(
          templateRef.replace(ACCOUNT_IDENTIFIER, ""), accRef, null, null, versionLabel, false));
    } else if (templateRef.contains(ORG_IDENTIFIER)) {
      response = NGRestUtils.getResponse(templateResourceClient.get(
          templateRef.replace(ORG_IDENTIFIER, ""), accRef, orgRef, null, versionLabel, false));
    } else {
      response = NGRestUtils.getResponse(
          templateResourceClient.get(templateRef, accRef, orgRef, projectRef, versionLabel, false));
    }
    return response;
  }

  public void validateInfrastructureYaml(@NotNull InfrastructureEntity infraEntity) {
    StepTemplateRef stepTemplateRef = getStepTemplateRefFromYaml(infraEntity.getYaml(), infraEntity.getAccountId());
    Map<String, String> infraVariablesFromYaml = getInfraVariables(infraEntity);
    String templateYaml = getScopedTemplateResponseDTO(infraEntity.getAccountId(), infraEntity.getOrgIdentifier(),
        infraEntity.getProjectIdentifier(), stepTemplateRef.getTemplateRef(), stepTemplateRef.getVersionLabel())
                              .getYaml();
    Map<String, String> templateVariables = getTemplateVariables(templateYaml);
    if (infraVariablesFromYaml.size() != templateVariables.size()) {
      throw new InvalidRequestException("Infrastructure Variables doesn't match the template Variables");
    }
    for (Map.Entry<String, String> entry : infraVariablesFromYaml.entrySet()) {
      if (!templateVariables.containsKey(entry.getKey())
          || !templateVariables.get(entry.getKey()).equals(entry.getValue())) {
        throw new InvalidRequestException("Infrastructure Variable doesn't match the template Variables");
      }
    }
  }

  public String getVariables(String yaml) {
    ObjectNode templateInfra = getInfra(yaml);
    JsonNode templateVariables = templateInfra.get(YAMLFieldNameConstants.VARIABLES);
    if (isNull(templateVariables) || isEmpty(templateVariables.toString())) {
      log.error("Template yaml provided does not have variables in it.");
      throw new InvalidRequestException("Template yaml provided does not have variables in it.");
    }
    return YamlPipelineUtils.writeYamlString(templateVariables);
  }

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

  public void validateTemplateYaml(String entityYaml) {
    try {
      JsonNode fetchInstancesScriptNode = YamlUtils.getTopRootFieldInYaml(entityYaml)
                                              .getNode()
                                              .getCurrJsonNode()
                                              .get("infrastructure")
                                              .get("fetchInstancesScript");
      if (isNull(fetchInstancesScriptNode)) {
        log.error("Template yaml provided does not have Fetch Instance Script in it.");
        throw new InvalidRequestException("Template yaml provided does not have Fetch Instance Script in it.");
      }
      JsonNode store = fetchInstancesScriptNode.get("store");
      if (isNull(store)) {
        log.error("Template yaml provided does not have store in it.");
        throw new InvalidRequestException("Template yaml provided does not have store in it.");
      }
      if (store.get("type").asText().equals(ManifestStoreType.INLINE)) {
        if (store.get("spec").get("content").asText().length() <= 0) {
          log.error("Fetch Instance script cannot be empty");
          throw new InvalidRequestException("Fetch Instance script cannot be empty");
        }
      } else if (store.get("type").asText().equals(ManifestStoreType.HARNESS)) {
        JsonNode files = store.get("spec").get("files");
        int count = 0;
        for (JsonNode file : files) {
          if (file.asText().length() <= 0) {
            log.error("Scoped file path cannot be null or empty");
            throw new InvalidRequestException("Scoped file path cannot be null or empty");
          }
          count++;
          log.info(file.asText());
        }
        if (count != 1) {
          log.error("Only one fetch instance script is allowed");
          throw new InvalidRequestException("Only one fetch instance script is allowed");
        }
      } else {
        log.error("Only Inline/Harness Store can be used for fetch instance script");
        throw new InvalidRequestException("Only Inline/Harness Store can be used for fetch instance script");
      }
    } catch (Exception e) {
      log.error("Template yaml is not valid: ", e);
      throw new InvalidRequestException("Template yaml is not valid: " + e.getMessage());
    }
  }

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

  public String getUpdatedYaml(String templateYaml, String infraYaml, String accId) {
    try {
      YamlConfig templateConfig = new YamlConfig(templateYaml);
      JsonNode templateNode = templateConfig.getYamlMap().get("template");
      if (templateNode.isNull()) {
        log.info("Error encountered while updating infra, template node is null for accId :{}", accId);
        throw new InvalidRequestException("template yaml cannot be empty");
      }
      JsonNode templateSpecNode = templateNode.get("spec");
      if (templateSpecNode.isNull()) {
        log.info("Error encountered while updating infra, template spec node is null for accId :{}", accId);
        throw new InvalidRequestException("template yaml spec cannot be empty");
      }
      JsonNode templateInfraNode = templateSpecNode.get("infrastructure");
      if (templateInfraNode.isNull()) {
        log.info("Error encountered while updating infra, template infrastructure node is null for accId :{}", accId);
        throw new InvalidRequestException("template yaml infra cannot be empty");
      }

      YamlConfig infraYamlConfig = new YamlConfig(infraYaml);
      JsonNode infraNode = infraYamlConfig.getYamlMap().get("infrastructureDefinition");
      if (infraNode.isNull()) {
        log.info("Error encountered while updating infra, infra node is null for accId :{}", accId);
        throw new InvalidRequestException("infra yaml cannot be empty");
      }
      JsonNode infraSpecNode = infraNode.get("spec");
      if (infraSpecNode.isNull()) {
        log.info("Error encountered while updating infra, infra spec node is null for accId :{}", accId);
        throw new InvalidRequestException("infra yaml spec cannot be empty");
      }

      ObjectMapper mapper = new ObjectMapper();
      JsonNode infraVariableNode = infraSpecNode.get("variables");
      ArrayNode updatedVariableNode = mapper.createArrayNode();
      Map<String, JsonNode> infraVariables = new HashMap<>();
      for (JsonNode variable : infraVariableNode) {
        infraVariables.put(variable.get("name").asText(), variable);
      }
      List<JsonNode> updateVariablesList = new ArrayList<>();
      JsonNode templateVariableNode = templateInfraNode.get("variables");
      for (JsonNode variable : templateVariableNode) {
        JsonNode var = variable;
        if (infraVariables.containsKey(variable.get("name").asText())) {
          ((ObjectNode) var).set("value", variable.get("value"));
        }
        updateVariablesList.add(var);
      }
      updatedVariableNode.addAll(updateVariablesList);
      ((ObjectNode) infraSpecNode).set("variables", updatedVariableNode);
      return YamlUtils.write(infraYamlConfig.getYamlMap()).replace("---\n", "");
    } catch (Exception e) {
      log.error(
          "Error Encountered in infra updation while reading yamls for template ans Infra for acc Id :{} ", accId);
      throw new InvalidRequestException(
          "Error Encountered in infra updation while reading yamls for template ans Infra");
    }
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
      if (EmptyPredicate.isEmpty(arrayElement.getIdentifier())) {
        if (EmptyPredicate.isNotEmpty(arrayElement.getArrayUniqueIdentifier())) {
          if (isNull(arrayElement.getField(YAMLFieldNameConstants.VALUE))
              || isNull(arrayElement.getField(YAMLFieldNameConstants.TYPE))) {
            continue;
          }
          EntityDetailProtoDTO referredEntity =
              getConnectorReferredEntity(accountId, orgId, projectId, arrayElement, path);
          if (!isNull(referredEntity)) {
            referredEntities.add(referredEntity);
          }
        } else {
          EntityDetailProtoDTO referredEntity = getTemplateReferredEntity(accountId, orgId, projectId, arrayElement);
          if (!isNull(referredEntity)) {
            referredEntities.add(referredEntity);
          }
        }
      }
    }
  }

  private static EntityDetailProtoDTO getConnectorReferredEntity(
      String accountId, String orgId, String projectId, YamlNode arrayElement, Stack<String> path) {
    if (arrayElement.getField(YAMLFieldNameConstants.TYPE).getNode().asText().equalsIgnoreCase("connector")) {
      String connectorRef = arrayElement.getField(YAMLFieldNameConstants.VALUE).getNode().asText();
      if (connectorRef.equals(INPUT_STRING)) {
        return null;
      }
      Map<String, String> metadata =
          Map.of("fqn", getFQNFromPath(path, arrayElement.getArrayUniqueIdentifier()).getFqn());
      return buildConnectorEntityDetailProtoDTO(accountId, orgId, projectId, connectorRef, metadata);
    }
    return null;
  }

  private static EntityDetailProtoDTO getTemplateReferredEntity(
      String accountId, String orgId, String projectId, YamlNode arrayElement) {
    if (arrayElement.getField(TEMPLATE_REF) != null && arrayElement.getField(VERSION_LABEL_KEY) != null) {
      String templateRef = arrayElement.getField(TEMPLATE_REF).getNode().asText();
      String versionLabel = arrayElement.getField(VERSION_LABEL_KEY).getNode().asText();
      return buildTemplateEntityDetailProtoDTO(accountId, orgId, projectId, templateRef, versionLabel);
    }
    return null;
  }

  private static EntityDetailProtoDTO buildConnectorEntityDetailProtoDTO(
      String accountId, String orgId, String projectId, String connectorRef, Map<String, String> metadata) {
    IdentifierRefProtoDTO.Builder identifierRefProtoDTO =
        IdentifierRefProtoDTO.newBuilder().setAccountIdentifier(StringValue.of(accountId));
    if (!isNull(metadata)) {
      identifierRefProtoDTO.putAllMetadata(metadata);
    }
    if (connectorRef.contains(ACCOUNT_IDENTIFIER)) {
      identifierRefProtoDTO.setScope(ScopeProtoEnum.ACCOUNT)
          .setIdentifier(StringValue.of(connectorRef.replace(ACCOUNT_IDENTIFIER, "")));
    } else if (connectorRef.contains(ORG_IDENTIFIER)) {
      identifierRefProtoDTO.setScope(ScopeProtoEnum.ORG)
          .setOrgIdentifier(StringValue.of(orgId))
          .setIdentifier(StringValue.of(connectorRef.replace(ORG_IDENTIFIER, "")));
    } else {
      identifierRefProtoDTO.setScope(ScopeProtoEnum.PROJECT)
          .setOrgIdentifier(StringValue.of(orgId))
          .setProjectIdentifier(StringValue.of(projectId))
          .setIdentifier(StringValue.of(connectorRef));
    }
    return EntityDetailProtoDTO.newBuilder()
        .setType(CONNECTORS)
        .setIdentifierRef(identifierRefProtoDTO.build())
        .build();
  }

  private static EntityDetailProtoDTO buildTemplateEntityDetailProtoDTO(
      String accountId, String orgId, String projectId, String templateRef, String versionLabel) {
    TemplateReferenceProtoDTO.Builder templateReferenceProtoDTO = TemplateReferenceProtoDTO.newBuilder()
                                                                      .setAccountIdentifier(StringValue.of(accountId))
                                                                      .setVersionLabel(StringValue.of(versionLabel));
    if (templateRef.contains(ACCOUNT_IDENTIFIER)) {
      templateReferenceProtoDTO.setScope(ScopeProtoEnum.ACCOUNT)
          .setIdentifier(StringValue.of(templateRef.replace(ACCOUNT_IDENTIFIER, "")));
    } else if (templateRef.contains(ORG_IDENTIFIER)) {
      templateReferenceProtoDTO.setScope(ScopeProtoEnum.ORG)
          .setOrgIdentifier(StringValue.of(orgId))
          .setIdentifier(StringValue.of(templateRef.replace(ORG_IDENTIFIER, "")));
    } else {
      templateReferenceProtoDTO.setScope(ScopeProtoEnum.PROJECT)
          .setOrgIdentifier(StringValue.of(orgId))
          .setProjectIdentifier(StringValue.of(projectId))
          .setIdentifier(StringValue.of(templateRef));
    }
    return EntityDetailProtoDTO.newBuilder()
        .setType(TEMPLATE)
        .setTemplateRef(templateReferenceProtoDTO.build())
        .build();
  }

  private static CustomDeploymentVariableProperties getFQNFromPath(Stack<String> path, String fieldName) {
    StringBuilder fqnBuilder = new StringBuilder();
    path.stream().forEach(pathSring -> fqnBuilder.append(pathSring).append("."));
    fqnBuilder.append(fieldName);
    return CustomDeploymentVariableProperties.builder()
        .fqn(fqnBuilder.toString().replace(
            CUSTOM_DEPLOYMENT_ROOT_FIELD + "." + INFRASTRUCTURE, "stage.spec.infrastructure.output"))
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
