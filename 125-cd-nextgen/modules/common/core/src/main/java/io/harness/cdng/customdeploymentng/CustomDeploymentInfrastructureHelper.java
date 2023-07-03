/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.customdeploymentng;

import static io.harness.authorization.AuthorizationServiceHeader.TEMPLATE_SERVICE;
import static io.harness.cdng.customDeployment.constants.CustomDeploymentConstants.INFRASTRUCTURE_DEFINITION;
import static io.harness.cdng.customDeployment.constants.CustomDeploymentConstants.STABLE_VERSION;
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
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.plancreator.customDeployment.StepTemplateRef;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomDeploymentInfrastructureHelper {
  @Inject TemplateResourceClient templateResourceClient;
  @Inject protected ExecutionSweepingOutputService executionSweepingOutputService;
  @Named(ConnectorModule.DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject InfrastructureEntityService infrastructureEntityService;
  @Inject FileStoreService fileStoreService;
  @JsonIgnore private final ObjectMapper jsonObjectMapper = new ObjectMapper();
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

  public boolean checkIfInfraIsObsolete(String infraYaml, String templateYaml, String accountId) {
    try {
      YamlConfig infraYamlConfig = checkIfValidInfraYaml(infraYaml, accountId);
      if (isNull(infraYamlConfig)) {
        // infrastructure yaml is invalid, so not obsolete
        return false;
      }
      YamlConfig templateYamlConfig = checkIfValidTemplateYaml(templateYaml, accountId);
      if (isNull(templateYamlConfig)) {
        // template yaml is invalid, so not obsolete
        return false;
      }

      Map<String, String> templateVariables = getTemplateVariables(templateYamlConfig);
      Map<String, String> infraVariables = getInfraVariables(infraYamlConfig);
      return variableListSizeNotSame(templateVariables, infraVariables)
          || variablesNameAndTypeNotSame(templateVariables, infraVariables);
    } catch (Exception e) {
      log.error("Error Encountered while validating infra for acc Id :{}: {}", accountId, e);
      throw new InvalidRequestException("Error Encountered while validating infra: " + e.getMessage());
    }
  }

  private boolean variablesNameAndTypeNotSame(
      Map<String, String> variablesInTemplate, Map<String, String> variablesInInfra) {
    for (Map.Entry<String, String> templateVariable : variablesInTemplate.entrySet()) {
      if (!variablesInInfra.containsKey(templateVariable.getKey())
          || !variablesInInfra.get(templateVariable.getKey()).equals(templateVariable.getValue())) {
        return true;
      }
    }
    return false;
  }

  private boolean variableListSizeNotSame(
      Map<String, String> variablesInTemplate, Map<String, String> variablesInInfra) {
    return variablesInTemplate.size() != variablesInInfra.size();
  }

  public boolean isNotValidInfrastructureYaml(InfrastructureEntity infraEntity) {
    StepTemplateRef stepTemplateRef =
        getStepTemplateRefFromInfraYaml(infraEntity.getYaml(), infraEntity.getAccountId());
    String templateYaml = getTemplateYaml(infraEntity.getAccountId(), infraEntity.getOrgIdentifier(),
        infraEntity.getProjectIdentifier(), stepTemplateRef.getTemplateRef(), stepTemplateRef.getVersionLabel());
    return checkIfInfraIsObsolete(infraEntity.getYaml(), templateYaml, infraEntity.getAccountId());
  }

  public StepTemplateRef getStepTemplateRefFromInfraYaml(String infrastructureYaml, String accountId) {
    YamlConfig yamlConfig = new YamlConfig(infrastructureYaml);
    JsonNode yamlMap = yamlConfig.getYamlMap();
    JsonNode infraDef = yamlMap.get(INFRASTRUCTURE_DEFINITION);
    try {
      if (isNull(infraDef)) {
        log.error("Infra definition is null in yaml for account id :{}", accountId);
        throw new InvalidRequestException("Infra definition is null in yaml");
      }
      JsonNode spec = infraDef.get("spec");
      if (isNull(spec)) {
        log.error("spec is null in yaml for account id :{}", accountId);
        throw new InvalidRequestException("Infra definition spec is null in yaml");
      }
      JsonNode customDeploymentRef = spec.get("customDeploymentRef");
      if (isNull(customDeploymentRef)) {
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

  public String getTemplateYaml(
      String accRef, String orgRef, String projectRef, String templateRef, String versionLabel) {
    TemplateResponseDTO response;
    if (STABLE_VERSION.equals(versionLabel)) {
      versionLabel = null;
    }
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

  public void validateInfra(Ambiance ambiance, CustomDeploymentInfrastructure infrastructure) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    EnvironmentOutcome environmentOutcome = (EnvironmentOutcome) executionSweepingOutputService.resolve(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.ENVIRONMENT));

    Optional<InfrastructureEntity> infraEntity = infrastructureEntityService.get(accountIdentifier, orgIdentifier,
        projectIdentifier, environmentOutcome.getIdentifier(), infrastructure.getInfraIdentifier());
    if (infraEntity.isPresent()) {
      if (infraEntity.get().getObsolete()) {
        throw new InvalidRequestException(String.format(
            "Infrastructure - [%s] is obsolete as Deployment Template with Id: [%s] has been updated, please reconcile the infrastructure",
            infrastructure.getInfraName(), infrastructure.getCustomDeploymentRef().getTemplateRef()));
      }
    } else {
      throw new InvalidRequestException(
          String.format("Infra does not exist for this infra id - [%s], and env id - [%s], infra - [%s]",
              infrastructure.getInfraIdentifier(), environmentOutcome.getIdentifier(), infrastructure.getInfraName()));
    }
  }

  private Map<String, String> getTemplateVariables(YamlConfig templateYamlConfig) {
    JsonNode templateVariableNode = templateYamlConfig.getYamlMap()
                                        .get("template")
                                        .get("spec")
                                        .get("infrastructure")
                                        .get(YAMLFieldNameConstants.VARIABLES);
    Map<String, String> variables = new HashMap<>();
    if (isNull(templateVariableNode) || isEmpty(templateVariableNode.toString())) {
      return variables;
    }
    for (JsonNode variable : templateVariableNode) {
      variables.put(variable.get("name").asText(), variable.get("type").asText());
    }
    return variables;
  }

  private Map<String, String> getInfraVariables(YamlConfig infraYamlConfig) {
    JsonNode variablesNode =
        infraYamlConfig.getYamlMap().get(INFRASTRUCTURE_DEFINITION).get("spec").get(YAMLFieldNameConstants.VARIABLES);
    Map<String, String> variables = new HashMap<>();
    if (isNull(variablesNode) || isEmpty(variablesNode.toString())) {
      return variables;
    }
    for (JsonNode variable : variablesNode) {
      variables.put(variable.get("name").asText(), variable.get("type").asText());
    }
    return variables;
  }

  private YamlConfig checkIfValidInfraYaml(String infraYaml, String accountId) {
    if (isNull(infraYaml)) {
      return null;
    }
    YamlConfig yamlConfig = new YamlConfig(infraYaml);
    JsonNode yamlMap = yamlConfig.getYamlMap();
    JsonNode infraDef = yamlMap.get(INFRASTRUCTURE_DEFINITION);
    try {
      if (isNull(infraDef)) {
        log.error(
            "Error encountered while validating infra, Infra definition is null in yaml for account id :{}", accountId);
        return null;
      }
      JsonNode spec = infraDef.get("spec");
      if (isNull(spec)) {
        log.error("Error encountered while validating infra, Infra spec is null in yaml for account id :{}", accountId);
        return null;
      }
      JsonNode infraVariables = spec.get(YAMLFieldNameConstants.VARIABLES);
      if (isNull(infraVariables) || isEmpty(infraVariables.toString())) {
        return yamlConfig;
      }
      for (JsonNode variable : infraVariables) {
        if (isNull(variable) || variable.get("name").isNull() || variable.get("type").isNull()) {
          log.error("Error encountered while validating infra, Infra variable: {} is invalid for account id: {}",
              variable, accountId);
          return null;
        }
      }
      return yamlConfig;
    } catch (Exception e) {
      log.error("Error occurred while validating infrastructure yaml", e);
      throw new InvalidRequestException("Error occurred while validating infrastructure yaml " + e.getMessage());
    }
  }

  private YamlConfig checkIfValidTemplateYaml(String templateYaml, String accountId) {
    if (isNull(templateYaml)) {
      return null;
    }
    YamlConfig templateConfig = new YamlConfig(templateYaml);
    JsonNode templateNode = templateConfig.getYamlMap().get("template");
    try {
      if (isNull(templateNode)) {
        log.error("Error encountered while validating infra, template node is null for accId :{}", accountId);
        return null;
      }
      JsonNode templateSpecNode = templateNode.get("spec");
      if (isNull(templateSpecNode)) {
        log.error("Error encountered while validating infra, template spec node is null for accId :{}", accountId);
        return null;
      }
      JsonNode templateInfraNode = templateSpecNode.get("infrastructure");
      if (isNull(templateInfraNode)) {
        log.error(
            "Error encountered while validating infra, template infrastructure node is null for accId :{}", accountId);
        return null;
      }
      JsonNode templateVariableNode = templateInfraNode.get("variables");
      if (isNull(templateVariableNode) || isEmpty(templateVariableNode.toString())) {
        return templateConfig;
      }
      for (JsonNode variable : templateVariableNode) {
        if (isNull(variable) || variable.get("name").isNull() || variable.get("type").isNull()) {
          log.error("Error encountered while validating infra, Template variable: {} is invalid for account id: {}",
              variable, accountId);
          return null;
        }
      }
      return templateConfig;
    } catch (Exception e) {
      log.error("Error occurred while validating template yaml", e);
      throw new InvalidRequestException("Error occurred while validating template yaml " + e.getMessage());
    }
  }
}
