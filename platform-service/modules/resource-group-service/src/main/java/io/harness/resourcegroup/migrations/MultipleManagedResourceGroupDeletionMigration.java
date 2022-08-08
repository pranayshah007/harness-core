/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.migrations;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.resourcegroup.framework.v1.repositories.spring.ResourceGroupRepository;
import io.harness.resourcegroup.v1.model.ResourceGroup.ResourceGroupKeys;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class MultipleManagedResourceGroupDeletionMigration implements NGMigration {
  private final ResourceGroupRepository resourceGroupRepository;

  @Inject
  public MultipleManagedResourceGroupDeletionMigration(ResourceGroupRepository resourceGroupRepository) {
    this.resourceGroupRepository = resourceGroupRepository;
  }

  @Override
  public void migrate() {
    Criteria criteria = Criteria.where(ResourceGroupKeys.accountIdentifier)
                            .exists(true)
                            .ne(null)
                            .and(ResourceGroupKeys.harnessManaged)
                            .is(Boolean.TRUE);
    try {
      if (!resourceGroupRepository.delete(criteria)) {
        log.error("Deletion of managed resource groups was not acknowledged.");
      }
    } catch (Exception exception) {
      log.error("Unexpected error occurred during ManagedResourceGroupMigrationJob.", exception);
    }
  }
}
