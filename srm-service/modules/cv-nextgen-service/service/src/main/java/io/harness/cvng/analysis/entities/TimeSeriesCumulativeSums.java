/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.entities;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.cvng.CVConstants;
import io.harness.cvng.analysis.beans.ServiceGuardTxnMetricAnalysisDataDTO.MetricSumDTO;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
@FieldNameConstants(innerTypeName = "TimeSeriesCumulativeSumsKeys")
@EqualsAndHashCode(callSuper = false, exclude = {"transactionMetricSums"})
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.CVNG)
@Entity(value = "timeseriesCumulativeSums", noClassnameStored = true)
@HarnessEntity(exportable = false)
public final class TimeSeriesCumulativeSums implements PersistentEntity, UuidAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("query_idx")
                 .field(TimeSeriesCumulativeSumsKeys.verificationTaskId)
                 .field(TimeSeriesCumulativeSumsKeys.analysisStartTime)
                 .field(TimeSeriesCumulativeSumsKeys.analysisEndTime)
                 .build())
        .build();
  }

  @Id private String uuid;
  @NotEmpty private String verificationTaskId;
  @NotEmpty private Instant analysisStartTime;
  @NotEmpty private Instant analysisEndTime;

  private List<TransactionMetricSums> transactionMetricSums;
  @Builder.Default
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plus(CVConstants.MAX_DATA_RETENTION_DURATION).toInstant());
  @Data
  @Builder
  @FieldNameConstants(innerTypeName = "TransactionMetricSumsKeys")
  public static class TransactionMetricSums {
    private String transactionName;
    private List<MetricSum> metricSums;
  }

  @Data
  @Builder
  @FieldNameConstants(innerTypeName = "MetricSumsKeys")
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MetricSum {
    @Deprecated private String metricName;
    private String metricIdentifier;
    private double risk;
    private double data;

    public String getMetricIdentifier() {
      if (isEmpty(metricIdentifier)) {
        return metricName;
      }
      return metricIdentifier;
    }
  }

  public static List<TransactionMetricSums> convertMapToTransactionMetricSums(
      Map<String, Map<String, MetricSum>> txnMetricMap) {
    List<TransactionMetricSums> txnMetricSumList = new ArrayList<>();
    if (txnMetricMap == null) {
      return null;
    }
    txnMetricMap.forEach((txnName, metricMap) -> {
      if (isNotEmpty(metricMap)) {
        TransactionMetricSums txnMetricSums = TransactionMetricSums.builder().transactionName(txnName).build();
        List<MetricSum> metricSumsList = new ArrayList<>();
        metricMap.forEach((metricIdentifier, metricSums) -> {
          metricSums.setMetricIdentifier(metricIdentifier);
          metricSumsList.add(metricSums);
        });
        txnMetricSums.setMetricSums(metricSumsList);
        txnMetricSumList.add(txnMetricSums);
      }
    });
    return txnMetricSumList;
  }

  public static Map<String, Map<String, List<MetricSumDTO>>> convertToMap(
      List<TimeSeriesCumulativeSums> timeSeriesCumulativeSumsList) {
    if (isEmpty(timeSeriesCumulativeSumsList)) {
      return new HashMap<>();
    }
    timeSeriesCumulativeSumsList.sort(Comparator.comparing(TimeSeriesCumulativeSums::getAnalysisStartTime));
    Map<String, Map<String, List<MetricSumDTO>>> txnMetricMap = new HashMap<>();

    for (TimeSeriesCumulativeSums timeSeriesCumulativeSums : timeSeriesCumulativeSumsList) {
      if (isEmpty(timeSeriesCumulativeSums.getTransactionMetricSums())) {
        continue;
      }
      for (TransactionMetricSums transactionSum : timeSeriesCumulativeSums.getTransactionMetricSums()) {
        String transactionName = transactionSum.getTransactionName();
        if (!txnMetricMap.containsKey(transactionName)) {
          txnMetricMap.put(transactionName, new HashMap<>());
        }
        transactionSum.getMetricSums().forEach(metricSum -> {
          String metricIdentifier = metricSum.getMetricIdentifier();
          if (!txnMetricMap.get(transactionName).containsKey(metricIdentifier)) {
            txnMetricMap.get(transactionName).put(metricIdentifier, new ArrayList<>());
          }
          txnMetricMap.get(transactionName).get(metricIdentifier).add(MetricSumDTO.toMetricSumDTO(metricSum));
        });
      }
    }
    return txnMetricMap;
  }
}
