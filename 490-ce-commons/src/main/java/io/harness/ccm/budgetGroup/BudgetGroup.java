/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.budgetGroup;

import io.harness.annotations.StoreIn;
import io.harness.ccm.budget.AlertThreshold;
import io.harness.ccm.budget.BudgetMonthlyBreakdown;
import io.harness.ccm.budget.BudgetPeriod;
import io.harness.ccm.commons.entities.budget.BudgetCostData;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashMap;
import java.util.List;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@StoreIn(DbAliases.CENG)
@FieldNameConstants(innerTypeName = "BudgetGroupKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "budgetGroups", noClassnameStored = true)
@Schema(name = "BudgetGroup", description = "The Cloud Cost Budget Group definition")
public final class BudgetGroup implements PersistentEntity, UuidAware, AccountAccess, CreatedAtAware, UpdatedAtAware {
  @Id String uuid;
  @FdIndex String accountId;
  @Size(min = 1, max = 80, message = "for budget group name must be between 1 and 80 characters long") String name;
  BudgetMonthlyBreakdown budgetGroupMonthlyBreakdown;
  BudgetPeriod period;
  Double budgetGroupAmount;
  Double actualCost;
  Double forecastCost;
  Double lastMonthCost;
  AlertThreshold[] alertThresholds;
  List<BudgetGroupChildEntityDTO> childEntities;
  String parentBudgetGroupId;
  CascadeType cascadeType;
  long startTime;
  long endTime;
  long createdAt;
  long lastUpdatedAt;
  HashMap<Long, BudgetCostData> budgetGroupHistory;

  public BudgetGroup toDTO() {
    return BudgetGroup.builder()
        .uuid(getUuid())
        .accountId(getAccountId())
        .name(getName())
        .budgetGroupMonthlyBreakdown(getBudgetGroupMonthlyBreakdown())
        .period(getPeriod())
        .budgetGroupAmount(getBudgetGroupAmount())
        .actualCost(getActualCost())
        .forecastCost(getForecastCost())
        .lastMonthCost(getLastMonthCost())
        .alertThresholds(getAlertThresholds())
        .childEntities(getChildEntities())
        .parentBudgetGroupId(getParentBudgetGroupId())
        .cascadeType(getCascadeType())
        .startTime(getStartTime())
        .endTime(getEndTime())
        .createdAt(getCreatedAt())
        .lastUpdatedAt(getLastUpdatedAt())
        .budgetGroupHistory(getBudgetGroupHistory())
        .build();
  }
}
