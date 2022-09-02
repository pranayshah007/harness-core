/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.metrics;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.GoogleDataStoreAware.addFieldIfNotEmpty;
import static io.harness.persistence.GoogleDataStoreAware.readBlob;
import static io.harness.persistence.GoogleDataStoreAware.readLong;
import static io.harness.persistence.GoogleDataStoreAware.readString;

import static software.wings.common.VerificationConstants.CONNECTOR;
import static software.wings.common.VerificationConstants.DEFAULT_GROUP_NAME;
import static software.wings.common.VerificationConstants.ML_RECORDS_TTL_MONTHS;

import static java.lang.System.currentTimeMillis;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.exception.WingsException;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.GoogleDataStoreAware;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import software.wings.common.VerificationConstants;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.impl.verification.generated.TimeSeriesMetricRecordProto.MetricDeeplinks;
import software.wings.service.impl.verification.generated.TimeSeriesMetricRecordProto.MetricValues;
import software.wings.service.impl.verification.generated.TimeSeriesMetricRecordProto.TxnMetricValues;
import software.wings.service.intfc.analysis.ClusterLevel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.cloud.datastore.Blob;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Key;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import com.google.protobuf.InvalidProtocolBufferException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.PrePersist;
import org.mongodb.morphia.annotations.Transient;

/**
 * Created by rsingh on 08/30/17.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false,
    exclude = {"validUntil", "values", "valuesBytes", "deeplinkMetadata", "deeplinkUrl", "createdAt", "lastUpdatedAt"})
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "TimeSeriesMetricRecordKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "timeSeriesMetricRecords", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class TimeSeriesDataRecord
    implements GoogleDataStoreAware, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  private static final String ANCESTRY_ACCOUNT_ID = "Fi9wSBlxQmmjZxnsPBbFOQ";
  private static final String BUILD_DOT_COM_ACCOUNT_ID = "JWNrP_OyRrSL6qe9pCSI0g";
  private static final String BUILD_DOT_COM_SERVICE_ID = "ZoYZjErvQGqTKYv9obNy8w";
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("stateExIdx")
                 .field(TimeSeriesMetricRecordKeys.stateExecutionId)
                 .field(TimeSeriesMetricRecordKeys.groupName)
                 .descSortField(TimeSeriesMetricRecordKeys.dataCollectionMinute)
                 .build(),
            SortCompoundMongoIndex.builder()
                .name("workflowExIdx")
                .field(TimeSeriesMetricRecordKeys.workflowExecutionId)
                .field(TimeSeriesMetricRecordKeys.groupName)
                .descSortField(TimeSeriesMetricRecordKeys.dataCollectionMinute)
                .build(),
            SortCompoundMongoIndex.builder()
                .name("service_guard_idx")
                .field(TimeSeriesMetricRecordKeys.cvConfigId)
                .descSortField(TimeSeriesMetricRecordKeys.dataCollectionMinute)
                .build())
        .build();
  }

  @Id private String uuid;

  private DelegateStateType stateType; // could be null for older values

  private String workflowId;

  private String workflowExecutionId;

  private String serviceId;

  private String cvConfigId;

  private String stateExecutionId;

  @NotEmpty private long timeStamp;

  private int dataCollectionMinute;

  private String host;

  private ClusterLevel level;

  private String tag;

  @Default private String groupName = DEFAULT_GROUP_NAME;

  private byte[] valuesBytes;

  @Transient @Default private HashBasedTable<String, String, Double> values = HashBasedTable.create();

  @Transient @Default private HashBasedTable<String, String, String> deeplinkMetadata = HashBasedTable.create();

  private transient HashBasedTable<String, String, String> deeplinkUrl;

  private long createdAt;

  private long lastUpdatedAt;

  @FdIndex private String accountId;

  @JsonIgnore
  @SchemaIgnore
  @Default
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(1).toInstant());

  public void compress() {
    Preconditions.checkNotNull(values);

    final TxnMetricValues.Builder txnMetricBuilder = TxnMetricValues.newBuilder();

    values.rowMap().forEach((txnName, metricMap) -> {
      final MetricValues.Builder metricValueBuilder = MetricValues.newBuilder();
      metricMap.forEach(metricValueBuilder::putMetricValues);
      txnMetricBuilder.putValues(txnName, metricValueBuilder.build());
    });

    if (deeplinkMetadata != null) {
      deeplinkMetadata.rowMap().forEach((txnName, deepLinkMap) -> {
        final MetricDeeplinks.Builder deepLinkBuilder = MetricDeeplinks.newBuilder();
        deepLinkMap.forEach(deepLinkBuilder::putMetricDeeplinks);
        txnMetricBuilder.putDeeplinkMetadata(txnName, deepLinkBuilder.build());
      });
    }

    valuesBytes = txnMetricBuilder.build().toByteArray();
  }

  public void decompress() {
    if (isEmpty(valuesBytes)) {
      return;
    }

    values = HashBasedTable.create();
    deeplinkMetadata = HashBasedTable.create();

    try {
      TxnMetricValues txnMetricValues = TxnMetricValues.parseFrom(valuesBytes);
      txnMetricValues.getValuesMap().forEach(
          (txnName, metricValues) -> metricValues.getMetricValuesMap().forEach((metricName, value) -> {
            values.put(txnName, metricName, value);
          }));

      if (isNotEmpty(txnMetricValues.getDeeplinkMetadataMap())) {
        txnMetricValues.getDeeplinkMetadataMap().forEach(
            (txnName, deeplinks) -> deeplinks.getMetricDeeplinksMap().forEach((metricName, deepLink) -> {
              deeplinkMetadata.put(txnName, metricName, deepLink);
            }));
      }

      valuesBytes = null;
    } catch (InvalidProtocolBufferException e) {
      throw new WingsException(e);
    }
  }

  @PrePersist
  public void onSave() {
    final long currentTime = currentTimeMillis();
    if (createdAt == 0) {
      createdAt = currentTime;
    }
    lastUpdatedAt = currentTime;
  }

  @Override
  public com.google.cloud.datastore.Entity convertToCloudStorageEntity(Datastore datastore) {
    onSave();
    Key taskKey = datastore.newKeyFactory()
                      .setKind(this.getClass().getAnnotation(Entity.class).value())
                      .newKey(generateUniqueKey());
    com.google.cloud.datastore.Entity.Builder recordBuilder = com.google.cloud.datastore.Entity.newBuilder(taskKey);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesMetricRecordKeys.stateType, stateType.name(), true);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesMetricRecordKeys.workflowId, workflowId, true);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesMetricRecordKeys.workflowExecutionId, workflowExecutionId, false);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesMetricRecordKeys.serviceId, serviceId, true);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesMetricRecordKeys.cvConfigId, cvConfigId, false);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesMetricRecordKeys.stateExecutionId, stateExecutionId, false);
    recordBuilder.set(TimeSeriesMetricRecordKeys.timeStamp, timeStamp);
    recordBuilder.set(TimeSeriesMetricRecordKeys.dataCollectionMinute, dataCollectionMinute);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesMetricRecordKeys.host, host, false);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesMetricRecordKeys.level, level == null ? null : level.name(), false);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesMetricRecordKeys.tag, tag, false);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesMetricRecordKeys.groupName, groupName, false);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesMetricRecordKeys.accountId, accountId, false);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesMetricRecordKeys.createdAt, createdAt, true);
    addFieldIfNotEmpty(recordBuilder, TimeSeriesMetricRecordKeys.lastUpdatedAt, lastUpdatedAt, true);

    if (isNotEmpty(valuesBytes)) {
      addFieldIfNotEmpty(recordBuilder, TimeSeriesMetricRecordKeys.valuesBytes, Blob.copyFrom(valuesBytes), true);
    }

    if (validUntil == null) {
      validUntil = Date.from(OffsetDateTime.now().plusMonths(ML_RECORDS_TTL_MONTHS).toInstant());
    }
    recordBuilder.set(TimeSeriesMetricRecordKeys.validUntil, validUntil.getTime());

    return recordBuilder.build();
  }

  @Override
  public GoogleDataStoreAware readFromCloudStorageEntity(com.google.cloud.datastore.Entity entity) {
    final TimeSeriesDataRecord dataRecord =
        TimeSeriesDataRecord.builder()
            .workflowId(readString(entity, TimeSeriesMetricRecordKeys.workflowId))
            .workflowExecutionId(readString(entity, TimeSeriesMetricRecordKeys.workflowExecutionId))
            .serviceId(readString(entity, TimeSeriesMetricRecordKeys.serviceId))
            .cvConfigId(readString(entity, TimeSeriesMetricRecordKeys.cvConfigId))
            .stateExecutionId(readString(entity, TimeSeriesMetricRecordKeys.stateExecutionId))
            .accountId(readString(entity, TimeSeriesMetricRecordKeys.accountId))
            .timeStamp(readLong(entity, TimeSeriesMetricRecordKeys.timeStamp))
            .dataCollectionMinute((int) readLong(entity, TimeSeriesMetricRecordKeys.dataCollectionMinute))
            .host(readString(entity, TimeSeriesMetricRecordKeys.host))
            .tag(readString(entity, TimeSeriesMetricRecordKeys.tag))
            .groupName(readString(entity, TimeSeriesMetricRecordKeys.groupName))
            .valuesBytes(readBlob(entity, TimeSeriesMetricRecordKeys.valuesBytes))
            .createdAt(readLong(entity, TimeSeriesMetricRecordKeys.createdAt))
            .lastUpdatedAt(readLong(entity, TimeSeriesMetricRecordKeys.lastUpdatedAt))
            .build();

    final String level = readString(entity, TimeSeriesMetricRecordKeys.level);
    if (isNotEmpty(level)) {
      dataRecord.setLevel(ClusterLevel.valueOf(level));
    }

    final String stateType = readString(entity, TimeSeriesMetricRecordKeys.stateType);
    if (isNotEmpty(stateType)) {
      dataRecord.setStateType(DelegateStateType.valueOf(stateType));
    }

    return dataRecord;
  }

  private String generateUniqueKey() {
    StringBuilder keyBuilder = new StringBuilder();
    appendIfNecessary(keyBuilder, host);
    keyBuilder.append(CONNECTOR).append(timeStamp);
    appendIfNecessary(keyBuilder, workflowExecutionId);
    appendIfNecessary(keyBuilder, stateExecutionId);
    appendIfNecessary(keyBuilder, serviceId);
    appendIfNecessary(keyBuilder, workflowId);
    appendIfNecessary(keyBuilder, stateType.name());
    appendIfNecessary(keyBuilder, groupName);
    appendIfNecessary(keyBuilder, String.valueOf(dataCollectionMinute));
    appendIfNecessary(keyBuilder, tag);
    return Hashing.sha256().hashString(keyBuilder.toString(), StandardCharsets.UTF_8).toString();
  }

  private void appendIfNecessary(StringBuilder keyBuilder, String value) {
    if (isNotEmpty(value)) {
      keyBuilder.append(CONNECTOR).append(value);
    }
  }

  public static List<TimeSeriesDataRecord> getTimeSeriesDataRecordsFromNewRelicDataRecords(
      List<NewRelicMetricDataRecord> metricData) {
    Map<TimeSeriesDataRecord, TimeSeriesDataRecord> timeSeriesDataRecords = new HashMap();
    metricData.forEach(metric -> {
      TimeSeriesDataRecord timeSeriesDataRecord = TimeSeriesDataRecord.builder()
                                                      .stateType(metric.getStateType())
                                                      .workflowId(metric.getWorkflowId())
                                                      .workflowExecutionId(metric.getWorkflowExecutionId())
                                                      .serviceId(metric.getServiceId())
                                                      .cvConfigId(metric.getCvConfigId())
                                                      .accountId(metric.getAccountId())
                                                      .stateExecutionId(metric.getStateExecutionId())
                                                      .groupName(metric.getGroupName())
                                                      .timeStamp(metric.getTimeStamp())
                                                      .dataCollectionMinute(metric.getDataCollectionMinute())
                                                      .host(metric.getHost())
                                                      .level(metric.getLevel())
                                                      .tag(metric.getTag())
                                                      .createdAt(metric.getCreatedAt())
                                                      .lastUpdatedAt(metric.getLastUpdatedAt())
                                                      .build();

      HashBasedTable<String, String, Double> values = HashBasedTable.create();
      HashBasedTable<String, String, String> deeplinkMetadata = HashBasedTable.create();
      metric.getValues().forEach((metricName, value) -> values.put(metric.getName(), metricName, value));
      if (isNotEmpty(metric.getDeeplinkMetadata())) {
        metric.getDeeplinkMetadata().forEach(
            (metricName, deepLink) -> deeplinkMetadata.put(metric.getName(), metricName, deepLink));
      }
      if (timeSeriesDataRecords.containsKey(timeSeriesDataRecord)) {
        timeSeriesDataRecords.get(timeSeriesDataRecord).getValues().putAll(values);
        timeSeriesDataRecords.get(timeSeriesDataRecord).getDeeplinkMetadata().putAll(deeplinkMetadata);
      } else {
        timeSeriesDataRecord.setValues(values);
        timeSeriesDataRecord.setDeeplinkMetadata(deeplinkMetadata);
        timeSeriesDataRecords.put(timeSeriesDataRecord, timeSeriesDataRecord);
      }
    });
    return new ArrayList<>(timeSeriesDataRecords.values());
  }

  public static List<NewRelicMetricDataRecord> getNewRelicDataRecordsFromTimeSeriesDataRecords(
      List<TimeSeriesDataRecord> metricData) {
    metricData.forEach(TimeSeriesDataRecord::decompress);
    List<NewRelicMetricDataRecord> newRelicRecords = new ArrayList<>();
    metricData.forEach(metric -> {
      HashBasedTable<String, String, Double> values = metric.getValues();
      HashBasedTable<String, String, String> deeplinkMetadata = metric.getDeeplinkMetadata();

      if (metric.getLevel() != null) {
        NewRelicMetricDataRecord newRelicMetricDataRecord = NewRelicMetricDataRecord.builder()
                                                                .name(VerificationConstants.HEARTBEAT_METRIC_NAME)
                                                                .stateType(metric.getStateType())
                                                                .workflowId(metric.getWorkflowId())
                                                                .workflowExecutionId(metric.getWorkflowExecutionId())
                                                                .serviceId(metric.getServiceId())
                                                                .cvConfigId(metric.getCvConfigId())
                                                                .accountId(metric.getAccountId())
                                                                .stateExecutionId(metric.getStateExecutionId())
                                                                .groupName(metric.getGroupName())
                                                                .timeStamp(metric.getTimeStamp())
                                                                .dataCollectionMinute(metric.getDataCollectionMinute())
                                                                .host(metric.getHost())
                                                                .level(metric.getLevel())
                                                                .tag(metric.getTag())
                                                                .createdAt(metric.getCreatedAt())
                                                                .lastUpdatedAt(metric.getLastUpdatedAt())
                                                                .build();
        newRelicRecords.add(newRelicMetricDataRecord);
      } else {
        Set<String> names = values.rowKeySet();
        names.forEach(name -> {
          NewRelicMetricDataRecord newRelicMetricDataRecord =
              NewRelicMetricDataRecord.builder()
                  .name(name)
                  .stateType(metric.getStateType())
                  .workflowId(metric.getWorkflowId())
                  .workflowExecutionId(metric.getWorkflowExecutionId())
                  .serviceId(metric.getServiceId())
                  .cvConfigId(metric.getCvConfigId())
                  .accountId(metric.getAccountId())
                  .stateExecutionId(metric.getStateExecutionId())
                  .groupName(metric.getGroupName())
                  .timeStamp(metric.getTimeStamp())
                  .dataCollectionMinute(metric.getDataCollectionMinute())
                  .host(metric.getHost())
                  .level(metric.getLevel())
                  .tag(metric.getTag())
                  .values(values.row(name))
                  .deeplinkMetadata(deeplinkMetadata.row(name))
                  .build();
          newRelicRecords.add(newRelicMetricDataRecord);
        });
      }
    });
    return newRelicRecords;
  }

  // TODO: remove once CV-5872, CV-5904 is solved
  public static boolean shouldLogDetailedInfoForDebugging(String accountId, String serviceId) {
    return ANCESTRY_ACCOUNT_ID.equals(accountId)
        || (BUILD_DOT_COM_ACCOUNT_ID.equals(accountId) && BUILD_DOT_COM_SERVICE_ID.equals(serviceId));
  }
}
