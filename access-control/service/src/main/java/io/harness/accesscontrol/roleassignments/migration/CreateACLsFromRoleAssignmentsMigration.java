package io.harness.accesscontrol.roleassignments.migration;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.aggregator.consumers.ChangeConsumerService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.CloseableIterator;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class CreateACLsFromRoleAssignmentsMigration implements NGMigration {
  private final ACLRepository aclRepository;
  private final ChangeConsumerService changeConsumerService;
  public static final int BATCH_SIZE = 1000;
  private MongoTemplate mongoTemplate;

  @Inject
  public CreateACLsFromRoleAssignmentsMigration(@Named(ACL.PRIMARY_COLLECTION) ACLRepository aclRepository,
      ChangeConsumerService changeConsumerService, MongoTemplate mongoTemplate) {
    this.aclRepository = aclRepository;
    this.changeConsumerService = changeConsumerService;
    this.mongoTemplate = mongoTemplate;
  }

  private CloseableIterator<RoleAssignmentDBO> runQueryWithBatch(int batchSize) {
    Pattern startsWithScope = Pattern.compile("^/ACCOUNT/2gf_S_jTQMuh2aiLSELxTw|^/ACCOUNT/px7xd_BFRCi-pfWPYXVjvw");
    Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.scopeIdentifier).regex(startsWithScope);
    Query query = new Query();
    query.addCriteria(criteria);
    query.cursorBatchSize(batchSize);
    return mongoTemplate.stream(query, RoleAssignmentDBO.class);
  }

  private long upsertACLs(RoleAssignmentDBO roleAssignment) {
    aclRepository.deleteByRoleAssignmentId(roleAssignment.getId());
    List<ACL> aclsToCreate = changeConsumerService.getAClsForRoleAssignment(roleAssignment);
    aclsToCreate.addAll(
        changeConsumerService.getImplicitACLsForRoleAssignment(roleAssignment, new HashSet<>(), new HashSet<>()));
    return aclRepository.insertAllIgnoringDuplicates(aclsToCreate);
  }

  @Override
  public void migrate() {
    log.info("[CreateACLsFromRoleAssignmentsMigration] starting migration....");
    try (CloseableIterator<RoleAssignmentDBO> iterator = runQueryWithBatch(BATCH_SIZE)) {
      while (iterator.hasNext()) {
        RoleAssignmentDBO roleAssignmentDBO = iterator.next();
        log.info(
            "Number of ACLs created during CreateACLsFromRoleAssignmentsMigration: {}", upsertACLs(roleAssignmentDBO));
      }
    }
    log.info("[CreateACLsFromRoleAssignmentsMigration] migration successful....");
  }
}
