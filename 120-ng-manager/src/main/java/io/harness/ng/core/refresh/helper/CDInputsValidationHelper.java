/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.refresh.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.template.resources.beans.NGTemplateConstants.GIT_BRANCH;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.gitx.GitXTransientBranchGuard;
import io.harness.ng.core.refresh.bean.EntityRefreshContext;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.template.refresh.v2.InputsValidationResponse;
import io.harness.persistence.PersistentEntity;
import io.harness.pms.merger.helpers.RuntimeInputsValidator;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class CDInputsValidationHelper {
  private static final List<String> keysToIgnoreInNodeToValidate =
      Collections.singletonList("artifacts.primary.sources");
  @Inject ServiceEntityService serviceEntityService;
  @Inject EntityFetchHelper entityFetchHelper;
  @Inject EnvironmentRefreshHelper environmentRefreshHelper;
  public InputsValidationResponse validateInputsForYaml(
      String accountId, String orgId, String projectId, String yaml, String resolvedTemplatesYaml) {
    return validateInputsForYaml(accountId, orgId, projectId, yaml, resolvedTemplatesYaml, new HashMap<>());
  }

  private InputsValidationResponse validateInputsForYaml(String accountId, String orgId, String projectId, String yaml,
      String resolvedTemplatesYaml, Map<String, PersistentEntity> cacheMap) {
    YamlNode yamlNode = validateAndGetYamlNode(yaml);
    YamlNode resolvedTemplatesYamlNode = isEmpty(resolvedTemplatesYaml) ? null : getYamlNode(resolvedTemplatesYaml);
    InputsValidationResponse inputsValidationResponse =
        InputsValidationResponse.builder().isValid(true).childrenErrorNodes(new ArrayList<>()).build();
    EntityRefreshContext entityRefreshContext = EntityRefreshContext.builder()
                                                    .accountId(accountId)
                                                    .orgId(orgId)
                                                    .projectId(projectId)
                                                    .resolvedTemplatesYamlNode(resolvedTemplatesYamlNode)
                                                    .cacheMap(cacheMap)
                                                    .build();
    if (yamlNode.isObject()) {
      validateInputsInObject(yamlNode, entityRefreshContext, inputsValidationResponse);
    } else if (yamlNode.isArray()) {
      validateInputsInArray(yamlNode, entityRefreshContext, inputsValidationResponse);
    }
    return inputsValidationResponse;
  }

  private YamlNode validateAndGetYamlNode(String yaml) {
    // Case -> empty YAML, cannot validate
    if (isEmpty(yaml)) {
      throw new InvalidRequestException("Yaml to be validated cannot be empty.");
    }
    return getYamlNode(yaml);
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

  private void validateInputsInObject(
      YamlNode yamlNode, EntityRefreshContext context, InputsValidationResponse inputsValidationResponse) {
    for (YamlField childYamlField : yamlNode.fields()) {
      String fieldName = childYamlField.getName();
      YamlNode currentYamlNode = childYamlField.getNode();
      JsonNode value = currentYamlNode.getCurrJsonNode();

      // If Service is present, validate the Service Inputs
      if (serviceEntityService.isServiceField(fieldName, value)) {
        validateServiceInputs(currentYamlNode, context, inputsValidationResponse);
        continue;
      } else if (inputsValidationResponse.isValid() && environmentRefreshHelper.isEnvironmentField(fieldName, value)) {
        environmentRefreshHelper.validateEnvironmentInputs(currentYamlNode, context, inputsValidationResponse);
        continue;
      }

      if (value.isArray() && !YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(value)) {
        // Value -> Array
        validateInputsInArray(childYamlField.getNode(), context, inputsValidationResponse);
      } else if (value.isObject()) {
        // Value -> Object
        validateInputsInObject(childYamlField.getNode(), context, inputsValidationResponse);
      }
    }
  }

  private void validateInputsInArray(
      YamlNode yamlNode, EntityRefreshContext context, InputsValidationResponse childrenNodeErrorSummary) {
    // Iterate over the array
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (arrayElement.isArray()) {
        // Value -> Array
        validateInputsInArray(arrayElement, context, childrenNodeErrorSummary);
      } else if (arrayElement.isObject()) {
        // Value -> Object
        validateInputsInObject(arrayElement, context, childrenNodeErrorSummary);
      }
    }
  }

  void validateServiceInputs(
      YamlNode entityNode, EntityRefreshContext context, InputsValidationResponse errorNodeSummary) {
    JsonNode serviceNode = entityNode.getCurrJsonNode();
    String serviceRef =
        serviceNode.get(YamlTypes.SERVICE_REF) != null ? serviceNode.get(YamlTypes.SERVICE_REF).asText() : null;
    JsonNode serviceInputs = serviceNode.get(YamlTypes.SERVICE_INPUTS);
    if (serviceNode.get(YamlTypes.SERVICE_REF) == null) {
      if (serviceInputs != null) {
        errorNodeSummary.setValid(false);
      }
      return;
    }

    if (NGExpressionUtils.isRuntimeField(serviceRef)) {
      if (serviceInputs != null) {
        if (serviceInputs.isObject()
            || (serviceInputs.isValueNode() && !NGExpressionUtils.matchesInputSetPattern(serviceInputs.asText()))) {
          errorNodeSummary.setValid(false);
        }
      }
      return;
    }

    // if serviceRef is expression, we cannot validate service inputs. We will allow user to save any input because
    // there is no way as of now, to provide service inputs in this case
    if (NGExpressionUtils.isExpressionField(serviceRef)) {
      return;
    }

    String gitBranch = serviceNode.get(GIT_BRANCH) != null ? serviceNode.get(GIT_BRANCH).asText() : null;

    // fetch without cache since we're explicitly validating service YAML
    ServiceEntity serviceEntity;
    try (GitXTransientBranchGuard ignore = new GitXTransientBranchGuard(gitBranch)) {
      serviceEntity = entityFetchHelper.getService(
          context.getAccountId(), context.getOrgId(), context.getProjectId(), serviceRef, context.getCacheMap());
    }

    String serviceYaml = serviceEntity.fetchNonEmptyYaml();

    // TODO: call Template service to resolve artifact source templates. If inputs issue, add service as nodeError.

    YamlNode primaryArtifactRefNode = YamlNodeUtils.goToPathUsingFqn(
        entityNode, "serviceInputs.serviceDefinition.spec.artifacts.primary.primaryArtifactRef");

    String primaryArtifactRefNodeValue = primaryArtifactRefNode == null ? null : primaryArtifactRefNode.asText();
    String serviceRuntimeInputYaml = serviceEntityService.createServiceInputsYamlGivenPrimaryArtifactRef(
        serviceYaml, serviceRef, primaryArtifactRefNodeValue);

    YamlNode primaryArtifactRefNodeInServiceEntity = YamlNodeUtils.goToPathUsingFqn(
        validateAndGetYamlNode(serviceYaml), "service.serviceDefinition.spec.artifacts.primary.primaryArtifactRef");
    String primaryArtifactRefNodeValueInServiceEntity =
        primaryArtifactRefNodeInServiceEntity == null ? null : primaryArtifactRefNodeInServiceEntity.asText();

    // in case the primary artifact field is strictly expression in service entity
    // and also in pipeline yaml, and the expression match, then don't check for service inputs validity
    // and validate manual service inputs given by the user
    if (EmptyPredicate.isNotEmpty(primaryArtifactRefNodeValueInServiceEntity)
        && NGExpressionUtils.isStrictlyExpressionField(primaryArtifactRefNodeValueInServiceEntity)
        && EmptyPredicate.isNotEmpty(primaryArtifactRefNodeValue)
        && NGExpressionUtils.isStrictlyExpressionField(primaryArtifactRefNodeValue)
        && primaryArtifactRefNodeValueInServiceEntity.equals(primaryArtifactRefNodeValue)) {
      return;
    }

    // in case the primary artifact field is runtime input in service entity
    // but strictly expression in pipeline yaml, then don't check for service inputs validity
    // and validate manual service inputs given by the user
    if (EmptyPredicate.isNotEmpty(primaryArtifactRefNodeValueInServiceEntity)
        && NGExpressionUtils.isRuntimeField(primaryArtifactRefNodeValueInServiceEntity)
        && EmptyPredicate.isNotEmpty(primaryArtifactRefNodeValue)
        && NGExpressionUtils.isStrictlyExpressionField(primaryArtifactRefNodeValue)) {
      return;
    }

    if (EmptyPredicate.isEmpty(serviceRuntimeInputYaml)) {
      if (EnvironmentRefreshHelper.isNodeNotNullAndNotHaveRuntimeValue(serviceInputs)) {
        errorNodeSummary.setValid(false);
      }
      return;
    }

    ObjectMapper mapper = new ObjectMapper();
    ObjectNode serviceInputsNode = mapper.createObjectNode();
    serviceInputsNode.set(YamlTypes.SERVICE_INPUTS, serviceInputs);
    String linkedServiceInputsYaml = YamlUtils.writeYamlString(serviceInputsNode);
    if (!RuntimeInputsValidator.validateInputsAgainstSourceNode(linkedServiceInputsYaml, serviceRuntimeInputYaml,
            new HashSet<>(), new HashSet<>(keysToIgnoreInNodeToValidate))) {
      errorNodeSummary.setValid(false);
    }
  }
}
