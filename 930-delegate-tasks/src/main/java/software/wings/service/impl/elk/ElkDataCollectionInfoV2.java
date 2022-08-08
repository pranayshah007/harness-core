/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.elk;

import static software.wings.beans.TaskType.ELK_COLLECT_LOG_DATAV2;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.ElkConfig;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.cv.DataCollector;
import software.wings.delegatetasks.cv.ElkDataCollector;
import software.wings.delegatetasks.delegatecapability.CapabilityHelper;
import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.analysis.LogDataCollectionInfoV2;
import software.wings.settings.SettingValue;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
@FieldNameConstants(innerTypeName = "ElkDataCollectionInfoV2Keys")
@Data
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class ElkDataCollectionInfoV2 extends LogDataCollectionInfoV2 {
  private ElkConfig elkConfig;
  private String indices;
  private String messageField;
  private String timestampField;
  private String timestampFieldFormat;
  private ElkQueryType queryType;
  @Builder
  public ElkDataCollectionInfoV2(String accountId, String applicationId, String envId, Instant startTime,
      Instant endTime, Set<String> hosts, String cvConfigId, String stateExecutionId, String workflowId,
      String workflowExecutionId, String serviceId, String cvTaskId, String connectorId, String query,
      String hostnameField, List<EncryptedDataDetail> encryptedDataDetails, Instant dataCollectionStartTime,
      ElkConfig elkConfig, String indices, String messageField, String timestampField, String timestampFieldFormat,
      ElkQueryType queryType) {
    super(accountId, applicationId, envId, startTime, endTime, hosts, cvConfigId, stateExecutionId, workflowId,
        workflowExecutionId, serviceId, cvTaskId, connectorId, encryptedDataDetails, dataCollectionStartTime, query,
        hostnameField);
    this.elkConfig = elkConfig;
    this.indices = indices;
    this.messageField = messageField;
    this.timestampField = timestampField;
    this.timestampFieldFormat = timestampFieldFormat;
    this.queryType = queryType;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return CapabilityHelper.generateDelegateCapabilities(elkConfig, getEncryptedDataDetails(), maskingEvaluator);
  }

  @Override
  public TaskType getTaskType() {
    return ELK_COLLECT_LOG_DATAV2;
  }

  @Override
  public DelegateStateType getStateType() {
    return DelegateStateType.ELK;
  }

  @Override
  public Class<? extends DataCollector<? extends DataCollectionInfoV2>> getDataCollectorImplClass() {
    return ElkDataCollector.class;
  }

  @Override
  public Optional<String> getUrlForValidation() {
    return Optional.of(elkConfig.getElkUrl());
  }

  @Override
  public Optional<EncryptionConfig> getEncryptionConfig() {
    if (getEncryptedDataDetails().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(getEncryptedDataDetails().get(0).getEncryptionConfig());
  }

  @Override
  public Optional<EncryptableSetting> getEncryptableSetting() {
    return Optional.of(elkConfig);
  }

  @Override
  public DataCollectionInfoV2 deepCopy() {
    ElkDataCollectionInfoV2 elkDataCollectionInfo = ElkDataCollectionInfoV2.builder()
                                                        .elkConfig(elkConfig)
                                                        .indices(indices)
                                                        .messageField(messageField)
                                                        .timestampField(timestampField)
                                                        .timestampFieldFormat(timestampFieldFormat)
                                                        .queryType(queryType)
                                                        .build();
    super.copy(elkDataCollectionInfo);
    return elkDataCollectionInfo;
  }

  @Override
  public void setSettingValue(SettingValue settingValue) {
    elkConfig = (ElkConfig) settingValue;
  }

  @Override
  protected void validateParams() {
    Preconditions.checkNotNull(elkConfig, ElkDataCollectionInfoV2Keys.elkConfig);
    Preconditions.checkNotNull(indices, ElkDataCollectionInfoV2Keys.indices);
    Preconditions.checkNotNull(messageField, ElkDataCollectionInfoV2Keys.messageField);
    Preconditions.checkNotNull(timestampField, ElkDataCollectionInfoV2Keys.timestampField);
    Preconditions.checkNotNull(timestampFieldFormat, ElkDataCollectionInfoV2Keys.timestampFieldFormat);
  }
}
