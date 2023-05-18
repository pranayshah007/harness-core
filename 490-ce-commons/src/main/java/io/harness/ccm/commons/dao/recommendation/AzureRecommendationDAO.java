/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.dao.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.constants.ViewFieldConstants.THRESHOLD_DAYS_TO_SHOW_RECOMMENDATION;
import static io.harness.ccm.commons.utils.TimeUtils.offsetDateTimeNow;
import static io.harness.ccm.commons.utils.TimeUtils.toOffsetDateTime;
import static io.harness.persistence.HPersistence.upsertReturnNewOptions;
import static io.harness.persistence.HQuery.excludeValidate;
import static io.harness.timescaledb.Tables.CE_RECOMMENDATIONS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.retry.RetryOnException;
import io.harness.ccm.commons.beans.recommendation.CCMJiraDetails;
import io.harness.ccm.commons.beans.recommendation.RecommendationState;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.commons.entities.azure.AzureRecommendation;
import io.harness.ccm.commons.entities.azure.AzureRecommendation.AzureRecommendationKeys;
import io.harness.ccm.commons.entities.recommendations.RecommendationAzureVmId;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.jooq.Condition;
import org.jooq.DSLContext;

@Slf4j
@Singleton
@OwnedBy(CE)
public class AzureRecommendationDAO {
  private static final int RETRY_COUNT = 3;
  private static final int SLEEP_DURATION = 100;

  @Inject private HPersistence hPersistence;
  @Inject private DSLContext dslContext;

  /**
   * This method fetches the recommendation from mongo table AzureRecommendation.
   * @param accountIdentifier
   * @param id
   * @return
   */
  @NonNull
  public AzureRecommendation fetchAzureRecommendationById(@NonNull String accountIdentifier, @NonNull String id) {
    Query<AzureRecommendation> query = hPersistence.createQuery(AzureRecommendation.class, excludeValidate)
                                           .field(AzureRecommendationKeys.accountId)
                                           .equal(accountIdentifier)
                                           .field(AzureRecommendationKeys.uuid)
                                           .equal(new ObjectId(id));
    return query.get();
  }

  /**
   * This method saves the recommendation to the mongo table AzureRecommendation.
   * @param azureRecommendation
   * @return
   */
  @NonNull
  public AzureRecommendation saveRecommendation(AzureRecommendation azureRecommendation) {
    Query<AzureRecommendation> query = hPersistence.createQuery(AzureRecommendation.class)
                                           .field(AzureRecommendationKeys.accountId)
                                           .equal(azureRecommendation.getAccountId())
                                           .field(AzureRecommendationKeys.recommendationId)
                                           .equal(azureRecommendation.getRecommendationId());
    UpdateOperations<AzureRecommendation> updateOperations =
        hPersistence.createUpdateOperations(AzureRecommendation.class)
            .set(AzureRecommendationKeys.accountId, azureRecommendation.getAccountId())
            .set(AzureRecommendationKeys.recommendationId, azureRecommendation.getRecommendationId())
            .set(AzureRecommendationKeys.impactedField, azureRecommendation.getImpactedField())
            .set(AzureRecommendationKeys.impactedValue, azureRecommendation.getImpactedValue())
            .set(AzureRecommendationKeys.maxCpuP95, azureRecommendation.getMaxCpuP95())
            .set(AzureRecommendationKeys.maxMemoryP95, azureRecommendation.getMaxMemoryP95())
            .set(AzureRecommendationKeys.maxTotalNetworkP95, azureRecommendation.getMaxTotalNetworkP95())
            .set(AzureRecommendationKeys.currencyCode, azureRecommendation.getCurrencyCode())
            .set(AzureRecommendationKeys.expectedMonthlySavings, azureRecommendation.getExpectedMonthlySavings())
            .set(AzureRecommendationKeys.expectedAnnualSavings, azureRecommendation.getExpectedAnnualSavings())
            .set(AzureRecommendationKeys.currentVmDetails, azureRecommendation.getCurrentVmDetails())
            .set(AzureRecommendationKeys.targetVmDetails, azureRecommendation.getTargetVmDetails())
            .set(AzureRecommendationKeys.recommendationMessage, azureRecommendation.getRecommendationMessage())
            .set(AzureRecommendationKeys.recommendationType, azureRecommendation.getRecommendationType())
            .set(AzureRecommendationKeys.regionName, azureRecommendation.getRegionName())
            .set(AzureRecommendationKeys.subscriptionId, azureRecommendation.getSubscriptionId())
            .set(AzureRecommendationKeys.tenantId, azureRecommendation.getTenantId())
            .set(AzureRecommendationKeys.duration, azureRecommendation.getDuration())
            .set(AzureRecommendationKeys.vmId, azureRecommendation.getVmId())
            .set(AzureRecommendationKeys.connectorId, azureRecommendation.getConnectorId())
            .set(AzureRecommendationKeys.connectorName, azureRecommendation.getConnectorName());

    return hPersistence.upsert(query, updateOperations, upsertReturnNewOptions);
  }

  /**
   * This method saves the azure recommendation to the common table in timescale CE_RECOMMENDATIONS.
   * This will be used by the overview api to list down all recommendations.
   * @param azureRecommendation
   * In CLUSTERNAME we save the Subscription Id
   * In NAMESPACE we save the Resource Group Id
   * In NAME we save the vm name
   */
  @RetryOnException(retryCount = RETRY_COUNT, sleepDurationInMilliseconds = SLEEP_DURATION)
  public void upsertCeRecommendation(
      @NonNull AzureRecommendation azureRecommendation, @NonNull Instant lastReceivedUntilAt, String resourceGroupId) {
    dslContext.insertInto(CE_RECOMMENDATIONS)
        .set(CE_RECOMMENDATIONS.ACCOUNTID, azureRecommendation.getAccountId())
        .set(CE_RECOMMENDATIONS.ID, azureRecommendation.getUuid())
        .set(CE_RECOMMENDATIONS.CLUSTERNAME, azureRecommendation.getSubscriptionId())
        .set(CE_RECOMMENDATIONS.NAMESPACE, resourceGroupId)
        .set(CE_RECOMMENDATIONS.NAME, azureRecommendation.getImpactedValue())
        .set(CE_RECOMMENDATIONS.RESOURCETYPE, ResourceType.AZURE_INSTANCE.name())
        .set(CE_RECOMMENDATIONS.MONTHLYSAVING, azureRecommendation.getExpectedMonthlySavings())
        .set(CE_RECOMMENDATIONS.MONTHLYCOST, azureRecommendation.getCurrentVmDetails().getCost())
        .set(CE_RECOMMENDATIONS.ISVALID, true)
        .set(CE_RECOMMENDATIONS.LASTPROCESSEDAT,
            toOffsetDateTime(lastReceivedUntilAt.minus(THRESHOLD_DAYS_TO_SHOW_RECOMMENDATION - 2, ChronoUnit.DAYS)))
        .set(CE_RECOMMENDATIONS.UPDATEDAT, offsetDateTimeNow())
        .onConflictOnConstraint(CE_RECOMMENDATIONS.getPrimaryKey())
        .doUpdate()
        .set(CE_RECOMMENDATIONS.MONTHLYSAVING, azureRecommendation.getExpectedMonthlySavings())
        .set(CE_RECOMMENDATIONS.MONTHLYCOST, azureRecommendation.getCurrentVmDetails().getCost())
        .set(CE_RECOMMENDATIONS.LASTPROCESSEDAT,
            toOffsetDateTime(lastReceivedUntilAt.minus(THRESHOLD_DAYS_TO_SHOW_RECOMMENDATION - 2, ChronoUnit.DAYS)))
        .set(CE_RECOMMENDATIONS.UPDATEDAT, offsetDateTimeNow())
        .execute();
  }

  @NonNull
  public void updateJiraInAzureRecommendation(
      @NonNull String accountId, @NonNull String id, CCMJiraDetails jiraDetails) {
    hPersistence.upsert(hPersistence.createQuery(AzureRecommendation.class)
                            .filter(AzureRecommendationKeys.accountId, accountId)
                            .filter(AzureRecommendationKeys.uuid, new ObjectId(id)),
        hPersistence.createUpdateOperations(AzureRecommendation.class)
            .set(AzureRecommendationKeys.jiraDetails, jiraDetails));
  }

  @RetryOnException(retryCount = RETRY_COUNT, sleepDurationInMilliseconds = SLEEP_DURATION)
  public void ignoreAzureVmRecommendations(
      @NonNull String accountId, @NonNull List<RecommendationAzureVmId> azureVmIds) {
    if (azureVmIds.isEmpty()) {
      return;
    }
    dslContext.update(CE_RECOMMENDATIONS)
        .set(CE_RECOMMENDATIONS.RECOMMENDATIONSTATE, RecommendationState.IGNORED.name())
        .where(CE_RECOMMENDATIONS.ACCOUNTID.eq(accountId)
                   .and(CE_RECOMMENDATIONS.RECOMMENDATIONSTATE.eq(RecommendationState.OPEN.name()))
                   .and(CE_RECOMMENDATIONS.RESOURCETYPE.eq(ResourceType.AZURE_INSTANCE.name()))
                   .and(getAzureVmCondition(azureVmIds)))
        .execute();
  }

  @RetryOnException(retryCount = RETRY_COUNT, sleepDurationInMilliseconds = SLEEP_DURATION)
  public void unIgnoreAzureVmRecommendations(
      @NonNull String accountId, @NonNull List<RecommendationAzureVmId> azureVmIds) {
    if (azureVmIds.isEmpty()) {
      return;
    }
    dslContext.update(CE_RECOMMENDATIONS)
        .set(CE_RECOMMENDATIONS.RECOMMENDATIONSTATE, RecommendationState.OPEN.name())
        .where(CE_RECOMMENDATIONS.ACCOUNTID.eq(accountId)
                   .and(CE_RECOMMENDATIONS.RECOMMENDATIONSTATE.eq(RecommendationState.IGNORED.name()))
                   .and(CE_RECOMMENDATIONS.RESOURCETYPE.eq(ResourceType.AZURE_INSTANCE.name()))
                   .and(getAzureVmCondition(azureVmIds)))
        .execute();
  }

  private Condition getAzureVmCondition(List<RecommendationAzureVmId> azureVmIds) {
    RecommendationAzureVmId azureVmId = azureVmIds.get(0);
    Condition condition = CE_RECOMMENDATIONS.CLUSTERNAME.eq(azureVmId.getSubscriptionId())
                              .and(CE_RECOMMENDATIONS.NAMESPACE.eq(azureVmId.getResourceGroupId()))
                              .and(CE_RECOMMENDATIONS.NAME.eq(azureVmId.getVmName()));
    for (int i = 1; i < azureVmIds.size(); i++) {
      azureVmId = azureVmIds.get(i);
      condition.or(CE_RECOMMENDATIONS.CLUSTERNAME.eq(azureVmId.getSubscriptionId())
                       .and(CE_RECOMMENDATIONS.NAMESPACE.eq(azureVmId.getResourceGroupId()))
                       .and(CE_RECOMMENDATIONS.NAME.eq(azureVmId.getVmName())));
    }
    return condition;
  }
}
