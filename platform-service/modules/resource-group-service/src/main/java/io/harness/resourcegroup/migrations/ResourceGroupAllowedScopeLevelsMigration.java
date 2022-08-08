/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.resourcegroup.migrations;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.resourcegroup.framework.v1.repositories.spring.ResourceGroupRepository;
import io.harness.resourcegroup.v1.model.ResourceGroup.ResourceGroupKeys;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@OwnedBy(PL)
public class ResourceGroupAllowedScopeLevelsMigration implements NGMigration {
  private final ResourceGroupRepository resourceGroupRepository;

  @Inject
  public ResourceGroupAllowedScopeLevelsMigration(ResourceGroupRepository resourceGroupRepository) {
    this.resourceGroupRepository = resourceGroupRepository;
  }

  @Override
  public void migrate() {
    log.info("[ResourceGroupAllowedScopeLevelsMigration] starting migration....");
    addAccountScopeLevel();
    addOrgScopeLevel();
    addProjectScopeLevel();
  }

  private void addProjectScopeLevel() {
    Criteria criteria = Criteria.where(ResourceGroupKeys.accountIdentifier)
                            .ne(null)
                            .and(ResourceGroupKeys.orgIdentifier)
                            .ne(null)
                            .and(ResourceGroupKeys.projectIdentifier)
                            .ne(null);
    Query query = new Query(criteria);
    Set<String> allowedScopeLevels = Sets.newHashSet("project");
    Update update = new Update().set(ResourceGroupKeys.allowedScopeLevels, allowedScopeLevels);
    if (!resourceGroupRepository.updateMultiple(query, update)) {
      log.error("[ResourceGroupAllowedScopeLevelsMigration] migration for projects was not acknowledged.");
      return;
    }
    log.info("[ResourceGroupAllowedScopeLevelsMigration] migration for projects completed successfully.");
  }

  private void addOrgScopeLevel() {
    Criteria criteria = Criteria.where(ResourceGroupKeys.accountIdentifier)
                            .ne(null)
                            .and(ResourceGroupKeys.orgIdentifier)
                            .ne(null)
                            .and(ResourceGroupKeys.projectIdentifier)
                            .is(null);
    Query query = new Query(criteria);
    Set<String> allowedScopeLevels = Sets.newHashSet("organization");
    Update update = new Update().set(ResourceGroupKeys.allowedScopeLevels, allowedScopeLevels);
    if (!resourceGroupRepository.updateMultiple(query, update)) {
      log.error("[ResourceGroupAllowedScopeLevelsMigration] migration for organizations was not acknowledged.");
      return;
    }
    log.info("[ResourceGroupAllowedScopeLevelsMigration] migration for organizations completed successfully.");
  }

  private void addAccountScopeLevel() {
    Criteria criteria = Criteria.where(ResourceGroupKeys.accountIdentifier)
                            .ne(null)
                            .and(ResourceGroupKeys.orgIdentifier)
                            .is(null)
                            .and(ResourceGroupKeys.projectIdentifier)
                            .is(null);
    Query query = new Query(criteria);
    Set<String> allowedScopeLevels = Sets.newHashSet("account");
    Update update = new Update().set(ResourceGroupKeys.allowedScopeLevels, allowedScopeLevels);
    if (!resourceGroupRepository.updateMultiple(query, update)) {
      log.error("[ResourceGroupAllowedScopeLevelsMigration] migration for accounts was not acknowledged.");
      return;
    }
    log.info("[ResourceGroupAllowedScopeLevelsMigration] migration for accounts completed successfully.");
  }
}
