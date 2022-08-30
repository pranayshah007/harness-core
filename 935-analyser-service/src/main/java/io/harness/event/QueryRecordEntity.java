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
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;

import io.harness.ng.DbAliases;
import lombok.Builder;
import lombok.Builder.Default;
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
@FieldNameConstants(innerTypeName = "QueryRecordEntityKeys")
@StoreIn(DbAliases.ANALYSER)
@Entity(value = "queryRecords", noClassnameStored = true)
@Document("queryRecords")
@TypeAlias("queryRecords")
@HarnessEntity(exportable = true)
public class QueryRecordEntity {
  public static final long TTL_HOURS = 1;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("createdAt_idx")
                 .field("createdAt")
                 .descSortField(QueryRecordEntityKeys.createdAt)
                 .build())
        .build();
  }

  @Wither @Id @org.mongodb.morphia.annotations.Id String id;
  @NonNull String hash;
  @NonNull String fullVersion;
  @NonNull String majorVersion;
  @NonNull String serviceName;

  QueryExplainResult explainResult;
  ParsedQuery parsedQuery;
  String collectionName;
  byte[] data;

  @Wither @Default @FdTtlIndex Date validUntil = Date.from(OffsetDateTime.now().plusHours(TTL_HOURS).toInstant());
  @Wither @CreatedDate Long createdAt;
}
