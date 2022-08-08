/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.ServiceNowConfig;
import software.wings.delegatetasks.servicenow.ServiceNowAction;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@OwnedBy(CDC)
@Data
@Builder
@ToString(exclude = {"encryptionDetails"})
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@BreakDependencyOn("software.wings.service.impl.servicenow.ServiceNowServiceImpl")
public class ServiceNowTaskParameters implements TaskParameters, ExecutionCapabilityDemander {
  private String accountId;
  private ServiceNowConfig serviceNowConfig;
  private ServiceNowAction action;
  private String issueNumber;
  private String issueId;
  List<EncryptedDataDetail> encryptionDetails;
  private ServiceNowTicketType ticketType;
  private Map<ServiceNowFields, String> fields;
  private Map<String, String> additionalFields;
  private boolean updateMultiple;
  private ServiceNowFieldType typeFilter;
  // import set fields
  private String importSetTableName;
  private String jsonBody;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    // As it extends TaskParameters, no need to pass encryptionDetails.
    // It will be resolved to valut capability in DelegateSErviceImp
    return serviceNowConfig.fetchRequiredExecutionCapabilities(maskingEvaluator);
  }
}
