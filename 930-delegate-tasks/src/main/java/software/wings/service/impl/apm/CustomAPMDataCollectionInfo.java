/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.apm;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.TaskType.CUSTOM_APM_COLLECT_METRICS_V2;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.APMVerificationConfig;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.cv.CustomAPMDataCollector;
import software.wings.delegatetasks.cv.DataCollector;
import software.wings.delegatetasks.delegatecapability.CapabilityHelper;
import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.analysis.MetricsDataCollectionInfo;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingValue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

/**
 * @author Praveen
 */
@FieldNameConstants(innerTypeName = "CustomAPMDataCollectionInfoKeys")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CustomAPMDataCollectionInfo extends MetricsDataCollectionInfo {
  private APMVerificationConfig apmConfig;
  private Map<String, String> headers;
  private Map<String, String> options;
  private List<APMMetricInfo> canaryMetricInfos;
  private List<APMMetricInfo> metricEndpoints;

  @Builder
  public CustomAPMDataCollectionInfo(String accountId, String applicationId, String envId, Instant startTime,
      Instant endTime, Set<String> hosts, String cvConfigId, String stateExecutionId, String workflowId,
      String workflowExecutionId, String serviceId, String cvTaskId, String connectorId,
      Map<String, String> hostsToGroupNameMap, Instant dataCollectionStartTime, APMVerificationConfig apmConfig,
      Map<String, String> headers, Map<String, String> options, List<EncryptedDataDetail> encryptedDataDetails,
      List<APMMetricInfo> canaryMetricInfos, List<APMMetricInfo> metricEndpoints, boolean shouldSendHeartbeat) {
    super(accountId, applicationId, envId, startTime, endTime, hosts, cvConfigId, stateExecutionId, workflowId,
        workflowExecutionId, serviceId, cvTaskId, connectorId, encryptedDataDetails, dataCollectionStartTime,
        hostsToGroupNameMap, shouldSendHeartbeat);
    this.apmConfig = apmConfig;
    this.headers = headers;
    this.options = options;
    this.canaryMetricInfos = canaryMetricInfos;
    this.metricEndpoints = metricEndpoints;
  }
  @Override
  public TaskType getTaskType() {
    return CUSTOM_APM_COLLECT_METRICS_V2;
  }

  @Override
  public DelegateStateType getStateType() {
    return DelegateStateType.APM_VERIFICATION;
  }

  @Override
  public Class<? extends DataCollector<? extends DataCollectionInfoV2>> getDataCollectorImplClass() {
    return CustomAPMDataCollector.class;
  }

  @Override
  public Optional<String> getUrlForValidation() {
    // We will use APMValidation.java for the validation of this task
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<EncryptionConfig> getEncryptionConfig() {
    return Optional.empty();
  }

  @Override
  public Optional<EncryptableSetting> getEncryptableSetting() {
    return Optional.of(apmConfig);
  }

  @Override
  public DataCollectionInfoV2 deepCopy() {
    CustomAPMDataCollectionInfo customAPMDataCollectionInfo =
        CustomAPMDataCollectionInfo.builder()
            .apmConfig(apmConfig)
            .metricEndpoints(metricEndpoints)
            .headers(headers)
            .options(options)
            .canaryMetricInfos(canaryMetricInfos != null ? new ArrayList<>(canaryMetricInfos) : null)
            .build();
    super.copy(customAPMDataCollectionInfo);
    return customAPMDataCollectionInfo;
  }

  @Override
  protected void validateParams() {
    Preconditions.checkNotNull(apmConfig, CustomAPMDataCollectionInfoKeys.apmConfig);
    Preconditions.checkNotNull(metricEndpoints, CustomAPMDataCollectionInfoKeys.metricEndpoints);
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return CapabilityHelper.generateDelegateCapabilities(apmConfig, getEncryptedDataDetails(), maskingEvaluator);
  }

  @JsonIgnore
  public boolean isCanaryUrlPresent() {
    return isNotEmpty(canaryMetricInfos);
  }

  @Override
  public void setSettingValue(SettingValue settingValue) {
    apmConfig = (APMVerificationConfig) settingValue;
  }

  @Override
  public void setEncryptionDataDetails(SecretManager secretManager) {
    setEncryptedDataDetails(getApmConfig().encryptedDataDetails(secretManager));
  }
}
