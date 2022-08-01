package io.harness.accesscontrol.acl.migration;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.ACL.ACLKeys;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@OwnedBy(PL)
public class ACLAddBooleanFieldsMigration implements NGMigration {
  private final MongoTemplate mongoTemplate;

  @Inject
  public ACLAddBooleanFieldsMigration(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public void migrate() {
    Query conditionalQuery = new Query(Criteria.where(ACLKeys.conditional).exists(false));
    Update updateConditionalField = new Update().set(ACLKeys.conditional, false);
    mongoTemplate.updateMulti(conditionalQuery, updateConditionalField, ACL.class, ACL.PRIMARY_COLLECTION);
    Update updateImplicitlyCreatedForScopeField =
        new Update().set(ACL.IMPLICITLY_CREATED_FOR_SCOPE_ACCESS_KEY, false).set(ACLKeys.conditional, false);
    mongoTemplate.updateMulti(new Query(), updateImplicitlyCreatedForScopeField, ACL.class, ACL.PRIMARY_COLLECTION);
  }
}
