/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.stackdriver;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.GcpConfig;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.service.impl.analysis.LogDataCollectionInfo;

import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class StackDriverLogDataCollectionInfo extends LogDataCollectionInfo {
  private GcpConfig gcpConfig;
  private String logMessageField;
  private String projectId;

  @Builder
  public StackDriverLogDataCollectionInfo(GcpConfig gcpConfig, String accountId, String applicationId,
      String stateExecutionId, String cvConfigId, String workflowId, String workflowExecutionId, String serviceId,
      String query, long startTime, long endTime, int startMinute, int collectionTime, String hostnameField,
      Set<String> hosts, DelegateStateType stateType, List<EncryptedDataDetail> encryptedDataDetails,
      int initialDelayMinutes, String logMessageField, String projectId) {
    super(accountId, applicationId, stateExecutionId, cvConfigId, workflowId, workflowExecutionId, serviceId, query,
        startTime, endTime, startMinute, collectionTime, hostnameField, hosts, stateType, encryptedDataDetails,
        initialDelayMinutes);
    this.gcpConfig = gcpConfig;
    this.logMessageField = logMessageField;
    this.projectId = projectId;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return StackdriverUtils.fetchRequiredExecutionCapabilitiesForLogs(getEncryptedDataDetails(), maskingEvaluator);
  }
}
