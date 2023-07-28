/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.ecs.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.ccm.commons.beans.recommendation.CCMJiraDetails;
import io.harness.ccm.commons.beans.recommendation.CCMServiceNowDetails;
import io.harness.data.structure.MongoMapSanitizer;
import io.harness.histogram.HistogramCheckpoint;
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

import software.wings.graphql.datafetcher.ce.recommendation.entity.Cost;

import com.amazonaws.services.ecs.model.LaunchType;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "ECSServiceRecommendationKeys")
@StoreIn(DbAliases.CENG)
@Entity(value = "ecsServiceRecommendation", noClassnameStored = true)
@OwnedBy(CE)
public final class ECSServiceRecommendation
    implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_clusterId_serviceArn")
                 .unique(true)
                 .field(ECSServiceRecommendationKeys.accountId)
                 .field(ECSServiceRecommendationKeys.clusterId)
                 .field(ECSServiceRecommendationKeys.serviceArn)
                 .build())
        .build();
  }

  private static final MongoMapSanitizer SANITIZER = new MongoMapSanitizer('~');

  @Id String uuid;
  long createdAt;
  long lastUpdatedAt;

  @NotEmpty String accountId;
  @NotEmpty String clusterId;
  @NotEmpty String clusterName;
  @NotEmpty String serviceArn; // instanceId from utilData
  @NotEmpty String serviceName; // last part of serviceArn
  String awsAccountId;
  LaunchType launchType;

  // Recommendation
  Map<String, String> currentResourceRequirements;
  //  @Deprecated ECSResourceRequirement burstable;
  //  @Deprecated ECSResourceRequirement guaranteed;
  //  @Deprecated ECSResourceRequirement recommended;
  Map<String, Map<String, String>> percentileBasedResourceRecommendation;
  Cost lastDayCost;
  int totalSamplesCount;

  // Checkpoint
  Instant lastUpdateTime;
  HistogramCheckpoint cpuHistogram;
  HistogramCheckpoint memoryHistogram;
  Instant firstSampleStart;
  Instant lastSampleStart;
  long memoryPeak;
  Instant windowEnd;
  int version;

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
}
