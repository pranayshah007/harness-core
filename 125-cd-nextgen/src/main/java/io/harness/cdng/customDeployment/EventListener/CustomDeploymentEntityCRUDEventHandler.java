/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.customDeployment.EventListener;

import static software.wings.beans.AccountType.log;

import static java.util.Objects.isNull;

import io.harness.EntityType;
import io.harness.beans.IdentifierRef;
import io.harness.beans.Scope;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.pms.merger.YamlConfig;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.beans.TemplateResponseDTO;
import io.harness.template.remote.TemplateResourceClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.mongodb.core.query.Update;

public class CustomDeploymentEntityCRUDEventHandler {
  @Inject EntitySetupUsageService entitySetupUsageService;
  @Inject InfrastructureEntityService infrastructureEntityService;
  @Inject TemplateResourceClient templateResourceClient;
  public boolean updateInfraAsObsolete(
      String accountRef, String orgRef, String projectRef, String identifier, String versionLabel) {
    Scope scope =
        Scope.builder().accountIdentifier(accountRef).orgIdentifier(orgRef).projectIdentifier(projectRef).build();
    String entityFQN = getFullyQualifiedIdentifier(accountRef, orgRef, projectRef, identifier) + "/";
    if (versionLabel != null) {
      entityFQN = entityFQN + versionLabel + "/";
    }
    List<EntitySetupUsageDTO> entitySetupUsages = entitySetupUsageService.listAllEntityUsagePerReferredEntityScope(
        scope, entityFQN, EntityType.TEMPLATE, EntityType.INFRASTRUCTURE, null, null);
    if (entitySetupUsages.isEmpty()) {
      log.info("No infra Update for  Deployment Template with id :{}, for AccountId : {}", identifier, accountRef);
    }
    Map<String, List<String>> envMap = new HashMap<>();
    List<String> infraIdsList = new ArrayList();
    Map<String, List<String>> envToInfraMap = new HashMap<>();

    for (EntitySetupUsageDTO entitySetupUsage : entitySetupUsages) {
      String infraId = entitySetupUsage.getReferredByEntity().getEntityRef().getIdentifier();
      String environment =
          ((IdentifierRef) entitySetupUsage.getReferredByEntity().getEntityRef()).getMetadata().get("envId");
      String orgIdentifierEnv = entitySetupUsage.getReferredByEntity().getEntityRef().getOrgIdentifier();
      String projectIdentifierEnv = entitySetupUsage.getReferredByEntity().getEntityRef().getProjectIdentifier();
      infraIdsList.add(infraId);
      envMap.put(environment, Arrays.asList(orgIdentifierEnv, projectIdentifierEnv));
      envToInfraMap.computeIfAbsent(environment, k -> new ArrayList<>()).add(infraId);
    }
    if (infraIdsList.isEmpty() || entitySetupUsages.isEmpty()) {
      log.info("No infra found  in Db for given Deployment Template with id: {}", identifier);
      return false;
    }
    String infraYaml = getInfraYaml(entitySetupUsages.get(0), accountRef);
    String templateYaml = getTemplateYaml(accountRef, orgRef, projectRef, identifier, versionLabel);
    boolean updateRequired = checkIfUpdateRequired(infraYaml, templateYaml, accountRef);
    if (updateRequired) {
      updateInfrasAsObsolete(envToInfraMap, accountRef, envMap);
    }
    return true;
  }

  public String getInfraYaml(EntitySetupUsageDTO entitySetupUsage, String accRef) {
    String infraId = entitySetupUsage.getReferredByEntity().getEntityRef().getIdentifier();
    String orgId = entitySetupUsage.getReferredByEntity().getEntityRef().getOrgIdentifier();
    String projectId = entitySetupUsage.getReferredByEntity().getEntityRef().getProjectIdentifier();
    String environment =
        ((IdentifierRef) entitySetupUsage.getReferredByEntity().getEntityRef()).getMetadata().get("envId");
    Optional<InfrastructureEntity> infrastructureOptional =
        infrastructureEntityService.get(entitySetupUsage.getReferredByEntity().getEntityRef().getAccountIdentifier(),
            orgId, projectId, environment, infraId);
    if (!infrastructureOptional.isPresent()) {
      log.error("No infra found to update for given deployment template with acc Id:{}", accRef);
      return null;
    }
    InfrastructureEntity infrastructure = infrastructureOptional.get();
    return infrastructure.getYaml();
  }
  public String getTemplateYaml(
      String accountRef, String orgRef, String projectRef, String identifier, String versionLabel) {
    TemplateResponseDTO response = NGRestUtils.getResponse(
        templateResourceClient.get(identifier, accountRef, orgRef, projectRef, versionLabel, false));
    return response.getYaml();
  }
  public boolean checkIfUpdateRequired(String infraYaml, String templateYaml, String accId) {
    try {
      YamlConfig templateConfig = new YamlConfig(templateYaml);
      JsonNode templateNode = templateConfig.getYamlMap().get("template");
      if (isNull(templateNode)) {
        log.info("Error encountered while updating infra, template node is null for accId :{}", accId);
        return false;
      }
      JsonNode templateSpecNode = templateNode.get("spec");
      if (isNull(templateSpecNode)) {
        log.info("Error encountered while updating infra, template spec node is null for accId :{}", accId);
        return false;
      }
      JsonNode templateInfraNode = templateSpecNode.get("infrastructure");
      if (isNull(templateInfraNode)) {
        log.info("Error encountered while updating infra, template infrastructure node is null for accId :{}", accId);
        return false;
      }
      JsonNode templateVariableNode = templateInfraNode.get("variables");

      YamlConfig infraYamlConfig = new YamlConfig(infraYaml);
      JsonNode infraNode = infraYamlConfig.getYamlMap().get("infrastructureDefinition");
      if (isNull(infraNode)) {
        log.info("Error encountered while updating infra, infra node is null for accId :{}", accId);
        return false;
      }
      JsonNode infraSpecNode = infraNode.get("spec");
      if (isNull(infraSpecNode)) {
        log.info("Error encountered while updating infra, infra spec node is null for accId :{}", accId);
        return false;
      }

      JsonNode infraVariableNode = infraSpecNode.get("variables");
      Map<String, JsonNode> templateVariables = new HashMap<>();
      Map<String, JsonNode> infraVariables = new HashMap<>();
      boolean isObsolete = false;
      for (JsonNode variable : templateVariableNode) {
        templateVariables.put(variable.get("name").asText(), variable);
      }
      for (JsonNode variable : infraVariableNode) {
        if (!templateVariables.containsKey(variable.get("name").asText())) {
          isObsolete = true;
        } else if (!templateVariables.get(variable.get("name").asText())
                        .get("type")
                        .asText()
                        .equals(variable.get("type").asText())) {
          isObsolete = true;
        }
        infraVariables.put(variable.get("name").asText(), variable);
      }
      for (JsonNode variable : templateVariableNode) {
        if (!infraVariables.containsKey(variable.get("name").asText())) {
          isObsolete = true;
        }
      }
      return isObsolete;
    } catch (Exception e) {
      log.error(
          "Error Encountered in infra updation while reading yamls for template ans Infra for acc Id :{} ", accId);
      throw new InvalidRequestException(
          "Error Encountered in infra updation while reading yamls for template ans Infra");
    }
  }

  public void updateInfrasAsObsolete(
      Map<String, List<String>> envToInfraMap, String accountIdentifier, Map<String, List<String>> envMap) {
    for (String environment : envToInfraMap.keySet()) {
      updateInfras(envToInfraMap.get(environment), environment, accountIdentifier, envMap);
    }
  }
  public void updateInfras(
      List<String> Infras, String environment, String accountId, Map<String, List<String>> envMap) {
    String orgId = envMap.get(environment).get(0);
    String projectId = envMap.get(environment).get(1);
    Update update = new Update();
    update.set(InfrastructureEntityKeys.obsolete, true);
    UpdateResult updateResult =
        infrastructureEntityService.batchUpdateInfrastructure(accountId, orgId, projectId, environment, Infras, update);
    log.info("Infras updated successfully for accRef :{}, Environment :{} with updated result :{}", accountId,
        environment, updateResult);
  }
  public String getFullyQualifiedIdentifier(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier) {
    validateIdentifier(identifier);
    if (EmptyPredicate.isNotEmpty(projectIdentifier)) {
      validateOrgIdentifier(orgIdentifier);
      validateAccountIdentifier(accountId);
      return String.format("%s/%s/%s/%s", accountId, orgIdentifier, projectIdentifier, identifier);
    } else if (EmptyPredicate.isNotEmpty(orgIdentifier)) {
      validateAccountIdentifier(accountId);
      return String.format("%s/%s/%s", accountId, orgIdentifier, identifier);
    } else if (EmptyPredicate.isNotEmpty(accountId)) {
      return String.format("%s/%s", accountId, identifier);
    }
    throw new InvalidRequestException("No account ID provided.");
  }
  private void validateIdentifier(String identifier) {
    if (EmptyPredicate.isEmpty(identifier)) {
      throw new InvalidRequestException("No identifier provided.");
    }
  }
  private void validateAccountIdentifier(String accountIdentifier) {
    if (EmptyPredicate.isEmpty(accountIdentifier)) {
      throw new InvalidRequestException("No account identifier provided.");
    }
  }

  private void validateOrgIdentifier(String orgIdentifier) {
    if (EmptyPredicate.isEmpty(orgIdentifier)) {
      throw new InvalidRequestException("No org identifier provided.");
    }
  }
}
