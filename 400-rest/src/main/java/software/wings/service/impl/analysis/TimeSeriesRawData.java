/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.GoogleDataStoreAware.addFieldIfNotEmpty;
import static io.harness.persistence.GoogleDataStoreAware.readList;
import static io.harness.persistence.GoogleDataStoreAware.readLong;
import static io.harness.persistence.GoogleDataStoreAware.readString;

import io.harness.annotation.StoreIn;
import io.harness.beans.ExecutionStatus;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.GoogleDataStoreAware;
import io.harness.persistence.UuidAware;

import software.wings.metrics.MetricType;
import software.wings.metrics.RiskLevel;
import software.wings.service.intfc.DataStoreService;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.inject.Inject;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, exclude = {"validUntil"})
@StoreIn(DbAliases.HARNESS)
@org.mongodb.morphia.annotations.Entity(value = "timeSeriesRawData", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "TimeSeriesRawDataKeys")
@Slf4j
public class TimeSeriesRawData implements GoogleDataStoreAware, UuidAware, AccountAccess {
  public static final String connector = ":";

  @Inject private DataStoreService dataStoreService;

  @Id private String uuid;

  private String accountId;
  private String appId;
  private String cvConfigId;
  private String stateExecutionId;
  private String serviceId;
  private String transactionName;
  private String metricName;
  private MetricType metricType;
  private String executionStatus;
  private List<Double> controlData;
  private List<Double> testData;
  private List<Double> optimalData;
  private Long createdAt;
  private Long lastUpdatedAt;

  @JsonIgnore
  @SchemaIgnore
  @FdTtlIndex
  @Default
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(6).toInstant());

  private String getKey() {
    return String.join(connector, stateExecutionId, transactionName, metricName);
  }

  @Override
  public Entity convertToCloudStorageEntity(Datastore datastore) {
    Key taskKey = datastore.newKeyFactory()
                      .setKind(this.getClass().getAnnotation(org.mongodb.morphia.annotations.Entity.class).value())
                      .newKey(getKey());
    com.google.cloud.datastore.Entity.Builder recordBuilder = com.google.cloud.datastore.Entity.newBuilder(taskKey);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesRawDataKeys.accountId, accountId, false);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesRawDataKeys.appId, appId, false);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesRawDataKeys.cvConfigId, cvConfigId, false);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesRawDataKeys.stateExecutionId, stateExecutionId, false);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesRawDataKeys.serviceId, serviceId, false);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesRawDataKeys.transactionName, transactionName, false);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesRawDataKeys.metricName, metricName, false);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesRawDataKeys.metricType, metricType.name(), true);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesRawDataKeys.executionStatus, executionStatus, false);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesRawDataKeys.controlData, controlData, Double.class);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesRawDataKeys.testData, testData, Double.class);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesRawDataKeys.optimalData, optimalData, Double.class);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesRawDataKeys.createdAt, createdAt, false);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesRawDataKeys.lastUpdatedAt, lastUpdatedAt, false);

    if (validUntil != null) {
      recordBuilder.set(TimeSeriesRawDataKeys.validUntil, validUntil.getTime());
    }

    return recordBuilder.build();
  }

  @Override
  public GoogleDataStoreAware readFromCloudStorageEntity(Entity entity) {
    return TimeSeriesRawData.builder()
        .accountId(readString(entity, TimeSeriesRawDataKeys.accountId))
        .appId(readString(entity, TimeSeriesRawDataKeys.appId))
        .cvConfigId(readString(entity, TimeSeriesRawDataKeys.cvConfigId))
        .stateExecutionId(readString(entity, TimeSeriesRawDataKeys.stateExecutionId))
        .serviceId(readString(entity, TimeSeriesRawDataKeys.serviceId))
        .transactionName(readString(entity, TimeSeriesRawDataKeys.transactionName))
        .metricName(readString(entity, TimeSeriesRawDataKeys.metricName))
        .metricType(MetricType.valueOf(readString(entity, TimeSeriesRawDataKeys.metricType)))
        .executionStatus(readString(entity, TimeSeriesRawDataKeys.executionStatus))
        .controlData(readList(entity, TimeSeriesRawDataKeys.controlData, Double.class))
        .testData(readList(entity, TimeSeriesRawDataKeys.testData, Double.class))
        .optimalData(readList(entity, TimeSeriesRawDataKeys.optimalData, Double.class))
        .createdAt(readLong(entity, TimeSeriesRawDataKeys.createdAt))
        .lastUpdatedAt(readLong(entity, TimeSeriesRawDataKeys.lastUpdatedAt))
        .validUntil(new Date(readLong(entity, TimeSeriesRawDataKeys.validUntil)))
        .build();
  }

  public static void populateRawDataFromAnalysisRecords(MetricAnalysisRecord record, String accountId,
      ExecutionStatus executionStatus, Map<String, Map<String, TimeSeriesRawData>> existingRawDataMap,
      String serviceId) {
    record.decompress(false);

    Map<String, TimeSeriesMLTxnSummary> transactionSummaryMap = record.getTransactions();

    if (isEmpty(transactionSummaryMap)) {
      return;
    }

    transactionSummaryMap.values().forEach(summary -> {
      if (summary == null) {
        return;
      }

      summary.getMetrics().values().forEach(metric -> {
        Map<String, TimeSeriesMLHostSummary> hostSummaryMap = metric.getResults();
        List<Double> controlData = new ArrayList<>();
        List<Double> testData = new ArrayList<>();
        List<Double> optimalData = new ArrayList<>();

        hostSummaryMap.values().forEach(hostSummary -> {
          if (RiskLevel.LOW == RiskLevel.getRiskLevel(hostSummary.getRisk())) {
            if (isNotEmpty(hostSummary.getControl_data())) {
              controlData.addAll(hostSummary.getControl_data());
            }
            if (isNotEmpty(hostSummary.getTest_data())) {
              testData.addAll(hostSummary.getTest_data());
            }
            if (isNotEmpty(hostSummary.getOptimal_data())) {
              optimalData.addAll(hostSummary.getOptimal_data());
            }
          }
        });

        if (existingRawDataMap.containsKey(summary.getTxn_name())
            && existingRawDataMap.get(summary.getTxn_name()).containsKey(metric.getMetric_name())) {
          TimeSeriesRawData rawData = existingRawDataMap.get(summary.getTxn_name()).get(metric.getMetric_name());
          rawData.getControlData().addAll(controlData);
          rawData.getTestData().addAll(testData);
          rawData.getOptimalData().addAll(optimalData);
        } else {
          TimeSeriesRawData rawData = TimeSeriesRawData.builder()
                                          .accountId(accountId)
                                          .appId(record.getAppId())
                                          .cvConfigId(record.getCvConfigId())
                                          .stateExecutionId(record.getStateExecutionId())
                                          .serviceId(serviceId)
                                          .transactionName(summary.getTxn_name())
                                          .metricName(metric.getMetric_name())
                                          .metricType(MetricType.valueOf(metric.getMetric_type()))
                                          .executionStatus(executionStatus.name())
                                          .controlData(controlData)
                                          .testData(testData)
                                          .optimalData(optimalData)
                                          .createdAt(record.getCreatedAt())
                                          .lastUpdatedAt(record.getLastUpdatedAt())
                                          .build();
          if (!existingRawDataMap.containsKey(summary.getTxn_name())) {
            existingRawDataMap.put(summary.getTxn_name(), new HashMap<>());
          }
          existingRawDataMap.get(summary.getTxn_name()).put(metric.getMetric_name(), rawData);
        }
      });
    });
  }
}
