/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.k8s.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.recommendation.CCMJiraDetails;
import io.harness.ccm.commons.beans.recommendation.CCMServiceNowDetails;
import io.harness.ccm.commons.beans.recommendation.K8sServiceProvider;
import io.harness.ccm.commons.beans.recommendation.NodePoolId;
import io.harness.ccm.commons.beans.recommendation.NodePoolId.NodePoolIdKeys;
import io.harness.ccm.commons.beans.recommendation.TotalResourceUsage;
import io.harness.ccm.commons.beans.recommendation.models.RecommendClusterRequest;
import io.harness.ccm.commons.beans.recommendation.models.RecommendationResponse;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;
import io.harness.persistence.ValidUntilAccess;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "K8sNodeRecommendationKeys")
@StoreIn(DbAliases.CENG)
@Entity(value = "k8sNodeRecommendation", noClassnameStored = true)
@OwnedBy(CE)
public final class K8sNodeRecommendation
    implements PersistentEntity, UuidAware, AccountAccess, CreatedAtAware, UpdatedAtAware, ValidUntilAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_clusterid_nodepoolname")
                 .unique(true)
                 .field(K8sNodeRecommendationKeys.accountId)
                 .field(K8sNodeRecommendationKeys.nodePoolId + "." + NodePoolIdKeys.clusterid)
                 // It's okay that nodepoolname is nullable, right?
                 .field(K8sNodeRecommendationKeys.nodePoolId + "." + NodePoolIdKeys.nodepoolname)
                 .build())
        .build();
  }

  @Id String uuid;
  String accountId;
  NodePoolId nodePoolId;

  RecommendClusterRequest recommendClusterRequest;

  K8sServiceProvider currentServiceProvider;

  RecommendationResponse recommendation;

  TotalResourceUsage totalResourceUsage;

  CCMJiraDetails jiraDetails;
  CCMServiceNowDetails serviceNowDetails;

  long createdAt;
  long lastUpdatedAt;

  @JsonIgnore
  @EqualsAndHashCode.Exclude
  @Builder.Default
  @FdTtlIndex
  Date validUntil = Date.from(OffsetDateTime.now().plusDays(90).toInstant());

  @Builder.Default int version = 2;
}
