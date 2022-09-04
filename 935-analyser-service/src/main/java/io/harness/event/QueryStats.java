/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.alerts.AlertMetadata;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PIPELINE)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "QueryStatsKeys")
@StoreIn(DbAliases.ANALYSER)
@Entity(value = "queryStats", noClassnameStored = true)
@Document("queryStats")
@TypeAlias("queryStats")
@HarnessEntity(exportable = true)
public class QueryStats {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("hash_serviceName_version_idx")
                 .unique(true)
                 .field(QueryStatsKeys.hash)
                 .field(QueryStatsKeys.serviceId)
                 .field(QueryStatsKeys.version)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("alertList_category_idx")
                 .field(QueryStatsKeys.alerts + ".alertCategory")
                 .build())
        .build();
  }

  @Wither @Id @org.mongodb.morphia.annotations.Id String id;
  @NonNull @Getter String hash;
  @NonNull String version;
  @NonNull String serviceId;

  QueryExplainResult explainResult;
  Boolean indexUsed;
  List<AlertMetadata> alerts;
  ParsedQuery parsedQuery;
  String collectionName;

  @Getter Long count;
  @Wither @CreatedDate Long createdAt;

  // Moving Average executionTime
  long executionTimeMillis;
}
