/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.entities;

import static io.harness.cvng.core.utils.DateTimeUtils.instantToEpochMinute;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.cvng.analysis.beans.LogClusterDTO;
import io.harness.cvng.analysis.beans.LogClusterLevel;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "ClusteredLogKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.CVNG)
@Entity(value = "clusteredLogs", noClassnameStored = true)
@HarnessEntity(exportable = false)
public final class ClusteredLog extends VerificationTaskBase implements PersistentEntity, UuidAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("query_idx")
                 .field(ClusteredLogKeys.verificationTaskId)
                 .field(ClusteredLogKeys.clusterLevel)
                 .field(ClusteredLogKeys.timestamp)
                 .build())
        .build();
  }

  @Id private String uuid;
  private String verificationTaskId;
  private LogClusterLevel clusterLevel;
  private String log;
  private Instant timestamp;
  private String host;
  private String clusterLabel;
  private int clusterCount;

  @JsonIgnore
  @SchemaIgnore
  @Builder.Default
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(1).toInstant());

  public LogClusterDTO toDTO() {
    return LogClusterDTO.builder()
        .verificationTaskId(verificationTaskId)
        .clusterCount(clusterCount)
        .clusterLabel(clusterLabel)
        .host(host)
        .log(log)
        .epochMinute(instantToEpochMinute(timestamp))
        .build();
  }
}
