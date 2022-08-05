/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.cvng.CVConstants;
import io.harness.cvng.analysis.beans.DeploymentTimeSeriesAnalysisDTO;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "DeploymentTimeSeriesAnalysisKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity(value = "deploymentTimeSeriesAnalyses", noClassnameStored = true)
@HarnessEntity(exportable = false)
@StoreIn(DbAliases.CVNG)
public final class DeploymentTimeSeriesAnalysis
    implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("query_idx")
                 .field(DeploymentTimeSeriesAnalysisKeys.verificationTaskId)
                 .field(DeploymentTimeSeriesAnalysisKeys.startTime)
                 .build())
        .build();
  }

  @Id private String uuid;
  private long createdAt;
  private long lastUpdatedAt;
  private String accountId;
  private String verificationTaskId;
  private Instant startTime;
  private Instant endTime;
  private Risk risk;
  private Double score;
  private boolean isFailFast;
  private List<DeploymentTimeSeriesAnalysisDTO.HostInfo> hostSummaries;
  private List<DeploymentTimeSeriesAnalysisDTO.TransactionMetricHostData> transactionMetricSummaries;

  public List<DeploymentTimeSeriesAnalysisDTO.TransactionMetricHostData> getTransactionMetricSummaries() {
    if (this.transactionMetricSummaries == null) {
      return Collections.emptyList();
    }
    return transactionMetricSummaries;
  }

  public List<DeploymentTimeSeriesAnalysisDTO.HostInfo> getHostSummaries() {
    if (hostSummaries == null) {
      return Collections.emptyList();
    }
    return hostSummaries;
  }
  @JsonIgnore
  @SchemaIgnore
  @Builder.Default
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plus(CVConstants.MAX_DATA_RETENTION_DURATION).toInstant());
}
