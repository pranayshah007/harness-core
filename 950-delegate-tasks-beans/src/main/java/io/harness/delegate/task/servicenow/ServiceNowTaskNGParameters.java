/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.servicenow;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.servicenow.ServiceNowCapabilityHelper;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.servicenow.ServiceNowActionNG;

import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceNowTaskNGParameters implements TaskParameters, ExecutionCapabilityDemander {
  ServiceNowConnectorDTO serviceNowConnectorDTO;
  List<EncryptedDataDetail> encryptionDetails;

  ServiceNowActionNG action;
  String ticketType;
  String ticketNumber;
  String ticketId;

  // paginated template list
  int templateListLimit;
  int templateListOffset;
  String templateListSearchTerm;

  /// Fields sent while creating/updating issue.
  Map<String, String> fields;

  // templateName
  String templateName;
  // use template for creating/updating issues
  boolean useServiceNowTemplate;

  // import set fields
  String stagingTableName;
  @Expression(ALLOW_SECRETS) String importData;

  List<String> delegateSelectors;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return ServiceNowCapabilityHelper.generateDelegateCapabilities(
        serviceNowConnectorDTO, encryptionDetails, maskingEvaluator);
  }
}
