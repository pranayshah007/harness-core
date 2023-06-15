/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.entities.recommendations;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "RecommendationsIgnoreListKeys")
@StoreIn(DbAliases.CENG)
@Entity(value = "recommendationsIgnoreList", noClassnameStored = true)
@OwnedBy(CE)
public final class RecommendationsIgnoreList implements PersistentEntity, AccountAccess {
  @Id String accountId;
  Set<RecommendationWorkloadId> workloadIgnoreList;
  Set<RecommendationNodepoolId> nodepoolIgnoreList;
  Set<RecommendationECSServiceId> ecsServiceIgnoreList;
  Set<RecommendationEC2InstanceId> ec2InstanceIgnoreList;
  Set<RecommendationAzureVmId> azureVmIgnoreList;
}
