/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.servicenow.entities;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapperDTO;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalOutCome;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalSpecParameters;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@Builder
@FieldNameConstants(innerTypeName = "ServiceNowApprovalInstanceKeys")
@EqualsAndHashCode(callSuper = false)
@Entity(value = "approvalInstances", noClassnameStored = true)
@Persistent
@TypeAlias("serviceNowApprovalInstances")
public class ServiceNowApprovalInstance extends ApprovalInstance {
  @NotEmpty String connectorRef;
  @NotEmpty String ticketNumber;
  @NotEmpty String ticketType;
  @NotNull CriteriaSpecWrapperDTO approvalCriteria;
  CriteriaSpecWrapperDTO rejectionCriteria;
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  public static ServiceNowApprovalInstance fromStepParameters(Ambiance ambiance, StepElementParameters stepParameters) {
    if (stepParameters == null) {
      return null;
    }

    ServiceNowApprovalSpecParameters specParameters = (ServiceNowApprovalSpecParameters) stepParameters.getSpec();
    String ticketNumber = specParameters.getTicketNumber().getValue();
    String connectorRef = specParameters.getConnectorRef().getValue();

    if (isBlank(ticketNumber)) {
      throw new InvalidRequestException("ticketNumber can't be empty");
    }
    if (isBlank(connectorRef)) {
      throw new InvalidRequestException("connectorRef can't be empty");
    }

    ServiceNowApprovalInstance instance =
        ServiceNowApprovalInstance.builder()
            .connectorRef(connectorRef)
            .ticketNumber(ticketNumber)
            .ticketType(specParameters.getTicketType().getValue())
            .approvalCriteria(
                CriteriaSpecWrapperDTO.fromCriteriaSpecWrapper(specParameters.getApprovalCriteria(), false))
            .rejectionCriteria(
                CriteriaSpecWrapperDTO.fromCriteriaSpecWrapper(specParameters.getRejectionCriteria(), true))
            .delegateSelectors(specParameters.getDelegateSelectors())
            .build();
    instance.updateFromStepParameters(ambiance, stepParameters);
    return instance;
  }

  public ServiceNowApprovalOutCome toServiceNowApprovalOutcome() {
    return ServiceNowApprovalOutCome.builder().build();
  }
}
