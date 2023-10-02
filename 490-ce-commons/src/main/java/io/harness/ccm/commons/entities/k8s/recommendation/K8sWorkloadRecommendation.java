/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.k8s.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.ccm.commons.beans.recommendation.CCMJiraDetails;
import io.harness.ccm.commons.beans.recommendation.CCMServiceNowDetails;
import io.harness.data.structure.MongoMapSanitizer;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import software.wings.graphql.datafetcher.ce.recommendation.entity.ContainerCheckpoint;
import software.wings.graphql.datafetcher.ce.recommendation.entity.ContainerRecommendation;
import software.wings.graphql.datafetcher.ce.recommendation.entity.Cost;
import software.wings.graphql.datafetcher.ce.recommendation.entity.ResourceRequirement;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.PostLoad;
import dev.morphia.annotations.PrePersist;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "K8sWorkloadRecommendationKeys")
@StoreIn(DbAliases.CENG)
@Entity(value = "k8sWorkloadRecommendation", noClassnameStored = true)
@OwnedBy(CE)
public final class K8sWorkloadRecommendation
    implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_clusterId_namespace_workloadName_workloadType")
                 .unique(true)
                 .field(K8sWorkloadRecommendationKeys.accountId)
                 .field(K8sWorkloadRecommendationKeys.clusterId)
                 .field(K8sWorkloadRecommendationKeys.namespace)
                 .field(K8sWorkloadRecommendationKeys.workloadName)
                 .field(K8sWorkloadRecommendationKeys.workloadType)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_dirty")
                 .field(K8sWorkloadRecommendationKeys.accountId)
                 .field(K8sWorkloadRecommendationKeys.dirty)
                 .build())
        .build();
  }

  private static final MongoMapSanitizer SANITIZER = new MongoMapSanitizer('~');

  @Id String uuid;
  long createdAt;
  long lastUpdatedAt;

  @NotEmpty String accountId;
  @NotEmpty String clusterId;
  @NotEmpty String namespace;
  @NotEmpty String workloadType;
  @NotEmpty String workloadName;

  @Singular @NotEmpty Map<String, ContainerRecommendation> containerRecommendations;
  @Singular @NotEmpty Map<String, ContainerCheckpoint> containerCheckpoints;

  Cost lastDayCost;
  @FdIndex BigDecimal estimatedSavings;

  @EqualsAndHashCode.Exclude @FdTtlIndex Instant ttl;

  // Timestamp at which we last sampled util data for this workload
  // max(lastSampleStart) across containerCheckpoints
  Instant lastReceivedUtilDataAt;

  // Timestamp at which we last computed recommendations for this workload
  Instant lastComputedRecommendationAt;

  // For intermediate stages in batch-processing
  boolean dirty;

  // Set to true if we have non-empty recommendations
  boolean validRecommendation;

  // To avoid showing recommendation if cost computation cannot be done due to lastDay's cost not being available
  boolean lastDayCostAvailable;

  // number of days of data (min across containers)
  int numDays;

  HarnessServiceInfo harnessServiceInfo;

  CCMJiraDetails jiraDetails;
  CCMServiceNowDetails serviceNowDetails;

  // decision whether to show the recommendation in the Recommendation Overview List page or not.
  public boolean shouldShowRecommendation() {
    return validRecommendation && lastDayCostAvailable && numDays >= 1;
  }

  @PostLoad
  public void postLoad() {
    if (containerRecommendations != null) {
      for (ContainerRecommendation cr : containerRecommendations.values()) {
        if (cr.getCurrent() != null) {
          cr.setCurrent(ResourceRequirement.builder()
                            .requests(SANITIZER.decodeDotsInKey(cr.getCurrent().getRequests()))
                            .limits(SANITIZER.decodeDotsInKey(cr.getCurrent().getLimits()))
                            .build());
        }
        if (cr.getBurstable() != null) {
          cr.setBurstable(ResourceRequirement.builder()
                              .requests(SANITIZER.decodeDotsInKey(cr.getBurstable().getRequests()))
                              .limits(SANITIZER.decodeDotsInKey(cr.getBurstable().getLimits()))
                              .build());
        }
        if (cr.getGuaranteed() != null) {
          cr.setGuaranteed(ResourceRequirement.builder()
                               .requests(SANITIZER.decodeDotsInKey(cr.getGuaranteed().getRequests()))
                               .limits(SANITIZER.decodeDotsInKey(cr.getGuaranteed().getLimits()))
                               .build());
        }
        if (cr.getRecommended() != null) {
          cr.setRecommended(ResourceRequirement.builder()
                                .requests(SANITIZER.decodeDotsInKey(cr.getRecommended().getRequests()))
                                .limits(SANITIZER.decodeDotsInKey(cr.getRecommended().getLimits()))
                                .build());
        }

        if (cr.getPercentileBased() != null) {
          // for p80, p90, p95, etc.
          for (String percentile : cr.getPercentileBased().keySet()) {
            cr.getPercentileBased().compute(percentile,
                (k, v)
                    -> ResourceRequirement.builder()
                           .requests(SANITIZER.decodeDotsInKey(v.getRequests()))
                           .limits(SANITIZER.decodeDotsInKey(v.getLimits()))
                           .build());
          }
        }
      }
    }
  }

  @PrePersist
  public void prePersist() {
    // set validRecommendation to false in case empty recommendation
    validRecommendation = false;
    boolean noDiffInAllContainers = true;
    if (containerRecommendations != null) {
      validRecommendation = true;
      for (ContainerRecommendation cr : containerRecommendations.values()) {
        if (!isEmpty(cr.getCurrent())) {
          cr.setCurrent(ResourceRequirement.builder()
                            .requests(SANITIZER.encodeDotsInKey(cr.getCurrent().getRequests()))
                            .limits(SANITIZER.encodeDotsInKey(cr.getCurrent().getLimits()))
                            .build());
        }
        if (isEmpty(cr.getBurstable())) {
          validRecommendation = false;
        } else {
          cr.setBurstable(ResourceRequirement.builder()
                              .requests(SANITIZER.encodeDotsInKey(cr.getBurstable().getRequests()))
                              .limits(SANITIZER.encodeDotsInKey(cr.getBurstable().getLimits()))
                              .build());
          if (!Objects.equals(cr.getCurrent(), cr.getBurstable())) {
            noDiffInAllContainers = false;
          }
        }
        if (isEmpty(cr.getGuaranteed())) {
          validRecommendation = false;
        } else {
          cr.setGuaranteed(ResourceRequirement.builder()
                               .requests(SANITIZER.encodeDotsInKey(cr.getGuaranteed().getRequests()))
                               .limits(SANITIZER.encodeDotsInKey(cr.getGuaranteed().getLimits()))
                               .build());
          if (!Objects.equals(cr.getCurrent(), cr.getGuaranteed())) {
            noDiffInAllContainers = false;
          }
        }
        if (isEmpty(cr.getRecommended())) {
          validRecommendation = false;
        } else {
          cr.setRecommended(ResourceRequirement.builder()
                                .requests(SANITIZER.encodeDotsInKey(cr.getRecommended().getRequests()))
                                .limits(SANITIZER.encodeDotsInKey(cr.getRecommended().getLimits()))
                                .build());
          if (!Objects.equals(cr.getCurrent(), cr.getRecommended())) {
            noDiffInAllContainers = false;
          }
        }

        // for p80, p90, p95, etc.
        if (cr.getPercentileBased() != null) {
          for (Map.Entry<String, ResourceRequirement> pair : cr.getPercentileBased().entrySet()) {
            if (isEmpty(pair.getValue())) {
              validRecommendation = false;
            } else {
              ResourceRequirement requirement = ResourceRequirement.builder()
                                                    .requests(SANITIZER.encodeDotsInKey(pair.getValue().getRequests()))
                                                    .limits(SANITIZER.encodeDotsInKey(pair.getValue().getLimits()))
                                                    .build();
              cr.getPercentileBased().put(pair.getKey(), requirement);

              if (!Objects.equals(cr.getCurrent(), requirement)) {
                noDiffInAllContainers = false;
              }
            }
          }
        }
      }
    }
    if (noDiffInAllContainers) {
      validRecommendation = false;
    }
  }
}
