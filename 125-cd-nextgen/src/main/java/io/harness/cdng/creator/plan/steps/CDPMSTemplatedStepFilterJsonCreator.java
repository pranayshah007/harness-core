/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;

import io.harness.cdng.ssh.CommandUnitSourceType;
import io.harness.cdng.ssh.CommandUnitSpecType;
import io.harness.cdng.ssh.CommandUnitWrapper;
import io.harness.cdng.ssh.CopyCommandUnitSpec;
import io.harness.cdng.template.TemplateConfigNode;
import io.harness.cdng.template.TemplateNode;
import io.harness.cdng.template.TemplatedStepNode;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.exception.InvalidYamlException;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.remote.TemplateResourceClient;

import software.wings.api.DeploymentType;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class CDPMSTemplatedStepFilterJsonCreator implements FilterJsonCreator<TemplatedStepNode> {
  @Inject private TemplateResourceClient templateResourceClient;

  @Override
  public Class<TemplatedStepNode> getFieldClass() {
    return TemplatedStepNode.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    Set<String> stepTypes = getSupportedStepTypes();
    if (isEmpty(stepTypes)) {
      return Collections.emptyMap();
    }
    return Collections.singletonMap(STEP, stepTypes);
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, TemplatedStepNode yamlField) {
    validateCopyCommandUnitForWinRm(filterCreationContext, yamlField);
    return FilterCreationResponse.builder().build();
  }

  private Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(PlanCreatorUtils.TEMPLATE_TYPE);
  }

  private void validateCopyCommandUnitForWinRm(
      FilterCreationContext filterCreationContext, TemplatedStepNode stepNode) {
    JsonNode deploymentType = findDeploymentType(filterCreationContext);
    if (deploymentType != null && DeploymentType.WINRM.name().equalsIgnoreCase(deploymentType.textValue())) {
      Optional<CommandUnitWrapper> copyCommandUnit = findCopyCommandUnit(filterCreationContext, stepNode);
      if (copyCommandUnit.isPresent() && isArtifactSourceType(copyCommandUnit.get())) {
        throw new InvalidYamlException(
            "Copy command unit is not supported for WinRm deployment type. Please use download command unit instead.");
      }
    }
  }

  private boolean isArtifactSourceType(CommandUnitWrapper commandUnitWrapper) {
    if (commandUnitWrapper.getSpec() instanceof CopyCommandUnitSpec) {
      return CommandUnitSourceType.Artifact.equals(
          ((CopyCommandUnitSpec) commandUnitWrapper.getSpec()).getSourceType());
    } else {
      return false;
    }
  }

  private Optional<CommandUnitWrapper> findCopyCommandUnit(
      FilterCreationContext filterCreationContext, TemplatedStepNode templatedStepNode) {
    if (templatedStepNode == null) {
      return Optional.empty();
    }
    if (templatedStepNode.getTemplatedStepInfo() == null
        || templatedStepNode.getTemplatedStepInfo().getTemplateRef() == null) {
      return Optional.empty();
    }

    Optional<TemplateResponseDTO> templateResponseDTO = getTemplate(filterCreationContext, templatedStepNode);
    if (templateResponseDTO.isPresent()) {
      String templateYaml = templateResponseDTO.get().getYaml();
      try {
        TemplateNode obj = YamlUtils.read(templateYaml, TemplateNode.class);
        if (obj != null) {
          TemplateConfigNode commandStepNode = obj.getTemplate();
          if (commandStepNode == null || commandStepNode.getSpec() == null
              || commandStepNode.getSpec().getCommandStepInfo() == null
                  && commandStepNode.getSpec().getCommandStepInfo().getCommandUnits() == null) {
            return Optional.empty();
          }
          if (!StepSpecTypeConstants.COMMAND.equals(commandStepNode.getSpec().getType())) {
            return Optional.empty();
          }
          return commandStepNode.getSpec()
              .getCommandStepInfo()
              .getCommandUnits()
              .stream()
              .filter(i -> CommandUnitSpecType.COPY.equals(i.getType()))
              .findFirst();
        }
      } catch (IOException e) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private Optional<TemplateResponseDTO> getTemplate(
      FilterCreationContext filterCreationContext, TemplatedStepNode templatedStepNode) {
    if (templatedStepNode == null) {
      return Optional.empty();
    }
    if (templatedStepNode.getTemplatedStepInfo() == null
        || templatedStepNode.getTemplatedStepInfo().getTemplateRef() == null) {
      return Optional.empty();
    }
    try {
      TemplateResponseDTO response = NGRestUtils.getResponse(
          templateResourceClient.get(unscoped(templatedStepNode.getTemplatedStepInfo().getTemplateRef()),
              filterCreationContext.getSetupMetadata().getAccountId(),
              scopedOrg(templatedStepNode.getTemplatedStepInfo().getTemplateRef(),
                  filterCreationContext.getSetupMetadata().getOrgId()),
              scopedProject(templatedStepNode.getTemplatedStepInfo().getTemplateRef(),
                  filterCreationContext.getSetupMetadata().getProjectId()),
              templatedStepNode.getTemplatedStepInfo().getVersionLabel(), false));
      if (response != null) {
        return Optional.of(response);
      }
    } catch (Exception e) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  private JsonNode findDeploymentType(FilterCreationContext filterCreationContext) {
    if (filterCreationContext == null || filterCreationContext.getCurrentField() == null) {
      return null;
    }
    YamlNode currentNode = filterCreationContext.getCurrentField().getNode();
    return findDeploymentTypeInHierarchy(currentNode);
  }

  private JsonNode findDeploymentTypeInHierarchy(YamlNode node) {
    if (node == null) {
      return null;
    }
    if (node.getCurrJsonNode() == null) {
      return node.getParentNode() != null ? findDeploymentTypeInHierarchy(node.getParentNode()) : null;
    }
    JsonNode deploymentType = node.getCurrJsonNode().get(YamlTypes.DEPLOYMENT_TYPE);
    if (deploymentType != null) {
      return deploymentType;
    } else {
      return findDeploymentTypeInHierarchy(node.getParentNode());
    }
  }

  private String unscoped(String identifier) {
    if (identifier.startsWith("account.")) {
      return identifier.replaceFirst("account.", "");
    } else if (identifier.startsWith("org.")) {
      return identifier.replaceFirst("org.", "");
    }
    return identifier;
  }

  private String scopedOrg(String identifier, String orgIdentifier) {
    if (identifier.startsWith("account.")) {
      return null;
    } else if (identifier.startsWith("org.")) {
      return orgIdentifier;
    }
    return orgIdentifier;
  }

  private String scopedProject(String identifier, String projectIdentifier) {
    if (identifier.startsWith("account.") || identifier.startsWith("org.")) {
      return null;
    }
    return projectIdentifier;
  }
}
