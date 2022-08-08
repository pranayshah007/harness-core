/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.rule.Owner;

import software.wings.beans.ApmMetricCollectionInfo;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.apm.Method;
import software.wings.beans.apm.ResponseType;
import software.wings.common.VerificationConstants;
import software.wings.dl.WingsPersistence;
import software.wings.metrics.MetricType;
import software.wings.metrics.TimeSeriesDataRecord;
import software.wings.service.impl.analysis.AnalysisTolerance;
import software.wings.service.impl.analysis.ContinuousVerificationService;
import software.wings.service.impl.analysis.TimeSeries;
import software.wings.service.impl.analysis.TimeSeriesFilter;
import software.wings.service.impl.analysis.TimeSeriesMLAnalysisRecord;
import software.wings.service.impl.analysis.TimeSeriesMLHostSummary;
import software.wings.service.impl.analysis.TimeSeriesMLMetricSummary;
import software.wings.service.impl.analysis.TimeSeriesMLTxnSummary;
import software.wings.service.impl.cloudwatch.CloudWatchMetric;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.sm.StateType;
import software.wings.verification.CVConfiguration;
import software.wings.verification.TransactionTimeSeries;
import software.wings.verification.apm.APMCVServiceConfiguration;
import software.wings.verification.appdynamics.AppDynamicsCVServiceConfiguration;
import software.wings.verification.cloudwatch.CloudWatchCVServiceConfiguration;
import software.wings.verification.datadog.DatadogCVServiceConfiguration;
import software.wings.verification.dynatrace.DynaTraceCVServiceConfiguration;
import software.wings.verification.newrelic.NewRelicCVServiceConfiguration;
import software.wings.verification.prometheus.PrometheusCVServiceConfiguration;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.mongodb.DuplicateKeyException;
import io.fabric8.utils.Lists;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author Vaibhav Tulsyan
 * 22/Oct/2018
 */
@Slf4j
public class HeatMapApiIntegrationTest extends IntegrationTestBase {
  private static final SecureRandom random = new SecureRandom();

  @Inject WingsPersistence wingsPersistence;
  @Inject private ContinuousVerificationService continuousVerificationService;

  private PrometheusCVServiceConfiguration prometheusCVServiceConfiguration;
  private NewRelicCVServiceConfiguration newRelicCVServiceConfiguration;
  private APMCVServiceConfiguration apmcvServiceConfiguration;
  private AppDynamicsCVServiceConfiguration appDynamicsCVServiceConfiguration;
  private DatadogCVServiceConfiguration datadogCVServiceConfiguration;
  private DynaTraceCVServiceConfiguration dynaTraceCVServiceConfiguration;
  private CloudWatchCVServiceConfiguration cloudWatchCVServiceConfiguration;

  private SettingAttribute settingAttribute;
  private String settingAttributeId;
  private Application savedApp;
  private Service savedService;
  private Environment savedEnv;

  private long endTime = System.currentTimeMillis();
  private long start12HoursAgo = endTime - TimeUnit.HOURS.toMillis(12);
  private long start2HoursAgo = endTime - TimeUnit.HOURS.toMillis(2);

  @Override
  @Before
  public void setUp() {
    loginAdminUser();

    savedApp = wingsPersistence.saveAndGet(
        Application.class, Application.Builder.anApplication().name(generateUuid()).accountId(accountId).build());

    savedService = wingsPersistence.saveAndGet(
        Service.class, Service.builder().name(generateUuid()).appId(savedApp.getUuid()).build());

    savedEnv = wingsPersistence.saveAndGet(
        Environment.class, Environment.Builder.anEnvironment().name(generateUuid()).appId(savedApp.getUuid()).build());

    settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                           .withAccountId(accountId)
                           .withName("someSettingAttributeName")
                           .withCategory(SettingCategory.CONNECTOR)
                           .withEnvId(savedEnv.getUuid())
                           .withAppId(savedApp.getUuid())
                           .build();
    settingAttributeId = wingsPersistence.save(settingAttribute);

    createPrometheusConfig();
    createNewRelicConfig();
    createAPMConfig();
    createAppDConfig();
    createDataDogConfig();
    createDynaTraceConfig();
    createCloudWatchConfig();

    Map<String, List<String>> prometheusMetricMap = new HashMap<>();
    prometheusMetricMap.put("TRANSACTION1", Arrays.asList("METRIC1", "METRIC2"));
    prometheusMetricMap.put("TRANSACTION2", Arrays.asList("METRIC3", "METRIC4"));
    generate2HoursRandomData(StateType.PROMETHEUS, prometheusCVServiceConfiguration.getUuid(), prometheusMetricMap);

    Map<String, List<String>> newRelicMetricMap = new HashMap<>();
    newRelicMetricMap.put("/login", Arrays.asList("apdexScore", "averageResponseTime"));
    newRelicMetricMap.put("/exception", Collections.singletonList("error"));
    generate2HoursRandomData(StateType.NEW_RELIC, newRelicCVServiceConfiguration.getUuid(), newRelicMetricMap);

    Map<String, List<String>> apmMetricMap = new HashMap<>();
    apmMetricMap.put("METRIC1", Collections.singletonList("METRIC1"));
    apmMetricMap.put("METRIC2", Collections.singletonList("METRIC2"));
    generate2HoursRandomData(StateType.APM_VERIFICATION, apmcvServiceConfiguration.getUuid(), apmMetricMap);

    generate2HoursRandomData(StateType.APP_DYNAMICS, appDynamicsCVServiceConfiguration.getUuid(), newRelicMetricMap);

    Map<String, List<String>> datadogMetricMap = new HashMap<>();
    datadogMetricMap.put("ECS Container CPU Usage", Collections.singletonList("ECS Container CPU Usage"));
    datadogMetricMap.put("ECS Container RSS Memory", Collections.singletonList("ECS Container RSS Memory"));
    generate2HoursRandomData(StateType.DATA_DOG, datadogCVServiceConfiguration.getUuid(), datadogMetricMap);

    generate2HoursRandomData(StateType.DYNA_TRACE, dynaTraceCVServiceConfiguration.getUuid(), newRelicMetricMap);

    Map<String, List<String>> cloudWatchMetricMap = new HashMap<>();
    cloudWatchMetricMap.put("CPU Reservation", Collections.singletonList("CPU Reservation"));
    cloudWatchMetricMap.put("CPU Utilization", Collections.singletonList("CPU Utilization"));
    generate2HoursRandomData(StateType.CLOUD_WATCH, cloudWatchCVServiceConfiguration.getUuid(), cloudWatchMetricMap);
  }

  private void createCloudWatchConfig() {
    List<CloudWatchMetric> ecsMetrics = new ArrayList<>();
    ecsMetrics.add(CloudWatchMetric.builder().metricName("CPUReservation").metricType(MetricType.INFRA.name()).build());
    ecsMetrics.add(CloudWatchMetric.builder().metricName("CPUUtilization").metricType(MetricType.INFRA.name()).build());
    cloudWatchCVServiceConfiguration = CloudWatchCVServiceConfiguration.builder().ec2Metrics(ecsMetrics).build();
    cloudWatchCVServiceConfiguration.setStateType(StateType.CLOUD_WATCH);
    setCommonConfigDetails(cloudWatchCVServiceConfiguration);
    wingsPersistence.save(cloudWatchCVServiceConfiguration);
  }

  private void createDynaTraceConfig() {
    dynaTraceCVServiceConfiguration = DynaTraceCVServiceConfiguration.builder().build();
    dynaTraceCVServiceConfiguration.setStateType(StateType.DYNA_TRACE);
    setCommonConfigDetails(dynaTraceCVServiceConfiguration);
    wingsPersistence.save(dynaTraceCVServiceConfiguration);
  }

  private void createDataDogConfig() {
    Map<String, String> ecsMetricMap = new HashMap<>();
    ecsMetricMap.put(generateUuid(), "ecs.fargate.cpu.user");
    ecsMetricMap.put(generateUuid(), "ecs.fargate.mem.rss");
    datadogCVServiceConfiguration = DatadogCVServiceConfiguration.builder().ecsMetrics(ecsMetricMap).build();
    datadogCVServiceConfiguration.setStateType(StateType.DATA_DOG);
    setCommonConfigDetails(datadogCVServiceConfiguration);
    wingsPersistence.save(datadogCVServiceConfiguration);
  }

  private void createAppDConfig() {
    appDynamicsCVServiceConfiguration = AppDynamicsCVServiceConfiguration.builder()
                                            .appDynamicsApplicationId(generateUuid())
                                            .tierId(generateUuid())
                                            .build();
    appDynamicsCVServiceConfiguration.setStateType(StateType.APP_DYNAMICS);
    setCommonConfigDetails(appDynamicsCVServiceConfiguration);
    wingsPersistence.save(appDynamicsCVServiceConfiguration);
  }

  private void createAPMConfig() {
    List<ApmMetricCollectionInfo> timeSeries = Lists.newArrayList(ApmMetricCollectionInfo.builder()
                                                                      .collectionUrl("URL1")
                                                                      .metricName("METRIC1")
                                                                      .method(Method.GET)
                                                                      .responseType(ResponseType.JSON)
                                                                      .metricType(MetricType.THROUGHPUT)
                                                                      .build(),
        ApmMetricCollectionInfo.builder()
            .collectionUrl("URL2")
            .metricName("METRIC2")
            .method(Method.GET)
            .responseType(ResponseType.JSON)
            .metricType(MetricType.ERROR)
            .build());
    apmcvServiceConfiguration = APMCVServiceConfiguration.builder().metricCollectionInfos(timeSeries).build();
    apmcvServiceConfiguration.setStateType(StateType.APM_VERIFICATION);
    setCommonConfigDetails(apmcvServiceConfiguration);
    wingsPersistence.save(apmcvServiceConfiguration);
  }

  private void setCommonConfigDetails(CVConfiguration cvConfiguration) {
    cvConfiguration.setName(generateUuid());
    cvConfiguration.setAppId(savedApp.getUuid());
    cvConfiguration.setEnvId(savedEnv.getUuid());
    cvConfiguration.setEnvName(savedEnv.getName());
    cvConfiguration.setServiceId(savedService.getUuid());
    cvConfiguration.setServiceName(savedService.getName());
    cvConfiguration.setEnabled24x7(true);
    cvConfiguration.setConnectorName(settingAttribute.getName());
    cvConfiguration.setConnectorId(settingAttributeId);
    cvConfiguration.setAnalysisTolerance(AnalysisTolerance.MEDIUM);
  }

  private void createPrometheusConfig() {
    List<TimeSeries> timeSeries = Lists.newArrayList(TimeSeries.builder()
                                                         .url("URL1")
                                                         .txnName("TRANSACTION1")
                                                         .metricName("METRIC1")
                                                         .metricType(MetricType.VALUE.name())
                                                         .build(),
        TimeSeries.builder()
            .url("URL2")
            .txnName("TRANSACTION1")
            .metricName("METRIC2")
            .metricType(MetricType.THROUGHPUT.name())
            .build(),
        TimeSeries.builder()
            .url("URL3")
            .txnName("TRANSACTION2")
            .metricName("METRIC3")
            .metricType(MetricType.INFRA.name())
            .build(),
        TimeSeries.builder()
            .url("URL4")
            .txnName("TRANSACTION2")
            .metricName("METRIC4")
            .metricType(MetricType.ERROR.name())
            .build());
    prometheusCVServiceConfiguration =
        PrometheusCVServiceConfiguration.builder().timeSeriesToAnalyze(timeSeries).build();
    prometheusCVServiceConfiguration.setStateType(StateType.PROMETHEUS);
    setCommonConfigDetails(prometheusCVServiceConfiguration);

    wingsPersistence.save(prometheusCVServiceConfiguration);
  }

  private void createNewRelicConfig() {
    newRelicCVServiceConfiguration = new NewRelicCVServiceConfiguration();
    newRelicCVServiceConfiguration.setStateType(StateType.NEW_RELIC);
    newRelicCVServiceConfiguration.setApplicationId(generateUuid());
    newRelicCVServiceConfiguration.setMetrics(Collections.singletonList("apdexScore"));

    setCommonConfigDetails(newRelicCVServiceConfiguration);

    newRelicCVServiceConfiguration = (NewRelicCVServiceConfiguration) wingsPersistence.saveAndGet(
        CVConfiguration.class, newRelicCVServiceConfiguration);
  }

  private void generate2HoursRandomData(
      StateType stateType, String configId, Map<String, List<String>> transactionMetricMap) {
    // Add time series analysis records for each minute in 30 days
    int currentMinute = (int) TimeUnit.MILLISECONDS.toMinutes(start2HoursAgo);
    int minutesIn2Hours = (int) TimeUnit.HOURS.toMinutes(2);
    log.info("Creating {} units", minutesIn2Hours);

    List<NewRelicMetricDataRecord> metricDataRecords = new ArrayList<>();
    for (int i = 0; i < minutesIn2Hours; i++) {
      TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord = TimeSeriesMLAnalysisRecord.builder().build();
      timeSeriesMLAnalysisRecord.setAppId(savedApp.getUuid());
      timeSeriesMLAnalysisRecord.setAnalysisMinute(currentMinute + i);
      timeSeriesMLAnalysisRecord.setCvConfigId(configId);
      timeSeriesMLAnalysisRecord.setStateType(stateType);

      Map<String, TimeSeriesMLTxnSummary> txnMap = new HashMap<>();

      int finalI = i;
      transactionMetricMap.forEach((txnName, metricList) -> {
        TimeSeriesMLTxnSummary txnSummary = new TimeSeriesMLTxnSummary();
        txnSummary.setTxn_name(txnName);
        txnSummary.setGroup_name("default");

        Map<String, TimeSeriesMLMetricSummary> summary = new HashMap<>();
        metricList.forEach(metric -> {
          TimeSeriesMLMetricSummary metricSummary = new TimeSeriesMLMetricSummary();
          metricSummary.setMetric_name(metric);

          TimeSeriesMLHostSummary timeSeriesMLHostSummary;
          int risk = -1; // NA by default

          if (finalI >= minutesIn2Hours - (VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES) * 32) {
            risk = 2;
          } else if (finalI >= minutesIn2Hours - 2 * (VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES) * 32) {
            risk = 0;
          } else {
            int randomNum = random.nextInt(2);
            if (randomNum % 2 == 0) {
              risk = random.nextInt(3);
            }
          }

          timeSeriesMLHostSummary = TimeSeriesMLHostSummary.builder().risk(risk).build();
          Map<String, TimeSeriesMLHostSummary> results = new HashMap<>();
          results.put(generateUuid(), timeSeriesMLHostSummary);
          metricSummary.setResults(results);

          summary.put(metric, metricSummary);
        });
        txnSummary.setMetrics(summary);
        txnMap.put(txnName, txnSummary);
      });

      timeSeriesMLAnalysisRecord.setTransactions(txnMap);

      try {
        wingsPersistence.save(timeSeriesMLAnalysisRecord);
      } catch (DuplicateKeyException e) {
        // Eating exception and not logging because there would be too many logs (one per record)
        // Hitting this point is expected in all tests after the first test.
      }

      transactionMetricMap.forEach(
          (txn, metric)
              -> metricDataRecords.add(getNewRelicMetricRecord(txn, currentMinute + 1, configId, metric, stateType)));
    }
    final List<TimeSeriesDataRecord> dataRecords =
        TimeSeriesDataRecord.getTimeSeriesDataRecordsFromNewRelicDataRecords(metricDataRecords);
    dataRecords.forEach(TimeSeriesDataRecord::compress);
    wingsPersistence.save(dataRecords);
  }

  private NewRelicMetricDataRecord getNewRelicMetricRecord(
      String transactionName, int minute, String configId, List<String> metrics, StateType stateType) {
    // Add new relic metric record for minute=currentMinute + i
    NewRelicMetricDataRecord newRelicMetricDataRecord = NewRelicMetricDataRecord.builder()
                                                            .appId(savedApp.getUuid())
                                                            .groupName("default")
                                                            .name(transactionName)
                                                            .serviceId(savedService.getUuid())
                                                            .stateType(stateType.getDelegateStateType())
                                                            .dataCollectionMinute(minute)
                                                            .timeStamp(TimeUnit.MINUTES.toMillis(minute))
                                                            .host(generateUuid())
                                                            .workflowExecutionId(generateUuid())
                                                            .stateExecutionId(generateUuid())
                                                            .cvConfigId(configId)
                                                            .build();

    final Double[] val = {0.5 + random.nextDouble() * 10};
    Map<String, Double> valueMap = new HashMap<>();
    metrics.forEach(metric -> {
      valueMap.put(metric, val[0]);
      val[0] = 0.5 + random.nextDouble() * 10;
    });

    newRelicMetricDataRecord.setValues(valueMap);
    return newRelicMetricDataRecord;
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testTimeSeriesUnitPrometheus() {
    TimeSeriesFilter filter = TimeSeriesFilter.builder()
                                  .cvConfigId(prometheusCVServiceConfiguration.getUuid())
                                  .startTime(start12HoursAgo)
                                  .endTime(endTime)
                                  .build();
    List<TransactionTimeSeries> fetchedObject = new ArrayList<>(
        continuousVerificationService.getTimeSeriesOfHeatMapUnitV2(filter, Optional.empty(), Optional.empty())
            .getTimeSeriesSet());
    assertThat(fetchedObject).hasSize(2);
    assertThat(fetchedObject.get(0).getTransactionName()).isEqualTo("TRANSACTION1");
    assertThat(fetchedObject.get(0).getMetricTimeSeries().first().getMetricName()).isEqualTo("METRIC1");
    assertThat(fetchedObject.get(0).getMetricTimeSeries().last().getMetricName()).isEqualTo("METRIC2");
    assertThat(fetchedObject.get(1).getTransactionName()).isEqualTo("TRANSACTION2");
    assertThat(fetchedObject.get(1).getMetricTimeSeries().first().getMetricName()).isEqualTo("METRIC3");
    assertThat(fetchedObject.get(1).getMetricTimeSeries().last().getMetricName()).isEqualTo("METRIC4");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testTimeSeriesUnitNewRelic() {
    TimeSeriesFilter filter = TimeSeriesFilter.builder()
                                  .cvConfigId(newRelicCVServiceConfiguration.getUuid())
                                  .startTime(start12HoursAgo)
                                  .endTime(endTime)
                                  .build();
    List<TransactionTimeSeries> fetchedObject = new ArrayList<>(
        continuousVerificationService.getTimeSeriesOfHeatMapUnitV2(filter, Optional.empty(), Optional.empty())
            .getTimeSeriesSet());
    assertThat(fetchedObject).hasSize(2);
    assertThat(fetchedObject.get(0).getTransactionName()).isEqualTo("/exception");
    assertThat(fetchedObject.get(0).getMetricTimeSeries().first().getMetricName()).isEqualTo("Error Percentage");
    assertThat(fetchedObject.get(1).getTransactionName()).isEqualTo("/login");
    assertThat(fetchedObject.get(1).getMetricTimeSeries().first().getMetricName()).isEqualTo("apdexScore");
    assertThat(fetchedObject.get(1).getMetricTimeSeries().last().getMetricName()).isEqualTo("averageResponseTime");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testTimeSeriesUnitAPM() {
    TimeSeriesFilter filter = TimeSeriesFilter.builder()
                                  .cvConfigId(apmcvServiceConfiguration.getUuid())
                                  .startTime(start12HoursAgo)
                                  .endTime(endTime)
                                  .build();
    List<TransactionTimeSeries> fetchedObject = new ArrayList<>(
        continuousVerificationService.getTimeSeriesOfHeatMapUnitV2(filter, Optional.empty(), Optional.empty())
            .getTimeSeriesSet());
    assertThat(fetchedObject).hasSize(2);
    assertThat(fetchedObject.get(0).getTransactionName()).isEqualTo("METRIC1");
    assertThat(fetchedObject.get(0).getMetricTimeSeries().first().getMetricName()).isEqualTo("METRIC1");
    assertThat(fetchedObject.get(1).getTransactionName()).isEqualTo("METRIC2");
    assertThat(fetchedObject.get(1).getMetricTimeSeries().first().getMetricName()).isEqualTo("METRIC2");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testTimeSeriesUnitAppD() {
    TimeSeriesFilter filter = TimeSeriesFilter.builder()
                                  .cvConfigId(appDynamicsCVServiceConfiguration.getUuid())
                                  .startTime(start12HoursAgo)
                                  .endTime(endTime)
                                  .build();
    List<TransactionTimeSeries> fetchedObject = new ArrayList<>(
        continuousVerificationService.getTimeSeriesOfHeatMapUnitV2(filter, Optional.empty(), Optional.empty())
            .getTimeSeriesSet());
    assertThat(fetchedObject).hasSize(2);
    assertThat(fetchedObject.get(0).getTransactionName()).isEqualTo("/exception");
    assertThat(fetchedObject.get(0).getMetricTimeSeries().first().getMetricName()).isEqualTo("Error Percentage");
    assertThat(fetchedObject.get(1).getTransactionName()).isEqualTo("/login");
    assertThat(fetchedObject.get(1).getMetricTimeSeries().first().getMetricName()).isEqualTo("apdexScore");
    assertThat(fetchedObject.get(1).getMetricTimeSeries().last().getMetricName()).isEqualTo("averageResponseTime");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testTimeSeriesUnitDatadog() {
    TimeSeriesFilter filter = TimeSeriesFilter.builder()
                                  .cvConfigId(datadogCVServiceConfiguration.getUuid())
                                  .startTime(start12HoursAgo)
                                  .endTime(endTime)
                                  .build();
    List<TransactionTimeSeries> fetchedObject = new ArrayList<>(
        continuousVerificationService.getTimeSeriesOfHeatMapUnitV2(filter, Optional.empty(), Optional.empty())
            .getTimeSeriesSet());
    assertThat(fetchedObject).hasSize(2);
    assertThat(fetchedObject.get(0).getTransactionName()).isEqualTo("ECS Container CPU Usage");
    assertThat(fetchedObject.get(0).getMetricTimeSeries().first().getMetricName()).isEqualTo("ECS Container CPU Usage");
    assertThat(fetchedObject.get(1).getTransactionName()).isEqualTo("ECS Container RSS Memory");
    assertThat(fetchedObject.get(1).getMetricTimeSeries().first().getMetricName())
        .isEqualTo("ECS Container RSS Memory");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testTimeSeriesUnitDynaTrace_withMetricNameFilter() {
    TimeSeriesFilter filter = TimeSeriesFilter.builder()
                                  .cvConfigId(dynaTraceCVServiceConfiguration.getUuid())
                                  .startTime(start12HoursAgo)
                                  .endTime(endTime)
                                  .metricNames(Sets.newHashSet("apdexScore", "averageResponseTime"))
                                  .build();
    List<TransactionTimeSeries> fetchedObject = new ArrayList<>(
        continuousVerificationService.getTimeSeriesOfHeatMapUnitV2(filter, Optional.empty(), Optional.empty())
            .getTimeSeriesSet());
    assertThat(fetchedObject).hasSize(1);
    assertThat(fetchedObject.get(0).getTransactionName()).isEqualTo("/login");
    assertThat(fetchedObject.get(0).getMetricTimeSeries().first().getMetricName()).isEqualTo("apdexScore");
    assertThat(fetchedObject.get(0).getMetricTimeSeries().last().getMetricName()).isEqualTo("averageResponseTime");
  }
  @Test
  @Owner(developers = SOWMYA)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testTimeSeriesUnitDynaTrace() {
    TimeSeriesFilter filter = TimeSeriesFilter.builder()
                                  .cvConfigId(dynaTraceCVServiceConfiguration.getUuid())
                                  .startTime(start12HoursAgo)
                                  .endTime(endTime)
                                  .build();
    List<TransactionTimeSeries> fetchedObject = new ArrayList<>(
        continuousVerificationService.getTimeSeriesOfHeatMapUnitV2(filter, Optional.empty(), Optional.empty())
            .getTimeSeriesSet());
    assertThat(fetchedObject).hasSize(2);
    assertThat(fetchedObject.get(0).getTransactionName()).isEqualTo("/exception");
    assertThat(fetchedObject.get(0).getMetricTimeSeries().first().getMetricName()).isEqualTo("Error Percentage");
    assertThat(fetchedObject.get(1).getTransactionName()).isEqualTo("/login");
    assertThat(fetchedObject.get(1).getMetricTimeSeries().first().getMetricName()).isEqualTo("apdexScore");
    assertThat(fetchedObject.get(1).getMetricTimeSeries().last().getMetricName()).isEqualTo("averageResponseTime");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testTimeSeriesUnitCloudWatch() {
    TimeSeriesFilter filter = TimeSeriesFilter.builder()
                                  .cvConfigId(cloudWatchCVServiceConfiguration.getUuid())
                                  .startTime(start12HoursAgo)
                                  .endTime(endTime)
                                  .build();
    List<TransactionTimeSeries> fetchedObject = new ArrayList<>(
        continuousVerificationService.getTimeSeriesOfHeatMapUnitV2(filter, Optional.empty(), Optional.empty())
            .getTimeSeriesSet());
    assertThat(fetchedObject).hasSize(2);
    assertThat(fetchedObject.get(0).getTransactionName()).isEqualTo("CPU Reservation");
    assertThat(fetchedObject.get(0).getMetricTimeSeries().first().getMetricName()).isEqualTo("CPU Reservation");
    assertThat(fetchedObject.get(1).getTransactionName()).isEqualTo("CPU Utilization");
    assertThat(fetchedObject.get(1).getMetricTimeSeries().first().getMetricName()).isEqualTo("CPU Utilization");
  }
}
