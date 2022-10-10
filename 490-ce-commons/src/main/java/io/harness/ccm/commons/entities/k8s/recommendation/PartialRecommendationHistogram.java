/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.k8s.recommendation;

import io.harness.annotations.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import software.wings.graphql.datafetcher.ce.recommendation.entity.ContainerCheckpoint;

import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

/*
Stores histogram data for a single day
 */
@Data
@Builder
@FieldNameConstants(innerTypeName = "PartialRecommendationHistogramKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@StoreIn(DbAliases.CENG)
@Entity(value = "partialRecommendationHistogram", noClassnameStored = true)
public final class PartialRecommendationHistogram
    implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_clusterId_namespace_workloadName_workloadType_date")
                 .unique(true)
                 .field(PartialRecommendationHistogramKeys.accountId)
                 .field(PartialRecommendationHistogramKeys.clusterId)
                 .field(PartialRecommendationHistogramKeys.namespace)
                 .field(PartialRecommendationHistogramKeys.workloadName)
                 .field(PartialRecommendationHistogramKeys.workloadType)
                 .field(PartialRecommendationHistogramKeys.date)
                 .build())
        .build();
  }

  @Id String uuid;
  long createdAt;
  long lastUpdatedAt;

  @NotEmpty String accountId;
  @NotEmpty String clusterId;
  @NotEmpty String namespace;
  @NotEmpty String workloadName;
  @NotEmpty String workloadType;
  // Date for which the data corresponds to.
  Instant date;

  @NotEmpty Map<String, ContainerCheckpoint> containerCheckpoints;
}
