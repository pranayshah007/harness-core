/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupRepository;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.aggregator.consumers.ACLGeneratorService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.HashSet;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.CloseableIterator;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class GenerateACLsFromRoleAssignmentsJob {
  private final ACLRepository aclRepository;
  private final ACLGeneratorService aclGeneratorService;
  public static final int BATCH_SIZE = 1000;
  private MongoTemplate mongoTemplate;
  private UserGroupRepository userGroupRepository;

  @Inject
  public GenerateACLsFromRoleAssignmentsJob(@Named(ACL.PRIMARY_COLLECTION) ACLRepository aclRepository,
      ACLGeneratorService aclGeneratorService, MongoTemplate mongoTemplate, UserGroupRepository userGroupRepository) {
    this.aclRepository = aclRepository;
    this.aclGeneratorService = aclGeneratorService;
    this.mongoTemplate = mongoTemplate;
    this.userGroupRepository = userGroupRepository;
  }

  private CloseableIterator<RoleAssignmentDBO> runQueryWithBatch(String scopeIdentifier, int batchSize) {
    Pattern startsWithScope = Pattern.compile("^" + scopeIdentifier);
    Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.scopeIdentifier).regex(startsWithScope);
    Query query = new Query();
    query.addCriteria(criteria);
    query.cursorBatchSize(batchSize);
    return mongoTemplate.stream(query, RoleAssignmentDBO.class);
  }

  private long upsertACLs(RoleAssignmentDBO roleAssignment) {
    aclRepository.deleteByRoleAssignmentId(roleAssignment.getId());
    long numberOfACLsCreated = aclGeneratorService.createACLsForRoleAssignment(roleAssignment);
    numberOfACLsCreated +=
        aclGeneratorService.createImplicitACLsForRoleAssignment(roleAssignment, new HashSet<>(), new HashSet<>());
    return numberOfACLsCreated;
  }

  public void migrate(String accountIdentifier) {
    String scopeIdentifier = "/ACCOUNT/" + accountIdentifier;
    log.info("[CreateACLsFromRoleAssignmentsMigration] starting migration....");
    try (CloseableIterator<RoleAssignmentDBO> iterator = runQueryWithBatch(scopeIdentifier, BATCH_SIZE)) {
      while (iterator.hasNext()) {
        RoleAssignmentDBO roleAssignmentDBO = iterator.next();
        try {
          log.info(
              "[CreateACLsFromRoleAssignmentsMigration] Number of ACLs created during for roleAssignment {} is : {}",
              roleAssignmentDBO.getIdentifier(), upsertACLs(roleAssignmentDBO));
        } catch (Exception e) {
          log.info("[CreateACLsFromRoleAssignmentsMigration] Unable to process roleassignment: {} due to exception {}",
              roleAssignmentDBO.getIdentifier(), e);
        }
      }
    }
    log.info("[CreateACLsFromRoleAssignmentsMigration] migration successful....");
  }
}