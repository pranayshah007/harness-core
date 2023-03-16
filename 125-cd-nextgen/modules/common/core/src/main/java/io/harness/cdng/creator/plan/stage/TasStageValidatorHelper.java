/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage;

import static io.harness.authorization.AuthorizationServiceHeader.TEMPLATE_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.executions.steps.StepSpecTypeConstants.DEPLOYMENT_STAGE;
import static io.harness.executions.steps.StepSpecTypeConstants.SWAP_ROLLBACK;
import static io.harness.executions.steps.StepSpecTypeConstants.TANZU_COMMAND;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_BASIC_APP_SETUP;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_BG_APP_SETUP;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_CANARY_APP_SETUP;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_ROLLBACK;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_ROLLING_DEPLOY;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_ROLLING_ROLLBACK;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_ROUTE_MAPPING;
import static io.harness.executions.steps.StepSpecTypeConstants.TAS_SWAP_ROUTES;
import static io.harness.ng.core.template.TemplateListType.STABLE_TEMPLATE_TYPE;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
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
import io.harness.template.TemplateFilterPropertiesDTO;
import io.harness.template.remote.TemplateResourceClient;

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

@OwnedBy(HarnessTeam.CDP)
public class TasStageValidatorHelper implements StageValidatorHelper {
  @Inject TemplateResourceClient templateResourceClient;
  private static final String ACCOUNT_IDENTIFIER = "account.";
  private static final String ORG_IDENTIFIER = "org.";

  @Override
  public void validate(Object object, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    DeploymentStageConfig stageConfig = (DeploymentStageConfig) object;
    ExecutionElementConfig executionElementConfig = stageConfig.getExecution();
    List<ExecutionWrapperConfig> allSteps = new ArrayList<>();
    Map<String, Integer> stepTypeToCount = new HashMap<>();
    Map<Scope, List<String>> stepTemplateScopeToIds = new EnumMap<>(Scope.class);
    allSteps.addAll(executionElementConfig.getSteps());
    if (!isNull(executionElementConfig.getRollbackSteps())) {
      allSteps.addAll(executionElementConfig.getRollbackSteps());
    }
    SecurityContextBuilder.setContext(new ServicePrincipal(TEMPLATE_SERVICE.getServiceId()));
    getCountByStepType(
        accountIdentifier, orgIdentifier, projectIdentifier, allSteps, stepTypeToCount, stepTemplateScopeToIds);
    getTemplateResponseDTO(
        accountIdentifier, orgIdentifier, projectIdentifier, stepTypeToCount, stepTemplateScopeToIds);
    int basicAppSetupStepCount = stepTypeToCount.getOrDefault(TAS_BASIC_APP_SETUP, 0);
    int canaryAppSetupStepCount = stepTypeToCount.getOrDefault(TAS_CANARY_APP_SETUP, 0);
    int bgAppSetupStepCount = stepTypeToCount.getOrDefault(TAS_BG_APP_SETUP, 0);
    int commandStepCount = stepTypeToCount.getOrDefault(TANZU_COMMAND, 0);
    int rollingDeployStepCount = stepTypeToCount.getOrDefault(TAS_ROLLING_DEPLOY, 0);
    int routeMappingStepCount = stepTypeToCount.getOrDefault(TAS_ROUTE_MAPPING, 0);
    validateStepCount(basicAppSetupStepCount, "Only one Basic App Setup step is valid, found: %d");
    validateStepCount(canaryAppSetupStepCount, "Only one Canary App Setup step is valid, found: %d");
    validateStepCount(bgAppSetupStepCount, "Only one BG App Setup step is valid, found: %d");
    validateStepCount(rollingDeployStepCount, "Only one Rolling Deploy step is valid, found: %d");

    if (((basicAppSetupStepCount + bgAppSetupStepCount + canaryAppSetupStepCount + rollingDeployStepCount) > 1)
        || ((basicAppSetupStepCount + bgAppSetupStepCount + canaryAppSetupStepCount + rollingDeployStepCount) == 0
            && commandStepCount == 0 && routeMappingStepCount == 0)) {
      throw new InvalidYamlException("Only one App Setup or Rolling Deploy is supported");
    }
    int swapRouteStepCount = stepTypeToCount.getOrDefault(TAS_SWAP_ROUTES, 0);
    validateStepCount(swapRouteStepCount, "At max one Swap Route step is valid, found: %d");
    int appRollbackStepCount = stepTypeToCount.getOrDefault(TAS_ROLLBACK, 0);
    int swapRollbackStepCount = stepTypeToCount.getOrDefault(SWAP_ROLLBACK, 0);
    int rollingRollbackStepCount = stepTypeToCount.getOrDefault(TAS_ROLLING_ROLLBACK, 0);
    validateStepCount(rollingRollbackStepCount, "At max one Rolling Rollback step is valid, found: %d");
    validateStepCount(appRollbackStepCount, "At max one App Rollback step is valid, found: %d");
    validateStepCount(swapRollbackStepCount, "At max one Swap Rollback step is valid, found: %d");
    if ((appRollbackStepCount + swapRollbackStepCount + rollingRollbackStepCount) > 1) {
      throw new InvalidYamlException(format(
          "Only one Rollback step out of %s is supported", List.of(TAS_ROLLBACK, SWAP_ROLLBACK, TAS_ROLLING_ROLLBACK)));
    }
  }

  private void validateStepCount(int count, String errorMsg) {
    if (count > 1) {
      throw new InvalidYamlException(format(errorMsg, count));
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
