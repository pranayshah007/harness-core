/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.refresh.helper;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.dto.NoInputMergeInputAction;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.refresh.bean.EntityRefreshContext;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.template.refresh.v2.InputsValidationResponse;
import io.harness.ng.core.utils.ServiceOverrideV2ValidationHelper;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.RuntimeInputsValidator;
import io.harness.pms.merger.helpers.YamlRefreshHelper;
import io.harness.pms.yaml.YAMLFieldNameConstants;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class EnvironmentRefreshHelper {
  EnvironmentService environmentService;
  InfrastructureEntityService infrastructureEntityService;
  ServiceOverrideService serviceOverrideService;

  ServiceOverridesServiceV2 serviceOverridesServiceV2;
  AccountClient accountClient;

  ServiceOverrideV2ValidationHelper overrideV2ValidationHelper;
  private static final String STAGES_KEY = "stages";
  private static final String DUMMY_NODE = "dummy";

  public boolean isEnvironmentField(String fieldName, JsonNode envValue) {
    return YamlTypes.ENVIRONMENT_YAML.equals(fieldName) && envValue.isObject()
        && (envValue.get(YamlTypes.ENVIRONMENT_REF) != null || envValue.get(YamlTypes.INFRASTRUCTURE_DEFS) != null
            || isNodeNotNullAndValueRuntime(envValue.get(YamlTypes.SERVICE_OVERRIDE_INPUTS))
            || envValue.get(YamlTypes.USE_FROM_STAGE) != null);
  }

  public void validateEnvironmentInputs(
      YamlNode entityNode, EntityRefreshContext context, InputsValidationResponse errorNodeSummary) {
    JsonNode envJsonNode = entityNode.getCurrJsonNode();
    JsonNode envRefJsonNode = envJsonNode.get(YamlTypes.ENVIRONMENT_REF);
    String envRefValue;
    ObjectMapper mapper = new ObjectMapper();
    JsonNode infraDefsNode = envJsonNode.get(YamlTypes.INFRASTRUCTURE_DEFS);
    JsonNode serviceOverrideInputs = envJsonNode.get(YamlTypes.SERVICE_OVERRIDE_INPUTS);
    validateEnvironmentYamlWithUseFromStage(envJsonNode, errorNodeSummary);
    if (envRefJsonNode != null) {
      envRefValue = envRefJsonNode.asText();
      JsonNode envInputsNode = envJsonNode.get(YamlTypes.ENVIRONMENT_INPUTS);
      if (NGExpressionUtils.isRuntimeField(envRefValue)) {
        if (isNodeNotNullAndNotHaveRuntimeValue(envInputsNode) || (isNodeNotNullAndNotHaveRuntimeValue(infraDefsNode))
            || (isNodeNotNullAndNotHaveRuntimeValue(serviceOverrideInputs))) {
          errorNodeSummary.setValid(false);
        }
        return;
      } else if (NGExpressionUtils.isExpressionField(envRefValue)) {
        return;
      }

      // If envInputs not valid, no need to check infraDefs inputs
      if (!validateEnvInputs(context, errorNodeSummary, envRefValue, mapper, envInputsNode)) {
        return;
      }

      // infraDefinitions inputs are not valid, no need to check for serviceOverride Inputs.
      if (!validateInfraDefsInput(context, errorNodeSummary, envRefValue, mapper, infraDefsNode)) {
        return;
      }

      validateServiceOverrideInputsWithEnvRef(
          entityNode, context, errorNodeSummary, envRefJsonNode, mapper, serviceOverrideInputs);
    } else {
      if (serviceOverrideInputs == null && infraDefsNode == null) {
        return;
      }
      YamlNode stageYamlNodeInResolvedTemplatesYaml =
          getCorrespondingStageNodeInResolvedTemplatesYaml(entityNode, context.getResolvedTemplatesYamlNode());
      if (stageYamlNodeInResolvedTemplatesYaml == null) {
        log.warn("Could not find corresponding stage node in resolved templates yaml. Exiting.");
        return;
      }

      if (infraDefsNode != null) {
        if (!validateInfraDefsWithoutEnvRef(
                context, errorNodeSummary, mapper, infraDefsNode, stageYamlNodeInResolvedTemplatesYaml)) {
          return;
        }
      }

      if (serviceOverrideInputs != null) {
        validateServiceOverrideInputsWithoutEnvRef(
            context, errorNodeSummary, mapper, serviceOverrideInputs, stageYamlNodeInResolvedTemplatesYaml);
      }
    }
  }

  private void validateEnvironmentYamlWithUseFromStage(
      JsonNode envJsonNode, InputsValidationResponse errorNodeSummary) {
    JsonNode environmentInputs = envJsonNode.get(YamlTypes.ENVIRONMENT_INPUTS);
    JsonNode serviceOverrideInputs = envJsonNode.get(YamlTypes.SERVICE_OVERRIDE_INPUTS);
    JsonNode infraDefs = envJsonNode.get(YamlTypes.INFRASTRUCTURE_DEFS);
    if (envJsonNode.get(YamlTypes.USE_FROM_STAGE) != null) {
      if (environmentInputs != null || serviceOverrideInputs != null || infraDefs != null) {
        errorNodeSummary.setValid(false);
      }
    }
  }

  private void validateServiceOverrideInputsWithEnvRef(YamlNode entityNode, EntityRefreshContext context,
      InputsValidationResponse errorNodeSummary, JsonNode envRefJsonNode, ObjectMapper mapper,
      JsonNode serviceOverrideInputs) {
    YamlNode stageYamlNodeInResolvedTemplatesYaml =
        getCorrespondingStageNodeInResolvedTemplatesYaml(entityNode, context.getResolvedTemplatesYamlNode());
    if (stageYamlNodeInResolvedTemplatesYaml == null) {
      log.warn("Could not find corresponding stage node in resolved templates yaml. Exiting.");
      return;
    }

    YamlNode serviceNodeInResolvedTemplatesYaml =
        YamlNodeUtils.goToPathUsingFqn(stageYamlNodeInResolvedTemplatesYaml, "spec.service");
    if (serviceNodeInResolvedTemplatesYaml == null) {
      log.warn("Env node in Resolved templates yaml is null");
      return;
    }
    if (serviceNodeInResolvedTemplatesYaml.getField(YamlTypes.SERVICE_REF) == null) {
      log.warn("service ref in Resolved templates yaml is null " + serviceNodeInResolvedTemplatesYaml);
      return;
    }
    JsonNode serviceRefInResolvedTemplatesYaml =
        serviceNodeInResolvedTemplatesYaml.getField(YamlTypes.SERVICE_REF).getNode().getCurrJsonNode();
    validateServiceOverrideInputs(
        context, errorNodeSummary, mapper, serviceOverrideInputs, serviceRefInResolvedTemplatesYaml, envRefJsonNode);
  }

  private void validateServiceOverrideInputsWithoutEnvRef(EntityRefreshContext context,
      InputsValidationResponse errorNodeSummary, ObjectMapper mapper, JsonNode serviceOverrideInputs,
      YamlNode stageYamlNodeInResolvedTemplatesYaml) {
    YamlNode serviceNodeInResolvedTemplatesYaml =
        YamlNodeUtils.goToPathUsingFqn(stageYamlNodeInResolvedTemplatesYaml, "spec.service");
    if (serviceNodeInResolvedTemplatesYaml == null) {
      log.warn("service node in Resolved templates yaml is null");
      return;
    }
    YamlNode envNodeInResolvedTemplatesYaml =
        YamlNodeUtils.goToPathUsingFqn(stageYamlNodeInResolvedTemplatesYaml, "spec.environment");
    if (envNodeInResolvedTemplatesYaml == null) {
      log.warn("env node in Resolved templates yaml is null");
      return;
    }
    if (serviceNodeInResolvedTemplatesYaml.getField(YamlTypes.SERVICE_REF) == null
        || envNodeInResolvedTemplatesYaml.getField(YamlTypes.ENVIRONMENT_REF) == null) {
      log.warn("Service ref or env ref in yaml is null. Exiting.");
      return;
    }
    JsonNode serviceRefInResolvedTemplatesYaml =
        serviceNodeInResolvedTemplatesYaml.getField(YamlTypes.SERVICE_REF).getNode().getCurrJsonNode();
    JsonNode envRefInResolvedTemplatesYaml =
        envNodeInResolvedTemplatesYaml.getField(YamlTypes.ENVIRONMENT_REF).getNode().getCurrJsonNode();
    validateServiceOverrideInputs(context, errorNodeSummary, mapper, serviceOverrideInputs,
        serviceRefInResolvedTemplatesYaml, envRefInResolvedTemplatesYaml);
  }

  private void validateServiceOverrideInputs(EntityRefreshContext context, InputsValidationResponse errorNodeSummary,
      ObjectMapper mapper, JsonNode serviceOverrideInputs, JsonNode serviceRefInResolvedTemplatesYaml,
      JsonNode envRefInResolvedTemplatesYaml) {
    if (serviceRefInResolvedTemplatesYaml == null || envRefInResolvedTemplatesYaml == null) {
      log.warn("Service ref or env ref in yaml is null. Exiting.");
      return;
    }
    if (isNodeNotNullAndValueRuntime(serviceRefInResolvedTemplatesYaml)
        || isNodeNotNullAndValueRuntime(envRefInResolvedTemplatesYaml)) {
      if (isNodeNotNullAndNotHaveRuntimeValue(serviceOverrideInputs)) {
        errorNodeSummary.setValid(false);
      }
      return;
    }

    String serviceOverrideInputsYaml = overrideV2ValidationHelper.isOverridesV2Enabled(
                                           context.getAccountId(), context.getOrgId(), context.getProjectId())
        ? serviceOverridesServiceV2.createServiceOverrideInputsYaml(context.getAccountId(), context.getOrgId(),
            context.getProjectId(), envRefInResolvedTemplatesYaml.asText(), serviceRefInResolvedTemplatesYaml.asText())
        : serviceOverrideService.createServiceOverrideInputsYaml(context.getAccountId(), context.getOrgId(),
            context.getProjectId(), envRefInResolvedTemplatesYaml.asText(), serviceRefInResolvedTemplatesYaml.asText());
    if (EmptyPredicate.isEmpty(serviceOverrideInputsYaml)) {
      if (isNodeNotNullAndNotHaveRuntimeValue(serviceOverrideInputs)) {
        errorNodeSummary.setValid(false);
      }
      return;
    }

    ObjectNode linkedServiceOverridesInputs = mapper.createObjectNode();
    linkedServiceOverridesInputs.set(YamlTypes.SERVICE_OVERRIDE_INPUTS, serviceOverrideInputs);
    String linkedServiceOverridesYaml = YamlUtils.writeYamlString(linkedServiceOverridesInputs);
    if (!RuntimeInputsValidator.validateInputsAgainstSourceNode(
            linkedServiceOverridesYaml, serviceOverrideInputsYaml)) {
      errorNodeSummary.setValid(false);
    }
  }

  private boolean validateInfraDefsWithoutEnvRef(EntityRefreshContext context,
      InputsValidationResponse errorNodeSummary, ObjectMapper mapper, JsonNode infraDefsNode,
      YamlNode stageYamlNodeInResolvedTemplatesYaml) {
    YamlNode envNodeInResolvedTemplatesYaml =
        YamlNodeUtils.goToPathUsingFqn(stageYamlNodeInResolvedTemplatesYaml, "spec.environment");
    if (envNodeInResolvedTemplatesYaml == null) {
      log.warn("Env node in Resolved templates yaml is null");
      return true;
    }
    JsonNode infraDefsNodeInResolvedTemplatesYaml =
        envNodeInResolvedTemplatesYaml.getField(YamlTypes.INFRASTRUCTURE_DEFS).getNode().getCurrJsonNode();
    if (infraDefsNodeInResolvedTemplatesYaml == null) {
      log.warn("Something wrong happened finding infraDefinitions node in resolved templates yaml");
      return true;
    }
    if (checkIfInfraDefsToBeValidated(infraDefsNode, infraDefsNodeInResolvedTemplatesYaml, mapper)) {
      if (envNodeInResolvedTemplatesYaml.getField(YamlTypes.ENVIRONMENT_REF) == null) {
        log.warn("Skipping because couldn't find envRef value");
        return true;
      }
      JsonNode envRefNode =
          envNodeInResolvedTemplatesYaml.getField(YamlTypes.ENVIRONMENT_REF).getNode().getCurrJsonNode();
      String envRefValue = envRefNode.asText();
      return validateInfraDefsInput(context, errorNodeSummary, envRefValue, mapper, infraDefsNode);
    }
    return true;
  }

  public JsonNode refreshEnvironmentInputs(YamlNode entityNode, EntityRefreshContext context) {
    ObjectNode envObjectNode = (ObjectNode) entityNode.getCurrJsonNode();
    JsonNode envRefJsonNode = envObjectNode.get(YamlTypes.ENVIRONMENT_REF);
    String envRefValue;
    ObjectMapper mapper = new ObjectMapper();
    JsonNode infraDefsNode = envObjectNode.get(YamlTypes.INFRASTRUCTURE_DEFS);
    JsonNode serviceOverrideInputs = envObjectNode.get(YamlTypes.SERVICE_OVERRIDE_INPUTS);
    if (refreshEnvironmentYamlWithUseFromStage(envObjectNode)) {
      return envObjectNode;
    }
    if (envRefJsonNode != null) {
      envRefValue = envRefJsonNode.asText();
      JsonNode envInputsNode = envObjectNode.get(YamlTypes.ENVIRONMENT_INPUTS);
      if (NGExpressionUtils.isRuntimeField(envRefValue)) {
        envObjectNode.put(YamlTypes.ENVIRONMENT_INPUTS, "<+input>");
        if (isNodeNotNullAndNotHaveRuntimeValue(infraDefsNode)) {
          envObjectNode.put(YamlTypes.INFRASTRUCTURE_DEFS, "<+input>");
        }
        if (isNodeNotNullAndNotHaveRuntimeValue(serviceOverrideInputs)) {
          envObjectNode.put(YamlTypes.SERVICE_OVERRIDE_INPUTS, "<+input>");
        }
        return envObjectNode;
      } else if (NGExpressionUtils.isExpressionField(envRefValue)) {
        return envObjectNode;
      }

      refreshEnvInputs(context, envRefValue, mapper, envObjectNode, envInputsNode);
      refreshInfraDefsInput(context, envRefValue, mapper, envObjectNode, infraDefsNode);
      refreshServiceOverrideInputsWithEnvRef(
          entityNode, context, envObjectNode, envRefJsonNode, mapper, serviceOverrideInputs);
    } else {
      if (serviceOverrideInputs == null && infraDefsNode == null) {
        return envObjectNode;
      }
      YamlNode stageYamlNodeInResolvedTemplatesYaml =
          getCorrespondingStageNodeInResolvedTemplatesYaml(entityNode, context.getResolvedTemplatesYamlNode());
      if (stageYamlNodeInResolvedTemplatesYaml == null) {
        log.warn("Could not find corresponding stage node in resolved templates yaml. Exiting.");
        return envObjectNode;
      }
      if (infraDefsNode != null) {
        refreshInfraDefsWithoutEnvRef(
            context, envObjectNode, mapper, infraDefsNode, stageYamlNodeInResolvedTemplatesYaml);
      }
      if (serviceOverrideInputs != null) {
        refreshServiceOverrideInputsWithoutEnvRef(
            context, envObjectNode, mapper, serviceOverrideInputs, stageYamlNodeInResolvedTemplatesYaml);
      }
    }
    return envObjectNode;
  }

  private boolean refreshEnvironmentYamlWithUseFromStage(ObjectNode envObjectNode) {
    if (envObjectNode.get(YamlTypes.USE_FROM_STAGE) != null) {
      envObjectNode.remove(YamlTypes.ENVIRONMENT_INPUTS);
      envObjectNode.remove(YamlTypes.SERVICE_OVERRIDE_INPUTS);
      envObjectNode.remove(YamlTypes.INFRASTRUCTURE_DEFS);
      return true;
    }
    return false;
  }
  private void refreshServiceOverrideInputsWithEnvRef(YamlNode entityNode, EntityRefreshContext context,
      ObjectNode envObjectNode, JsonNode envRefJsonNode, ObjectMapper mapper, JsonNode serviceOverrideInputs) {
    YamlNode stageYamlNodeInResolvedTemplatesYaml =
        getCorrespondingStageNodeInResolvedTemplatesYaml(entityNode, context.getResolvedTemplatesYamlNode());
    if (stageYamlNodeInResolvedTemplatesYaml == null) {
      log.warn("Could not find corresponding stage node in resolved templates yaml. Exiting.");
      return;
    }
    YamlNode serviceNodeInResolvedTemplatesYaml =
        YamlNodeUtils.goToPathUsingFqn(stageYamlNodeInResolvedTemplatesYaml, "spec.service");
    if (serviceNodeInResolvedTemplatesYaml == null) {
      log.warn("service node in Resolved templates yaml is null");
      return;
    }

    // ex. use from stage
    if (serviceNodeInResolvedTemplatesYaml.getField(YamlTypes.SERVICE_REF) == null) {
      envObjectNode.remove(YamlTypes.SERVICE_OVERRIDE_INPUTS);
      return;
    }

    JsonNode serviceRefInResolvedTemplatesYaml =
        serviceNodeInResolvedTemplatesYaml.getField(YamlTypes.SERVICE_REF).getNode().getCurrJsonNode();
    refreshServiceOverrideInputs(
        context, envObjectNode, mapper, serviceOverrideInputs, serviceRefInResolvedTemplatesYaml, envRefJsonNode);
  }

  private void refreshServiceOverrideInputs(EntityRefreshContext context, ObjectNode envObjectNode, ObjectMapper mapper,
      JsonNode serviceOverrideInputs, JsonNode serviceRefInResolvedTemplatesYaml,
      JsonNode envRefInResolvedTemplatesYaml) {
    if (serviceRefInResolvedTemplatesYaml == null || envRefInResolvedTemplatesYaml == null) {
      if (serviceOverrideInputs != null) {
        envObjectNode.remove(YamlTypes.SERVICE_OVERRIDE_INPUTS);
      }
      log.warn("Service ref or env ref in yaml is null. Exiting.");
      return;
    }
    if (isNodeNotNullAndValueRuntime(serviceRefInResolvedTemplatesYaml)
        || isNodeNotNullAndValueRuntime(envRefInResolvedTemplatesYaml)) {
      if (isNodeNotNullAndNotHaveRuntimeValue(serviceOverrideInputs)) {
        envObjectNode.put(YamlTypes.SERVICE_OVERRIDE_INPUTS, "<+input>");
      }
      return;
    }

    String serviceOverrideInputsYaml = overrideV2ValidationHelper.isOverridesV2Enabled(
                                           context.getAccountId(), context.getOrgId(), context.getProjectId())
        ? serviceOverridesServiceV2.createServiceOverrideInputsYaml(context.getAccountId(), context.getOrgId(),
            context.getProjectId(), envRefInResolvedTemplatesYaml.asText(), serviceRefInResolvedTemplatesYaml.asText())
        : serviceOverrideService.createServiceOverrideInputsYaml(context.getAccountId(), context.getOrgId(),
            context.getProjectId(), envRefInResolvedTemplatesYaml.asText(), serviceRefInResolvedTemplatesYaml.asText());
    if (EmptyPredicate.isEmpty(serviceOverrideInputsYaml)) {
      if (serviceOverrideInputs != null) {
        envObjectNode.remove(YamlTypes.SERVICE_OVERRIDE_INPUTS);
      }
      return;
    }

    ObjectNode linkedServiceOverridesInputs = mapper.createObjectNode();
    linkedServiceOverridesInputs.set(YamlTypes.SERVICE_OVERRIDE_INPUTS, serviceOverrideInputs);
    String linkedServiceOverridesYaml = YamlUtils.writeYamlString(linkedServiceOverridesInputs);

    JsonNode refreshedJsonNode =
        YamlRefreshHelper.refreshYamlFromSourceYaml(linkedServiceOverridesYaml, serviceOverrideInputsYaml);
    envObjectNode.set(YamlTypes.SERVICE_OVERRIDE_INPUTS, refreshedJsonNode.get(YamlTypes.SERVICE_OVERRIDE_INPUTS));
  }

  private void refreshServiceOverrideInputsWithoutEnvRef(EntityRefreshContext context, ObjectNode envObjectNode,
      ObjectMapper mapper, JsonNode serviceOverrideInputs, YamlNode stageYamlNodeInResolvedTemplatesYaml) {
    YamlNode serviceNodeInResolvedTemplatesYaml =
        YamlNodeUtils.goToPathUsingFqn(stageYamlNodeInResolvedTemplatesYaml, "spec.service");
    if (serviceNodeInResolvedTemplatesYaml == null) {
      log.warn("service node in Resolved templates yaml is null");
      return;
    }
    YamlNode envNodeInResolvedTemplatesYaml =
        YamlNodeUtils.goToPathUsingFqn(stageYamlNodeInResolvedTemplatesYaml, "spec.environment");
    if (envNodeInResolvedTemplatesYaml == null) {
      log.warn("Env node in Resolved templates yaml is null");
      return;
    }
    if (serviceNodeInResolvedTemplatesYaml.getField(YamlTypes.SERVICE_REF) == null
        || envNodeInResolvedTemplatesYaml.getField(YamlTypes.ENVIRONMENT_REF) == null) {
      envObjectNode.remove(YamlTypes.SERVICE_OVERRIDE_INPUTS);
      log.warn("Service ref or env ref in yaml is null. Exiting.");
      return;
    }
    JsonNode serviceRefInResolvedTemplatesYaml =
        serviceNodeInResolvedTemplatesYaml.getField(YamlTypes.SERVICE_REF).getNode().getCurrJsonNode();
    JsonNode envRefInResolvedTemplatesYaml =
        envNodeInResolvedTemplatesYaml.getField(YamlTypes.ENVIRONMENT_REF).getNode().getCurrJsonNode();
    refreshServiceOverrideInputs(context, envObjectNode, mapper, serviceOverrideInputs,
        serviceRefInResolvedTemplatesYaml, envRefInResolvedTemplatesYaml);
  }

  private void refreshInfraDefsWithoutEnvRef(EntityRefreshContext context, ObjectNode envObjectNode,
      ObjectMapper mapper, JsonNode infraDefsNode, YamlNode stageYamlNodeInResolvedTemplatesYaml) {
    String envRefValue;
    YamlNode envNodeInResolvedTemplatesYaml =
        YamlNodeUtils.goToPathUsingFqn(stageYamlNodeInResolvedTemplatesYaml, "spec.environment");
    if (envNodeInResolvedTemplatesYaml == null) {
      log.warn("Env node in Resolved templates yaml is null");
      return;
    }
    JsonNode infraDefsNodeInResolvedTemplatesYaml =
        envNodeInResolvedTemplatesYaml.getField(YamlTypes.INFRASTRUCTURE_DEFS).getNode().getCurrJsonNode();
    if (infraDefsNodeInResolvedTemplatesYaml == null) {
      log.warn("Something wrong happened finding infraDefinitions node in resolved templates yaml");
      return;
    }
    if (checkIfInfraDefsToBeValidated(infraDefsNode, infraDefsNodeInResolvedTemplatesYaml, mapper)) {
      JsonNode envRefNode =
          envNodeInResolvedTemplatesYaml.getField(YamlTypes.ENVIRONMENT_REF).getNode().getCurrJsonNode();
      if (envRefNode == null) {
        log.warn("Skipping because couldn't find envRef value");
        return;
      }
      envRefValue = envRefNode.asText();
      refreshInfraDefsInput(context, envRefValue, mapper, envObjectNode, infraDefsNode);
    }
  }

  private void refreshInfraDefsInput(EntityRefreshContext context, String envRefValue, ObjectMapper mapper,
      ObjectNode envObjectNode, JsonNode infraDefsNode) {
    if (infraDefsNode == null) {
      return;
    }
    if (NGExpressionUtils.isRuntimeField(infraDefsNode.asText())) {
      return;
    }
    List<String> infraDefIdentifiers = collectAllInfraIdentifiers(infraDefsNode);
    if (infraDefIdentifiers.stream().anyMatch(NGExpressionUtils::isExpressionField)) {
      return;
    }
    if (EmptyPredicate.isNotEmpty(infraDefIdentifiers)) {
      String infraInputs = infrastructureEntityService.createInfrastructureInputsFromYaml(context.getAccountId(),
          context.getOrgId(), context.getProjectId(), envRefValue, infraDefIdentifiers, false,
          NoInputMergeInputAction.ADD_IDENTIFIER_NODE);
      if (EmptyPredicate.isEmpty(infraInputs)) {
        envObjectNode.remove(YamlTypes.INFRASTRUCTURE_DEFS);
        return;
      }

      ObjectNode linkedInfraDefs = mapper.createObjectNode();
      linkedInfraDefs.set(YamlTypes.INFRASTRUCTURE_DEFS, infraDefsNode);

      // Adding dummy node because down the line we create yamlConfig which doesn't work if root is an array.
      JsonNode dummyLinkedInfraDefs = addDummyRootToJsonNode(linkedInfraDefs, mapper);
      JsonNode infraInputsNode = readTree(infraInputs);
      JsonNode dummyInfraInputsNode = addDummyRootToJsonNode(infraInputsNode, mapper);

      JsonNode refreshedJsonNode = YamlRefreshHelper.refreshYamlFromSourceYaml(
          YamlUtils.writeYamlString(dummyLinkedInfraDefs), YamlUtils.writeYamlString(dummyInfraInputsNode));
      if (refreshedJsonNode != null) {
        envObjectNode.setAll((ObjectNode) refreshedJsonNode.get(DUMMY_NODE));
      }
    }
  }

  private void refreshEnvInputs(EntityRefreshContext context, String envRefValue, ObjectMapper mapper,
      ObjectNode envObjectNode, JsonNode linkedEnvInputsValue) {
    String envInputsYaml = overrideV2ValidationHelper.isOverridesV2Enabled(
                               context.getAccountId(), context.getOrgId(), context.getProjectId())
        ? serviceOverridesServiceV2.createEnvOverrideInputsYaml(
            context.getAccountId(), context.getOrgId(), context.getProjectId(), envRefValue)
        : environmentService.createEnvironmentInputsYaml(
            context.getAccountId(), context.getOrgId(), context.getProjectId(), envRefValue);
    if (EmptyPredicate.isEmpty(envInputsYaml)) {
      envObjectNode.remove(YamlTypes.ENVIRONMENT_INPUTS);
      return;
    }

    ObjectNode linkedEnvInputs = mapper.createObjectNode();
    linkedEnvInputs.set(YamlTypes.ENVIRONMENT_INPUTS, linkedEnvInputsValue);
    String linkedEnvInputsYaml = YamlUtils.writeYamlString(linkedEnvInputs);
    JsonNode refreshedJsonNode = YamlRefreshHelper.refreshYamlFromSourceYaml(linkedEnvInputsYaml, envInputsYaml);
    envObjectNode.set(YamlTypes.ENVIRONMENT_INPUTS, refreshedJsonNode.get(YamlTypes.ENVIRONMENT_INPUTS));
  }

  private boolean validateInfraDefsInput(EntityRefreshContext context, InputsValidationResponse errorNodeSummary,
      String envRefValue, ObjectMapper mapper, JsonNode infraDefsNode) {
    if (infraDefsNode == null) {
      return true;
    }
    if (NGExpressionUtils.isRuntimeField(infraDefsNode.asText())) {
      return true;
    }
    List<String> infraDefIdentifiers = collectAllInfraIdentifiers(infraDefsNode);
    if (infraDefIdentifiers.stream().anyMatch(NGExpressionUtils::isExpressionField)) {
      return true;
    }

    if (EmptyPredicate.isNotEmpty(infraDefIdentifiers)) {
      String infraInputs = infrastructureEntityService.createInfrastructureInputsFromYaml(context.getAccountId(),
          context.getOrgId(), context.getProjectId(), envRefValue, infraDefIdentifiers, false,
          NoInputMergeInputAction.ADD_IDENTIFIER_NODE);
      if (EmptyPredicate.isEmpty(infraInputs)) {
        errorNodeSummary.setValid(false);
        return false;
      }

      ObjectNode linkedInfraDefs = mapper.createObjectNode();
      linkedInfraDefs.set(YamlTypes.INFRASTRUCTURE_DEFS, infraDefsNode);

      // Adding dummy node because down the line we create yamlConfig which doesn't work if root is an array.
      JsonNode dummyLinkedInfraDefs = addDummyRootToJsonNode(linkedInfraDefs, mapper);
      JsonNode infraInputsNode = readTree(infraInputs);
      JsonNode dummyInfraInputsNode = addDummyRootToJsonNode(infraInputsNode, mapper);

      // TODO(@Inder): Create another validation method taking jsonNodes instead of yaml
      if (!RuntimeInputsValidator.validateInputsAgainstSourceNode(
              YamlUtils.writeYamlString(dummyLinkedInfraDefs), YamlUtils.writeYamlString(dummyInfraInputsNode))) {
        errorNodeSummary.setValid(false);
        return false;
      }
    }
    return true;
  }

  private JsonNode readTree(String yaml) {
    try {
      YamlField yamlField = YamlUtils.readTree(yaml);
      return yamlField.getNode().getCurrJsonNode();
    } catch (IOException e) {
      log.error("Error while converting yaml to jsonNode");
      throw new InvalidRequestException("Exception occurred while converting yaml to jsonNode");
    }
  }

  private boolean validateEnvInputs(EntityRefreshContext context, InputsValidationResponse errorNodeSummary,
      String envRefValue, ObjectMapper mapper, JsonNode envInputsNode) {
    String envInputsYaml = overrideV2ValidationHelper.isOverridesV2Enabled(
                               context.getAccountId(), context.getOrgId(), context.getProjectId())
        ? serviceOverridesServiceV2.createEnvOverrideInputsYaml(
            context.getAccountId(), context.getOrgId(), context.getProjectId(), envRefValue)
        : environmentService.createEnvironmentInputsYaml(
            context.getAccountId(), context.getOrgId(), context.getProjectId(), envRefValue);
    if (EmptyPredicate.isEmpty(envInputsYaml)) {
      if (isNodeNotNullAndNotHaveRuntimeValue(envInputsNode)) {
        errorNodeSummary.setValid(false);
        return false;
      }
      return true;
    }

    ObjectNode linkedEnvInputs = mapper.createObjectNode();
    linkedEnvInputs.set(YamlTypes.ENVIRONMENT_INPUTS, envInputsNode);
    String linkedEnvInputsYaml = YamlUtils.writeYamlString(linkedEnvInputs);
    if (!RuntimeInputsValidator.validateInputsAgainstSourceNode(linkedEnvInputsYaml, envInputsYaml)) {
      errorNodeSummary.setValid(false);
      return false;
    }
    return true;
  }

  private boolean checkIfInfraDefsToBeValidated(
      JsonNode infraNode, JsonNode resolvedTemplatesInfraNode, ObjectMapper objectMapper) {
    ObjectNode newInfraNode = objectMapper.createObjectNode();
    newInfraNode.set(YamlTypes.INFRASTRUCTURE_DEFS, infraNode);
    ObjectNode envNode = objectMapper.createObjectNode();
    envNode.set(YamlTypes.ENVIRONMENT_YAML, newInfraNode);
    ObjectNode newResolvedTemplatesInfraNode = objectMapper.createObjectNode();
    newResolvedTemplatesInfraNode.set(YamlTypes.INFRASTRUCTURE_DEFS, resolvedTemplatesInfraNode);
    ObjectNode envNodeForResolvedTemplates = objectMapper.createObjectNode();
    envNodeForResolvedTemplates.set(YamlTypes.ENVIRONMENT_YAML, newResolvedTemplatesInfraNode);
    // Passing envNode instead of infraNode in YamlConfig because it requires root field to be an object.
    YamlConfig envNodeYamlConfig = new YamlConfig(envNode);
    YamlConfig resolvedTemplatesEnvNodeYamlConfig = new YamlConfig(envNodeForResolvedTemplates);

    Set<String> envNodeKeySet =
        envNodeYamlConfig.getFqnToValueMap().keySet().stream().map(FQN::getExpressionFqn).collect(Collectors.toSet());
    Set<String> envNodeInResolvedTemplatesYamlKeySet = resolvedTemplatesEnvNodeYamlConfig.getFqnToValueMap()
                                                           .keySet()
                                                           .stream()
                                                           .map(FQN::getExpressionFqn)
                                                           .collect(Collectors.toSet());
    return envNodeKeySet.containsAll(envNodeInResolvedTemplatesYamlKeySet);
  }

  private List<String> collectAllInfraIdentifiers(JsonNode infraDefsNode) {
    List<String> infraDefs = new ArrayList<>();
    if (infraDefsNode.isArray()) {
      for (JsonNode arrayElement : infraDefsNode) {
        JsonNode identifier = arrayElement.get(YamlTypes.IDENTIFIER);
        if (identifier != null) {
          infraDefs.add(identifier.asText());
        }
      }
    }
    return infraDefs;
  }

  private YamlNode getCorrespondingStageNodeInResolvedTemplatesYaml(
      YamlNode entityNode, YamlNode resolvedTemplatesYamlNode) {
    if (isStageTemplate(resolvedTemplatesYamlNode)) {
      return new YamlNode(resolvedTemplatesYamlNode.getCurrJsonNode().get(YamlTypes.TEMPLATE).get(YamlTypes.SPEC));
    }
    String stageIdentifier = getStageIdentifierForGivenEnvironmentField(entityNode);
    if (stageIdentifier == null) {
      log.warn("Stage not found, returning null");
      return null;
    }
    return findStageWithGivenIdentifier(resolvedTemplatesYamlNode, stageIdentifier);
  }

  private boolean isStageTemplate(YamlNode resolvedTemplatesYamlNode) {
    return resolvedTemplatesYamlNode != null && resolvedTemplatesYamlNode.getTemplate() != null
        && resolvedTemplatesYamlNode.getTemplate().get("type") != null
        && "Stage".equals(resolvedTemplatesYamlNode.getTemplate().get("type").asText());
  }

  public static boolean isNodeNotNullAndNotHaveRuntimeValue(JsonNode jsonNode) {
    return jsonNode != null
        && (jsonNode.isObject() || jsonNode.isArray()
            || (jsonNode.isValueNode() && !NGExpressionUtils.matchesInputSetPattern(jsonNode.asText())));
  }

  private String getStageIdentifierForGivenEnvironmentField(YamlNode entityNode) {
    if (entityNode == null) {
      return null;
    }
    if (YamlTypes.STAGE.equals(entityNode.getFieldName())) {
      return entityNode.getIdentifier();
    }
    return getStageIdentifierForGivenEnvironmentField(entityNode.getParentNode());
  }

  private YamlNode findStageWithGivenIdentifier(YamlNode resolvedTemplatesYamlNode, String stageIdentifier) {
    if (resolvedTemplatesYamlNode == null) {
      return null;
    }
    YamlNode stagesYamlNode = YamlNodeUtils.findFirstNodeMatchingFieldName(resolvedTemplatesYamlNode, STAGES_KEY);
    if (stagesYamlNode == null || stagesYamlNode.isObject()) {
      return null;
    }

    return findStageNodeWithGivenStageIdentifier(stageIdentifier, stagesYamlNode);
  }

  private YamlNode findStageNodeWithGivenStageIdentifier(String stageIdentifier, YamlNode stagesYamlNode) {
    for (YamlNode stageNode : stagesYamlNode.asArray()) {
      YamlNode requiredStageNode = null;
      if (stageNode.getField(YamlTypes.STAGE) != null
          && stageIdentifier.equals(stageNode.getField(YamlTypes.STAGE).getNode().getIdentifier())) {
        requiredStageNode = stageNode.getField(YamlTypes.STAGE).getNode();
      } else if (stageNode.getField(YAMLFieldNameConstants.PARALLEL) != null) {
        requiredStageNode = findStageNodeWithGivenStageIdentifier(
            stageIdentifier, stageNode.getField(YAMLFieldNameConstants.PARALLEL).getNode());
      }
      if (requiredStageNode != null) {
        return requiredStageNode;
      }
    }
    return null;
  }

  private JsonNode addDummyRootToJsonNode(JsonNode node, ObjectMapper mapper) {
    ObjectNode dummyObjectNode = mapper.createObjectNode();
    dummyObjectNode.set(DUMMY_NODE, node);
    return dummyObjectNode;
  }

  private boolean isNodeNotNullAndValueRuntime(JsonNode node) {
    return node != null && node.isValueNode() && NGExpressionUtils.matchesInputSetPattern(node.asText());
  }
}
