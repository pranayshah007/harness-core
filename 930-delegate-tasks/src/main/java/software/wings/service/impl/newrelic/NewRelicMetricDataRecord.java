/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.newrelic;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.GoogleDataStoreAware.addFieldIfNotEmpty;
import static io.harness.persistence.GoogleDataStoreAware.readLong;
import static io.harness.persistence.GoogleDataStoreAware.readString;

import static software.wings.delegatetasks.cv.CVConstants.ML_RECORDS_TTL_MONTHS;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.IgnoreUnusedIndex;
import io.harness.annotations.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.GoogleDataStoreAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.persistence.ValidUntilAccess;
import io.harness.serializer.JsonUtils;
import io.harness.validation.Update;

import software.wings.delegatetasks.DelegateStateType;
import software.wings.service.intfc.analysis.ClusterLevel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Key;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Transient;

/**
 * Created by rsingh on 08/30/17.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, exclude = {"validUntil", "values", "deeplinkMetadata", "deeplinkUrl"})
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "NewRelicMetricDataRecordKeys")
@IgnoreUnusedIndex
@StoreIn(DbAliases.HARNESS)
@Entity(value = "newRelicMetricRecords", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class NewRelicMetricDataRecord
    implements PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware, UpdatedAtAware, UpdatedByAware,
               AccountAccess, GoogleDataStoreAware, ValidUntilAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("stateExIdx")
                 .field(NewRelicMetricDataRecordKeys.stateExecutionId)
                 .field(NewRelicMetricDataRecordKeys.groupName)
                 .descSortField(NewRelicMetricDataRecordKeys.dataCollectionMinute)
                 .build(),
            SortCompoundMongoIndex.builder()
                .name("workflowExIdx")
                .field(NewRelicMetricDataRecordKeys.workflowExecutionId)
                .field(NewRelicMetricDataRecordKeys.groupName)
                .descSortField(NewRelicMetricDataRecordKeys.dataCollectionMinute)
                .build(),
            SortCompoundMongoIndex.builder()
                .name("serviceGuardIndex")
                .descSortField(NewRelicMetricDataRecordKeys.dataCollectionMinute)
                .field(NewRelicMetricDataRecordKeys.cvConfigId)
                .build())
        .build();
  }
  @Deprecated public static final String ID_KEY2 = "_id";

  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @FdIndex @NotNull @SchemaIgnore protected String appId;
  @SchemaIgnore private EmbeddedUser createdBy;
  @SchemaIgnore @FdIndex private long createdAt;

  @SchemaIgnore private EmbeddedUser lastUpdatedBy;
  @SchemaIgnore @NotNull private long lastUpdatedAt;

  /**
   * TODO: Add isDeleted boolean field to enable soft delete. @swagat
   */

  @JsonIgnore @SchemaIgnore @Transient private transient String entityYamlPath;

  @Transient public static String DEFAULT_GROUP_NAME = "default";

  @NotEmpty private DelegateStateType stateType;

  @NotEmpty private String name;

  private String workflowId;

  private String workflowExecutionId;

  private String serviceId;

  @FdIndex private String cvConfigId;

  private String stateExecutionId;

  @NotEmpty private long timeStamp;

  private int dataCollectionMinute;

  private String host;

  private ClusterLevel level;

  private String tag;

  private String groupName = DEFAULT_GROUP_NAME;

  private Map<String, Double> values = new HashMap<>();

  private Map<String, String> deeplinkMetadata = new HashMap<>();

  private transient Map<String, String> deeplinkUrl;

  @FdIndex private String accountId;

  public Map<String, Double> getValues() {
    if (values == null) {
      return new HashMap<>();
    }
    return values;
  }

  @JsonIgnore
  @SchemaIgnore
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(ML_RECORDS_TTL_MONTHS).toInstant());

  @Builder
  public NewRelicMetricDataRecord(String uuid, String appId, EmbeddedUser createdBy, long createdAt,
      EmbeddedUser lastUpdatedBy, long lastUpdatedAt, String entityYamlPath, DelegateStateType stateType, String name,
      String workflowId, String workflowExecutionId, String serviceId, String cvConfigId, String stateExecutionId,
      long timeStamp, int dataCollectionMinute, String host, ClusterLevel level, String tag, String groupName,
      Map<String, Double> values, Map<String, String> deeplinkMetadata, String accountId) {
    this.uuid = uuid;
    this.appId = appId;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.lastUpdatedBy = lastUpdatedBy;
    this.lastUpdatedAt = lastUpdatedAt;
    this.entityYamlPath = entityYamlPath;
    this.stateType = stateType;
    this.name = name;
    this.workflowId = workflowId;
    this.workflowExecutionId = workflowExecutionId;
    this.serviceId = serviceId;
    this.cvConfigId = cvConfigId;
    this.accountId = accountId;
    this.stateExecutionId = stateExecutionId;
    this.timeStamp = timeStamp;
    this.dataCollectionMinute = dataCollectionMinute;
    this.host = host;
    this.level = level;
    this.tag = tag;
    this.groupName = isEmpty(groupName) ? DEFAULT_GROUP_NAME : groupName;
    this.values = isEmpty(values) ? new HashMap<>() : values;
    this.deeplinkMetadata = isEmpty(deeplinkMetadata) ? new HashMap<>() : deeplinkMetadata;
    this.validUntil = Date.from(OffsetDateTime.now().plusMonths(ML_RECORDS_TTL_MONTHS).toInstant());
  }

  @Override
  public com.google.cloud.datastore.Entity convertToCloudStorageEntity(Datastore datastore) {
    Key taskKey = datastore.newKeyFactory()
                      .setKind(this.getClass().getAnnotation(org.mongodb.morphia.annotations.Entity.class).value())
                      .newKey(generateUniqueKey());
    com.google.cloud.datastore.Entity.Builder recordBuilder = com.google.cloud.datastore.Entity.newBuilder(taskKey);
    addFieldIfNotEmpty(recordBuilder, "stateType", stateType.name(), true);
    addFieldIfNotEmpty(recordBuilder, "appId", appId, false);
    addFieldIfNotEmpty(recordBuilder, "name", name, false);
    addFieldIfNotEmpty(recordBuilder, "workflowId", workflowId, true);
    addFieldIfNotEmpty(recordBuilder, "workflowExecutionId", workflowExecutionId, false);
    addFieldIfNotEmpty(recordBuilder, "serviceId", serviceId, true);
    addFieldIfNotEmpty(recordBuilder, "cvConfigId", cvConfigId, false);
    addFieldIfNotEmpty(recordBuilder, "accountId", accountId, false);
    addFieldIfNotEmpty(recordBuilder, "stateExecutionId", stateExecutionId, false);
    recordBuilder.set("timeStamp", timeStamp);
    recordBuilder.set("dataCollectionMinute", dataCollectionMinute);
    addFieldIfNotEmpty(recordBuilder, "host", host, false);
    addFieldIfNotEmpty(recordBuilder, "level", level == null ? null : level.name(), false);
    addFieldIfNotEmpty(recordBuilder, "tag", tag, false);
    addFieldIfNotEmpty(recordBuilder, "groupName", groupName, false);

    if (isNotEmpty(values)) {
      addFieldIfNotEmpty(recordBuilder, "values", JsonUtils.asJson(values), true);
    }
    if (isNotEmpty(deeplinkMetadata)) {
      addFieldIfNotEmpty(recordBuilder, "deeplinkMetadata", JsonUtils.asJson(deeplinkMetadata), true);
    }

    if (validUntil == null) {
      validUntil = Date.from(OffsetDateTime.now().plusMonths(ML_RECORDS_TTL_MONTHS).toInstant());
    }
    recordBuilder.set("validUntil", validUntil.getTime());

    return recordBuilder.build();
  }

  @Override
  public GoogleDataStoreAware readFromCloudStorageEntity(com.google.cloud.datastore.Entity entity) {
    final NewRelicMetricDataRecord dataRecord =
        NewRelicMetricDataRecord.builder()
            .appId(readString(entity, "appId"))
            .stateType(DelegateStateType.valueOf(readString(entity, "stateType")))
            .name(readString(entity, "name"))
            .workflowId(readString(entity, "workflowId"))
            .workflowExecutionId(readString(entity, "workflowExecutionId"))
            .serviceId(readString(entity, "serviceId"))
            .cvConfigId(readString(entity, "cvConfigId"))
            .accountId(readString(entity, "accountId"))
            .stateExecutionId(readString(entity, "stateExecutionId"))
            .timeStamp(readLong(entity, "timeStamp"))
            .dataCollectionMinute((int) readLong(entity, "dataCollectionMinute"))
            .host(readString(entity, "host"))
            .tag(readString(entity, "tag"))
            .groupName(readString(entity, "groupName"))
            .build();

    final String level = readString(entity, "level");
    if (isNotEmpty(level)) {
      dataRecord.setLevel(ClusterLevel.valueOf(level));
    }

    final String valuesJson = readString(entity, "values");
    if (isNotEmpty(valuesJson)) {
      dataRecord.setValues(JsonUtils.asObject(valuesJson, new TypeReference<Map<String, Double>>() {}));
    }

    final String deepLinkJson = readString(entity, "deeplinkMetadata");
    if (isNotEmpty(deepLinkJson)) {
      dataRecord.setDeeplinkMetadata(JsonUtils.asObject(deepLinkJson, new TypeReference<Map<String, String>>() {}));
    }
    return dataRecord;
  }

  private String generateUniqueKey() {
    StringBuilder keyBuilder = new StringBuilder();
    keyBuilder.append(name);
    appendIfNecessary(keyBuilder, host);
    keyBuilder.append(':').append(timeStamp);
    appendIfNecessary(keyBuilder, workflowExecutionId);
    appendIfNecessary(keyBuilder, stateExecutionId);
    appendIfNecessary(keyBuilder, serviceId);
    appendIfNecessary(keyBuilder, workflowId);
    appendIfNecessary(keyBuilder, stateType.name());
    appendIfNecessary(keyBuilder, groupName);
    return Hashing.sha256().hashString(keyBuilder.toString(), StandardCharsets.UTF_8).toString();
  }

  private void appendIfNecessary(StringBuilder keyBuilder, String value) {
    if (isNotEmpty(value)) {
      keyBuilder.append(':').append(value);
    }
  }

  public void convertErrorsToPercentage(Map<String, List<String>> throughputToErrorsMap) {
    Preconditions.checkNotNull(throughputToErrorsMap);
    DecimalFormat twoDForm = new DecimalFormat("#.00");
    throughputToErrorsMap.forEach((throughput, errors) -> {
      if (values.containsKey(throughput) && values.get(throughput) != 0) {
        errors.forEach(error -> {
          if (values.containsKey(error)) {
            values.put(error, Double.valueOf(twoDForm.format(values.get(error) * 100 / values.get(throughput))));
          }
        });
      }
    });
  }
}
