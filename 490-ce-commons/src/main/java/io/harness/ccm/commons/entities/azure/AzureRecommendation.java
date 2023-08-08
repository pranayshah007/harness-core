/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.azure;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.recommendation.CCMJiraDetails;
import io.harness.ccm.commons.beans.recommendation.CCMServiceNowDetails;
import io.harness.data.structure.MongoMapSanitizer;
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
import org.hibernate.validator.constraints.NotEmpty;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "AzureRecommendationKeys")
@StoreIn(DbAliases.CENG)
@Entity(value = "azureRecommendation", noClassnameStored = true)
@OwnedBy(CE)
public class AzureRecommendation
    implements PersistentEntity, UuidAware, AccountAccess, CreatedAtAware, UpdatedAtAware, ValidUntilAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_accountId_recommendationId")
                 .unique(true)
                 .field(AzureRecommendationKeys.accountId)
                 .field(AzureRecommendationKeys.recommendationId)
                 .build())
        .build();
  }

  private static final MongoMapSanitizer SANITIZER = new MongoMapSanitizer('~');

  @Id String uuid;
  long createdAt;
  long lastUpdatedAt;

  @NotEmpty String accountId;
  @NotEmpty String recommendationId;
  String vmId;
  String impactedField;
  String impactedValue;
  String maxCpuP95;
  String maxTotalNetworkP95;
  String maxMemoryP95;
  String currencyCode;
  String currencyCodeInDefaultCurrencyPref;
  Double expectedMonthlySavings;
  Double expectedMonthlySavingsInDefaultCurrencyPref;
  Double expectedAnnualSavings;
  Double expectedAnnualSavingsInDefaultCurrencyPref;
  AzureVmDetails currentVmDetails;
  AzureVmDetails targetVmDetails;
  String recommendationMessage;
  String recommendationType;
  String regionName;
  String subscriptionId;
  String tenantId;
  String duration;
  String connectorId;
  String connectorName;
  CCMJiraDetails jiraDetails;
  CCMServiceNowDetails serviceNowDetails;
  @JsonIgnore
  @EqualsAndHashCode.Exclude
  @Builder.Default
  @FdTtlIndex
  Date validUntil = Date.from(OffsetDateTime.now().plusDays(90).toInstant());
}
