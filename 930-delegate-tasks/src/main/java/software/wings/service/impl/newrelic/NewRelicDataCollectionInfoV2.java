/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.newrelic;

import static software.wings.beans.TaskType.NEWRELIC_COLLECT_METRIC_DATAV2;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.NewRelicConfig;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.cv.NewRelicDataCollector;
import software.wings.delegatetasks.delegatecapability.CapabilityHelper;
import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.analysis.MetricsDataCollectionInfo;
import software.wings.settings.SettingValue;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
@FieldNameConstants(innerTypeName = "NewRelicDataCollectionInfoV2Keys")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NewRelicDataCollectionInfoV2 extends MetricsDataCollectionInfo {
  private NewRelicConfig newRelicConfig;
  private long newRelicAppId;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return CapabilityHelper.generateDelegateCapabilities(newRelicConfig, getEncryptedDataDetails(), maskingEvaluator);
  }

  @Builder
  public NewRelicDataCollectionInfoV2(String accountId, String applicationId, String envId, Instant startTime,
      Instant endTime, Set<String> hosts, String cvConfigId, String stateExecutionId, String workflowId,
      String workflowExecutionId, String serviceId, String cvTaskId, String connectorId,
      List<EncryptedDataDetail> encryptedDataDetails, Instant dataCollectionStartTime, NewRelicConfig newRelicConfig,
      long newRelicAppId, Map<String, String> hostsToGroupNameMap) {
    super(accountId, applicationId, envId, startTime, endTime, hosts, cvConfigId, stateExecutionId, workflowId,
        workflowExecutionId, serviceId, cvTaskId, connectorId, encryptedDataDetails, dataCollectionStartTime,
        hostsToGroupNameMap, true);
    this.newRelicConfig = newRelicConfig;
    this.newRelicAppId = newRelicAppId;
  }

  @Override
  public TaskType getTaskType() {
    return NEWRELIC_COLLECT_METRIC_DATAV2;
  }

  @Override
  public DelegateStateType getStateType() {
    return DelegateStateType.NEW_RELIC;
  }

  @Override
  public Class<NewRelicDataCollector> getDataCollectorImplClass() {
    return NewRelicDataCollector.class;
  }

  @Override
  public Optional<String> getUrlForValidation() {
    return Optional.of(newRelicConfig.getNewRelicUrl());
  }

  @Override
  public Optional<EncryptionConfig> getEncryptionConfig() {
    return Optional.of(getEncryptedDataDetails().get(0).getEncryptionConfig());
  }

  @Override
  public Optional<EncryptableSetting> getEncryptableSetting() {
    return Optional.of(newRelicConfig);
  }

  @Override
  public DataCollectionInfoV2 deepCopy() {
    NewRelicDataCollectionInfoV2 newRelicDataCollectionInfo =
        NewRelicDataCollectionInfoV2.builder().newRelicConfig(this.newRelicConfig).newRelicAppId(newRelicAppId).build();
    super.copy(newRelicDataCollectionInfo);
    return newRelicDataCollectionInfo;
  }

  @Override
  public void setSettingValue(SettingValue settingValue) {
    newRelicConfig = (NewRelicConfig) settingValue;
  }

  @Override
  protected void validateParams() {
    Preconditions.checkNotNull(newRelicConfig, NewRelicDataCollectionInfoV2Keys.newRelicConfig);
    Preconditions.checkNotNull(newRelicAppId, NewRelicDataCollectionInfoV2Keys.newRelicAppId);
  }
}
