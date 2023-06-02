/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.appdynamics;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.dto.ThirdPartyApiCallLog.createApiCallLog;
import static software.wings.delegatetasks.AbstractDelegateDataCollectionTask.getUnsafeHttpClient;
import static software.wings.delegatetasks.cv.CVConstants.DURATION_TO_ASK_MINUTES;

import io.harness.cvng.beans.appd.AppDynamicsApplication;
import io.harness.cvng.beans.appd.AppDynamicsTier;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsConnectorDTO;
import io.harness.delegate.task.common.DataCollectionExecutorService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.cv.RequestExecutor;
import software.wings.helpers.ext.appdynamics.AppdynamicsRestClient;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse.VerificationLoadResponse;
import software.wings.service.impl.appdynamics.AppdynamicsMetric.AppdynamicsMetricType;
import software.wings.service.impl.newrelic.NewRelicApplication;
import software.wings.service.intfc.appdynamics.AppdynamicsDelegateService;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Created by rsingh on 4/17/17.
 */
@Singleton
@Slf4j
public class AppdynamicsDelegateServiceImpl implements AppdynamicsDelegateService {
  public static final String BT_PERFORMANCE_PATH_PREFIX = "Business Transaction Performance|Business Transactions|";
  public static final String EXTERNAL_CALLS = "External Calls";
  public static final String INDIVIDUAL_NODES = "Individual Nodes";
  @Inject private EncryptionService encryptionService;
  @Inject private SecretDecryptionService secretDecryptionService;

  @Inject private DataCollectionExecutorService dataCollectionService;
  @Inject private DelegateLogService delegateLogService;
  @Inject private RequestExecutor requestExecutor;

  @Override
  public List<NewRelicApplication> getAllApplications(
      AppDynamicsConfig appDynamicsConfig, List<EncryptedDataDetail> encryptionDetails) {
    final Call<List<NewRelicApplication>> request =
        getAppdynamicsRestClient(appDynamicsConfig.getControllerUrl())
            .listAllApplications(getHeaderWithCredentials(appDynamicsConfig, encryptionDetails));
    List<NewRelicApplication> newRelicApplications = requestExecutor.executeRequest(request);

    if (isNotEmpty(newRelicApplications)) {
      newRelicApplications = newRelicApplications.stream()
                                 .filter(application -> isNotEmpty(application.getName()))
                                 .sorted(Comparator.comparing(NewRelicApplication::getName))
                                 .collect(Collectors.toList());
    }
    return newRelicApplications;
  }

  @Override
  public List<AppDynamicsApplication> getApplications(
      AppDynamicsConnectorDTO appDynamicsConnector, List<EncryptedDataDetail> encryptionDetails) {
    final Call<List<NewRelicApplication>> request =
        getAppdynamicsRestClient(appDynamicsConnector)
            .listAllApplications(getHeaderWithCredentials(appDynamicsConnector, encryptionDetails));
    List<NewRelicApplication> newRelicApplications = requestExecutor.executeRequest(request);

    if (isNotEmpty(newRelicApplications)) {
      newRelicApplications = newRelicApplications.stream()
                                 .filter(application -> isNotEmpty(application.getName()))
                                 .sorted(Comparator.comparing(NewRelicApplication::getName))
                                 .collect(Collectors.toList());
    }
    return newRelicApplications.stream()
        .map(newRelicApplication
            -> AppDynamicsApplication.builder()
                   .id(newRelicApplication.getId())
                   .name(newRelicApplication.getName())
                   .build())
        .collect(Collectors.toList());
  }

  @Override
  public Set<AppdynamicsTier> getTiers(AppDynamicsConfig appDynamicsConfig, long appdynamicsAppId,
      List<EncryptedDataDetail> encryptionDetails, ThirdPartyApiCallLog thirdPartyApiCallLog) {
    Preconditions.checkNotNull(thirdPartyApiCallLog);
    ThirdPartyApiCallLog apiCallLog = thirdPartyApiCallLog.copy();
    final Call<Set<AppdynamicsTier>> request =
        getAppdynamicsRestClient(appDynamicsConfig.getControllerUrl())
            .listTiers(getHeaderWithCredentials(appDynamicsConfig, encryptionDetails), appdynamicsAppId);
    apiCallLog.setTitle("Fetching tiers for application " + appdynamicsAppId);
    final Set<AppdynamicsTier> response = requestExecutor.executeRequest(apiCallLog, request);
    response.forEach(tier -> tier.setExternalTiers(new HashSet<>()));
    return new HashSet<>(response);
  }

  @Override
  public Set<AppDynamicsTier> getTiers(AppDynamicsConnectorDTO appDynamicsConnector,
      List<EncryptedDataDetail> encryptedDataDetails, long appDynamicsAppId) {
    final Call<Set<AppDynamicsTier>> request =
        getAppdynamicsRestClient(appDynamicsConnector)
            .listTiersNg(getHeaderWithCredentials(appDynamicsConnector, encryptedDataDetails), appDynamicsAppId);
    final Set<AppDynamicsTier> response = requestExecutor.executeRequest(request);
    return new HashSet<>(response);
  }

  @Override
  public Set<AppdynamicsTier> getTierDependencies(AppDynamicsConfig appDynamicsConfig, long appdynamicsAppId,
      List<EncryptedDataDetail> encryptionDetails, ThirdPartyApiCallLog apiCallLog) {
    Preconditions.checkNotNull(apiCallLog);
    Set<AppdynamicsTier> tiers = getTiers(appDynamicsConfig, appdynamicsAppId, encryptionDetails, apiCallLog);
    List<Callable<Void>> callables = new ArrayList<>();
    tiers.forEach(tier -> callables.add(() -> {
      final String tierBTsPath = BT_PERFORMANCE_PATH_PREFIX + tier.getName();
      Call<List<AppdynamicsMetric>> tierBTMetricRequest =
          getAppdynamicsRestClient(appDynamicsConfig.getControllerUrl())
              .listMetrices(
                  getHeaderWithCredentials(appDynamicsConfig, encryptionDetails), appdynamicsAppId, tierBTsPath);

      List<AppdynamicsMetric> tierBtMetrics = requestExecutor.executeRequest(tierBTMetricRequest);
      tierBtMetrics.forEach(tierBtMetric -> {
        List<AppdynamicsMetric> externalCallMetrics =
            getExternalCallMetrics(appDynamicsConfig, appdynamicsAppId, tierBtMetric, tierBTsPath + "|",
                encryptionDetails, createApiCallLog(appDynamicsConfig.getAccountId(), null));
        externalCallMetrics.forEach(
            externalCallMetric -> parseAndAddExternalTier(tier.getExternalTiers(), externalCallMetric, tiers));
      });
      return null;
    }));
    dataCollectionService.executeParrallel(callables);

    callables.clear();
    tiers.forEach(tier -> callables.add(() -> {
      tier.getExternalTiers().forEach(externalTier -> {
        tiers.forEach(parentTier -> {
          if (parentTier.equals(externalTier)) {
            externalTier.getExternalTiers().addAll(parentTier.getExternalTiers());
          }
        });
      });
      return null;
    }));
    dataCollectionService.executeParrallel(callables);
    return tiers;
  }

  private void parseAndAddExternalTier(
      Set<AppdynamicsTier> externalTiers, AppdynamicsMetric externalCallMetric, Set<AppdynamicsTier> allTiers) {
    List<AppdynamicsMetric> childMetrices = externalCallMetric.getChildMetrices();
    if (isEmpty(childMetrices)) {
      return;
    }

    childMetrices.forEach(childMetric -> {
      if (childMetric.getType() == AppdynamicsMetricType.folder) {
        for (AppdynamicsTier appdynamicsTier : allTiers) {
          if (childMetric.getName().equals(appdynamicsTier.getName())) {
            AppdynamicsTier externalTier = AppdynamicsTier.builder()
                                               .id(appdynamicsTier.getId())
                                               .name(appdynamicsTier.getName())
                                               .agentType(appdynamicsTier.getAgentType())
                                               .description(appdynamicsTier.getDescription())
                                               .build();
            externalTiers.add(externalTier);
            parseAndAddExternalTier(externalTier.getExternalTiers(), childMetric, allTiers);
          }
        }

        parseAndAddExternalTier(externalTiers, childMetric, allTiers);
      }
    });
  }

  @Override
  public Set<AppdynamicsNode> getNodes(AppDynamicsConfig appDynamicsConfig, long appdynamicsAppId, long tierId,
      List<EncryptedDataDetail> encryptionDetails, ThirdPartyApiCallLog apiCallLog, List<String> hosts) {
    Preconditions.checkNotNull(apiCallLog);
    apiCallLog.setTitle("Fetching node list for app: " + appdynamicsAppId + " tier: " + tierId);
    final Call<List<AppdynamicsNode>> request =
        getAppdynamicsRestClient(appDynamicsConfig.getControllerUrl())
            .listNodes(getHeaderWithCredentials(appDynamicsConfig, encryptionDetails), appdynamicsAppId, tierId, hosts);
    return new HashSet<>(requestExecutor.executeRequest(apiCallLog, request));
  }

  @Override
  public List<AppdynamicsMetric> getTierBTMetrics(AppDynamicsConfig appDynamicsConfig, long appdynamicsAppId,
      long tierId, List<EncryptedDataDetail> encryptionDetails, ThirdPartyApiCallLog apiCallLog) {
    Preconditions.checkNotNull(apiCallLog);
    final AppdynamicsTier tier =
        getAppdynamicsTier(appDynamicsConfig, appdynamicsAppId, tierId, encryptionDetails, apiCallLog);
    final String tierBTsPath = BT_PERFORMANCE_PATH_PREFIX + tier.getName();
    apiCallLog.setTitle("Fetching business transactions for tier from " + appDynamicsConfig.getControllerUrl());
    Call<List<AppdynamicsMetric>> tierBTMetricRequest =
        getAppdynamicsRestClient(appDynamicsConfig.getControllerUrl())
            .listMetrices(
                getHeaderWithCredentials(appDynamicsConfig, encryptionDetails), appdynamicsAppId, tierBTsPath);

    return requestExecutor.executeRequest(apiCallLog, tierBTMetricRequest);
  }

  @Override
  public List<AppdynamicsMetricData> getTierBTMetricData(AppDynamicsConfig appDynamicsConfig, long appdynamicsAppId,
      String tierName, String btName, String hostName, Long startTime, Long endTime,
      List<EncryptedDataDetail> encryptionDetails, ThirdPartyApiCallLog apiCallLog) {
    Preconditions.checkNotNull(apiCallLog);
    Preconditions.checkNotNull(startTime, "Start time was null while getting data from Appdynamics. StateExecutionId: ",
        apiCallLog.getStateExecutionId());
    Preconditions.checkNotNull(endTime,
        "End time was null while getting data from Appdynamics. StateExecutionId: ", apiCallLog.getStateExecutionId());

    String metricPath = BT_PERFORMANCE_PATH_PREFIX + tierName + "|" + btName + "|"
        + (isEmpty(hostName) ? "*" : "Individual Nodes|" + hostName + "|*");
    apiCallLog.setTitle("Fetching metric data for " + metricPath);
    log.debug("fetching metrics for path {} ", metricPath);
    Call<List<AppdynamicsMetricData>> tierBTMetricRequest =
        getAppdynamicsRestClient(appDynamicsConfig.getControllerUrl())
            .getMetricDataTimeRange(getHeaderWithCredentials(appDynamicsConfig, encryptionDetails), appdynamicsAppId,
                metricPath, startTime, endTime, false);
    return requestExecutor.executeRequest(apiCallLog, tierBTMetricRequest);
  }

  @Override
  public AppdynamicsTier getAppdynamicsTier(AppDynamicsConfig appDynamicsConfig, long appdynamicsAppId, long tierId,
      List<EncryptedDataDetail> encryptionDetails, ThirdPartyApiCallLog apiCallLog) {
    Preconditions.checkNotNull(apiCallLog);
    apiCallLog.setTitle("Fetching tiers from " + appDynamicsConfig.getControllerUrl());
    final Call<List<AppdynamicsTier>> tierDetail =
        getAppdynamicsRestClient(appDynamicsConfig.getControllerUrl())
            .getTierDetails(getHeaderWithCredentials(appDynamicsConfig, encryptionDetails), appdynamicsAppId, tierId);
    List<AppdynamicsTier> tiers = requestExecutor.executeRequest(apiCallLog, tierDetail);
    return tiers.get(0);
  }

  private AppdynamicsTier getAppdynamicsTier(AppDynamicsConnectorDTO appDynamicsConnectorDTO, long appdynamicsAppId,
      long tierId, List<EncryptedDataDetail> encryptionDetails, ThirdPartyApiCallLog apiCallLog) {
    Preconditions.checkNotNull(apiCallLog);
    apiCallLog.setTitle("Fetching tiers from " + appDynamicsConnectorDTO.getControllerUrl());
    final Call<List<AppdynamicsTier>> tierDetail =
        getAppdynamicsRestClient(appDynamicsConnectorDTO)
            .getTierDetails(
                getHeaderWithCredentials(appDynamicsConnectorDTO, encryptionDetails), appdynamicsAppId, tierId);
    List<AppdynamicsTier> tiers = requestExecutor.executeRequest(apiCallLog, tierDetail);
    return tiers.get(0);
  }

  private List<AppdynamicsMetric> getChildMetrics(AppDynamicsConfig appDynamicsConfig, long applicationId,
      AppdynamicsMetric appdynamicsMetric, String parentMetricPath, boolean includeExternal,
      List<EncryptedDataDetail> encryptionDetails, ThirdPartyApiCallLog apiCallLog) {
    Preconditions.checkNotNull(apiCallLog);
    if (appdynamicsMetric.getType() != AppdynamicsMetricType.folder) {
      return Collections.emptyList();
    }

    if (parentMetricPath.contains("|" + appdynamicsMetric.getName() + "|")) {
      return Collections.emptyList();
    }

    final String childMetricPath = parentMetricPath + appdynamicsMetric.getName() + "|";
    apiCallLog.setTitle("Fetching metric names from " + appDynamicsConfig.getControllerUrl());
    Call<List<AppdynamicsMetric>> request =
        getAppdynamicsRestClient(appDynamicsConfig.getControllerUrl())
            .listMetrices(
                getHeaderWithCredentials(appDynamicsConfig, encryptionDetails), applicationId, childMetricPath);
    final List<AppdynamicsMetric> allMetrics = requestExecutor.executeRequest(apiCallLog, request);
    for (Iterator<AppdynamicsMetric> iterator = allMetrics.iterator(); iterator.hasNext();) {
      final AppdynamicsMetric metric = iterator.next();

      // While getting the metric names we do not need to go to individual metrics names since the metric names in
      // each node are the same and there can be thousands of nodes in case of recycled nodes for container world
      // We would not be monitoring external calls metrics because one deployment is not going to effect multiple
      // tiers
      if (metric.getName().contains(INDIVIDUAL_NODES)
          || !includeExternal && metric.getName().contains(EXTERNAL_CALLS)) {
        iterator.remove();
        continue;
      }

      metric.setChildMetrices(getChildMetrics(appDynamicsConfig, applicationId, metric, childMetricPath,
          includeExternal, encryptionDetails, apiCallLog.copy()));
    }
    return allMetrics;
  }

  private List<AppdynamicsMetric> getExternalCallMetrics(AppDynamicsConfig appDynamicsConfig, long applicationId,
      AppdynamicsMetric appdynamicsMetric, String parentMetricPath, List<EncryptedDataDetail> encryptionDetails,
      ThirdPartyApiCallLog apiCallLog) {
    Preconditions.checkNotNull(apiCallLog);
    if (appdynamicsMetric.getType() != AppdynamicsMetricType.folder) {
      return Collections.emptyList();
    }

    final String childMetricPath = parentMetricPath + appdynamicsMetric.getName() + "|";
    apiCallLog.setTitle("Fetching external calls metric names from " + appDynamicsConfig.getControllerUrl());
    Call<List<AppdynamicsMetric>> request =
        getAppdynamicsRestClient(appDynamicsConfig.getControllerUrl())
            .listMetrices(
                getHeaderWithCredentials(appDynamicsConfig, encryptionDetails), applicationId, childMetricPath);
    final List<AppdynamicsMetric> allMetrics = requestExecutor.executeRequest(apiCallLog, request);
    for (Iterator<AppdynamicsMetric> iterator = allMetrics.iterator(); iterator.hasNext();) {
      final AppdynamicsMetric metric = iterator.next();
      if (!metric.getName().contains(EXTERNAL_CALLS)) {
        iterator.remove();
        continue;
      }
      metric.setChildMetrices(getChildMetrics(
          appDynamicsConfig, applicationId, metric, childMetricPath, true, encryptionDetails, apiCallLog.copy()));
    }
    return allMetrics;
  }

  @Override
  public VerificationNodeDataSetupResponse getMetricsWithDataForNode(AppDynamicsConfig appDynamicsConfig,
      List<EncryptedDataDetail> encryptionDetails, AppdynamicsSetupTestNodeData setupTestNodeData, String hostName,
      ThirdPartyApiCallLog apiCallLog) {
    final AppdynamicsTier tier = getAppdynamicsTier(appDynamicsConfig, setupTestNodeData.getApplicationId(),
        setupTestNodeData.getTierId(), encryptionDetails, apiCallLog.copy());
    final List<AppdynamicsMetric> tierMetrics = getTierBTMetrics(appDynamicsConfig,
        setupTestNodeData.getApplicationId(), setupTestNodeData.getTierId(), encryptionDetails, apiCallLog);

    if (isEmpty(tierMetrics)) {
      return VerificationNodeDataSetupResponse.builder()
          .providerReachable(true)
          .loadResponse(VerificationLoadResponse.builder().isLoadPresent(false).build())
          .build();
    }
    final SortedSet<AppdynamicsMetricData> metricsData = new TreeSet<>();
    if (setupTestNodeData.isServiceLevel()) {
      return VerificationNodeDataSetupResponse.builder()
          .providerReachable(true)
          .loadResponse(VerificationLoadResponse.builder().isLoadPresent(true).loadResponse(tierMetrics).build())
          .dataForNode(metricsData)
          .build();
    }
    List<Callable<List<AppdynamicsMetricData>>> callables = new ArrayList<>();
    long endTime = System.currentTimeMillis();
    long startTime = endTime - TimeUnit.MINUTES.toMillis(DURATION_TO_ASK_MINUTES);
    for (AppdynamicsMetric appdynamicsMetric : tierMetrics) {
      callables.add(()
                        -> getTierBTMetricData(appDynamicsConfig, setupTestNodeData.getApplicationId(), tier.getName(),
                            appdynamicsMetric.getName(), hostName, startTime, endTime, encryptionDetails, apiCallLog));
    }
    List<Optional<List<AppdynamicsMetricData>>> results = dataCollectionService.executeParrallel(callables);
    results.forEach(result -> {
      if (result.isPresent()) {
        metricsData.addAll(result.get());
      }
    });

    for (Iterator<AppdynamicsMetricData> iterator = metricsData.iterator(); iterator.hasNext();) {
      AppdynamicsMetricData appdynamicsMetricData = iterator.next();
      String metricName = appdynamicsMetricData.getMetricName();
      if (metricName.contains("|")) {
        metricName = metricName.substring(metricName.lastIndexOf('|') + 1);
      }
      if (!AppdynamicsTimeSeries.getMetricsToTrack().contains(metricName)) {
        iterator.remove();
      }
    }
    return VerificationNodeDataSetupResponse.builder()
        .providerReachable(true)
        .loadResponse(VerificationLoadResponse.builder().isLoadPresent(true).loadResponse(tierMetrics).build())
        .dataForNode(metricsData)
        .build();
  }

  @Override
  public boolean validateConfig(AppDynamicsConfig appDynamicsConfig, List<EncryptedDataDetail> encryptedDataDetails) {
    final Call<List<NewRelicApplication>> request =
        getAppdynamicsRestClient(appDynamicsConfig.getControllerUrl())
            .listAllApplications(getHeaderWithCredentials(appDynamicsConfig, encryptedDataDetails));
    requestExecutor.executeRequest(request);
    return true;
  }

  AppdynamicsRestClient getAppdynamicsRestClient(final String controllerUrl) {
    final Retrofit retrofit = new Retrofit.Builder()
                                  .baseUrl(controllerUrl.endsWith("/") ? controllerUrl : controllerUrl + "/")
                                  .addConverterFactory(JacksonConverterFactory.create())
                                  .client(getUnsafeHttpClient(controllerUrl))
                                  .build();
    return retrofit.create(AppdynamicsRestClient.class);
  }

  AppdynamicsRestClient getAppdynamicsRestClient(AppDynamicsConnectorDTO appDynamicsConnector) {
    final Retrofit retrofit = new Retrofit.Builder()
                                  .baseUrl(appDynamicsConnector.getControllerUrl().endsWith("/")
                                          ? appDynamicsConnector.getControllerUrl()
                                          : appDynamicsConnector.getControllerUrl() + "/")
                                  .addConverterFactory(JacksonConverterFactory.create())
                                  .client(getUnsafeHttpClient(appDynamicsConnector.getControllerUrl()))
                                  .build();
    return retrofit.create(AppdynamicsRestClient.class);
  }
  private String getHeaderWithCredentials(
      AppDynamicsConfig appDynamicsConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(appDynamicsConfig, encryptionDetails, false);
    return "Basic "
        + Base64.encodeBase64String(
            String
                .format("%s@%s:%s", appDynamicsConfig.getUsername(), appDynamicsConfig.getAccountname(),
                    new String(appDynamicsConfig.getPassword()))
                .getBytes(StandardCharsets.UTF_8));
  }

  private String getHeaderWithCredentials(
      AppDynamicsConnectorDTO appDynamicsConnector, List<EncryptedDataDetail> encryptionDetails) {
    secretDecryptionService.decrypt(appDynamicsConnector, encryptionDetails);
    return "Basic "
        + Base64.encodeBase64String(
            String
                .format("%s@%s:%s", appDynamicsConnector.getUsername(), appDynamicsConnector.getAccountname(),
                    new String(appDynamicsConnector.getPasswordRef().getDecryptedValue()))
                .getBytes(StandardCharsets.UTF_8));
  }
}
