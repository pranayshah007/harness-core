/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.migration;

import static org.springframework.data.mongodb.core.query.Update.update;

import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.roleassignments.persistence.repositories.RoleAssignmentRepository;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.CloseableIterator;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class AccountBasicRoleAssignmentInternalFieldMigration implements NGMigration {
  private final RoleAssignmentRepository roleAssignmentRepository;

  private static final String ACCOUNT_BASIC = "_account_basic";
  private MongoTemplate mongoTemplate;
  public static final int BATCH_SIZE = 1000;

  @Inject
  public AccountBasicRoleAssignmentInternalFieldMigration(
      RoleAssignmentRepository roleAssignmentRepository, MongoTemplate mongoTemplate) {
    this.roleAssignmentRepository = roleAssignmentRepository;
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public void migrate() {
    log.info("AccountBasicRoleAssignmentInternalFieldMigration started");

    try {
      Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.roleIdentifier).is(ACCOUNT_BASIC);
      CloseableIterator<RoleAssignmentDBO> iterator = runQueryWithBatch(criteria, BATCH_SIZE);

      while (iterator.hasNext()) {
        RoleAssignmentDBO roleAssignment = iterator.next();
        try {
          roleAssignmentRepository.updateById(roleAssignment.getId(), update(RoleAssignmentDBOKeys.internal, true));
        } catch (Exception exception) {
          log.error(
              String.format(
                  "[AccountBasicRoleAssignmentInternalFieldMigration] Unexpected error occurred while updating the roleassignment: %s",
                  roleAssignment.getId()),
              exception);
        }
      }
    } catch (Exception ex) {
      log.error("Unexpected error occurred during the migration: AccountBasicRoleAssignmentInternalFieldMigration", ex);
    }
    log.info("AccountBasicRoleAssignmentInternalFieldMigration completed.");
  }

  private CloseableIterator<RoleAssignmentDBO> runQueryWithBatch(Criteria criteria, int batchSize) {
    Query query = new Query(criteria);
    query.cursorBatchSize(batchSize);
    return mongoTemplate.stream(query, RoleAssignmentDBO.class);
  }
}
