/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.data.encoding.EncodingUtils.compressString;
import static io.harness.data.encoding.EncodingUtils.deCompressString;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotation.HarnessEntity;
import io.harness.exception.WingsException;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.persistence.AccountAccess;
import io.harness.serializer.JsonUtils;

import software.wings.beans.Base;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Date;
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
import org.mongodb.morphia.annotations.Transient;

/**
 * Class representing an entity of cumulative sums and risk for each window of analysis.
 * Created by Praveen.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "TimeSeriesRiskSummaryKeys")
@Entity(value = "timeSeriesRiskSummary", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class TimeSeriesRiskSummary extends Base implements AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("minute_idx")
                 .field(TimeSeriesRiskSummaryKeys.cvConfigId)
                 .field(TimeSeriesRiskSummaryKeys.analysisMinute)
                 .field(TimeSeriesRiskSummaryKeys.tag)
                 .build())
        .build();
  }
  @NotEmpty private String cvConfigId;
  @NotEmpty private int analysisMinute;
  @Transient Map<String, Map<String, Integer>> txnMetricRisk;
  @Transient Map<String, Map<String, Integer>> txnMetricLongTermPattern;
  private transient Map<String, Map<String, TimeSeriesRiskData>> txnMetricRiskData;
  @JsonIgnore private byte[] compressedMetricRisk;
  @JsonIgnore private byte[] compressedLongTermPattern;
  @JsonIgnore private byte[] compressedRiskData;
  @FdIndex private String accountId;

  @FdTtlIndex private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(6).toInstant());

  private String tag;

  public void compressMaps() {
    if (isEmpty(txnMetricRisk) && isEmpty(txnMetricLongTermPattern) && isEmpty(txnMetricRiskData)) {
      return;
    }
    try {
      if (isNotEmpty(txnMetricRisk)) {
        setCompressedMetricRisk(compressString(JsonUtils.asJson(txnMetricRisk)));
      }
      setTxnMetricRisk(null);

      if (isNotEmpty(txnMetricLongTermPattern)) {
        setCompressedLongTermPattern(compressString(JsonUtils.asJson(txnMetricLongTermPattern)));
      }
      setTxnMetricLongTermPattern(null);

      if (isNotEmpty(txnMetricRiskData)) {
        setCompressedRiskData(compressString(JsonUtils.asJson(txnMetricRiskData)));
      }
      setTxnMetricRiskData(null);
    } catch (IOException e) {
      throw new WingsException(e);
    }
  }

  public void decompressMaps() {
    if (isEmpty(compressedMetricRisk) && isEmpty(compressedLongTermPattern) && isEmpty(compressedRiskData)) {
      return;
    }
    try {
      if (isNotEmpty(compressedMetricRisk)) {
        String decompressedMetricRisk = deCompressString(compressedMetricRisk);
        setTxnMetricRisk(
            JsonUtils.asObject(decompressedMetricRisk, new TypeReference<Map<String, Map<String, Integer>>>() {}));
        setCompressedMetricRisk(null);
      }

      if (isNotEmpty(compressedLongTermPattern)) {
        String decompressedLongTermPattern = deCompressString(compressedLongTermPattern);
        setTxnMetricLongTermPattern(
            JsonUtils.asObject(decompressedLongTermPattern, new TypeReference<Map<String, Map<String, Integer>>>() {}));
        setCompressedLongTermPattern(null);
      }

      if (isNotEmpty(compressedRiskData)) {
        String decompressedRiskData = deCompressString(compressedRiskData);
        setTxnMetricRiskData(JsonUtils.asObject(
            decompressedRiskData, new TypeReference<Map<String, Map<String, TimeSeriesRiskData>>>() {}));
        setCompressedRiskData(null);
      }
    } catch (IOException e) {
      throw new WingsException(e);
    }
  }
}
