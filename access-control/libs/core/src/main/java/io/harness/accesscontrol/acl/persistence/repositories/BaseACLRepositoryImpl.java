/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl.persistence.repositories;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.springframework.data.mongodb.util.MongoDbErrorCodes.isDuplicateKeyCode;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.ACL.ACLKeys;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.MongoIndex;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoNamespace;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.RenameCollectionOptions;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.conversions.Bson;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.BulkOperations.BulkMode;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
@ValidateOnExecution
public abstract class BaseACLRepositoryImpl implements ACLRepository {
  protected final MongoTemplate mongoTemplate;

  protected abstract String getCollectionName();

  public long insertAllIgnoringDuplicates(List<ACL> acls) {
    try {
      if (isEmpty(acls)) {
        return 0;
      }
      return mongoTemplate.bulkOps(BulkMode.UNORDERED, ACL.class, getCollectionName())
          .insert(acls)
          .execute()
          .getInsertedCount();
    } catch (BulkOperationException ex) {
      if (ex.getErrors().stream().allMatch(bulkWriteError -> isDuplicateKeyCode(bulkWriteError.getCode()))) {
        return ex.getResult().getInsertedCount();
      }
      throw ex;
    } catch (Exception ex) {
      if (ex.getCause() instanceof MongoBulkWriteException) {
        MongoBulkWriteException bulkWriteException = (MongoBulkWriteException) ex.getCause();
        if (bulkWriteException.getWriteErrors().stream().allMatch(
                writeError -> isDuplicateKeyCode(writeError.getCode()))) {
          return bulkWriteException.getWriteResult().getInsertedCount();
        }
      }
      throw ex;
    }
  }

  @Override
  public long deleteByRoleAssignmentId(String id) {
    return mongoTemplate
        .remove(new Query(Criteria.where(ACLKeys.roleAssignmentId).is(id)), ACL.class, getCollectionName())
        .getDeletedCount();
  }

  @Override
  public List<String> getDistinctResourceSelectorsInACLs(String roleAssignmentId) {
    Criteria criteria = Criteria.where(ACLKeys.roleAssignmentId).is(roleAssignmentId);
    Query query = new Query();
    query.addCriteria(criteria);
    return mongoTemplate.findDistinct(query, ACLKeys.resourceSelector, getCollectionName(), ACL.class, String.class);
  }

  @Override
  public long deleteByRoleAssignmentIdAndResourceSelectors(
      String roleAssignmentId, Set<String> resourceSelectorsToDelete) {
    return mongoTemplate
        .remove(new Query(Criteria.where(ACLKeys.roleAssignmentId)
                              .is(roleAssignmentId)
                              .and(ACLKeys.resourceSelector)
                              .in(resourceSelectorsToDelete)),
            ACL.class, getCollectionName())
        .getDeletedCount();
  }

  @Override
  public long deleteByRoleAssignmentIdAndPermissions(String roleAssignmentId, Set<String> permissions) {
    return mongoTemplate
        .remove(new Query(Criteria.where(ACLKeys.roleAssignmentId)
                              .is(roleAssignmentId)
                              .and(ACLKeys.permissionIdentifier)
                              .in(permissions)),
            ACL.class, getCollectionName())
        .getDeletedCount();
  }

  @Override
  public long deleteByRoleAssignmentIdAndPrincipals(String roleAssignmentId, Set<String> principals) {
    return mongoTemplate
        .remove(new Query(Criteria.where(ACLKeys.roleAssignmentId)
                              .is(roleAssignmentId)
                              .and(ACLKeys.principalIdentifier)
                              .in(principals)),
            ACL.class, getCollectionName())
        .getDeletedCount();
  }

  @Override
  public List<String> getDistinctPermissionsInACLsForRoleAssignment(String roleAssignmentId) {
    Criteria criteria = Criteria.where(ACLKeys.roleAssignmentId).is(roleAssignmentId);
    Query query = new Query();
    query.addCriteria(criteria);
    return mongoTemplate.findDistinct(
        query, ACLKeys.permissionIdentifier, getCollectionName(), ACL.class, String.class);
  }

  @Override
  public List<String> getDistinctPrincipalsInACLsForRoleAssignment(String roleAssignmentId) {
    Criteria criteria = Criteria.where(ACLKeys.roleAssignmentId).is(roleAssignmentId);
    Query query = new Query();
    query.addCriteria(criteria);
    return mongoTemplate.findDistinct(query, ACLKeys.principalIdentifier, getCollectionName(), ACL.class, String.class);
  }

  @Override
  public List<ACL> getByAclQueryStringInAndEnabled(List<String> aclQueries, boolean enabled) {
    Query query = new Query(Criteria.where(ACLKeys.aclQueryString).in(aclQueries).and(ACLKeys.enabled).is(enabled));
    query.fields().include(ACLKeys.aclQueryString).include(ACLKeys.jexlCondition).include(ACLKeys.conditional);
    return mongoTemplate.find(query, ACL.class);
  }

  @Override
  public void cleanCollection() {
    mongoTemplate.dropCollection(getCollectionName());
    mongoTemplate.createCollection(getCollectionName());
    List<IndexModel> indexModels = ACL.mongoIndexes().stream().map(this::buildIndexModel).collect(Collectors.toList());
    mongoTemplate.getCollection(getCollectionName()).createIndexes(indexModels);
  }

  @Override
  public void renameCollection(@NotEmpty String newCollectionName) {
    MongoNamespace mongoNamespace = new MongoNamespace(mongoTemplate.getDb().getName(), newCollectionName);
    mongoTemplate.getCollection(getCollectionName())
        .renameCollection(mongoNamespace, new RenameCollectionOptions().dropTarget(true));
  }

  private IndexModel buildIndexModel(MongoIndex mongoIndex) {
    List<String> fields = mongoIndex.getFields();
    String name = mongoIndex.getName();
    boolean isUnique = mongoIndex.isUnique();
    boolean isSparse = mongoIndex.isSparse();
    Bson indexKeys = Indexes.ascending(fields);
    IndexOptions indexOptions = new IndexOptions().unique(isUnique).name(name).sparse(isSparse);
    return new IndexModel(indexKeys, indexOptions);
  }
}
