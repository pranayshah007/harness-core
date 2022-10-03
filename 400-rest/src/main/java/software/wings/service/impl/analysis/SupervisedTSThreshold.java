/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.persistence.GoogleDataStoreAware.addFieldIfNotEmpty;
import static io.harness.persistence.GoogleDataStoreAware.readDouble;
import static io.harness.persistence.GoogleDataStoreAware.readString;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.GoogleDataStoreAware;

import software.wings.metrics.MetricType;
import software.wings.metrics.Threshold;
import software.wings.metrics.ThresholdComparisonType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Key;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "SupervisedTSThresholdKeys")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@StoreIn(DbAliases.HARNESS)
@Entity(value = "supervisedTSThreshold", noClassnameStored = true)
@HarnessEntity(exportable = false)
@Slf4j
public class SupervisedTSThreshold implements GoogleDataStoreAware, CreatedAtAware, AccountAccess {
  public static final String connector = ":";

  @Id private String uuid;
  private String accountId;
  private String serviceId;
  private String transactionName;
  private String metricName;
  private MetricType metricType;
  private Double mean;
  private Double standardDeviation;
  private Double minThreshold;
  private Double maxThreshold;
  private Version version;
  private long createdAt;

  public static String getKey(
      String accountId, String serviceId, String transactionName, String metricName, Version version) {
    return String.join(connector, accountId, serviceId, transactionName, metricName, version.name());
  }

  @Override
  public com.google.cloud.datastore.Entity convertToCloudStorageEntity(Datastore datastore) {
    Key taskKey =
        datastore.newKeyFactory()
            .setKind(this.getClass().getAnnotation(org.mongodb.morphia.annotations.Entity.class).value())
            .newKey(this.getUuid() != null ? this.getUuid()
                                           : getKey(accountId, serviceId, transactionName, metricName, version));

    com.google.cloud.datastore.Entity.Builder dataStoreRecordBuilder =
        com.google.cloud.datastore.Entity.newBuilder(taskKey);
    addFieldIfNotEmpty(dataStoreRecordBuilder, SupervisedTSThresholdKeys.accountId, accountId, false);
    addFieldIfNotEmpty(dataStoreRecordBuilder, SupervisedTSThresholdKeys.serviceId, serviceId, false);
    addFieldIfNotEmpty(dataStoreRecordBuilder, SupervisedTSThresholdKeys.transactionName, transactionName, false);
    addFieldIfNotEmpty(dataStoreRecordBuilder, SupervisedTSThresholdKeys.metricName, metricName, false);
    addFieldIfNotEmpty(dataStoreRecordBuilder, SupervisedTSThresholdKeys.mean, mean, true);
    addFieldIfNotEmpty(dataStoreRecordBuilder, SupervisedTSThresholdKeys.standardDeviation, standardDeviation, true);
    addFieldIfNotEmpty(dataStoreRecordBuilder, SupervisedTSThresholdKeys.minThreshold, minThreshold, true);
    addFieldIfNotEmpty(dataStoreRecordBuilder, SupervisedTSThresholdKeys.maxThreshold, maxThreshold, true);
    addFieldIfNotEmpty(dataStoreRecordBuilder, SupervisedTSThresholdKeys.version, version.name(), false);
    return dataStoreRecordBuilder.build();
  }

  @Override
  public GoogleDataStoreAware readFromCloudStorageEntity(com.google.cloud.datastore.Entity entity) {
    final SupervisedTSThreshold tsThreshold =
        SupervisedTSThreshold.builder()
            .accountId(readString(entity, SupervisedTSThresholdKeys.accountId))
            .serviceId(readString(entity, SupervisedTSThresholdKeys.serviceId))
            .transactionName(readString(entity, SupervisedTSThresholdKeys.transactionName))
            .metricName(readString(entity, SupervisedTSThresholdKeys.metricName))
            .mean(readDouble(entity, SupervisedTSThresholdKeys.mean))
            .standardDeviation(readDouble(entity, SupervisedTSThresholdKeys.mean))
            .minThreshold(readDouble(entity, SupervisedTSThresholdKeys.minThreshold))
            .maxThreshold(readDouble(entity, SupervisedTSThresholdKeys.maxThreshold))
            .version(Version.valueOf(readString(entity, SupervisedTSThresholdKeys.version)))
            .build();

    tsThreshold.setUuid(entity.getKey().getName());

    return tsThreshold;
  }

  public static List<Threshold> getThresholds(SupervisedTSThreshold supervisedThreshold) {
    Optional<Threshold> defaultThreshold = supervisedThreshold.getMetricType()
                                               .getThresholds()
                                               .stream()
                                               .filter(t -> t.getComparisonType() == ThresholdComparisonType.DELTA)
                                               .findAny();

    Optional<Double> thresholdValue = Optional.empty();

    if (defaultThreshold.isPresent()) {
      switch (defaultThreshold.get().getThresholdType()) {
        case ALERT_HIGHER_OR_LOWER:
          thresholdValue = Optional.of(Math.min(
              Math.abs(supervisedThreshold.getMinThreshold()), Math.abs(supervisedThreshold.getMaxThreshold())));
          break;
        case ALERT_WHEN_HIGHER:
          thresholdValue = Optional.of(Math.abs(supervisedThreshold.getMinThreshold()));
          break;
        case ALERT_WHEN_LOWER:
          thresholdValue = Optional.of(Math.abs(supervisedThreshold.getMaxThreshold()));
          break;
        default:
          log.info(
              "Comparision type not handled for supervised thresholds {}", defaultThreshold.get().getThresholdType());
          break;
      }

      if (thresholdValue.isPresent() && defaultThreshold.get().getMl() > thresholdValue.get()) {
        Threshold threshold = Threshold.builder()
                                  .thresholdType(defaultThreshold.get().getThresholdType())
                                  .comparisonType(ThresholdComparisonType.DELTA)
                                  .ml(thresholdValue.get())
                                  .build();
        return Collections.singletonList(threshold);
      }
    }
    return new ArrayList<>();
  }
}
