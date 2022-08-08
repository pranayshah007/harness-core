/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.appdynamics;

import static software.wings.beans.TaskType.APPDYNAMICS_COLLECT_METRIC_DATA_V2;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.cv.AppDynamicsDataCollector;
import software.wings.delegatetasks.cv.DataCollector;
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

@FieldNameConstants(innerTypeName = "AppDynamicsDataCollectionInfoV2Keys")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AppDynamicsDataCollectionInfoV2 extends MetricsDataCollectionInfo {
  private AppDynamicsConfig appDynamicsConfig;
  private long appDynamicsApplicationId;
  private long appDynamicsTierId;

  @Builder
  public AppDynamicsDataCollectionInfoV2(String accountId, String applicationId, String envId, Instant startTime,
      Instant endTime, Set<String> hosts, String cvConfigId, String stateExecutionId, String workflowId,
      String workflowExecutionId, String serviceId, String cvTaskId, String connectorId,
      List<EncryptedDataDetail> encryptedDataDetails, Instant dataCollectionStartTime,
      AppDynamicsConfig appDynamicsConfig, long appDynamicsApplicationId, long appDynamicsTierId,
      Map<String, String> hostsToGroupNameMap) {
    super(accountId, applicationId, envId, startTime, endTime, hosts, cvConfigId, stateExecutionId, workflowId,
        workflowExecutionId, serviceId, cvTaskId, connectorId, encryptedDataDetails, dataCollectionStartTime,
        hostsToGroupNameMap, true);
    this.appDynamicsConfig = appDynamicsConfig;
    this.appDynamicsApplicationId = appDynamicsApplicationId;
    this.appDynamicsTierId = appDynamicsTierId;
  }

  @Override
  public TaskType getTaskType() {
    return APPDYNAMICS_COLLECT_METRIC_DATA_V2;
  }

  @Override
  public DelegateStateType getStateType() {
    return DelegateStateType.APP_DYNAMICS;
  }

  @Override
  public Class<? extends DataCollector<? extends DataCollectionInfoV2>> getDataCollectorImplClass() {
    return AppDynamicsDataCollector.class;
  }

  @Override
  public Optional<String> getUrlForValidation() {
    return Optional.of(appDynamicsConfig.getControllerUrl());
  }

  @Override
  public Optional<EncryptionConfig> getEncryptionConfig() {
    return Optional.of(getEncryptedDataDetails().get(0).getEncryptionConfig());
  }

  @Override
  public Optional<EncryptableSetting> getEncryptableSetting() {
    return Optional.of(appDynamicsConfig);
  }

  @Override
  public DataCollectionInfoV2 deepCopy() {
    AppDynamicsDataCollectionInfoV2 appDynamicsDataCollectionInfo =
        AppDynamicsDataCollectionInfoV2.builder()
            .appDynamicsConfig(appDynamicsConfig)
            .appDynamicsApplicationId(appDynamicsApplicationId)
            .appDynamicsTierId(appDynamicsTierId)
            .build();
    super.copy(appDynamicsDataCollectionInfo);
    return appDynamicsDataCollectionInfo;
  }

  @Override
  public void setSettingValue(SettingValue settingValue) {
    appDynamicsConfig = (AppDynamicsConfig) settingValue;
  }

  @Override
  protected void validateParams() {
    Preconditions.checkNotNull(appDynamicsConfig, AppDynamicsDataCollectionInfoV2Keys.appDynamicsConfig);
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return CapabilityHelper.generateDelegateCapabilities(
        appDynamicsConfig, getEncryptedDataDetails(), maskingEvaluator);
  }
}
