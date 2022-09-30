/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.refresh.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.EntityReferenceHelper;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.mappers.ServiceElementMapper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.persistence.PersistentEntity;
import io.harness.pms.merger.helpers.RuntimeInputsValidator;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlNodeUtils;
import io.harness.pms.yaml.YamlUtils;
import io.harness.template.beans.refresh.NodeInfo;
import io.harness.template.beans.refresh.v2.InputsValidationResponse;
import io.harness.template.beans.refresh.v2.NodeErrorSummary;
import io.harness.template.beans.refresh.v2.ServiceNodeErrorSummary;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class InputsValidationHelper {
  @Inject ServiceEntityService serviceEntityService;

  public InputsValidationResponse validateInputsForYaml(String accountId, String orgId, String projectId, String yaml) {
    return validateInputsForYaml(accountId, orgId, projectId, yaml, new HashMap<>());
  }

  private InputsValidationResponse validateInputsForYaml(
      String accountId, String orgId, String projectId, String yaml, Map<String, PersistentEntity> cacheMap) {
    YamlNode yamlNode = validateAndGetYamlNode(yaml);
    InputsValidationResponse inputsValidationResponse =
        InputsValidationResponse.builder().isValid(true).childrenErrorNodes(new ArrayList<>()).build();
    if (yamlNode.isObject()) {
      validateInputsInObject(accountId, orgId, projectId, yamlNode, cacheMap, inputsValidationResponse);
    } else if (yamlNode.isArray()) {
      validateInputsInArray(accountId, orgId, projectId, yamlNode, cacheMap, inputsValidationResponse);
    }
    return inputsValidationResponse;
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

  private void validateInputsInObject(String accountId, String orgId, String projectId, YamlNode yamlNode,
      Map<String, PersistentEntity> cacheMap, InputsValidationResponse inputsValidationResponse) {
    for (YamlField childYamlField : yamlNode.fields()) {
      String fieldName = childYamlField.getName();
      YamlNode currentYamlNode = childYamlField.getNode();
      JsonNode value = currentYamlNode.getCurrJsonNode();

      if (inputsValidationResponse.isValid()) {
        // If Service is present, validate the Service Inputs
        if (serviceEntityService.isServiceField(fieldName, value)) {
          validateServiceInputs(accountId, orgId, projectId, currentYamlNode, cacheMap, inputsValidationResponse);
          continue;
        }
      }

      if (value.isArray() && !YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(value)) {
        // Value -> Array
        validateInputsInArray(
            accountId, orgId, projectId, childYamlField.getNode(), cacheMap, inputsValidationResponse);
      } else if (value.isObject()) {
        // Value -> Object
        validateInputsInObject(
            accountId, orgId, projectId, childYamlField.getNode(), cacheMap, inputsValidationResponse);
      }
    }
  }

  private void validateInputsInArray(String accountId, String orgId, String projectId, YamlNode yamlNode,
      Map<String, PersistentEntity> templateCacheMap, InputsValidationResponse childrenNodeErrorSummary) {
    // Iterate over the array
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (arrayElement.isArray()) {
        // Value -> Array
        validateInputsInArray(accountId, orgId, projectId, arrayElement, templateCacheMap, childrenNodeErrorSummary);
      } else if (arrayElement.isObject()) {
        // Value -> Object
        validateInputsInObject(accountId, orgId, projectId, arrayElement, templateCacheMap, childrenNodeErrorSummary);
      }
    }
  }

  void validateServiceInputs(String accountId, String orgId, String projectId, YamlNode entityNode,
      Map<String, PersistentEntity> cacheMap, InputsValidationResponse errorNodeSummary) {
    JsonNode serviceNode = entityNode.getCurrJsonNode();
    String serviceRef = serviceNode.get(YamlTypes.SERVICE_REF).asText();
    JsonNode serviceInputs = serviceNode.get(YamlTypes.SERVICE_INPUTS);
    if (NGExpressionUtils.isRuntimeOrExpressionField(serviceRef)) {
      if (serviceInputs.isObject()
          || (serviceInputs.isValueNode() && !NGExpressionUtils.matchesInputSetPattern(serviceInputs.asText()))) {
        errorNodeSummary.setValid(false);
        return;
      }
    }
    ServiceEntity serviceEntity = getService(accountId, orgId, projectId, serviceRef, cacheMap);

    String serviceYaml = serviceEntity.fetchNonEmptyYaml();

    // TODO: call Template service to resolve artifact source templates. If inputs issue, add service as nodeError.

    YamlNode primaryArtifactRefNode = YamlNodeUtils.goToPathUsingFqn(
        entityNode, "serviceInputs.serviceDefinition.spec.artifacts.primary.primaryArtifactRef");
    String serviceRuntimeInputYaml = serviceEntityService.createServiceInputsYamlGivenPrimaryArtifactRef(
        serviceYaml, serviceRef, primaryArtifactRefNode == null ? null : primaryArtifactRefNode.asText());
    if (EmptyPredicate.isEmpty(serviceRuntimeInputYaml)) {
      if (serviceInputs != null) {
        errorNodeSummary.setValid(false);
      }
      return;
    }
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode serviceInputsNode = mapper.createObjectNode();
    serviceInputsNode.set(YamlTypes.SERVICE_INPUTS, serviceInputs);
    String linkedServiceInputsYaml = YamlPipelineUtils.writeYamlString(serviceInputsNode);
    if (!RuntimeInputsValidator.validateInputsAgainstSourceNode(linkedServiceInputsYaml, serviceRuntimeInputYaml)) {
      errorNodeSummary.setValid(false);
    }
  }

  private ServiceEntity getService(
      String accountId, String orgId, String projectId, String serviceRef, Map<String, PersistentEntity> cacheMap) {
    IdentifierRef serviceIdentifierRef = IdentifierRefHelper.getIdentifierRef(serviceRef, accountId, orgId, projectId);
    String uniqueServiceIdentifier =
        generateUniqueIdentifier(serviceIdentifierRef.getAccountIdentifier(), serviceIdentifierRef.getOrgIdentifier(),
            serviceIdentifierRef.getProjectIdentifier(), serviceIdentifierRef.getIdentifier(), EntityType.SERVICE);
    if (cacheMap.containsKey(uniqueServiceIdentifier)) {
      return (ServiceEntity) cacheMap.get(uniqueServiceIdentifier);
    }

    Optional<ServiceEntity> serviceEntity =
        serviceEntityService.get(serviceIdentifierRef.getAccountIdentifier(), serviceIdentifierRef.getOrgIdentifier(),
            serviceIdentifierRef.getProjectIdentifier(), serviceIdentifierRef.getIdentifier(), false);
    if (!serviceEntity.isPresent()) {
      throw new InvalidRequestException(String.format(
          "Service with identifier [%s] in project [%s], org [%s] not found", serviceIdentifierRef.getIdentifier(),
          serviceIdentifierRef.getProjectIdentifier(), serviceIdentifierRef.getOrgIdentifier()));
    }
    cacheMap.put(uniqueServiceIdentifier, serviceEntity.get());
    return serviceEntity.get();
  }

  private NodeErrorSummary createServiceErrorNode(
      YamlNode serviceNode, ServiceEntity serviceEntity, List<NodeErrorSummary> childrenErrorNodes) {
    // TODO: test this before it's usage.
    YamlNode parentNode = serviceNode.getParentNode();
    if (parentNode != null) {
      // this is stage node now.
      parentNode = parentNode.getParentNode();
    }
    return ServiceNodeErrorSummary.builder()
        .nodeInfo(NodeInfo.builder()
                      .identifier(parentNode != null ? parentNode.getIdentifier() : null)
                      .name(parentNode != null ? parentNode.getName() : null)
                      .localFqn(YamlUtils.getFullyQualifiedName(serviceNode))
                      .build())
        .serviceResponse(ServiceElementMapper.writeDTO(serviceEntity))
        .childrenErrorNodes(childrenErrorNodes)
        .build();
  }

  private String generateUniqueIdentifier(
      String accountId, String orgId, String projectId, String entityIdentifier, EntityType entityType) {
    List<String> fqnList = new LinkedList<>();
    fqnList.add(accountId);
    if (EmptyPredicate.isNotEmpty(orgId)) {
      fqnList.add(orgId);
    }
    if (EmptyPredicate.isNotEmpty(projectId)) {
      fqnList.add(projectId);
    }
    fqnList.add(entityIdentifier);
    fqnList.add(entityType.getYamlName());
    return EntityReferenceHelper.createFQN(fqnList);
  }
}
