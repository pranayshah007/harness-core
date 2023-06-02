/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.businessmapping.entities;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotBlank;

@Data
@Builder
@StoreIn(DbAliases.CENG)
@FieldNameConstants(innerTypeName = "BusinessMappingHistoryKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "businessMappingHistory", noClassnameStored = true)
@OwnedBy(CE)
public final class BusinessMappingHistory
    implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  @Id String uuid;
  String businessMappingId;
  // We consider a businessMappingHistory for duration in range [startAt, endAt)
  Integer startAt; // first four digits are Year, last two digits are month
  Integer endAt; // eg: 202301 means 2023 year and 01 month
  @Size(min = 1, max = 32, message = "Name must be between 1 and 32 characters long") @NotBlank String name;
  String accountId;

  List<CostTarget> costTargets;
  List<SharedCost> sharedCosts;
  UnallocatedCost unallocatedCost;
  List<ViewFieldIdentifier> dataSources;

  long createdAt;
  long lastUpdatedAt;

  public static BusinessMappingHistory fromBusinessMapping(
      BusinessMapping businessMapping, Integer startAt, Integer endAt) {
    return BusinessMappingHistory.builder()
        .businessMappingId(businessMapping.getUuid())
        .startAt(startAt)
        .endAt(endAt)
        .name(businessMapping.getName())
        .accountId(businessMapping.getAccountId())
        .costTargets(businessMapping.getCostTargets())
        .sharedCosts(businessMapping.getSharedCosts())
        .unallocatedCost(businessMapping.getUnallocatedCost())
        .dataSources(businessMapping.getDataSources())
        .build();
  }
}
