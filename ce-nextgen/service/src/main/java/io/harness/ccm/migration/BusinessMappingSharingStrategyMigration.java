/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.migration;

import static io.harness.ccm.views.businessmapping.entities.SharingStrategy.EQUAL;
import static io.harness.ccm.views.businessmapping.entities.SharingStrategy.FIXED;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.businessmapping.dao.BusinessMappingDao;
import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.entities.SharedCost;
import io.harness.migration.NGMigration;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class BusinessMappingSharingStrategyMigration implements NGMigration {
  @Inject private BusinessMappingDao businessMappingDao;
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    try {
      log.info("Starting migration (updates) of all Business Mapping Sharing Strategy from FIXED to EQUAL");
      final List<BusinessMapping> businessMappingList =
          hPersistence.createQuery(BusinessMapping.class, excludeAuthority).asList();
      for (final BusinessMapping businessMapping : businessMappingList) {
        try {
          migrateBusinessMappingSharingStrategy(businessMapping);
        } catch (final Exception e) {
          log.error("Migration Failed for Account {}, businessMappingId {}", businessMapping.getAccountId(),
              businessMapping.getUuid(), e);
        }
      }
      log.info("BusinessMappingSharingStrategyMigration has been completed");
    } catch (final Exception e) {
      log.error("Failure occurred in BusinessMappingSharingStrategyMigration", e);
    }
  }

  private void migrateBusinessMappingSharingStrategy(final BusinessMapping businessMapping) {
    modifyBusinessMappingSharingStrategy(businessMapping);
    businessMappingDao.update(businessMapping);
  }

  private void modifyBusinessMappingSharingStrategy(final BusinessMapping businessMapping) {
    if (Objects.nonNull(businessMapping.getSharedCosts())) {
      final List<SharedCost> modifiedSharedCosts = new ArrayList<>();
      for (final SharedCost sharedCost : businessMapping.getSharedCosts()) {
        if (Objects.nonNull(sharedCost)) {
          if (FIXED == sharedCost.getStrategy()) {
            modifiedSharedCosts.add(
                SharedCost.builder().name(sharedCost.getName()).rules(sharedCost.getRules()).strategy(EQUAL).build());
          } else {
            modifiedSharedCosts.add(sharedCost);
          }
        }
      }
      businessMapping.setSharedCosts(modifiedSharedCosts);
    }
  }
}
