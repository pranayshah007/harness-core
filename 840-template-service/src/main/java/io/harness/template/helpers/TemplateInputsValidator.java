/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.template.refresh.ErrorNodeSummary;
import io.harness.ng.core.template.refresh.TemplateInfo;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.ng.core.template.refresh.v2.ErrorNodeType;
import io.harness.ng.core.template.refresh.v2.NodeErrorSummary;
import io.harness.ng.core.template.refresh.v2.TemplateNodeErrorSummary;
import io.harness.ng.core.template.refresh.v2.ValidateInputsResponseDTO;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntityGetResponse;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class TemplateInputsValidator {
  @Inject private InputsValidator inputsValidator;

  public ValidateTemplateInputsResponseDTO validateNestedTemplateInputsForTemplates(
      String accountId, String orgId, String projectId, TemplateEntityGetResponse templateEntityGetResponse) {
    ValidateInputsResponseDTO validateInputsResponseDTO =
        inputsValidator.validateInputsForTemplate(accountId, orgId, projectId, templateEntityGetResponse);
    return toValidateTemplateInputsResponseDTO(validateInputsResponseDTO);
  }

  public ValidateTemplateInputsResponseDTO validateNestedTemplateInputsForGivenYaml(
      String accountId, String orgId, String projectId, String yaml, boolean loadFromCache) {
    ValidateInputsResponseDTO validateInputsResponseDTO =
        inputsValidator.validateInputsForYaml(accountId, orgId, projectId, yaml, loadFromCache);
    return toValidateTemplateInputsResponseDTO(validateInputsResponseDTO);
  }

  public ValidateTemplateInputsResponseDTO validateNestedTemplateInputsForGivenYaml(String accountId, String orgId,
      String projectId, String yaml, Map<String, TemplateEntity> templateCacheMap, boolean loadFromCache) {
    ValidateInputsResponseDTO validateInputsResponseDTO =
        inputsValidator.validateInputsForYaml(accountId, orgId, projectId, yaml, templateCacheMap, loadFromCache);
    return toValidateTemplateInputsResponseDTO(validateInputsResponseDTO);
  }

  private ValidateTemplateInputsResponseDTO toValidateTemplateInputsResponseDTO(
      ValidateInputsResponseDTO validateInputsResponseDTO) {
    NodeErrorSummary nodeErrorSummary = validateInputsResponseDTO.getNodeErrorSummary();
    ErrorNodeSummary errorNodeSummary = toErrorNodeSummary(nodeErrorSummary);
    populateChildrenErrorNodes(errorNodeSummary, nodeErrorSummary);
    return ValidateTemplateInputsResponseDTO.builder()
        .validYaml(validateInputsResponseDTO.isValidYaml())
        .errorNodeSummary(errorNodeSummary)
        .build();
  }

  private void populateChildrenErrorNodes(ErrorNodeSummary errorNodeSummary, NodeErrorSummary nodeErrorSummary) {
    if (EmptyPredicate.isEmpty(nodeErrorSummary.getChildrenErrorNodes())) {
      return;
    }
    List<ErrorNodeSummary> childrenNodes = new ArrayList<>();
    for (NodeErrorSummary childNode : nodeErrorSummary.getChildrenErrorNodes()) {
      ErrorNodeSummary newErrorNode = toErrorNodeSummary(childNode);
      populateChildrenErrorNodes(newErrorNode, childNode);
      childrenNodes.add(newErrorNode);
    }
    errorNodeSummary.setChildrenErrorNodes(childrenNodes);
  }

  private ErrorNodeSummary toErrorNodeSummary(NodeErrorSummary nodeErrorSummary) {
    if (nodeErrorSummary == null) {
      return null;
    }
    if (nodeErrorSummary.getType() == ErrorNodeType.TEMPLATE) {
      TemplateNodeErrorSummary templateNodeErrorSummary = (TemplateNodeErrorSummary) nodeErrorSummary;
      return ErrorNodeSummary.builder()
          .nodeInfo(nodeErrorSummary.getNodeInfo())
          .templateResponse(templateNodeErrorSummary.getTemplateResponse())
          .templateInfo(TemplateInfo.builder()
                            .templateIdentifier(templateNodeErrorSummary.getTemplateResponse().getIdentifier())
                            .versionLabel(templateNodeErrorSummary.getTemplateResponse().getVersionLabel())
                            .templateEntityType(templateNodeErrorSummary.getTemplateResponse().getTemplateEntityType())
                            .build())
          .childrenErrorNodes(new ArrayList<>())
          .build();
    }

    return ErrorNodeSummary.builder()
        .nodeInfo(nodeErrorSummary.getNodeInfo())
        .childrenErrorNodes(new ArrayList<>())
        .build();
  }
}
