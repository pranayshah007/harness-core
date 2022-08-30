/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;

import static io.harness.data.encoding.EncodingUtils.compressString;
import static io.harness.data.encoding.EncodingUtils.deCompressString;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.exception.WingsException;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.serializer.JsonUtils;

import software.wings.beans.Base;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
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
@FieldNameConstants(innerTypeName = "TimeSeriesCumulativeSumsKeys")
@EqualsAndHashCode(callSuper = false, exclude = {"transactionMetricSums", "compressedMetricSums"})
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "timeSeriesCumulativeSums", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class TimeSeriesCumulativeSums extends Base implements AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("uniqueIdx")
                 .unique(true)
                 .field(BaseKeys.appId)
                 .field(TimeSeriesCumulativeSumsKeys.cvConfigId)
                 .field(TimeSeriesCumulativeSumsKeys.analysisMinute)
                 .field(TimeSeriesCumulativeSumsKeys.tag)
                 .build(),
            SortCompoundMongoIndex.builder()
                .name("service_gd_idx")
                .field(TimeSeriesCumulativeSumsKeys.cvConfigId)
                .field(TimeSeriesCumulativeSumsKeys.analysisMinute)
                .descSortField(TimeSeriesCumulativeSumsKeys.analysisMinute)
                .field(TimeSeriesCumulativeSumsKeys.tag)
                .build())
        .build();
  }

  @NotEmpty @FdIndex private String cvConfigId;
  @NotEmpty @FdIndex private int analysisMinute;
  @Transient private Map<String, Map<String, Map<String, Double>>> transactionMetricSums;
  @JsonIgnore private byte[] compressedMetricSums;
  @FdIndex private String accountId;

  private String tag;

  public void compressMetricSums() {
    if (isEmpty(transactionMetricSums)) {
      return;
    }
    try {
      setCompressedMetricSums(compressString(JsonUtils.asJson(transactionMetricSums)));
      setTransactionMetricSums(null);
    } catch (IOException e) {
      throw new WingsException(e);
    }
  }

  public void decompressMetricSums() {
    if (isEmpty(compressedMetricSums)) {
      return;
    }
    try {
      String decompressedTransactionsJson = deCompressString(compressedMetricSums);
      setTransactionMetricSums(JsonUtils.asObject(
          decompressedTransactionsJson, new TypeReference<Map<String, Map<String, Map<String, Double>>>>() {}));
      setCompressedMetricSums(null);
    } catch (IOException e) {
      throw new WingsException(e);
    }
  }
}
