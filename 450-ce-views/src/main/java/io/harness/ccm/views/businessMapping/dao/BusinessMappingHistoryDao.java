/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.businessMapping.dao;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.businessMapping.entities.BusinessMappingHistory;
import io.harness.ccm.views.businessMapping.entities.BusinessMappingHistory.BusinessMappingHistoryKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class BusinessMappingHistoryDao {
  @Inject private HPersistence hPersistence;

  public BusinessMappingHistory save(BusinessMappingHistory businessMappingHistory) {
    hPersistence.save(businessMappingHistory);
    return businessMappingHistory;
  }

  public List<BusinessMappingHistory> getAll(String accountId, String businessMappingId) {
    return hPersistence.createQuery(BusinessMappingHistory.class)
        .filter(BusinessMappingHistoryKeys.accountId, accountId)
        .filter(BusinessMappingHistoryKeys.businessMappingId, businessMappingId)
        .asList();
  }

  public BusinessMappingHistory getLatest(String accountId, String businessMappingId, Integer currentYearMonth) {
    return hPersistence.createQuery(BusinessMappingHistory.class, excludeValidate)
        .filter(BusinessMappingHistoryKeys.accountId, accountId)
        .filter(BusinessMappingHistoryKeys.businessMappingId, businessMappingId)
        .field(BusinessMappingHistoryKeys.endAt)
        .greaterThan(currentYearMonth)
        .get();
  }

  public List<BusinessMappingHistory> getInRange(String accountId, Integer startYearMonth, Integer endYearMonth) {
    Query<BusinessMappingHistory> query =
        hPersistence.createQuery(BusinessMappingHistory.class).filter(BusinessMappingHistoryKeys.accountId, accountId);
    query.or(query.and(query.criteria(BusinessMappingHistoryKeys.startAt).lessThanOrEq(endYearMonth),
                 query.criteria(BusinessMappingHistoryKeys.startAt).greaterThanOrEq(startYearMonth)),
        query.and(query.criteria(BusinessMappingHistoryKeys.startAt).lessThan(startYearMonth),
            query.criteria(BusinessMappingHistoryKeys.endAt).greaterThanOrEq(startYearMonth)));
    return query.asList();
  }

  public List<BusinessMappingHistory> getInRange(
      String accountId, String businessMappingId, Integer startYearMonth, Integer endYearMonth) {
    Query<BusinessMappingHistory> query = hPersistence.createQuery(BusinessMappingHistory.class)
                                              .filter(BusinessMappingHistoryKeys.accountId, accountId)
                                              .filter(BusinessMappingHistoryKeys.businessMappingId, businessMappingId);
    query.or(query.and(query.criteria(BusinessMappingHistoryKeys.startAt).lessThanOrEq(endYearMonth),
                 query.criteria(BusinessMappingHistoryKeys.startAt).greaterThanOrEq(startYearMonth)),
        query.and(query.criteria(BusinessMappingHistoryKeys.startAt).lessThan(startYearMonth),
            query.criteria(BusinessMappingHistoryKeys.endAt).greaterThanOrEq(startYearMonth)));
    return query.asList();
  }

  public BusinessMappingHistory update(BusinessMappingHistory businessMappingHistory) {
    Query<BusinessMappingHistory> query = hPersistence.createQuery(BusinessMappingHistory.class)
                                              .field(BusinessMappingHistoryKeys.accountId)
                                              .equal(businessMappingHistory.getAccountId())
                                              .field(BusinessMappingHistoryKeys.uuid)
                                              .equal(businessMappingHistory.getUuid());

    hPersistence.update(query, getUpdateOperations(businessMappingHistory));
    return businessMappingHistory;
  }

  private UpdateOperations<BusinessMappingHistory> getUpdateOperations(BusinessMappingHistory businessMappingHistory) {
    UpdateOperations<BusinessMappingHistory> updateOperations =
        hPersistence.createUpdateOperations(BusinessMappingHistory.class);

    setUnsetUpdateOperations(updateOperations, BusinessMappingHistoryKeys.name, businessMappingHistory.getName());
    setUnsetUpdateOperations(
        updateOperations, BusinessMappingHistoryKeys.costTargets, businessMappingHistory.getCostTargets());
    setUnsetUpdateOperations(
        updateOperations, BusinessMappingHistoryKeys.sharedCosts, businessMappingHistory.getSharedCosts());
    setUnsetUpdateOperations(
        updateOperations, BusinessMappingHistoryKeys.unallocatedCost, businessMappingHistory.getUnallocatedCost());
    setUnsetUpdateOperations(
        updateOperations, BusinessMappingHistoryKeys.dataSources, businessMappingHistory.getDataSources());
    setUnsetUpdateOperations(updateOperations, BusinessMappingHistoryKeys.startAt, businessMappingHistory.getStartAt());
    setUnsetUpdateOperations(updateOperations, BusinessMappingHistoryKeys.endAt, businessMappingHistory.getEndAt());

    return updateOperations;
  }

  private void setUnsetUpdateOperations(
      UpdateOperations<BusinessMappingHistory> updateOperations, String key, Object value) {
    if (Objects.nonNull(value)) {
      updateOperations.set(key, value);
    } else {
      updateOperations.unset(key);
    }
  }
}
