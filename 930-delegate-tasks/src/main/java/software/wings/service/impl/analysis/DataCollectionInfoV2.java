/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.cv.DataCollector;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingValue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
@FieldNameConstants(innerTypeName = "DataCollectionInfoV2Keys")
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class DataCollectionInfoV2 implements TaskParameters, ExecutionCapabilityDemander {
  private String accountId;
  private String applicationId;
  private String envId;
  private Instant startTime;
  private Instant endTime;
  private Set<String> hosts;
  private String cvConfigId;
  private String stateExecutionId;
  private String workflowId;
  private String workflowExecutionId;
  private String serviceId;
  private String cvTaskId;
  private String connectorId;
  private List<EncryptedDataDetail> encryptedDataDetails;
  private Instant dataCollectionStartTime;
  private boolean shouldSendHeartbeat = true;

  public Set<String> getHosts() {
    // morphia converts empty objects to null while saving to database so making sure it's always returns empty set if
    // null.
    if (hosts == null) {
      return Collections.emptySet();
    }
    return hosts;
  }
  @JsonIgnore public abstract TaskType getTaskType();
  @JsonIgnore public abstract DelegateStateType getStateType();
  @JsonIgnore
  public abstract Class<? extends DataCollector<? extends DataCollectionInfoV2>> getDataCollectorImplClass();
  @JsonIgnore public abstract Optional<String> getUrlForValidation();

  @JsonIgnore public abstract Optional<EncryptionConfig> getEncryptionConfig();
  @JsonIgnore public abstract Optional<EncryptableSetting> getEncryptableSetting();

  @JsonIgnore public abstract DataCollectionInfoV2 deepCopy();
  public abstract void setSettingValue(SettingValue settingValue);
  protected abstract void validateParams();
  protected void copy(DataCollectionInfoV2 dataCollectionInfo) {
    dataCollectionInfo.setAccountId(this.accountId);
    dataCollectionInfo.setApplicationId(this.applicationId);
    dataCollectionInfo.setEnvId(this.envId);
    dataCollectionInfo.setStartTime(this.startTime);
    dataCollectionInfo.setEndTime(this.endTime);
    dataCollectionInfo.setHosts(new HashSet<>(this.hosts));
    dataCollectionInfo.setCvConfigId(this.cvConfigId);
    dataCollectionInfo.setStateExecutionId(this.stateExecutionId);
    dataCollectionInfo.setWorkflowId(this.workflowId);
    dataCollectionInfo.setWorkflowExecutionId(this.workflowExecutionId);
    dataCollectionInfo.setServiceId(this.serviceId);
    dataCollectionInfo.setCvTaskId(this.cvTaskId);
    dataCollectionInfo.setConnectorId(this.connectorId);
    dataCollectionInfo.setShouldSendHeartbeat(this.shouldSendHeartbeat);
    if (encryptedDataDetails != null) {
      dataCollectionInfo.setEncryptedDataDetails(new ArrayList<>(encryptedDataDetails));
    }
  }

  public void validate() {
    Preconditions.checkNotNull(cvTaskId, DataCollectionInfoV2Keys.cvTaskId);
    Preconditions.checkNotNull(connectorId, DataCollectionInfoV2Keys.connectorId);
    Preconditions.checkNotNull(accountId, DataCollectionInfoV2Keys.accountId);
    Preconditions.checkNotNull(startTime, DataCollectionInfoV2Keys.startTime);
    Preconditions.checkNotNull(endTime, DataCollectionInfoV2Keys.endTime);
    Preconditions.checkNotNull(stateExecutionId, DataCollectionInfoV2Keys.stateExecutionId);
    Preconditions.checkNotNull(applicationId, DataCollectionInfoV2Keys.applicationId);
    validateParams();
  }

  public void setEncryptionDataDetails(SecretManager secretManager) {
    getEncryptableSetting().ifPresent(encryptableSetting
        -> setEncryptedDataDetails(
            secretManager.getEncryptionDetails(encryptableSetting, getApplicationId(), getWorkflowExecutionId())));
  }
}
