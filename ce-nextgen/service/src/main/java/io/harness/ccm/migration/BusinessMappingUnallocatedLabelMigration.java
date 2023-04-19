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
import io.harness.ccm.views.businessmapping.dao.BusinessMappingDao;
import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.entities.UnallocatedCost;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingValidationService;
import io.harness.migration.NGMigration;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class BusinessMappingUnallocatedLabelMigration implements NGMigration {
  private static final String UNATTRIBUTED = "Unattributed";
  @Inject private BusinessMappingDao businessMappingDao;
  @Inject private HPersistence hPersistence;
  @Inject private BusinessMappingValidationService businessMappingValidationService;

  @Override
  public void migrate() {
    try {
      log.info(
          "Starting migration (updates) of all Business Mapping Unallocated Cost Label from 'Others' to 'Unattributed'");
      final List<BusinessMapping> businessMappingList =
          hPersistence.createQuery(BusinessMapping.class, excludeAuthority).asList();
      for (final BusinessMapping businessMapping : businessMappingList) {
        if (businessMappingValidationService.isInvalidBusinessMappingUnallocatedCostLabel(businessMapping)) {
          try {
            migrateBusinessMappingUnallocatedCostLabel(businessMapping);
          } catch (final Exception e) {
            log.error("Migration Failed for Account {}, businessMappingId {}", businessMapping.getAccountId(),
                businessMapping.getUuid(), e);
          }
        }
      }
      log.info("BusinessMappingUnallocatedLabelMigration has been completed");
    } catch (final Exception e) {
      log.error("Failure occurred in BusinessMappingUnallocatedLabelMigration", e);
    }
  }

  private void migrateBusinessMappingUnallocatedCostLabel(final BusinessMapping businessMapping) {
    modifyBusinessMapping(businessMapping);
    businessMappingDao.update(businessMapping);
  }

  private void modifyBusinessMapping(final BusinessMapping businessMapping) {
    UnallocatedCost unallocatedCost = businessMapping.getUnallocatedCost();
    UnallocatedCost unallocatedCostModify = UnallocatedCost.builder()
                                                .label(UNATTRIBUTED)
                                                .strategy(unallocatedCost.getStrategy())
                                                .sharingStrategy(unallocatedCost.getSharingStrategy())
                                                .splits(unallocatedCost.getSplits())
                                                .build();
    businessMapping.setUnallocatedCost(unallocatedCostModify);
  }
}
