/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.stackdriver;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.STACKDRIVER_ERROR;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.dto.ThirdPartyApiCallLog.createApiCallLog;
import static software.wings.common.VerificationConstants.STACK_DRIVER_METRIC;
import static software.wings.common.VerificationConstants.TIME_DURATION_FOR_LOGS_IN_MINUTES;

import io.harness.delegate.beans.TaskData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.VerificationOperationException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.YamlUtils;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.GcpConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.common.VerificationConstants;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.apm.MLServiceUtils;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.stackdriver.StackDriverDelegateService;
import software.wings.service.intfc.stackdriver.StackDriverService;
import software.wings.sm.states.StackDriverState;
import software.wings.verification.stackdriver.StackDriverMetricDefinition;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by Pranjal on 11/27/2018
 */
@ValidateOnExecution
@Singleton
@Slf4j
public class StackDriverServiceImpl implements StackDriverService {
  private static final URL STACKDRIVER_METRICS_URL = StackDriverService.class.getResource(STACK_DRIVER_METRIC);
  private static final String STACKDRIVER_YAML;
  static {
    String tmpStackDriverYaml = "";
    try {
      tmpStackDriverYaml = Resources.toString(STACKDRIVER_METRICS_URL, Charsets.UTF_8);
    } catch (IOException ex) {
      log.info("Exception while reading StackDriver yaml", ex);
    }
    STACKDRIVER_YAML = tmpStackDriverYaml;
  }
  @Inject private SettingsService settingsService;
  @Inject private DelegateProxyFactory delegateProxyFactory;
  @Inject private SecretManager secretManager;
  @Inject private MLServiceUtils mlServiceUtils;
  @Inject private EncryptionService encryptionService;

  private final Map<String, List<StackDriverMetric>> metricsByNameSpace;

  @Inject
  public StackDriverServiceImpl() {
    metricsByNameSpace = fetchMetrics();
  }

  @Override
  public void validateMetricDefinitions(List<StackDriverMetricDefinition> metricDefinitions) {
    Map<String, String> errorMap = StackDriverState.validateMetricDefinitions(metricDefinitions, true);
    if (isNotEmpty(errorMap)) {
      throw new VerificationOperationException(
          ErrorCode.STACKDRIVER_CONFIGURATION_ERROR, errorMap.get(errorMap.keySet().iterator().next()));
    }
  }

  @Override
  public VerificationNodeDataSetupResponse getDataForNode(StackDriverSetupTestNodeData setupTestNodeData) {
    String hostName = null;
    // check if it is for service level, serviceId is empty then get hostname
    if (!setupTestNodeData.isServiceLevel()) {
      hostName = mlServiceUtils.getHostName(setupTestNodeData);
    }

    if (setupTestNodeData.getStateType() == DelegateStateType.STACK_DRIVER) {
      validateMetricDefinitions(setupTestNodeData.getMetricDefinitions());
    }

    try {
      final SettingAttribute settingAttribute = settingsService.get(setupTestNodeData.getSettingId());
      List<EncryptedDataDetail> encryptionDetails =
          secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
      String appId = setupTestNodeData.getAppId() == null ? GLOBAL_APP_ID : setupTestNodeData.getAppId();
      SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                            .accountId(settingAttribute.getAccountId())
                                            .appId(appId)
                                            .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT * 3)
                                            .build();

      if (DelegateStateType.STACK_DRIVER_LOG == setupTestNodeData.getStateType()) {
        return delegateProxyFactory.getV2(StackDriverDelegateService.class, syncTaskContext)
            .getLogWithDataForNode(StackdriverLogGcpConfigTaskParams.builder()
                                       .gcpConfig((GcpConfig) settingAttribute.getValue())
                                       .encryptedDataDetails(encryptionDetails)
                                       .build(),
                setupTestNodeData.getGuid(), hostName, setupTestNodeData);
      } else {
        return delegateProxyFactory.getV2(StackDriverDelegateService.class, syncTaskContext)
            .getMetricsWithDataForNode(StackdriverGcpConfigTaskParams.builder()
                                           .gcpConfig((GcpConfig) settingAttribute.getValue())
                                           .encryptedDataDetails(encryptionDetails)
                                           .build(),
                setupTestNodeData, hostName,
                createApiCallLog(settingAttribute.getAccountId(), setupTestNodeData.getGuid()));
      }
    } catch (Exception e) {
      log.info("error getting metric data for node", e);
      throw new WingsException(STACKDRIVER_ERROR).addParam("reason", e.getMessage());
    }
  }

  @Override
  public Object getLogSample(String accountId, String appId, String serverConfigId, String query, String guid) {
    try {
      final SettingAttribute settingAttribute = settingsService.get(serverConfigId);
      List<EncryptedDataDetail> encryptionDetails =
          secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
      SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                            .accountId(settingAttribute.getAccountId())
                                            .appId(appId != null ? appId : GLOBAL_APP_ID)
                                            .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT * 3)
                                            .build();

      return delegateProxyFactory.getV2(StackDriverDelegateService.class, syncTaskContext)
          .getLogSample(StackdriverLogGcpConfigTaskParams.builder()
                            .gcpConfig((GcpConfig) settingAttribute.getValue())
                            .encryptedDataDetails(encryptionDetails)
                            .build(),
              guid, query, System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1), System.currentTimeMillis());
    } catch (Exception e) {
      log.info("error getting metric data for node", e);
      throw new WingsException(STACKDRIVER_ERROR)
          .addParam("reason", "Error in getting sample log data." + e.getMessage());
    }
  }

  @Override
  public Map<String, List<StackDriverMetric>> getMetrics() {
    return metricsByNameSpace;
  }

  @Override
  public List<String> listRegions(String settingId) throws IOException {
    SettingAttribute settingAttribute = settingsService.get(settingId);

    if (settingAttribute == null || !(settingAttribute.getValue() instanceof GcpConfig)) {
      throw new WingsException("GCP account setting not found " + settingId);
    }
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(settingAttribute.getAccountId())
                                          .appId(GLOBAL_APP_ID)
                                          .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT * 3)
                                          .build();
    return delegateProxyFactory.getV2(StackDriverDelegateService.class, syncTaskContext)
        .listRegions(StackdriverGcpConfigTaskParams.builder()
                         .gcpConfig((GcpConfig) settingAttribute.getValue())
                         .encryptedDataDetails(encryptionDetails)
                         .build());
  }

  @Override
  public Map<String, String> listForwardingRules(String settingId, String region) throws IOException {
    SettingAttribute settingAttribute = settingsService.get(settingId);

    if (settingAttribute == null || !(settingAttribute.getValue() instanceof GcpConfig)) {
      throw new WingsException("GCP account setting not found " + settingId);
    }
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(settingAttribute.getAccountId())
                                          .appId(GLOBAL_APP_ID)
                                          .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT * 3)
                                          .build();
    return delegateProxyFactory.getV2(StackDriverDelegateService.class, syncTaskContext)
        .listForwardingRules(StackdriverGcpConfigTaskParams.builder()
                                 .gcpConfig((GcpConfig) settingAttribute.getValue())
                                 .encryptedDataDetails(encryptionDetails)
                                 .build(),
            region);
  }

  @Override
  public Boolean validateQuery(
      String accountId, String appId, String connectorId, String query, String hostNameField, String logMessageField) {
    SettingAttribute settingAttribute = settingsService.get(connectorId);

    if (settingAttribute == null || !(settingAttribute.getValue() instanceof GcpConfig)) {
      throw new WingsException("GCP account setting not found " + connectorId);
    }
    List<EncryptedDataDetail> encryptionDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(accountId)
                                          .appId(appId != null ? appId : GLOBAL_APP_ID)
                                          .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT * 3)
                                          .build();

    StackDriverSetupTestNodeData stackDriverSetupTestNodeData =
        StackDriverSetupTestNodeData.builder()
            .query(query)
            .fromTime(OffsetDateTime.now().minusMinutes(TIME_DURATION_FOR_LOGS_IN_MINUTES + 2).toEpochSecond())
            .toTime(OffsetDateTime.now().minusMinutes(2).toEpochSecond())
            .hostnameField(hostNameField)
            .messageField(logMessageField)
            .isServiceLevel(true)
            .build();
    VerificationNodeDataSetupResponse nodeDataSetupResponse =
        delegateProxyFactory.getV2(StackDriverDelegateService.class, syncTaskContext)
            .getLogWithDataForNode(StackdriverLogGcpConfigTaskParams.builder()
                                       .gcpConfig((GcpConfig) settingAttribute.getValue())
                                       .encryptedDataDetails(encryptionDetails)
                                       .build(),
                null, null, stackDriverSetupTestNodeData);
    List<LogElement> response = nodeDataSetupResponse.getDataForNode() != null
        ? (List<LogElement>) nodeDataSetupResponse.getDataForNode()
        : Collections.emptyList();

    if (response.size() / TIME_DURATION_FOR_LOGS_IN_MINUTES > VerificationConstants.TOTAL_HITS_PER_MIN_THRESHOLD) {
      throw new WingsException(ErrorCode.STACKDRIVER_ERROR, "Too many logs to process, please refine your query")
          .addParam("reason", "Too many logs returned using query: '" + query + "'. Please refine your query.");
    }
    return true;
  }

  private static Map<String, List<StackDriverMetric>> fetchMetrics() {
    Map<String, List<StackDriverMetric>> stackDriverMetrics;
    YamlUtils yamlUtils = new YamlUtils();
    try {
      stackDriverMetrics =
          yamlUtils.read(STACKDRIVER_YAML, new TypeReference<Map<String, List<StackDriverMetric>>>() {});
    } catch (Exception e) {
      throw new WingsException(e);
    }
    return stackDriverMetrics;
  }
}
