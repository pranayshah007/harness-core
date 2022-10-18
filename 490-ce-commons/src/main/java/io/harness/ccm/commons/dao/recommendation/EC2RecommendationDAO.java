/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.dao.recommendation;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.ec2.recommendation.EC2Recommendation;
import io.harness.persistence.HPersistence;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.jooq.DSLContext;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

import java.util.Optional;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.persistence.HPersistence.upsertReturnNewOptions;
import static io.harness.persistence.HQuery.excludeValidate;


@Slf4j
@Singleton
@OwnedBy(CE)
public class EC2RecommendationDAO {
    private static final int RETRY_COUNT = 3;
    private static final int SLEEP_DURATION = 100;

    @Inject
    private HPersistence hPersistence;
    @Inject private DSLContext dslContext;


    @NonNull
    public Optional<EC2Recommendation> fetchECSRecommendationById(
            @NonNull String accountIdentifier, @NonNull String id) {
        return Optional.ofNullable(hPersistence.createQuery(EC2Recommendation.class, excludeValidate)
                .filter(EC2Recommendation.EC2RecommendationKeys.accountId, accountIdentifier)
                .filter(EC2Recommendation.EC2RecommendationKeys.uuid, new ObjectId(id))
                .get());
    }

    @NonNull
    public EC2Recommendation saveRecommendation(EC2Recommendation ec2Recommendation) {
        Query<EC2Recommendation> query = hPersistence.createQuery(EC2Recommendation.class)
                .field(EC2Recommendation.EC2RecommendationKeys.accountId)
                .equal(ec2Recommendation.getAccountId())
                .field(EC2Recommendation.EC2RecommendationKeys.recommendationType)
                .equal(ec2Recommendation.getRecommendationType())
                .field(EC2Recommendation.EC2RecommendationKeys.awsAccountId)
                .equal(ec2Recommendation.getAwsAccountId())
                .field(EC2Recommendation.EC2RecommendationKeys.instanceId)
                .equal(ec2Recommendation.getInstanceId());
        UpdateOperations<EC2Recommendation> updateOperations =
                hPersistence.createUpdateOperations(EC2Recommendation.class)
                        .set(EC2Recommendation.EC2RecommendationKeys.accountId, ec2Recommendation.getAccountId())
                        .set(EC2Recommendation.EC2RecommendationKeys.recommendationType, ec2Recommendation.getRecommendationType())
                        .set(EC2Recommendation.EC2RecommendationKeys.awsAccountId, ec2Recommendation.getAwsAccountId())
                        .set(EC2Recommendation.EC2RecommendationKeys.instanceId, ec2Recommendation.getInstanceId())
                        .set(EC2Recommendation.EC2RecommendationKeys.instanceName, ec2Recommendation.getInstanceName())
                        .set(EC2Recommendation.EC2RecommendationKeys.instanceType, ec2Recommendation.getInstanceType())
                        .set(EC2Recommendation.EC2RecommendationKeys.platform, ec2Recommendation.getPlatform())
                        .set(EC2Recommendation.EC2RecommendationKeys.region, ec2Recommendation.getRegion())
                        .set(EC2Recommendation.EC2RecommendationKeys.memory, ec2Recommendation.getMemory())
                        .set(EC2Recommendation.EC2RecommendationKeys.sku, ec2Recommendation.getSku())
                        .set(EC2Recommendation.EC2RecommendationKeys.currentMaxCPU, ec2Recommendation.getCurrentMaxCPU())
                        .set(EC2Recommendation.EC2RecommendationKeys.currentMaxMemory, ec2Recommendation.getCurrentMaxMemory())
                        .set(EC2Recommendation.EC2RecommendationKeys.expectedMaxCPU, ec2Recommendation.getExpectedMaxCPU())
                        .set(EC2Recommendation.EC2RecommendationKeys.expectedMaxMemory, ec2Recommendation.getExpectedMaxMemory())
                        .set(EC2Recommendation.EC2RecommendationKeys.currentMonthlyCost, ec2Recommendation.getCurrentMonthlyCost())
                        .set(EC2Recommendation.EC2RecommendationKeys.currencyCode, ec2Recommendation.getCurrencyCode())
                        .set(EC2Recommendation.EC2RecommendationKeys.recommendationInfo, ec2Recommendation.getRecommendationInfo())
                        .set(EC2Recommendation.EC2RecommendationKeys.expectedMonthlyCost, ec2Recommendation.getExpectedMonthlyCost())
                        .set(EC2Recommendation.EC2RecommendationKeys.expectedMonthlySaving, ec2Recommendation.getExpectedMonthlySaving())
                        .set(EC2Recommendation.EC2RecommendationKeys.rightsizingType, ec2Recommendation.getRightsizingType())
                        .set(EC2Recommendation.EC2RecommendationKeys.lastUpdatedAt, ec2Recommendation.getLastUpdatedAt());

        return hPersistence.upsert(query, updateOperations, upsertReturnNewOptions);
    }
}
