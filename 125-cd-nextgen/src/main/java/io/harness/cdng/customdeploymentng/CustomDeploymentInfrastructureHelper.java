/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.customdeploymentng;

import static io.harness.AuthorizationServiceHeader.TEMPLATE_SERVICE;
import static io.harness.ccm.anomaly.graphql.AnomaliesFilter.log;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.beans.FileReference;
import io.harness.cdng.customdeployment.CustomDeploymentConnectorNGVariable;
import io.harness.cdng.customdeployment.CustomDeploymentNGVariable;
import io.harness.cdng.customdeployment.CustomDeploymentSecretNGVariable;
import io.harness.cdng.infra.yaml.CustomDeploymentInfrastructure;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorModule;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CustomDeploymentInfrastructureHelper {
  @Inject TemplateResourceClient templateResourceClient;
  @Named(ConnectorModule.DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject FileStoreService fileStoreService;
  private static final String ACCOUNT_IDENTIFIER = "account.";
  private static final String ORG_IDENTIFIER = "org.";

  public Map<String, Object> convertListVariablesToMap(
      List<CustomDeploymentNGVariable> variables, String accRef, String orgRef, String projectRef) {
    Map<String, Object> mapOfVariables = new HashMap<>();
    if (!isNull(variables)) {
      for (CustomDeploymentNGVariable variable : variables) {
        if (variable instanceof CustomDeploymentConnectorNGVariable) {
          CustomDeploymentConnectorNGVariable connectorNGVariable = (CustomDeploymentConnectorNGVariable) variable;
          if (!isNull(connectorNGVariable.getValue().getValue())) {
            Optional<ConnectorResponseDTO> response =
                getConnectorRefByScope(accRef, orgRef, projectRef, connectorNGVariable.getValue().getValue());
            if (!response.isPresent()) {
              log.error("connector not found for connector ref :{}, for acc ref :{}",
                  connectorNGVariable.getValue().getValue(), accRef);
              throw new InvalidRequestException(format(
                  "Connector not found for given connector ref :[%s]", connectorNGVariable.getValue().getValue()));
            }
            ConnectorResponseDTO connectorResponseDTO = response.get();
            ConnectorInfoDTO connectorInfoDTO = connectorResponseDTO.getConnector();
            mapOfVariables.put(connectorNGVariable.getName(), connectorInfoDTO.toOutcome());
          } else if (!isNull(connectorNGVariable.getValue().getExpressionValue())) {
            mapOfVariables.put(connectorNGVariable.getName(), connectorNGVariable.getValue().getExpressionValue());
          }
        } else if (variable instanceof CustomDeploymentSecretNGVariable) {
          CustomDeploymentSecretNGVariable secretNGVariable = (CustomDeploymentSecretNGVariable) variable;
          if (!isNull(secretNGVariable.getValue().getValue())) {
            String secretIdentifier = secretNGVariable.getValue().getValue().getIdentifier();
            Scope scope = secretNGVariable.getValue().getValue().getScope();
            String prefix = "";
            if (scope != Scope.PROJECT) {
              prefix = (scope == Scope.ACCOUNT) ? ACCOUNT_IDENTIFIER : ORG_IDENTIFIER;
            }
            String expression = NGVariablesUtils.fetchSecretExpression(prefix + secretIdentifier);
            mapOfVariables.put(secretNGVariable.getName(), expression);
          } else if (!isNull(secretNGVariable.getValue().getExpressionValue())) {
            mapOfVariables.put(secretNGVariable.getName(), secretNGVariable.getValue().getExpressionValue());
          }
        } else {
          mapOfVariables.put(variable.getName(), variable.getCurrentValue().getValue());
        }
      }
    }
    return mapOfVariables;
  }

  public String getTemplateYaml(
      String accRef, String orgRef, String projectRef, String templateRef, String versionLabel) {
    TemplateResponseDTO response;
    SecurityContextBuilder.setContext(new ServicePrincipal(TEMPLATE_SERVICE.getServiceId()));
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
    return response.getYaml();
  }

  public Optional<ConnectorResponseDTO> getConnectorRefByScope(
      String accRef, String orgRef, String projectRef, String ref) {
    Optional<ConnectorResponseDTO> response;
    if (ref.contains(ACCOUNT_IDENTIFIER)) {
      response = connectorService.get(accRef, null, null, ref.replace(ACCOUNT_IDENTIFIER, ""));
    } else if (ref.contains(ORG_IDENTIFIER)) {
      response = connectorService.get(accRef, orgRef, null, ref.replace(ORG_IDENTIFIER, ""));
    } else {
      response = connectorService.get(accRef, orgRef, projectRef, ref);
    }
    return response;
  }

  public String getScript(String yaml, String accRef, String orgRef, String projectRef) {
    try {
      JsonNode templateInfra = getInfra(yaml);
      JsonNode scriptNode = templateInfra.get("fetchInstancesScript");
      if (isNull(scriptNode) || isEmpty(scriptNode.toString())) {
        log.error("No fetchInstance Script in Yaml for DT in acc :{}", accRef);
        throw new InvalidRequestException("Template yaml provided does not have script in it.");
      }
      JsonNode store = scriptNode.get("store");
      if (store.get("type").asText().equals("Inline")) {
        return store.get("spec").get("content").asText();
      } else {
        String path = store.get("spec").get("files").get(0).asText();
        FileReference fileReference = FileReference.of(path, accRef, orgRef, projectRef);
        Optional<FileStoreNodeDTO> fileStoreNodeDTOOptional = fileStoreService.getWithChildrenByPath(accRef,
            fileReference.getOrgIdentifier(), fileReference.getProjectIdentifier(), fileReference.getPath(), true);
        if (!fileStoreNodeDTOOptional.isPresent()) {
          log.error(
              "fetch instance script file path is invalid for this deployment template for account id :{}", accRef);
          throw new InvalidRequestException("fetch instance script file path is invalid");
        }
        FileStoreNodeDTO fileStoreNodeDTO = fileStoreNodeDTOOptional.get();
        FileNodeDTO fileNodeDTO = (FileNodeDTO) fileStoreNodeDTO;
        return fileNodeDTO.getContent();
      }
    } catch (Exception e) {
      log.error("Error occurred while fetching script for custom deployment " + e);
      throw new InvalidRequestException("Error occurred while fetching script for custom deployment ", e);
    }
  }

  public String getInstancePath(String yaml, String accRef) {
    JsonNode templateInfra = getInfra(yaml);
    JsonNode instancePath = templateInfra.get("instancesListPath");
    if (isNull(instancePath) || isEmpty(instancePath.toString())) {
      log.error("No instance path in Yaml for DT in acc :{}", accRef);
      throw new InvalidRequestException("Template yaml provided does not have instancePath in it.");
    }
    return instancePath.asText();
  }

  public Map<String, String> getInstanceAttributes(String yaml, String accRef) {
    JsonNode templateInfra = getInfra(yaml);
    JsonNode instanceAttributes = templateInfra.get("instanceAttributes");
    if (isNull(instanceAttributes) || isEmpty(instanceAttributes.toString())) {
      log.error("No instance attributes in Yaml for DT in acc :{}", accRef);
      throw new InvalidRequestException("Template yaml provided does not have attributes in it.");
    }
    Map<String, String> attributes = new HashMap<>();
    for (JsonNode attribute : instanceAttributes) {
      attributes.put(attribute.get("name").asText(), attribute.get("jsonPath").asText());
    }
    return attributes;
  }

  public ObjectNode getInfra(String yaml) {
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

  public void validateInfra(Ambiance ambiance, CustomDeploymentInfrastructure infrastructure) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    String templateYaml = getTemplateYaml(accountIdentifier, orgIdentifier, projectIdentifier,
        infrastructure.getCustomDeploymentRef().getTemplateRef(),
        infrastructure.getCustomDeploymentRef().getVersionLabel());
    List<CustomDeploymentNGVariable> variablesInInfra = infrastructure.getVariables();
    if (templateYaml.isEmpty()) {
      log.error("Template does not exist for this infrastructure for acc ID:{}", accountIdentifier);
      throw new InvalidRequestException("Template does not exist for this infrastructure");
    }
    Map<String, String> variablesInTemplate = getTemplateVariables(templateYaml, accountIdentifier);
    boolean isObsolete = variableListSizeNotSame(variablesInTemplate, variablesInInfra)
        || variablesNameNotSame(variablesInTemplate, variablesInInfra);
    if (isObsolete) {
      throw new InvalidRequestException("Infrastructure is obsolete, please update the infrastructure");
    }
  }

  private boolean variablesNameNotSame(
      Map<String, String> variablesInTemplate, List<CustomDeploymentNGVariable> variablesInInfra) {
    for (CustomDeploymentNGVariable variable : variablesInInfra) {
      if (!variablesInTemplate.containsKey(variable.getName())) {
        return true;
      }
    }
    return false;
  }

  private boolean variableListSizeNotSame(
      Map<String, String> variablesInTemplate, List<CustomDeploymentNGVariable> variablesInInfra) {
    return variablesInTemplate.size() != variablesInInfra.size();
  }

  private Map<String, String> getTemplateVariables(String templateYaml, String accId) {
    YamlConfig templateConfig = new YamlConfig(templateYaml);
    JsonNode templateNode = templateConfig.getYamlMap().get("template");
    if (templateNode.isNull()) {
      log.info("Error encountered while validating infra, template node is null for accId :{}", accId);
      throw new InvalidRequestException("template yaml cannot be empty");
    }
    JsonNode templateSpecNode = templateNode.get("spec");
    if (templateSpecNode.isNull()) {
      log.info("Error encountered while validating infra, template spec node is null for accId :{}", accId);
      throw new InvalidRequestException("template yaml spec cannot be empty");
    }
    JsonNode templateInfraNode = templateSpecNode.get("infrastructure");
    if (templateInfraNode.isNull()) {
      log.info("Error encountered while validating infra, template infrastructure node is null for accId :{}", accId);
      throw new InvalidRequestException("template yaml infra cannot be empty");
    }
    Map<String, String> mapOfVariables = new HashMap<>();
    JsonNode templateVariableNode = templateInfraNode.get("variables");
    for (JsonNode variable : templateVariableNode) {
      mapOfVariables.put(variable.get("name").asText(), variable.get("value").asText());
    }
    return mapOfVariables;
  }
}
