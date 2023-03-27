/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.newrelic;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.exception.WingsException.USER;
import static io.harness.govern.Switch.unhandled;

import static software.wings.app.ManagerCacheRegistrar.NEW_RELIC_APPLICATION_CACHE;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.dto.ThirdPartyApiCallLog.createApiCallLog;

import io.harness.beans.FeatureName;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.VerificationOperationException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.rest.RestResponse;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.YamlUtils;

import software.wings.APMFetchConfig;
import software.wings.annotation.EncryptableSetting;
import software.wings.beans.APMValidateCollectorConfig;
import software.wings.beans.APMVerificationConfig;
import software.wings.beans.APMVerificationConfig.KeyValues;
import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.DynaTraceConfig;
import software.wings.beans.NewRelicConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.apm.Method;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.common.VerificationConstants;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.dl.WingsPersistence;
import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.service.impl.analysis.APMDelegateService;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse;
import software.wings.service.impl.apm.MLServiceUtils;
import software.wings.service.impl.newrelic.NewRelicApplication.NewRelicApplications;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.appdynamics.AppdynamicsDelegateService;
import software.wings.service.intfc.dynatrace.DynaTraceDelegateService;
import software.wings.service.intfc.newrelic.NewRelicDelegateService;
import software.wings.service.intfc.newrelic.NewRelicService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContextFactory;
import software.wings.sm.StateType;
import software.wings.sm.states.NewRelicState;
import software.wings.sm.states.NewRelicState.Metric;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by rsingh on 8/28/17.
 */
@Singleton
@Slf4j
public class NewRelicServiceImpl implements NewRelicService {
  @Inject private SettingsService settingsService;
  @Inject private DelegateProxyFactory delegateProxyFactory;
  @Inject private SecretManager secretManager;
  @Inject @Named(NEW_RELIC_APPLICATION_CACHE) private Cache<String, NewRelicApplications> newRelicApplicationCache;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ExecutionContextFactory executionContextFactory;
  @Inject private MLServiceUtils mlServiceUtils;
  @Inject private FeatureFlagService featureFlagService;

  @Override
  public void validateAPMConfig(SettingAttribute settingAttribute, APMValidateCollectorConfig config) {
    if (isNotEmpty(config.getUrl()) && isNotEmpty(config.getBaseUrl())) {
      if (!config.getBaseUrl().endsWith("/")) {
        throw new VerificationOperationException(
            ErrorCode.APM_CONFIGURATION_ERROR, "The Base URL must end with a / (forward slash)");
      }
      if (config.getUrl().charAt(0) == '/') {
        throw new VerificationOperationException(
            ErrorCode.APM_CONFIGURATION_ERROR, "The validation path must not begin with a / (forward slash)");
      }
      if (config.getBaseUrl().trim().length() != config.getBaseUrl().length()) {
        throw new VerificationOperationException(
            ErrorCode.APM_CONFIGURATION_ERROR, "The base URL can not have leading or trailing spaces.");
      }
    } else {
      throw new VerificationOperationException(
          ErrorCode.APM_CONFIGURATION_ERROR, "The Base URL and validation path must not be empty");
    }
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(settingAttribute.getAccountId())
                                          .appId(GLOBAL_APP_ID)
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                          .build();
    delegateProxyFactory.getV2(APMDelegateService.class, syncTaskContext).validateCollector(config);
  }

  @Override
  public String fetch(String accountId, String serverConfigId, APMFetchConfig fetchConfig) {
    try {
      SettingAttribute settingAttribute = settingsService.get(serverConfigId);
      APMVerificationConfig apmVerificationConfig = (APMVerificationConfig) settingAttribute.getValue();
      List<KeyValues> bodySecrets = APMVerificationConfig.getSecretNameIdKeyValueList(fetchConfig.getBody());
      List<KeyValues> urlSecrets = APMVerificationConfig.getSecretNameIdKeyValueList(fetchConfig.getUrl());

      APMValidateCollectorConfig apmValidateCollectorConfig =
          APMValidateCollectorConfig.builder()
              .baseUrl(apmVerificationConfig.getUrl())
              .headers(apmVerificationConfig.collectionHeaders())
              .options(apmVerificationConfig.collectionParams())
              .url(apmVerificationConfig.resolveSecretNameInUrlOrBody(fetchConfig.getUrl()))
              .body(apmVerificationConfig.resolveSecretNameInUrlOrBody(fetchConfig.getBody()))
              .encryptedDataDetails(apmVerificationConfig.encryptedDataDetails(secretManager,
                  Arrays.asList(bodySecrets, urlSecrets, apmVerificationConfig.getHeadersList(),
                      apmVerificationConfig.getOptionsList())))
              .build();
      if (isNotEmpty(fetchConfig.getBody())) {
        apmValidateCollectorConfig.setCollectionMethod(Method.POST);
      }

      SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                            .accountId(accountId)
                                            .appId(GLOBAL_APP_ID)
                                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                            .build();
      return delegateProxyFactory.getV2(APMDelegateService.class, syncTaskContext)
          .fetch(apmValidateCollectorConfig, ThirdPartyApiCallLog.createApiCallLog(accountId, fetchConfig.getGuid()));
    } catch (Exception e) {
      String errorMsg = e.getCause() != null ? ExceptionUtils.getMessage(e.getCause()) : ExceptionUtils.getMessage(e);
      throw new WingsException(ErrorCode.APM_CONFIGURATION_ERROR, USER).addParam("reason", errorMsg);
    }
  }

  @Override
  public void validateConfig(
      SettingAttribute settingAttribute, StateType stateType, List<EncryptedDataDetail> encryptedDataDetails) {
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(settingAttribute.getAccountId())
                                          .appId(GLOBAL_APP_ID)
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                          .build();
    switch (stateType) {
      case NEW_RELIC:
        delegateProxyFactory.getV2(NewRelicDelegateService.class, syncTaskContext)
            .validateConfig((NewRelicConfig) settingAttribute.getValue(), encryptedDataDetails);
        return;
      case APP_DYNAMICS:
        AppDynamicsConfig appDynamicsConfig = (AppDynamicsConfig) settingAttribute.getValue();
        delegateProxyFactory.getV2(AppdynamicsDelegateService.class, syncTaskContext)
            .validateConfig(appDynamicsConfig, encryptedDataDetails);
        return;
      case DYNA_TRACE:
        DynaTraceConfig dynaTraceConfig = (DynaTraceConfig) settingAttribute.getValue();
        delegateProxyFactory.getV2(DynaTraceDelegateService.class, syncTaskContext)
            .validateConfig(dynaTraceConfig, encryptedDataDetails);
        return;
      default:
        unhandled(stateType);
    }
  }

  @Override
  public List<NewRelicApplication> getApplications(String settingId, StateType stateType) {
    ErrorCode errorCode = null;
    try {
      final SettingAttribute settingAttribute = settingsService.get(settingId);
      List<EncryptedDataDetail> encryptionDetails =
          secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
      SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                            .accountId(settingAttribute.getAccountId())
                                            .appId(GLOBAL_APP_ID)
                                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                            .build();
      switch (stateType) {
        case NEW_RELIC:
          String key = settingAttribute.getUuid();
          NewRelicApplications applications;
          try {
            applications = newRelicApplicationCache.get(key);
            if (applications != null) {
              return applications.getApplications();
            }
          } catch (Exception ex) {
            // If there was any exception, remove that entry from cache
            newRelicApplicationCache.remove(key);
          }

          errorCode = ErrorCode.NEWRELIC_ERROR;
          List<NewRelicApplication> allApplications =
              delegateProxyFactory.getV2(NewRelicDelegateService.class, syncTaskContext)
                  .getAllApplications((NewRelicConfig) settingAttribute.getValue(), encryptionDetails, null);
          // check if number of apps is too big to compute
          if (isNotEmpty(allApplications) && allApplications.size() == 1 && allApplications.get(0).getId() == -1) {
            return allApplications;
          }
          applications = NewRelicApplications.builder().applications(allApplications).build();
          newRelicApplicationCache.put(key, applications);
          return allApplications;
        case APP_DYNAMICS:
          errorCode = ErrorCode.APPDYNAMICS_ERROR;
          return delegateProxyFactory.getV2(AppdynamicsDelegateService.class, syncTaskContext)
              .getAllApplications((AppDynamicsConfig) settingAttribute.getValue(), encryptionDetails);
        default:
          throw new IllegalStateException("Invalid state" + stateType);
      }

    } catch (Exception e) {
      throw new WingsException(errorCode, USER)
          .addParam("message", "Error in getting new relic applications. " + ExceptionUtils.getMessage(e));
    }
  }

  @Override
  public List<NewRelicApplicationInstance> getApplicationInstances(
      String settingId, long applicationId, StateType stateType) {
    ErrorCode errorCode = null;
    try {
      final SettingAttribute settingAttribute = settingsService.get(settingId);
      List<EncryptedDataDetail> encryptionDetails =
          secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
      SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                            .accountId(settingAttribute.getAccountId())
                                            .appId(GLOBAL_APP_ID)
                                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT * 3)
                                            .build();
      switch (stateType) {
        case NEW_RELIC:
          errorCode = ErrorCode.NEWRELIC_ERROR;
          return delegateProxyFactory.getV2(NewRelicDelegateService.class, syncTaskContext)
              .getApplicationInstances(
                  (NewRelicConfig) settingAttribute.getValue(), encryptionDetails, applicationId, null);
        default:
          throw new IllegalStateException("Invalid state" + stateType);
      }

    } catch (Exception e) {
      throw new WingsException(errorCode, USER)
          .addParam("message", "Error in getting new relic applications. " + e.getMessage());
    }
  }

  @Override
  public List<NewRelicMetric> getTxnsWithData(String settingId, long applicationId, long instanceId) {
    try {
      final SettingAttribute settingAttribute = settingsService.get(settingId);
      List<EncryptedDataDetail> encryptionDetails =
          secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
      SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                            .accountId(settingAttribute.getAccountId())
                                            .appId(GLOBAL_APP_ID)
                                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT * 3)
                                            .build();
      return delegateProxyFactory.getV2(NewRelicDelegateService.class, syncTaskContext)
          .getTxnsWithData((NewRelicConfig) settingAttribute.getValue(), encryptionDetails, applicationId,
              featureFlagService.isEnabled(
                  FeatureName.DISABLE_METRIC_NAME_CURLY_BRACE_CHECK, settingAttribute.getAccountId()),
              null);
    } catch (Exception e) {
      throw new WingsException(ErrorCode.NEWRELIC_ERROR, USER)
          .addParam("message", "Error in getting txns with data. " + e.getMessage());
    }
  }

  @Override
  public RestResponse<VerificationNodeDataSetupResponse> getMetricsWithDataForNode(
      NewRelicSetupTestNodeData setupTestNodeData) {
    String hostName;
    long instanceId = -1;
    // check if it is for service level, serviceId is empty then get hostname
    if (!setupTestNodeData.isServiceLevel()) {
      hostName = mlServiceUtils.getHostName(setupTestNodeData);
      List<NewRelicApplicationInstance> applicationInstances = getApplicationInstances(
          setupTestNodeData.getSettingId(), setupTestNodeData.getNewRelicAppId(), StateType.NEW_RELIC);

      for (NewRelicApplicationInstance applicationInstance : applicationInstances) {
        if (applicationInstance.getHost().equals(hostName)) {
          instanceId = applicationInstance.getId();
          break;
        }
      }

      if (instanceId == -1) {
        throw new WingsException(ErrorCode.NEWRELIC_CONFIGURATION_ERROR, USER)
            .addParam("reason", "No node with name " + hostName + " found reporting to new relic");
      }
    }

    setupTestNodeData.setToTime(System.currentTimeMillis());
    setupTestNodeData.setFromTime(setupTestNodeData.getToTime() - TimeUnit.MINUTES.toMillis(15));
    return new RestResponse<>(getMetricsWithDataForNode(setupTestNodeData, instanceId));
  }

  private VerificationNodeDataSetupResponse getMetricsWithDataForNode(
      NewRelicSetupTestNodeData setupTestNodeData, long instanceId) {
    try {
      final SettingAttribute settingAttribute = settingsService.get(setupTestNodeData.getSettingId());
      List<EncryptedDataDetail> encryptionDetails =
          secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
      SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                            .accountId(settingAttribute.getAccountId())
                                            .appId(GLOBAL_APP_ID)
                                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT * 3)
                                            .build();
      return delegateProxyFactory.getV2(NewRelicDelegateService.class, syncTaskContext)
          .getMetricsWithDataForNode((NewRelicConfig) settingAttribute.getValue(), encryptionDetails, setupTestNodeData,
              instanceId,
              !featureFlagService.isEnabled(
                  FeatureName.DISABLE_METRIC_NAME_CURLY_BRACE_CHECK, settingAttribute.getAccountId()),
              createApiCallLog(settingAttribute.getAccountId(), setupTestNodeData.getGuid()));
    } catch (Exception e) {
      log.info("error getting metric data for node", e);
      throw new WingsException(ErrorCode.NEWRELIC_ERROR, USER)
          .addParam("message", "Error in getting metric data for the node. " + e.getMessage());
    }
  }

  @Override
  public Map<String, TimeSeriesMetricDefinition> metricDefinitions(Collection<Metric> metrics) {
    Map<String, TimeSeriesMetricDefinition> metricDefinitionByName = new HashMap<>();
    for (Metric metric : metrics) {
      metricDefinitionByName.put(metric.getMetricName(),
          TimeSeriesMetricDefinition.builder()
              .metricName(metric.getMetricName())
              .metricType(metric.getMlMetricType())
              .build());
    }
    return metricDefinitionByName;
  }

  /**
   *
   * @param yamlPath - String containing path from rest/src/main/java/resources
   *                   e.g. Path to new relic metrics yaml => /apm/newrelic_metrics.yml
   * @return Mapping of name of the group of metrics (e.g. WebTransactions) to List of Metric Objects
   */
  private Map<String, List<Metric>> getMetricsFromYaml(String yamlPath) {
    YamlUtils yamlUtils = new YamlUtils();
    URL url = NewRelicState.class.getResource(yamlPath);
    try {
      String yaml = Resources.toString(url, Charsets.UTF_8);
      return yamlUtils.read(yaml, new TypeReference<Map<String, List<Metric>>>() {});
    } catch (IOException ioex) {
      log.error("Could not read " + yamlPath);
      throw new WingsException("Unable to load New Relic metrics", ioex);
    }
  }

  /**
   * Get a mapping from metric name to {@link Metric} for the list of metric names
   * provided as input.
   * This method is meant to be called before saving a metric template.
   * The output of this method shall be consumed by metricDefinitions(...)
   * @param metricNames - List[String] containing metric names
   * @return - Map[String, Metric], a mapping from metric name to {@link Metric}
   */
  @Override
  public Map<String, Metric> getMetricsCorrespondingToMetricNames(List<String> metricNames) {
    Map<String, Metric> metricMap = new HashMap<>();
    try {
      Map<String, List<Metric>> metrics = getMetricsFromYaml(VerificationConstants.getNewRelicMetricsYamlUrl());
      if (metrics == null) {
        return metricMap;
      }

      Set<String> metricNamesSet = metricNames == null ? new HashSet<>() : Sets.newHashSet(metricNames);

      // Iterate over the metrics present in the YAML file
      for (Map.Entry<String, List<Metric>> entry : metrics.entrySet()) {
        if (entry == null) {
          log.error("Found a null entry in the NewRelic Metrics YAML file.");
        } else {
          entry.getValue().forEach(metric -> {
            /*
            We consider 2 cases:
            1. metricNames is empty - we add all metrics present in the YAML to the metricMap in this case
            2. metricNames is non-empty - we only add metrics which are present in the list
             */
            if (metric != null && (isEmpty(metricNames) || metricNamesSet.contains(metric.getMetricName()))) {
              if (metric.getTags() == null) {
                metric.setTags(new HashSet<>());
              }
              // Add top-level key of the YAML as a tag
              metric.getTags().add(entry.getKey());
              metricMap.put(metric.getMetricName(), metric);
            }
          });
        }
      }

      /*
      If metricNames is non-empty but metricMap is, it means that all
      metric names were spelt incorrectly.
       */
      if (!isEmpty(metricNames) && metricMap.isEmpty()) {
        log.warn("Incorrect set of metric names received. Maybe the UI is sending incorrect metric names.");
        throw new WingsException("Incorrect Metric Names received.");
      }

      return metricMap;
    } catch (WingsException wex) {
      // Return empty metricMap
      return metricMap;
    }
  }

  /**
   * Read the YAML file containing New Relic's Metric Information
   * and return the metrics as a list.
   * @return List[Metric]
   */
  @Override
  public List<Metric> getListOfMetrics() {
    try {
      Map<String, List<Metric>> metricsMap = getMetricsFromYaml(VerificationConstants.getNewRelicMetricsYamlUrl());
      if (metricsMap == null) {
        log.error("Metric Map read from new relic metrics YAML is null. This is unexpected behaviour. "
            + "Probably the path to the YAML is incorrect.");
        return new ArrayList<>();
      }
      return metricsMap.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
    } catch (Exception ex) {
      throw new WingsException("Unable to load New Relic metrics", ex);
    }
  }

  @Override
  public NewRelicApplication resolveApplicationName(String settingId, String newRelicApplicationName) {
    return resolveApplicationName(settingId, newRelicApplicationName, null, null);
  }

  @Override
  public NewRelicApplication resolveApplicationName(
      String settingId, String newRelicApplicationName, String appId, String workflowExecutionId) {
    final SettingAttribute settingAttribute = settingsService.get(settingId);
    List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(
        (EncryptableSetting) settingAttribute.getValue(), appId, workflowExecutionId);
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(settingAttribute.getAccountId())
                                          .appId(GLOBAL_APP_ID)
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                          .build();
    return delegateProxyFactory.getV2(NewRelicDelegateService.class, syncTaskContext)
        .resolveNewRelicApplicationName(
            (NewRelicConfig) settingAttribute.getValue(), encryptionDetails, newRelicApplicationName, null);
  }

  @Override
  public NewRelicApplication resolveApplicationId(String settingId, String newRelicApplicationId) {
    return resolveApplicationId(settingId, newRelicApplicationId, null, null);
  }

  @Override
  public NewRelicApplication resolveApplicationId(
      String settingId, String newRelicApplicationId, String appId, String workflowExecutionId) {
    final SettingAttribute settingAttribute = settingsService.get(settingId);
    List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(
        (EncryptableSetting) settingAttribute.getValue(), appId, workflowExecutionId);
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(settingAttribute.getAccountId())
                                          .appId(GLOBAL_APP_ID)
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                          .build();
    return delegateProxyFactory.getV2(NewRelicDelegateService.class, syncTaskContext)
        .resolveNewRelicApplicationId(
            (NewRelicConfig) settingAttribute.getValue(), encryptionDetails, newRelicApplicationId, null);
  }
}
