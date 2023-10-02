/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.businessmapping.service.impl;

import static org.apache.commons.lang3.ObjectUtils.max;

import io.harness.ccm.views.businessmapping.dao.BusinessMappingHistoryDao;
import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.entities.BusinessMappingHistory;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingHistoryService;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

public class BusinessMappingHistoryServiceImpl implements BusinessMappingHistoryService {
  private static final int MAX_YEAR_MONTH = 209912;

  @Inject private BusinessMappingHistoryDao businessMappingHistoryDao;

  @Override
  public BusinessMappingHistory save(BusinessMappingHistory businessMappingHistory) {
    return businessMappingHistoryDao.save(businessMappingHistory);
  }

  @Override
  public List<BusinessMappingHistory> getAll(String accountId, String businessMappingId) {
    return businessMappingHistoryDao.getAll(accountId, businessMappingId);
  }

  @Override
  public BusinessMappingHistory getLatest(String accountId, String businessMappingId, Instant currentInstant) {
    return businessMappingHistoryDao.getLatest(
        accountId, businessMappingId, getYearMonthInteger(getYearMonth(currentInstant)));
  }

  @Override
  public List<BusinessMappingHistory> getInRange(String accountId, Instant startTime, Instant endTime) {
    return businessMappingHistoryDao.getInRange(
        accountId, getYearMonthInteger(getYearMonth(startTime)), getYearMonthInteger(getYearMonth(endTime)));
  }

  @Override
  public List<BusinessMappingHistory> getInRange(
      String accountId, String businessMappingId, Instant startTime, Instant endTime) {
    return businessMappingHistoryDao.getInRange(accountId, businessMappingId,
        getYearMonthInteger(getYearMonth(startTime)), getYearMonthInteger(getYearMonth(endTime)));
  }

  @Override
  public BusinessMappingHistory update(BusinessMappingHistory businessMappingHistory) {
    return businessMappingHistoryDao.update(businessMappingHistory);
  }

  @Override
  public boolean delete(BusinessMappingHistory businessMappingHistory) {
    return businessMappingHistoryDao.delete(businessMappingHistory);
  }

  @Override
  public void handleCreateEvent(BusinessMapping businessMapping, Instant eventTime) {
    save(BusinessMappingHistory.fromBusinessMapping(
        businessMapping, getYearMonthInteger(getYearMonth(eventTime)), MAX_YEAR_MONTH));
  }

  @Override
  public void handleUpdateEvent(BusinessMapping businessMapping, Instant eventTime) {
    BusinessMappingHistory businessMappingHistory =
        getLatest(businessMapping.getAccountId(), businessMapping.getUuid(), eventTime);
    if (getYearMonthInteger(getYearMonth(eventTime)).equals(businessMappingHistory.getStartAt())) {
      businessMappingHistory.setName(businessMapping.getName());
      businessMappingHistory.setCostTargets(businessMapping.getCostTargets());
      businessMappingHistory.setSharedCosts(businessMapping.getSharedCosts());
      businessMappingHistory.setUnallocatedCost(businessMapping.getUnallocatedCost());
      businessMappingHistory.setDataSources(businessMapping.getDataSources());
      update(businessMappingHistory);
    } else {
      businessMappingHistory.setEndAt(
          max(getYearMonthInteger(getYearMonth(eventTime).minusMonths(1)), businessMappingHistory.getStartAt()));
      update(businessMappingHistory);
      BusinessMappingHistory newBusinessMappingHistory = BusinessMappingHistory.fromBusinessMapping(
          businessMapping, getYearMonthInteger(getYearMonth(eventTime)), MAX_YEAR_MONTH);
      save(newBusinessMappingHistory);
    }
  }

  @Override
  public void handleDeleteEvent(BusinessMapping businessMapping, Instant eventTime) {
    BusinessMappingHistory businessMappingHistory =
        getLatest(businessMapping.getAccountId(), businessMapping.getUuid(), eventTime);
    Integer yearMonth = getYearMonthInteger(getYearMonth(eventTime).minusMonths(1));
    if (yearMonth < businessMappingHistory.getStartAt()) {
      delete(businessMappingHistory);
    } else {
      businessMappingHistory.setEndAt(yearMonth);
      update(businessMappingHistory);
    }
  }

  private static YearMonth getYearMonth(Instant instant) {
    return YearMonth.from(instant.atZone(ZoneId.systemDefault()).toLocalDate());
  }

  private static Integer getYearMonthInteger(YearMonth yearMonth) {
    return yearMonth.getYear() * 100 + yearMonth.getMonthValue();
  }
}
