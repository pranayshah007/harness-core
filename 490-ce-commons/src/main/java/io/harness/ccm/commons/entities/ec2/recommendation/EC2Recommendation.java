package io.harness.ccm.commons.entities.ec2.recommendation;

import com.amazonaws.services.ecs.model.LaunchType;
import com.google.common.collect.ImmutableList;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.data.structure.MongoMapSanitizer;
import io.harness.histogram.HistogramCheckpoint;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import software.wings.graphql.datafetcher.ce.recommendation.entity.Cost;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "EC2RecommendationKeys")
@StoreIn(DbAliases.CENG)
@Entity(value = "ec2Recommendation", noClassnameStored = true)
@OwnedBy(CE)
public final class EC2Recommendation
        implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
    public static List<MongoIndex> mongoIndexes() {
        return ImmutableList.<MongoIndex>builder()
                .add(CompoundMongoIndex.builder()
                        .name("unique_accountId_instanceId_serviceArn")
                        .unique(true)
                        .field(EC2RecommendationKeys.accountId)
                        .field(EC2RecommendationKeys.accountId)
                        .field(EC2RecommendationKeys.accountId)
                        .build())
                .build();
    }

    private static final MongoMapSanitizer SANITIZER = new MongoMapSanitizer('~');

    @Id
    String uuid;
    long createdAt;
    long lastUpdatedAt;

    @NotEmpty String accountId;
    @NotEmpty String clusterId;
    @NotEmpty String clusterName;
    @NotEmpty String serviceArn; // instanceId from utilData
    @NotEmpty String serviceName; // last part of serviceArn
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

    @FdIndex
    BigDecimal estimatedSavings;

    @EqualsAndHashCode.Exclude @FdTtlIndex
    Instant ttl;

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

    // decision whether to show the recommendation in the Recommendation Overview List page or not.
    public boolean shouldShowRecommendation() {
        return validRecommendation && lastDayCostAvailable && numDays >= 1;
    }
}
