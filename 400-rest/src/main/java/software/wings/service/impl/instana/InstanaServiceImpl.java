/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instana;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.dto.ThirdPartyApiCallLog.createApiCallLog;
import static software.wings.common.VerificationConstants.INSTANA_DOCKER_PLUGIN;
import static software.wings.common.VerificationConstants.INSTANA_GROUPBY_TAG_TRACE_NAME;
import static software.wings.common.VerificationConstants.VERIFICATION_HOST_PLACEHOLDER;

import io.harness.eraro.ErrorCode;
import io.harness.exception.VerificationOperationException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.time.Timestamp;

import software.wings.beans.InstanaConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.apm.MLServiceUtils;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.instana.InstanaDelegateService;
import software.wings.service.intfc.instana.InstanaService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

@ValidateOnExecution
@Singleton
@Slf4j
public class InstanaServiceImpl implements InstanaService {
  @Inject private SettingsService settingsService;
  @Inject private SecretManager secretManager;
  @Inject private DelegateProxyFactory delegateProxyFactory;
  @Inject private MLServiceUtils mlServiceUtils;

  @Override
  public VerificationNodeDataSetupResponse getMetricsWithDataForNode(InstanaSetupTestNodeData setupTestNodeData) {
    final SettingAttribute settingAttribute = settingsService.get(setupTestNodeData.getSettingId());
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(settingAttribute.getAccountId())
                                          .appId(GLOBAL_APP_ID)
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                          .build();
    InstanaConfig instanaConfig = (InstanaConfig) settingAttribute.getValue();
    List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(instanaConfig);
    InstanaDelegateService instanaDelegateService =
        delegateProxyFactory.getV2(InstanaDelegateService.class, syncTaskContext);
    VerificationNodeDataSetupResponse verificationNodeDataSetupResponse =
        VerificationNodeDataSetupResponse.builder().build();
    VerificationNodeDataSetupResponse.VerificationLoadResponse verificationLoadResponse =
        VerificationNodeDataSetupResponse.VerificationLoadResponse.builder().isLoadPresent(false).build();
    List<Object> load = new ArrayList<>();
    InstanaTimeFrame timeFrame = InstanaTimeFrame.builder()
                                     .windowSize(TimeUnit.MINUTES.toMillis(15))
                                     .to(Timestamp.currentMinuteBoundary())
                                     .build();
    boolean isLoadPresent = false;

    if (setupTestNodeData.getInfraParams() != null) {
      InstanaInfraMetricRequest infraMetricRequest =
          InstanaInfraMetricRequest.builder()
              .timeframe(timeFrame)
              .metrics(setupTestNodeData.getInfraParams().getMetrics())
              .plugin(INSTANA_DOCKER_PLUGIN)
              .rollup(60)
              .query(setupTestNodeData.getInfraParams().getQuery().replace(
                  VERIFICATION_HOST_PLACEHOLDER, "\"" + mlServiceUtils.getHostName(setupTestNodeData) + "\""))
              .build();

      try {
        ThirdPartyApiCallLog apiCallLog =
            createApiCallLog(settingAttribute.getAccountId(), setupTestNodeData.getGuid());
        InstanaInfraMetrics infraMetrics =
            instanaDelegateService.getInfraMetrics(instanaConfig, encryptionDetails, infraMetricRequest, apiCallLog);
        if (infraMetrics.getItems().size() > 1) {
          throw new VerificationOperationException(ErrorCode.INSTANA_CONFIGURATION_ERROR,
              "Multiple time series values are returned for query '" + setupTestNodeData.getInfraParams().getQuery()
                  + "'. Please add more filters to your query to return only one time series.");
        }
        verificationNodeDataSetupResponse.setProviderReachable(true);
        if (infraMetrics.getItems().size() == 1) {
          isLoadPresent = true;
        }
        load.add(infraMetrics);
      } catch (DataCollectionException e) {
        verificationNodeDataSetupResponse.setProviderReachable(false);
      }
    }
    if (setupTestNodeData.getApplicationParams() != null || isNotEmpty(setupTestNodeData.getTagFilters())) {
      List<InstanaTagFilter> tagFilters;
      if (isNotEmpty(setupTestNodeData.getTagFilters())) {
        tagFilters = new ArrayList<>(setupTestNodeData.getTagFilters());
      } else {
        tagFilters = new ArrayList<>(setupTestNodeData.getApplicationParams().getTagFilters());
      }
      if (setupTestNodeData.getApplicationParams() != null) {
        tagFilters.add(InstanaTagFilter.builder()
                           .name(setupTestNodeData.getApplicationParams().getHostTagFilter())
                           .value(mlServiceUtils.getHostName(setupTestNodeData))
                           .operator(InstanaTagFilter.Operator.EQUALS)
                           .build());
      }
      List<InstanaAnalyzeMetricRequest.Metric> metrics = new ArrayList<>();
      InstanaUtils.getApplicationMetricTemplateMap().forEach(
          (metricName, instanaMetricTemplate)
              -> metrics.add(InstanaAnalyzeMetricRequest.Metric.builder()
                                 .metric(instanaMetricTemplate.getMetricName())
                                 .aggregation(instanaMetricTemplate.getAggregation())
                                 .granularity(60)
                                 .build()));
      InstanaAnalyzeMetricRequest.Group group =
          InstanaAnalyzeMetricRequest.Group.builder().groupByTag(INSTANA_GROUPBY_TAG_TRACE_NAME).build();
      InstanaAnalyzeMetricRequest instanaAnalyzeMetricRequest = InstanaAnalyzeMetricRequest.builder()
                                                                    .timeFrame(timeFrame)
                                                                    .tagFilters(tagFilters)
                                                                    .group(group)
                                                                    .metrics(metrics)
                                                                    .build();

      try {
        ThirdPartyApiCallLog apiCallLog =
            createApiCallLog(settingAttribute.getAccountId(), setupTestNodeData.getGuid());
        InstanaAnalyzeMetrics instanaAnalyzeMetrics = instanaDelegateService.getInstanaTraceMetrics(
            instanaConfig, encryptionDetails, instanaAnalyzeMetricRequest, apiCallLog);
        if (instanaAnalyzeMetrics.getItems().size() > 0) {
          isLoadPresent = true;
        }
        verificationNodeDataSetupResponse.setProviderReachable(true);
        load.add(instanaAnalyzeMetrics);
      } catch (DataCollectionException e) {
        verificationNodeDataSetupResponse.setProviderReachable(false);
      }
    }
    if (isNotEmpty(load) && isLoadPresent) {
      verificationNodeDataSetupResponse.setDataForNode(load);
      verificationLoadResponse.setLoadPresent(true);
      verificationLoadResponse.setLoadResponse(load);
      verificationNodeDataSetupResponse.setLoadResponse(verificationLoadResponse);
    }
    return verificationNodeDataSetupResponse;
  }
}
