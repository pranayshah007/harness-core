/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instana;

import static software.wings.beans.TaskType.INSTANA_COLLECT_METRIC_DATA;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.InstanaConfig;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.cv.DataCollector;
import software.wings.delegatetasks.delegatecapability.CapabilityHelper;
import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.analysis.MetricsDataCollectionInfo;
import software.wings.settings.SettingValue;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "InstanaDataCollectionInfoKeys")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InstanaDataCollectionInfo extends MetricsDataCollectionInfo {
  private InstanaConfig instanaConfig;
  private String query;
  private List<String> metrics;
  private List<InstanaTagFilter> tagFilters;
  private String hostTagFilter;

  @Builder
  public InstanaDataCollectionInfo(String accountId, String applicationId, String envId, Instant startTime,
      Instant endTime, Set<String> hosts, String cvConfigId, String stateExecutionId, String workflowId,
      String workflowExecutionId, String serviceId, String cvTaskId, String connectorId,
      List<EncryptedDataDetail> encryptedDataDetails, Instant dataCollectionStartTime,
      Map<String, String> hostsToGroupNameMap, InstanaConfig instanaConfig, String query, List<String> metrics,
      String hostTagFilter, List<InstanaTagFilter> tagFilters) {
    super(accountId, applicationId, envId, startTime, endTime, hosts, cvConfigId, stateExecutionId, workflowId,
        workflowExecutionId, serviceId, cvTaskId, connectorId, encryptedDataDetails, dataCollectionStartTime,
        hostsToGroupNameMap, true);
    this.query = query;
    this.metrics = metrics;
    this.instanaConfig = instanaConfig;
    this.hostTagFilter = hostTagFilter;
    this.tagFilters = tagFilters;
  }

  public List<InstanaTagFilter> getTagFilters() {
    if (tagFilters == null) {
      return Collections.emptyList();
    }
    return new ArrayList<>(tagFilters);
  }

  public List<String> getMetrics() {
    if (metrics == null) {
      return Collections.emptyList();
    }
    return new ArrayList<>(metrics);
  }
  @Override
  public TaskType getTaskType() {
    return INSTANA_COLLECT_METRIC_DATA;
  }

  @Override
  public DelegateStateType getStateType() {
    return DelegateStateType.INSTANA;
  }

  @Override
  public Class<? extends DataCollector<? extends DataCollectionInfoV2>> getDataCollectorImplClass() {
    return InstanaDataCollector.class;
  }

  @Override
  public Optional<String> getUrlForValidation() {
    return Optional.of(instanaConfig.getInstanaUrl());
  }

  @Override
  public Optional<EncryptionConfig> getEncryptionConfig() {
    return Optional.of(getEncryptedDataDetails().get(0).getEncryptionConfig());
  }

  @Override
  public Optional<EncryptableSetting> getEncryptableSetting() {
    return Optional.of(instanaConfig);
  }

  @Override
  public DataCollectionInfoV2 deepCopy() {
    InstanaDataCollectionInfo instanaDataCollectionInfo = InstanaDataCollectionInfo.builder()
                                                              .metrics(new ArrayList<>(getMetrics()))
                                                              .query(this.query)
                                                              .instanaConfig(instanaConfig)
                                                              .hostTagFilter(hostTagFilter)
                                                              .tagFilters(new ArrayList<>(getTagFilters()))
                                                              .build();
    super.copy(instanaDataCollectionInfo);
    return instanaDataCollectionInfo;
  }

  @Override
  public void setSettingValue(SettingValue settingValue) {
    instanaConfig = (InstanaConfig) settingValue;
  }

  @Override
  protected void validateParams() {
    Preconditions.checkNotNull(instanaConfig, InstanaDataCollectionInfoKeys.instanaConfig);
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return CapabilityHelper.generateDelegateCapabilities(instanaConfig, getEncryptedDataDetails(), maskingEvaluator);
  }
}
