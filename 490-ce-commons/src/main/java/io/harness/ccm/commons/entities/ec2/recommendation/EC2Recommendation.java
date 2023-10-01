/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.ec2.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.recommendation.CCMJiraDetails;
import io.harness.ccm.commons.beans.recommendation.CCMServiceNowDetails;
import io.harness.data.structure.MongoMapSanitizer;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

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
                 .name("unique_accountId_awsAccountId_instanceId")
                 .unique(true)
                 .field(EC2RecommendationKeys.accountId)
                 .field(EC2RecommendationKeys.awsAccountId)
                 .field(EC2RecommendationKeys.instanceId)
                 .build())
        .build();
  }

  private static final MongoMapSanitizer SANITIZER = new MongoMapSanitizer('~');

  @Id String uuid;
  long createdAt;
  long lastUpdatedAt;

  @NotEmpty String accountId;
  @NotEmpty String awsAccountId;
  @NotEmpty String instanceId;
  String rightsizingType;
  String instanceName;
  String instanceType;
  String platform;
  String region;
  String memory;
  String sku;
  String vcpu;
  String currentMaxCPU;
  String currentMaxMemory;
  String currentMonthlyCost;
  String currencyCode;
  List<EC2RecommendationDetail> recommendationInfo;
  String expectedSaving;
  Instant lastUpdatedTime;
  CCMJiraDetails jiraDetails;
  CCMServiceNowDetails serviceNowDetails;
}
