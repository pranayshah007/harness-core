/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.migration;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.entities.BusinessMappingHistory;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingHistoryService;
import io.harness.migration.NGMigration;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class BusinessMappingHistoryMigration implements NGMigration {
  private static final int MAX_YEAR_MONTH = 209912;

  @Inject private HPersistence hPersistence;
  @Inject BusinessMappingHistoryService businessMappingHistoryService;

  @Override
  public void migrate() {
    try {
      log.info("Starting migration of all Business Mappings to Business Mapping History");
      final List<BusinessMapping> businessMappingList =
          hPersistence.createQuery(BusinessMapping.class, excludeAuthority).asList();
      for (final BusinessMapping businessMapping : businessMappingList) {
        try {
          migrateToBusinessMappingHistory(businessMapping);
        } catch (final Exception e) {
          log.error("Migration Failed for Account {}, businessMappingId {}", businessMapping.getAccountId(),
              businessMapping.getUuid(), e);
        }
      }
      log.info("BusinessMappingHistory migration has been completed");
    } catch (final Exception e) {
      log.error("Failure occurred in BusinessMappingHistoryMigration", e);
    }
  }

  private void migrateToBusinessMappingHistory(final BusinessMapping businessMapping) {
    List<BusinessMappingHistory> businessMappingHistoryList =
        businessMappingHistoryService.getAll(businessMapping.getAccountId(), businessMapping.getUuid());
    if (businessMappingHistoryList.isEmpty()) {
      BusinessMappingHistory businessMappingHistory = BusinessMappingHistory.fromBusinessMapping(
          businessMapping, getYearMonthInteger(Instant.ofEpochMilli(businessMapping.getCreatedAt())), MAX_YEAR_MONTH);
      businessMappingHistoryService.save(businessMappingHistory);
    } else {
      log.info("Migration already done for businessMappingId: {}", businessMapping.getUuid());
    }
  }

  private static Integer getYearMonthInteger(Instant instant) {
    YearMonth yearMonth = YearMonth.from(instant.atZone(ZoneId.systemDefault()).toLocalDate());
    return yearMonth.getYear() * 100 + yearMonth.getMonthValue();
  }
}
