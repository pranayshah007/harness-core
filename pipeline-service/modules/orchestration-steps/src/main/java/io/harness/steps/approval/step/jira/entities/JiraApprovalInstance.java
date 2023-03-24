/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.jira.entities;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.DbAliases;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapperDTO;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.jira.JiraApprovalOutcome;
import io.harness.steps.approval.step.jira.JiraApprovalSpecParameters;

import dev.morphia.annotations.Entity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@Builder
@FieldNameConstants(innerTypeName = "JiraApprovalInstanceKeys")
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.PMS)
@Entity(value = "approvalInstances", noClassnameStored = true)
@Persistent
@TypeAlias("jiraApprovalInstances")
public class JiraApprovalInstance extends ApprovalInstance {
  @NotEmpty String connectorRef;
  @NotEmpty String issueKey;
  String issueType;
  String projectKey;
  @NotNull CriteriaSpecWrapperDTO approvalCriteria;
  CriteriaSpecWrapperDTO rejectionCriteria;
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;
  Map<String, Object> ticketFields;

  public static JiraApprovalInstance fromStepParameters(Ambiance ambiance, StepElementParameters stepParameters) {
    if (stepParameters == null) {
      return null;
    }

    JiraApprovalSpecParameters specParameters = (JiraApprovalSpecParameters) stepParameters.getSpec();
    String issueKey = specParameters.getIssueKey().getValue();
    String connectorRef = specParameters.getConnectorRef().getValue();

    if (isBlank(issueKey)) {
      throw new InvalidRequestException("issueKey can't be empty");
    }
    if (isBlank(connectorRef)) {
      throw new InvalidRequestException("connectorRef can't be empty");
    }

    String issueType = specParameters.getIssueType();

    String projectKey = specParameters.getProjectKey();

    JiraApprovalInstance instance =
        JiraApprovalInstance.builder()
            .connectorRef(connectorRef)
            .issueKey(issueKey)
            .approvalCriteria(
                CriteriaSpecWrapperDTO.fromCriteriaSpecWrapper(specParameters.getApprovalCriteria(), false))
            .rejectionCriteria(
                CriteriaSpecWrapperDTO.fromCriteriaSpecWrapper(specParameters.getRejectionCriteria(), true))
            .delegateSelectors(specParameters.getDelegateSelectors())
            .issueType(issueType)
            .projectKey(projectKey)
            .ticketFields(new HashMap<>())
            .build();
    instance.updateFromStepParameters(ambiance, stepParameters);
    return instance;
  }

  public JiraApprovalOutcome toJiraApprovalOutcome() {
    return JiraApprovalOutcome.builder().build();
  }
}
