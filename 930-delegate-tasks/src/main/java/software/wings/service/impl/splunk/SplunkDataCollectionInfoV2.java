/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.splunk;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.SplunkConfig;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.cv.SplunkDataCollector;
import software.wings.delegatetasks.delegatecapability.CapabilityHelper;
import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.analysis.LogDataCollectionInfoV2;
import software.wings.settings.SettingValue;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
@FieldNameConstants(innerTypeName = "SplunkDataCollectionInfoV2Keys")
@Data
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class SplunkDataCollectionInfoV2 extends LogDataCollectionInfoV2 {
  @Getter @Setter(AccessLevel.PRIVATE) private SplunkConfig splunkConfig;
  private boolean isAdvancedQuery;

  @Builder
  public SplunkDataCollectionInfoV2(String accountId, String applicationId, String envId, Instant startTime,
      Instant endTime, Set<String> hosts, String cvConfigId, String stateExecutionId, String workflowId,
      String workflowExecutionId, String serviceId, String cvTaskId, String connectorId, String query,
      String hostnameField, List<EncryptedDataDetail> encryptedDataDetails, Instant dataCollectionStartTime,
      SplunkConfig splunkConfig, boolean isAdvancedQuery) {
    super(accountId, applicationId, envId, startTime, endTime, hosts, cvConfigId, stateExecutionId, workflowId,
        workflowExecutionId, serviceId, cvTaskId, connectorId, encryptedDataDetails, dataCollectionStartTime, query,
        hostnameField);
    this.splunkConfig = splunkConfig;
    this.isAdvancedQuery = isAdvancedQuery;
  }
  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return CapabilityHelper.generateDelegateCapabilities(splunkConfig, getEncryptedDataDetails(), maskingEvaluator);
  }

  @Override
  public TaskType getTaskType() {
    return TaskType.SPLUNK_COLLECT_LOG_DATAV2;
  }

  @Override
  public DelegateStateType getStateType() {
    return DelegateStateType.SPLUNKV2;
  }

  @Override
  public Class<SplunkDataCollector> getDataCollectorImplClass() {
    return SplunkDataCollector.class;
  }

  @Override
  public Optional<String> getUrlForValidation() {
    return Optional.of(splunkConfig.getSplunkUrl());
  }

  @Override
  public Optional<EncryptionConfig> getEncryptionConfig() {
    return Optional.of(getEncryptedDataDetails().get(0).getEncryptionConfig());
  }

  @Override
  public Optional<EncryptableSetting> getEncryptableSetting() {
    return Optional.of(splunkConfig);
  }

  @Override
  public DataCollectionInfoV2 deepCopy() {
    SplunkDataCollectionInfoV2 splunkDataCollectionInfoV2 =
        SplunkDataCollectionInfoV2.builder().isAdvancedQuery(this.isAdvancedQuery).build();
    splunkDataCollectionInfoV2.setSplunkConfig(splunkConfig);
    super.copy(splunkDataCollectionInfoV2);
    return splunkDataCollectionInfoV2;
  }
  @Override
  public void setSettingValue(SettingValue settingValue) {
    setSplunkConfig((SplunkConfig) settingValue);
  }

  @Override
  protected void validateParams() {
    Preconditions.checkNotNull(splunkConfig, SplunkDataCollectionInfoV2Keys.splunkConfig);
  }
}
