/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.customDeployment.helper;

import static io.harness.cdng.customDeployment.constants.CustomDeploymentConstants.VARIABLES;
import static io.harness.common.EntityYamlRootNames.INFRASTRUCTURE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.CONNECTORS;
import static io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum.TEMPLATE;
import static io.harness.ng.core.template.TemplateEntityConstants.CUSTOM_DEPLOYMENT_ROOT_FIELD;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.customDeployment.constants.CustomDeploymentConstants;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.eventsframework.schemas.entity.TemplateReferenceProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.ng.core.customDeployment.CustomDeploymentVariableProperties;
import io.harness.ng.core.customDeployment.CustomDeploymentVariableResponseDTO;
import io.harness.ng.core.customDeployment.CustomDeploymentYamlRequestDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.utils.PageUtils;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class CustomDeploymentYamlHelper {
  public static final String INSTANCE_NAME_KEY = "instancename";
  private static final String ACCOUNT_IDENTIFIER = "account.";
  private static final String INPUT_STRING = "<+input>";
  private static final String ORG_IDENTIFIER = "org.";
  private static final String STEP_TEMPLATE_REFS = "stepTemplateRefs";
  @Inject TemplateResourceClient templateResourceClient;

  public Page<InfrastructureEntity> getFilteredInfraEntities(int page, int size, List<String> sort,
      String deploymentTemplateIdentifier, String versionLabel, Page<InfrastructureEntity> infraEntities) {
    List<InfrastructureEntity> entities =
        infraEntities.getContent()
            .stream()
            .filter(infra -> isDeploymentTemplateInfra(deploymentTemplateIdentifier, versionLabel, infra))
            .collect(Collectors.toList());
    return new PageImpl<>(entities, PageUtils.getPageRequest(page, size, sort), entities.size());
  }

  @NotNull
  public Page<ServiceEntity> getFilteredServiceEntities(int page, int size, List<String> sort,
      String deploymentTemplateIdentifier, String versionLabel, Page<ServiceEntity> serviceEntities) {
    List<ServiceEntity> entities =
        serviceEntities.getContent()
            .stream()
            .filter(s -> isDeploymentTemplateService(deploymentTemplateIdentifier, versionLabel, s))
            .collect(Collectors.toList());
    return new PageImpl<>(entities, PageUtils.getPageRequest(page, size, sort), entities.size());
  }

  public boolean isDeploymentTemplateService(
      String deploymentTemplateIdentifier, String versionLabel, ServiceEntity serviceEntity) {
    if (!isNull(serviceEntity)) {
      String yaml = serviceEntity.getYaml();
      if (!isNull(yaml)) {
        YamlConfig yamlConfig = new YamlConfig(yaml);
        JsonNode service = yamlConfig.getYamlMap().get("service");
        if (!isNull(service)) {
          JsonNode serviceDef = service.get("serviceDefinition");
          if (!isNull(serviceDef)) {
            JsonNode serviceSpec = serviceDef.get("spec");
            if (!isNull(serviceSpec)) {
              JsonNode customDeploymentRef = serviceSpec.get("customDeploymentRef");
              if (!isNull(customDeploymentRef)) {
                JsonNode ref = customDeploymentRef.get("templateRef");
                JsonNode versionLabelNode = customDeploymentRef.get("versionLabel");
                String versionLabelRef = isNull(versionLabelNode) ? "" : versionLabelNode.asText();
                return ref.asText().equals(deploymentTemplateIdentifier) && versionLabelRef.equals(versionLabel);
              }
            }
          }
        }
      }
    }
    return false;
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

  public String getVariables(String yaml) {
    ObjectNode templateInfra = getInfra(yaml);
    JsonNode templateVariables = templateInfra.get(YAMLFieldNameConstants.VARIABLES);
    if (isNull(templateVariables) || isEmpty(templateVariables.toString())) {
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
      JsonNode instanceAttributesNode = YamlUtils.getTopRootFieldInYaml(entityYaml)
                                            .getNode()
                                            .getCurrJsonNode()
                                            .get("infrastructure")
                                            .get("instanceAttributes");
      if (isNull(instanceAttributesNode)) {
        throw new InvalidYamlException("instanceAttributes cannot be empty");
      }
      boolean instanceNameAttributeFound = false;
      for (JsonNode instanceAttribute : instanceAttributesNode) {
        if (INSTANCE_NAME_KEY.equals(instanceAttribute.get("name").asText())) {
          instanceNameAttributeFound = true;
          break;
        }
      }
      if (!instanceNameAttributeFound) {
        throw new InvalidYamlException("instancename value in the Field Name setting is mandatory");
      }
      if (isNull(fetchInstancesScriptNode)) {
        throw new InvalidRequestException("Template yaml provided does not have Fetch Instance Script in it.");
      }
      JsonNode store = fetchInstancesScriptNode.get("store");
      if (isNull(store)) {
        throw new InvalidRequestException("Template yaml provided does not have store in it.");
      }
      JsonNode type = store.get("type");
      if (isNull(type)) {
        throw new InvalidRequestException("Template yaml provided does not have store type in it.");
      }
      if (type.asText().equals(ManifestStoreType.INLINE)) {
        if (store.get("spec").get("content").asText().length() == 0) {
          throw new InvalidRequestException("Fetch Instance script cannot be empty");
        }
      } else if (type.asText().equals(ManifestStoreType.HARNESS)) {
        JsonNode files = store.get("spec").get("files");
        int count = 0;
        for (JsonNode file : files) {
          if (file.asText().length() <= 0) {
            throw new InvalidRequestException("Scoped file path cannot be null or empty");
          }
          count++;
          log.info(file.asText());
        }
        if (count != 1) {
          throw new InvalidRequestException("Only one fetch instance script is allowed");
        }
      } else {
        throw new InvalidRequestException("Only Inline/Harness Store can be used for fetch instance script");
      }
    } catch (Exception e) {
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
      throw new InvalidRequestException(
          "Template yaml provided does not have valid entity references: " + e.getMessage());
    }
    return referredEntities;
  }

  public String getUpdatedYaml(String templateYaml, String infraYaml, String accId) {
    try {
      YamlConfig templateConfig = new YamlConfig(templateYaml);
      JsonNode templateNode = templateConfig.getYamlMap().get("template");
      if (isNull(templateNode)) {
        log.info("Error encountered while updating infra, template node is null for accId :{}", accId);
        throw new InvalidRequestException("template yaml cannot be empty");
      }
      JsonNode templateSpecNode = templateNode.get("spec");
      if (isNull(templateSpecNode)) {
        log.info("Error encountered while updating infra, template spec node is null for accId :{}", accId);
        throw new InvalidRequestException("template yaml spec cannot be empty");
      }
      JsonNode templateInfraNode = templateSpecNode.get("infrastructure");
      if (isNull(templateInfraNode)) {
        log.info("Error encountered while updating infra, template infrastructure node is null for accId :{}", accId);
        throw new InvalidRequestException("template yaml infra cannot be empty");
      }

      YamlConfig infraYamlConfig = new YamlConfig(infraYaml);
      JsonNode infraNode = infraYamlConfig.getYamlMap().get("infrastructureDefinition");
      if (isNull(infraNode)) {
        log.info("Error encountered while updating infra, infra node is null for accId :{}", accId);
        throw new InvalidRequestException("infra yaml cannot be empty");
      }
      JsonNode infraSpecNode = infraNode.get("spec");
      if (isNull(infraSpecNode)) {
        log.info("Error encountered while updating infra, infra spec node is null for accId :{}", accId);
        throw new InvalidRequestException("infra yaml spec cannot be empty");
      }

      ObjectMapper mapper = new ObjectMapper();
      ArrayNode updatedVariableNode = mapper.createArrayNode();
      Map<String, JsonNode> infraVariables = new HashMap<>();
      if (infraSpecNode.has(VARIABLES)) {
        JsonNode infraVariableNode = infraSpecNode.get(VARIABLES);
        for (JsonNode variable : infraVariableNode) {
          infraVariables.put(variable.get("name").asText(), variable);
        }
      }
      List<JsonNode> updateVariablesList = new ArrayList<>();
      if (templateInfraNode.has(VARIABLES)) {
        JsonNode templateVariableNode = templateInfraNode.get(VARIABLES);
        for (JsonNode variable : templateVariableNode) {
          JsonNode var = variable;
          if (infraVariables.containsKey(variable.get("name").asText())) {
            ((ObjectNode) var)
                .set(CustomDeploymentConstants.VALUE,
                    infraVariables.get(variable.get("name").asText()).get(CustomDeploymentConstants.VALUE));
          }
          updateVariablesList.add(var);
        }
      }
      updatedVariableNode.addAll(updateVariablesList);
      ((ObjectNode) infraSpecNode).set(VARIABLES, updatedVariableNode);
      return YamlUtils.writeYamlString(infraYamlConfig.getYamlMap());
    } catch (Exception e) {
      throw new InvalidRequestException(
          "Error Encountered in infra updation while reading yamls for template and Infra: " + e.getMessage());
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
      if (!field.getNode().getCurrJsonNode().isValueNode()) {
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
      if (isEmpty(arrayElement.getIdentifier()) && isNotEmpty(arrayElement.getArrayUniqueIdentifier())) {
        String fieldName = CustomDeploymentConstants.VALUE;
        if (isNull(arrayElement.getField(fieldName))) {
          if (isNull(arrayElement.getField("ref"))) {
            continue;
          }
          fieldName = "ref";
        }
        uuidToFQNMap.put(arrayElement.getField(fieldName).getNode().asText(),
            getFQNFromPath(path, arrayElement.getArrayUniqueIdentifier()));
      } else if (isNotEmpty(arrayElement.getIdentifier())) {
        path.push(arrayElement.getIdentifier());
        populateUuidToFQNMapForLeafNodesInObject(uuidToFQNMap, arrayElement, path);
        path.pop();
      }
    }
  }

  private void populateReferredEntitiesListForLeafNodes(String accountId, String orgId, String projectId,
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

  private void populateReferredEntitiesListForLeafNodesInObject(String accountId, String orgId, String projectId,
      List<EntityDetailProtoDTO> referredEntities, YamlNode yamlNode, Stack<String> path) {
    for (YamlField field : yamlNode.fields()) {
      if (!field.getNode().getCurrJsonNode().isValueNode()) {
        populateReferredEntitiesListForLeafNodes(accountId, orgId, projectId, referredEntities, field, path);
      }
    }
  }

  private void populateReferredEntitiesListForLeafNodesInArray(String accountId, String orgId, String projectId,
      List<EntityDetailProtoDTO> referredEntities, YamlNode yamlNode, Stack<String> path) {
    if (YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(yamlNode.getCurrJsonNode())) {
      if (!path.empty() && path.lastElement().equals(STEP_TEMPLATE_REFS)) {
        Set<String> checkForDuplicateSteps = new HashSet<>();
        for (YamlNode arrayElement : yamlNode.asArray()) {
          if (checkForDuplicateSteps.contains(arrayElement.asText())) {
            throw new InvalidRequestException(
                format("Duplicate step %s linked with the template", arrayElement.asText()));
          }
          checkForDuplicateSteps.add(arrayElement.asText());
          EntityDetailProtoDTO referredEntity =
              getTemplateReferredEntity(accountId, orgId, projectId, arrayElement.asText());
          if (!isNull(referredEntity)) {
            referredEntities.add(referredEntity);
          }
        }
      }
    } else {
      for (YamlNode arrayElement : yamlNode.asArray()) {
        if (isEmpty(arrayElement.getIdentifier()) && isNotEmpty(arrayElement.getArrayUniqueIdentifier())) {
          if (isNull(arrayElement.getField(YAMLFieldNameConstants.VALUE))
              || isNull(arrayElement.getField(YAMLFieldNameConstants.TYPE))) {
            continue;
          }
          EntityDetailProtoDTO referredEntity =
              getConnectorReferredEntity(accountId, orgId, projectId, arrayElement, path);
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

  private EntityDetailProtoDTO getTemplateReferredEntity(
      String accountId, String orgId, String projectId, String templateRef) {
    if (isEmpty(templateRef)) {
      throw new InvalidRequestException("step template linked cannot have empty identifier");
    }
    return buildStableTemplateEntityDetailProtoDTO(accountId, orgId, projectId, templateRef);
  }

  private static EntityDetailProtoDTO buildConnectorEntityDetailProtoDTO(
      String accountId, String orgId, String projectId, String connectorRef, Map<String, String> metadata) {
    if (isEmpty(connectorRef) || (connectorRef.startsWith("<+") && connectorRef.endsWith(">"))) {
      return null;
    }
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

  private EntityDetailProtoDTO buildStableTemplateEntityDetailProtoDTO(
      String accountId, String orgId, String projectId, String templateRef) {
    getScopedTemplateResponseDTO(accountId, orgId, projectId, templateRef, "");
    TemplateReferenceProtoDTO.Builder templateReferenceProtoDTO = TemplateReferenceProtoDTO.newBuilder()
                                                                      .setAccountIdentifier(StringValue.of(accountId))
                                                                      .setVersionLabel(StringValue.of("__STABLE__"));
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
      throw new InvalidRequestException("Error occurred while fetching template infrastructure " + e.getMessage());
    }
  }

  private boolean isDeploymentTemplateInfra(
      String deploymentTemplateIdentifier, String versionLabel, InfrastructureEntity infraEntity) {
    String yaml = infraEntity.getYaml();
    YamlConfig yamlConfig = new YamlConfig(yaml);
    JsonNode infraNode = yamlConfig.getYamlMap().get("infrastructureDefinition");
    if (!isNull(infraNode)) {
      JsonNode infraDefSpec = infraNode.get("spec");
      if (!isNull(infraDefSpec)) {
        JsonNode customDeploymentRef = infraDefSpec.get("customDeploymentRef");
        if (!isNull(customDeploymentRef)) {
          JsonNode ref = customDeploymentRef.get("templateRef");
          JsonNode versionLabelNode = customDeploymentRef.get("versionLabel");
          String versionLabelRef = isNull(versionLabelNode) ? "" : versionLabelNode.asText();
          return ref.asText().equals(deploymentTemplateIdentifier) && versionLabelRef.equals(versionLabel);
        }
      }
    }
    return false;
  }
}
