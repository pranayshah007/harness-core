/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.entities;

import static io.harness.data.encoding.EncodingUtils.compressString;
import static io.harness.data.encoding.EncodingUtils.deCompressString;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.cvng.CVConstants;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "TimeSeriesShortTermHistoryKeys")
@EqualsAndHashCode(callSuper = false, exclude = {"transactionMetricHistories"})
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.CVNG)
@Entity(value = "timeseriesShortTermHistory", noClassnameStored = true)
@HarnessEntity(exportable = false)
public final class TimeSeriesShortTermHistory implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware {
  @Id private String uuid;
  @FdIndex private long createdAt;
  @FdIndex private long lastUpdatedAt;
  @NotEmpty @FdIndex private String verificationTaskId;
  List<TransactionMetricHistory> transactionMetricHistories;
  private byte[] compressedMetricHistories;
  @Builder.Default
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plus(CVConstants.MAX_DATA_RETENTION_DURATION).toInstant());
  public byte[] getCompressedMetricHistories() {
    if (isEmpty(compressedMetricHistories)) {
      return new byte[0];
    }

    return compressedMetricHistories;
  }

  public void compressMetricHistories() {
    if (isNotEmpty(transactionMetricHistories)) {
      try {
        setCompressedMetricHistories(compressString(JsonUtils.asJson(transactionMetricHistories)));
        setTransactionMetricHistories(null);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  public void deCompressMetricHistories() {
    if (isNotEmpty(compressedMetricHistories)) {
      try {
        String decompressedMetricHistories = deCompressString(compressedMetricHistories);
        setTransactionMetricHistories(
            JsonUtils.asObject(decompressedMetricHistories, new TypeReference<List<TransactionMetricHistory>>() {}));
        setCompressedMetricHistories(null);
      } catch (Exception ex) {
        throw new IllegalStateException(ex);
      }
    }
  }

  @Data
  @Builder
  @FieldNameConstants(innerTypeName = "TransactionMetricHistoryKeys")
  public static class TransactionMetricHistory {
    private String transactionName;
    private List<MetricHistory> metricHistoryList;
  }

  @Data
  @Builder
  @FieldNameConstants(innerTypeName = "MetricHistoryKeys")
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MetricHistory {
    @Deprecated private String metricName;
    private String metricIdentifier;
    private List<Double> value;

    public String getMetricIdentifier() {
      if (isEmpty(metricIdentifier)) {
        String identifier = metricName.replaceAll(" ", "_").toLowerCase();
        identifier = identifier.replaceAll("\\(", "");
        identifier = identifier.replaceAll("\\)", "");
        return identifier;
      }
      return metricIdentifier;
    }
  }

  public static List<TransactionMetricHistory> convertFromMap(Map<String, Map<String, List<Double>>> txnMetricHistory) {
    if (txnMetricHistory == null) {
      return null;
    }
    List<TransactionMetricHistory> transactionMetricHistories = new ArrayList<>();
    txnMetricHistory.forEach((txn, metricHistory) -> {
      TransactionMetricHistory transactionMetricHistory =
          TransactionMetricHistory.builder().transactionName(txn).metricHistoryList(new ArrayList<>()).build();
      metricHistory.forEach((metricIdentifier, values) -> {
        MetricHistory shortTermHistory =
            MetricHistory.builder().metricIdentifier(metricIdentifier).value(values).build();
        transactionMetricHistory.getMetricHistoryList().add(shortTermHistory);
      });
      transactionMetricHistories.add(transactionMetricHistory);
    });
    return transactionMetricHistories;
  }

  public Map<String, Map<String, List<Double>>> convertToMap() {
    if (transactionMetricHistories == null) {
      return new HashMap<>();
    }
    Map<String, Map<String, List<Double>>> txnMetricHistoryMap = new HashMap<>();
    transactionMetricHistories.forEach(transactionMetricHistory -> {
      String txn = transactionMetricHistory.getTransactionName();
      if (!txnMetricHistoryMap.containsKey(txn)) {
        txnMetricHistoryMap.put(txn, new HashMap<>());
      }

      if (isNotEmpty(transactionMetricHistory.getMetricHistoryList())) {
        transactionMetricHistory.getMetricHistoryList().forEach(metricHistory -> {
          String metricIdentifier = metricHistory.getMetricIdentifier();
          if (!txnMetricHistoryMap.get(txn).containsKey(metricIdentifier)) {
            txnMetricHistoryMap.get(txn).put(metricIdentifier, new ArrayList<>());
          }
          if (metricHistory.getValue() != null) {
            txnMetricHistoryMap.get(txn).get(metricIdentifier).addAll(metricHistory.getValue());
          }
        });
      }
    });
    return txnMetricHistoryMap;
  }
}
