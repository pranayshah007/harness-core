/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import io.harness.security.encryption.EncryptedDataDetail;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
@FieldNameConstants(innerTypeName = "LogDataCollectionInfoV2Keys")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public abstract class LogDataCollectionInfoV2 extends DataCollectionInfoV2 {
  private String query;
  private String hostnameField;

  public LogDataCollectionInfoV2(String accountId, String applicationId, String envId, Instant startTime,
      Instant endTime, Set<String> hosts, String cvConfigId, String stateExecutionId, String workflowId,
      String workflowExecutionId, String serviceId, String cvTaskId, String connectorId,
      List<EncryptedDataDetail> encryptedDataDetails, Instant dataCollectionStartTime, String query,
      String hostnameField) {
    super(accountId, applicationId, envId, startTime, endTime, hosts, cvConfigId, stateExecutionId, workflowId,
        workflowExecutionId, serviceId, cvTaskId, connectorId, encryptedDataDetails, dataCollectionStartTime, true);
    this.query = query;
    this.hostnameField = hostnameField;
  }

  protected void copy(LogDataCollectionInfoV2 logDataCollectionInfo) {
    super.copy(logDataCollectionInfo);
    logDataCollectionInfo.setQuery(this.query);
    logDataCollectionInfo.setHostnameField(this.hostnameField);
  }

  @Override
  public void validate() {
    super.validate();
    Preconditions.checkNotNull(query, LogDataCollectionInfoV2Keys.query);
    Preconditions.checkNotNull(hostnameField, LogDataCollectionInfoV2Keys.hostnameField);
  }
}
