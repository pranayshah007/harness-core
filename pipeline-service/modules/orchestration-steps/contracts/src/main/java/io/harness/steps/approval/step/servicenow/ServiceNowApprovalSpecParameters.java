/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapper;
import io.harness.steps.approval.step.beans.ServiceNowChangeWindowSpec;
import io.harness.yaml.core.timeout.Timeout;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("serviceNowApprovalSpecParameters")
@RecasterAlias("io.harness.steps.approval.step.servicenow.ServiceNowApprovalSpecParameters")
public class ServiceNowApprovalSpecParameters implements SpecParameters {
  @NotNull ParameterField<String> connectorRef;
  @NotNull ParameterField<String> ticketNumber;
  @NotNull ParameterField<String> ticketType;
  @NotNull CriteriaSpecWrapper approvalCriteria;
  CriteriaSpecWrapper rejectionCriteria;
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;
  ServiceNowChangeWindowSpec changeWindowSpec;
  ParameterField<Timeout> retryInterval;
}
