/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.dao.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.constants.ViewFieldConstants.THRESHOLD_DAYS;
import static io.harness.ccm.commons.utils.TimeUtils.offsetDateTimeNow;
import static io.harness.ccm.commons.utils.TimeUtils.toOffsetDateTime;
import static io.harness.persistence.HPersistence.upsertReturnNewOptions;
import static io.harness.timescaledb.Tables.CE_RECOMMENDATIONS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.retry.RetryOnException;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.commons.entities.ec2.recommendation.EC2Recommendation;
import io.harness.ccm.commons.entities.ec2.recommendation.EC2Recommendation.EC2RecommendationKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@Singleton
@OwnedBy(CE)
public class EC2RecommendationDAO {
  private static final int RETRY_COUNT = 3;
  private static final int SLEEP_DURATION = 100;

  @Inject private HPersistence hPersistence;
  @Inject private DSLContext dslContext;

  @NonNull
  public EC2Recommendation saveRecommendation(EC2Recommendation ec2Recommendation) {
    Query<EC2Recommendation> query = hPersistence.createQuery(EC2Recommendation.class)
                                         .field(EC2RecommendationKeys.accountId)
                                         .equal(ec2Recommendation.getAccountId())
                                         .field(EC2RecommendationKeys.awsAccountId)
                                         .equal(ec2Recommendation.getAwsAccountId())
                                         .field(EC2RecommendationKeys.instanceId)
                                         .equal(ec2Recommendation.getInstanceId());
    UpdateOperations<EC2Recommendation> updateOperations =
        hPersistence.createUpdateOperations(EC2Recommendation.class)
            .set(EC2RecommendationKeys.accountId, ec2Recommendation.getAccountId())
            .set(EC2RecommendationKeys.awsAccountId, ec2Recommendation.getAwsAccountId())
            .set(EC2RecommendationKeys.instanceId, ec2Recommendation.getInstanceId())
            .set(EC2RecommendationKeys.instanceName, ec2Recommendation.getInstanceName())
            .set(EC2RecommendationKeys.instanceType, ec2Recommendation.getInstanceType())
            .set(EC2RecommendationKeys.platform, ec2Recommendation.getPlatform())
            .set(EC2RecommendationKeys.region, ec2Recommendation.getRegion())
            .set(EC2RecommendationKeys.memory, ec2Recommendation.getMemory())
            .set(EC2RecommendationKeys.sku, ec2Recommendation.getSku())
            .set(EC2RecommendationKeys.currentMaxCPU, ec2Recommendation.getCurrentMaxCPU())
            .set(EC2RecommendationKeys.currentMaxMemory, ec2Recommendation.getCurrentMaxMemory())
            .set(EC2RecommendationKeys.currentMonthlyCost, ec2Recommendation.getCurrentMonthlyCost())
            .set(EC2RecommendationKeys.currencyCode, ec2Recommendation.getCurrencyCode())
            .set(EC2RecommendationKeys.recommendationInfo, ec2Recommendation.getRecommendationInfo())
            .set(EC2RecommendationKeys.expectedSaving, ec2Recommendation.getExpectedSaving())
            .set(EC2RecommendationKeys.rightsizingType, ec2Recommendation.getRightsizingType())
            .set(EC2RecommendationKeys.lastUpdatedTime, ec2Recommendation.getLastUpdatedTime());

    return hPersistence.upsert(query, updateOperations, upsertReturnNewOptions);
  }

  @RetryOnException(retryCount = RETRY_COUNT, sleepDurationInMilliseconds = SLEEP_DURATION)
  public void upsertCeRecommendation(@NonNull String uuid, @NonNull String accountId, @NonNull String instanceId,
      @NonNull String awsAccountId, String instanceName, @Nullable Double monthlyCost, @Nullable Double monthlySaving,
      @NonNull Instant lastReceivedUntilAt) {
    dslContext.insertInto(CE_RECOMMENDATIONS)
        .set(CE_RECOMMENDATIONS.ACCOUNTID, accountId)
        .set(CE_RECOMMENDATIONS.ID, uuid)
        .set(CE_RECOMMENDATIONS.CLUSTERNAME, instanceId)
        .set(CE_RECOMMENDATIONS.NAMESPACE, awsAccountId)
        .set(CE_RECOMMENDATIONS.NAME, instanceName)
        .set(CE_RECOMMENDATIONS.RESOURCETYPE, ResourceType.EC2_INSTANCE.name())
        .set(CE_RECOMMENDATIONS.MONTHLYCOST, monthlyCost)
        .set(CE_RECOMMENDATIONS.MONTHLYSAVING, monthlySaving)
        .set(CE_RECOMMENDATIONS.ISVALID, true)
        .set(CE_RECOMMENDATIONS.LASTPROCESSEDAT,
            toOffsetDateTime(lastReceivedUntilAt.minus(THRESHOLD_DAYS - 1, ChronoUnit.DAYS)))
        .set(CE_RECOMMENDATIONS.UPDATEDAT, offsetDateTimeNow())
        .onConflictOnConstraint(CE_RECOMMENDATIONS.getPrimaryKey())
        .doUpdate()
        .set(CE_RECOMMENDATIONS.MONTHLYCOST, monthlyCost)
        .set(CE_RECOMMENDATIONS.MONTHLYSAVING, monthlySaving)
        .set(CE_RECOMMENDATIONS.LASTPROCESSEDAT,
            toOffsetDateTime(lastReceivedUntilAt.minus(THRESHOLD_DAYS - 1, ChronoUnit.DAYS)))
        .set(CE_RECOMMENDATIONS.UPDATEDAT, offsetDateTimeNow())
        .execute();
  }
}
