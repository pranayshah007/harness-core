/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.template.beans.refresh.ErrorNodeSummary;
import io.harness.template.beans.refresh.TemplateInfo;
import io.harness.template.beans.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.template.beans.refresh.YamlDiffResponseDTO;
import io.harness.template.beans.refresh.YamlFullRefreshResponseDTO;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.helpers.TemplateInputsRefreshHelper;
import io.harness.template.helpers.TemplateInputsValidator;
import io.harness.template.mappers.NGTemplateDtoMapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
@OwnedBy(CDC)
public class TemplateRefreshServiceImpl implements TemplateRefreshService {
  private TemplateInputsRefreshHelper templateInputsRefreshHelper;
  private NGTemplateService templateService;
  private TemplateInputsValidator templateInputsValidator;

  @Override
  public void refreshAndUpdateTemplate(
      String accountId, String orgId, String projectId, String templateIdentifier, String versionLabel) {
    TemplateEntity template = getTemplate(accountId, orgId, projectId, templateIdentifier, versionLabel);

    String refreshedYaml = refreshLinkedTemplateInputs(accountId, orgId, projectId, template.getYaml());
    TemplateEntity templateEntity = NGTemplateDtoMapper.toTemplateEntity(
        accountId, orgId, projectId, templateIdentifier, versionLabel, refreshedYaml);
    templateService.updateTemplateEntity(templateEntity, ChangeType.MODIFY, false, "Refreshed template inputs");
  }

  private TemplateEntity getTemplate(
      String accountId, String orgId, String projectId, String templateIdentifier, String versionLabel) {
    Optional<TemplateEntity> optionalTemplateEntity =
        templateService.get(accountId, orgId, projectId, templateIdentifier, versionLabel, false);

    if (!optionalTemplateEntity.isPresent()) {
      throw new InvalidRequestException(
          String.format("Template with the Identifier %s and versionLabel %s does not exist or has been deleted",
              templateIdentifier, versionLabel));
    }
    return optionalTemplateEntity.get();
  }

  @Override
  public String refreshLinkedTemplateInputs(String accountId, String orgId, String projectId, String yaml) {
    return templateInputsRefreshHelper.refreshTemplates(accountId, orgId, projectId, yaml);
  }

  @Override
  public ValidateTemplateInputsResponseDTO validateTemplateInputsInTemplate(
      String accountId, String orgId, String projectId, String templateIdentifier, String versionLabel) {
    TemplateEntity template = getTemplate(accountId, orgId, projectId, templateIdentifier, versionLabel);

    ValidateTemplateInputsResponseDTO validateTemplateInputsResponse =
        templateInputsValidator.validateNestedTemplateInputsForTemplates(accountId, orgId, projectId, template);

    if (!validateTemplateInputsResponse.isValidYaml()) {
      return validateTemplateInputsResponse;
    }
    return ValidateTemplateInputsResponseDTO.builder().validYaml(true).build();
  }

  @Override
  public ValidateTemplateInputsResponseDTO validateTemplateInputsForYaml(
      String accountId, String orgId, String projectId, String yaml) {
    return templateInputsValidator.validateNestedTemplateInputsForGivenYaml(accountId, orgId, projectId, yaml);
  }

  @Override
  public YamlDiffResponseDTO getYamlDiffOnRefreshingTemplate(
      String accountId, String orgId, String projectId, String templateIdentifier, String versionLabel) {
    TemplateEntity template = getTemplate(accountId, orgId, projectId, templateIdentifier, versionLabel);

    String templateYaml = template.getYaml();
    String refreshedYaml = refreshLinkedTemplateInputs(accountId, orgId, projectId, templateYaml);

    return YamlDiffResponseDTO.builder().originalYaml(templateYaml).refreshedYaml(refreshedYaml).build();
  }

  @Override
  public void recursivelyRefreshTemplates(
      String accountId, String orgId, String projectId, String templateIdentifier, String versionLabel) {
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponse =
        validateTemplateInputsInTemplate(accountId, orgId, projectId, templateIdentifier, versionLabel);
    if (validateTemplateInputsResponse.isValidYaml()) {
      return;
    }

    refreshTemplateInputsForErrorNodes(
        accountId, orgId, projectId, validateTemplateInputsResponse.getErrorNodeSummary());
  }

  @Override
  public YamlFullRefreshResponseDTO recursivelyRefreshTemplatesForYaml(
      String accountId, String orgId, String projectId, String yaml) {
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponse =
        validateTemplateInputsForYaml(accountId, orgId, projectId, yaml);

    if (validateTemplateInputsResponse.isValidYaml()) {
      return YamlFullRefreshResponseDTO.builder().shouldRefreshYaml(false).build();
    }

    refreshTemplateInputsForErrorNodes(
        accountId, orgId, projectId, validateTemplateInputsResponse.getErrorNodeSummary());
    String refreshedYaml = refreshLinkedTemplateInputs(accountId, orgId, projectId, yaml);
    return YamlFullRefreshResponseDTO.builder().shouldRefreshYaml(true).refreshedYaml(refreshedYaml).build();
  }

  private void refreshTemplateInputsForErrorNodes(
      String accountId, String orgId, String projectId, ErrorNodeSummary errorNodeSummary) {
    if (errorNodeSummary == null) {
      return;
    }

    Stack<ErrorNodeSummary> orderedStack = getProcessingOrderOfErrorNodes(errorNodeSummary);
    Set<TemplateInfo> visitedTemplateSet = new HashSet<>();
    while (!orderedStack.isEmpty()) {
      ErrorNodeSummary top = orderedStack.pop();
      if (top.getTemplateInfo() == null) {
        continue;
      }

      TemplateInfo templateInfo = top.getTemplateInfo();
      if (!visitedTemplateSet.contains(templateInfo)) {
        refreshAndUpdateTemplate(
            accountId, orgId, projectId, templateInfo.getTemplateIdentifier(), templateInfo.getVersionLabel());
        visitedTemplateSet.add(templateInfo);
      }
    }
  }

  private Stack<ErrorNodeSummary> getProcessingOrderOfErrorNodes(ErrorNodeSummary errorNodeSummary) {
    Stack<ErrorNodeSummary> processingStack = new Stack<>();
    Stack<ErrorNodeSummary> outputStack = new Stack<>();

    processingStack.push(errorNodeSummary);

    while (!processingStack.isEmpty()) {
      ErrorNodeSummary top = processingStack.pop();
      outputStack.push(top);
      if (EmptyPredicate.isEmpty(top.getChildrenErrorNodes())) {
        continue;
      }
      for (ErrorNodeSummary childrenErrorNodeSummary : top.getChildrenErrorNodes()) {
        processingStack.push(childrenErrorNodeSummary);
      }
    }
    return outputStack;
  }
}
