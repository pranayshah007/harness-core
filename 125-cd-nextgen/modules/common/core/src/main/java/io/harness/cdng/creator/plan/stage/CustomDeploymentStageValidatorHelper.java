/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage;

import static io.harness.authorization.AuthorizationServiceHeader.TEMPLATE_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.executions.steps.StepSpecTypeConstants.CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT;
import static io.harness.executions.steps.StepSpecTypeConstants.DEPLOYMENT_STAGE;
import static io.harness.ng.core.template.TemplateListType.STABLE_TEMPLATE_TYPE;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateMetadataSummaryResponseDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.resources.beans.TemplateFilterPropertiesDTO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_DEPLOYMENT_TEMPLATES})
@OwnedBy(HarnessTeam.CDP)
public class CustomDeploymentStageValidatorHelper implements StageValidatorHelper {
  @Inject TemplateResourceClient templateResourceClient;
  private static final String ACCOUNT_IDENTIFIER = "account.";
  private static final String ORG_IDENTIFIER = "org.";

  @Override
  public void validate(Object object, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    DeploymentStageConfig stageConfig = (DeploymentStageConfig) object;
    ExecutionElementConfig executionElementConfig = stageConfig.getExecution();
    Map<String, Integer> stepTypeToCount = new HashMap<>();
    Map<Scope, List<String>> stepTemplateScopeToIds = new EnumMap<>(Scope.class);
    SecurityContextBuilder.setContext(new ServicePrincipal(TEMPLATE_SERVICE.getServiceId()));
    getCountByStepType(accountIdentifier, orgIdentifier, projectIdentifier, executionElementConfig.getSteps(),
        stepTypeToCount, stepTemplateScopeToIds);
    getTemplateResponseDTO(
        accountIdentifier, orgIdentifier, projectIdentifier, stepTypeToCount, stepTemplateScopeToIds);
    int fetchInstanceStepCount = stepTypeToCount.getOrDefault(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT, 0);
    if (fetchInstanceStepCount != 1) {
      throw new InvalidYamlException(
          "Fetch instance script step should be present only 1 time found: " + fetchInstanceStepCount);
    }
    Map<String, Integer> rollbackStepTypeToCount = new HashMap<>();
    Map<Scope, List<String>> rollbackStepTemplateScopeToIds = new EnumMap<>(Scope.class);
    getCountByStepType(accountIdentifier, orgIdentifier, projectIdentifier, executionElementConfig.getRollbackSteps(),
        rollbackStepTypeToCount, rollbackStepTemplateScopeToIds);
    getTemplateResponseDTO(
        accountIdentifier, orgIdentifier, projectIdentifier, rollbackStepTypeToCount, rollbackStepTemplateScopeToIds);
    int rollbackFetchInstanceStepCount =
        rollbackStepTypeToCount.getOrDefault(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT, 0);
    if (rollbackFetchInstanceStepCount > 1) {
      throw new InvalidYamlException("Rollback: Fetch instance script step should be present at max 1 time, found: "
          + rollbackFetchInstanceStepCount);
    }
  }

  private void getCountByStepType(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      List<ExecutionWrapperConfig> steps, Map<String, Integer> stepTypeToCount,
      Map<Scope, List<String>> templateScopeToIds) {
    if (isNull(steps)) {
      return;
    }
    for (ExecutionWrapperConfig wrapperConfig : steps) {
      try {
        if (!isNull(wrapperConfig.getStep())) {
          if (wrapperConfig.getStep().has("type")) {
            String stepType = wrapperConfig.getStep().get("type").asText();
            Integer count = stepTypeToCount.getOrDefault(stepType, 0);
            stepTypeToCount.put(stepType, count + 1);
          } else if (wrapperConfig.getStep().has("template")) {
            String templateRef = wrapperConfig.getStep().get("template").get("templateRef").asText();
            addTemplateByScope(templateRef, templateScopeToIds);
          }
        } else if (!isNull(wrapperConfig.getParallel())) {
          ParallelStepElementConfig parallelSteps =
              YamlUtils.read(wrapperConfig.getParallel().toString(), ParallelStepElementConfig.class);
          getCountByStepType(accountIdentifier, orgIdentifier, projectIdentifier, parallelSteps.getSections(),
              stepTypeToCount, templateScopeToIds);
        } else if (!isNull(wrapperConfig.getStepGroup())) {
          StepGroupElementConfig stepGroup =
              YamlUtils.read(wrapperConfig.getStepGroup().toString(), StepGroupElementConfig.class);
          if (!isNull(stepGroup.getSteps())) {
            getCountByStepType(accountIdentifier, orgIdentifier, projectIdentifier, stepGroup.getSteps(),
                stepTypeToCount, templateScopeToIds);
          } else if (!isNull(stepGroup.getTemplate())) {
            getTemplateAndFilterByTemplateType(accountIdentifier, orgIdentifier, projectIdentifier,
                stepGroup.getTemplate().getTemplateRef(), stepGroup.getTemplate().getVersionLabel(), stepTypeToCount,
                templateScopeToIds);
          }
        }
      } catch (IOException e) {
        throw new InvalidRequestException("Unable to parse these steps in yaml: " + wrapperConfig);
      }
    }
  }

  private void addTemplateByScope(String templateRef, Map<Scope, List<String>> templateScopeToIds) {
    if (templateRef.contains(ACCOUNT_IDENTIFIER)) {
      if (!templateScopeToIds.containsKey(Scope.ACCOUNT)) {
        templateScopeToIds.put(Scope.ACCOUNT, new ArrayList<>());
      }
      templateScopeToIds.get(Scope.ACCOUNT).add(templateRef.replace(ACCOUNT_IDENTIFIER, ""));
    } else if (templateRef.contains(ORG_IDENTIFIER)) {
      if (!templateScopeToIds.containsKey(Scope.ORG)) {
        templateScopeToIds.put(Scope.ORG, new ArrayList<>());
      }
      templateScopeToIds.get(Scope.ORG).add(templateRef.replace(ORG_IDENTIFIER, ""));
    } else {
      if (!templateScopeToIds.containsKey(Scope.PROJECT)) {
        templateScopeToIds.put(Scope.PROJECT, new ArrayList<>());
      }
      templateScopeToIds.get(Scope.PROJECT).add(templateRef);
    }
  }

  private void getTemplateResponseDTO(String accountId, String orgId, String projectId,
      Map<String, Integer> stepTypeToCount, Map<Scope, List<String>> stepTemplateScopeToIds) {
    for (Map.Entry<Scope, List<String>> stepTemplateIds : stepTemplateScopeToIds.entrySet()) {
      if (!isEmpty(stepTemplateIds.getValue())) {
        TemplateFilterPropertiesDTO templateFilterPropertiesDTO =
            TemplateFilterPropertiesDTO.builder()
                .templateEntityTypes(Collections.singletonList(TemplateEntityType.STEP_TEMPLATE))
                .templateIdentifiers(stepTemplateIds.getValue())
                .build();
        List<TemplateMetadataSummaryResponseDTO> stepTemplates;
        switch (stepTemplateIds.getKey()) {
          case ACCOUNT:
            stepTemplates =
                NGRestUtils
                    .getResponse(templateResourceClient.listTemplateMetadata(accountId, null, null,
                        STABLE_TEMPLATE_TYPE, 0, stepTemplateIds.getValue().size(), templateFilterPropertiesDTO))
                    .getContent();
            break;
          case ORG:
            stepTemplates =
                NGRestUtils
                    .getResponse(templateResourceClient.listTemplateMetadata(accountId, orgId, null,
                        STABLE_TEMPLATE_TYPE, 0, stepTemplateIds.getValue().size(), templateFilterPropertiesDTO))
                    .getContent();
            break;
          default:
            stepTemplates =
                NGRestUtils
                    .getResponse(templateResourceClient.listTemplateMetadata(accountId, orgId, projectId,
                        STABLE_TEMPLATE_TYPE, 0, stepTemplateIds.getValue().size(), templateFilterPropertiesDTO))
                    .getContent();
        }
        stepTemplates.forEach(stepTemplate -> {
          Integer count = stepTypeToCount.getOrDefault(stepTemplate.getChildType(), 0);
          stepTypeToCount.put(stepTemplate.getChildType(), count + 1);
        });
      }
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

  public void getTemplateAndFilterByTemplateType(String accountId, String orgId, String projectId, String templateRef,
      String versionLabel, Map<String, Integer> stepTypeToCount, Map<Scope, List<String>> templateScopeToIds) {
    TemplateResponseDTO templateResponseDTO =
        getScopedTemplateResponseDTO(accountId, orgId, projectId, templateRef, versionLabel);
    if (DEPLOYMENT_STAGE.equals(templateResponseDTO.getChildType())) {
      try {
        if (isEmpty(templateResponseDTO.getYaml())) {
          return;
        }
        YamlField templateYamlField =
            YamlUtils.readTree(templateResponseDTO.getYaml()).getNode().getField(YAMLFieldNameConstants.TEMPLATE);
        if (templateYamlField == null) {
          return;
        }
        ObjectNode templateNode = (ObjectNode) templateYamlField.getNode().getCurrJsonNode();
        JsonNode templateSpec = templateNode.get(YAMLFieldNameConstants.SPEC);
        if (isNull(templateSpec) || isEmpty(templateSpec.toString())) {
          return;
        }
        StepGroupElementConfig stepGroup = YamlUtils.read(templateSpec.toString(), StepGroupElementConfig.class);
        if (!isNull(stepGroup.getSteps())) {
          getCountByStepType(accountId, orgId, projectId, stepGroup.getSteps(), stepTypeToCount, templateScopeToIds);
        } else if (!isNull(stepGroup.getTemplate())) {
          addTemplateByScope(stepGroup.getTemplate().getTemplateRef(), templateScopeToIds);
        }
      } catch (IOException e) {
        throw new InvalidRequestException("Unable to parse these steps in yaml");
      }
    }
  }
}
