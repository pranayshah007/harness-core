/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.data.encoding.EncodingUtils.compressString;
import static io.harness.data.encoding.EncodingUtils.deCompressString;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.common.VerificationConstants.ML_RECORDS_TTL_MONTHS;
import static software.wings.service.impl.newrelic.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.persistence.AccountAccess;
import io.harness.serializer.JsonUtils;

import software.wings.beans.Base;
import software.wings.sm.StateType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.reinert.jjschema.SchemaIgnore;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.jetbrains.annotations.NotNull;
import org.mongodb.morphia.annotations.Transient;

/**
 * Common Class extended by TimeSeries and Experimental Analysis Record.
 * Created by Pranjal on 08/16/2018
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = false,
    of = {"stateType", "workflowExecutionId", "stateExecutionId", "analysisMinute", "groupName", "baseLineExecutionId",
        "cvConfigId", "tag"})
@FieldNameConstants(innerTypeName = "MetricAnalysisRecordKeys")
public class MetricAnalysisRecord extends Base implements Comparable<MetricAnalysisRecord>, AccountAccess {
  @NotEmpty private StateType stateType;

  @NotEmpty private String workflowExecutionId;

  @NotEmpty private String stateExecutionId;

  // no. of minutes of analysis
  @NotEmpty private int analysisMinute;

  private String groupName = DEFAULT_GROUP_NAME;

  private String baseLineExecutionId;

  private Map<String, TimeSeriesMLTxnSummary> transactions;

  private Map<String, Double> overallMetricScores;

  private Map<String, Map<String, Double>> keyTransactionMetricScores;

  private byte[] transactionsCompressedJson;

  @Transient private Map<String, Map<String, List<TimeSeriesMLHostSummary>>> anomalies;

  @Transient private Map<String, Map<String, Map<String, Double>>> transactionMetricSums;

  private byte[] metricAnalysisValuesCompressed;

  private String message;

  private String cvConfigId;

  private int aggregatedRisk = -1;

  private String tag;

  private boolean shouldFailFast;

  private String failFastErrorMsg;

  @FdIndex private String accountId;

  private Double riskScore;

  @JsonIgnore
  @SchemaIgnore
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(ML_RECORDS_TTL_MONTHS).toInstant());

  /**
   * This is done so that the issues with dots in the key fields are handled while saving to mongo and also it gives us
   * improvments in storage and latency
   */
  public void bundleAsJosnAndCompress() {
    if (isEmpty(transactions)) {
      return;
    }

    try {
      setMetricAnalysisValuesCompressed(
          compressString(JsonUtils.asJson(MetricAnalysisValues.builder()
                                              .transactions(transactions)
                                              .overallMetricScores(overallMetricScores)
                                              .keyTransactionMetricScores(keyTransactionMetricScores)
                                              .anomalies(anomalies)
                                              .transactionMetricSums(transactionMetricSums)
                                              .build())));
      setTransactions(null);
      setOverallMetricScores(null);
      setKeyTransactionMetricScores(null);
      setAnomalies(null);
      setTransactionMetricSums(null);
      setTransactionsCompressedJson(null);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public void decompress(boolean onlyRiskScore) {
    if (isEmpty(metricAnalysisValuesCompressed) && isEmpty(transactionsCompressedJson)) {
      return;
    }

    if (isNotEmpty(transactionsCompressedJson)) {
      try {
        String decompressedTransactionsJson = deCompressString(transactionsCompressedJson);
        setTransactions(JsonUtils.asObject(
            decompressedTransactionsJson, new TypeReference<Map<String, TimeSeriesMLTxnSummary>>() {}));
        setTransactionsCompressedJson(null);
        return;
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    try {
      String decompressedMetricAnalysisValues = deCompressString(getMetricAnalysisValuesCompressed());
      setMetricAnalysisValuesCompressed(null);
      MetricAnalysisValues metricAnalysisValues =
          JsonUtils.asObject(decompressedMetricAnalysisValues, MetricAnalysisValues.class);
      setOverallMetricScores(metricAnalysisValues.getOverallMetricScores());
      if (onlyRiskScore) {
        return;
      }
      setTransactions(metricAnalysisValues.getTransactions());
      setKeyTransactionMetricScores(metricAnalysisValues.getKeyTransactionMetricScores());
      setAnomalies(metricAnalysisValues.getAnomalies());
      setTransactionMetricSums(metricAnalysisValues.getTransactionMetricSums());
      return;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public int compareTo(@NotNull MetricAnalysisRecord o) {
    return this.analysisMinute - o.analysisMinute;
  }

  @Data
  @Builder
  private static class MetricAnalysisValues {
    private Map<String, TimeSeriesMLTxnSummary> transactions;
    private Map<String, Double> overallMetricScores;
    private Map<String, Map<String, Double>> keyTransactionMetricScores;
    @Transient private Map<String, Map<String, List<TimeSeriesMLHostSummary>>> anomalies;
    @Transient private Map<String, Map<String, Map<String, Double>>> transactionMetricSums;
  }
}
