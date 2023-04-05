/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.user.custom;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.stream.Collectors.toList;
import static org.springframework.data.mongodb.util.MongoDbErrorCodes.isDuplicateKeyCode;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.user.entities.UserMetadata;
import io.harness.ng.core.user.entities.UserMetadata.UserMetadataKeys;

import com.google.inject.Inject;
import com.mongodb.MongoBulkWriteException;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;
import org.springframework.data.util.CloseableIterator;

@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class UserMetadataRepositoryCustomImpl implements UserMetadataRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public Page<UserMetadata> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    query.collation(Collation.of("en").strength(Collation.ComparisonLevel.secondary()));
    List<UserMetadata> users = mongoTemplate.find(query, UserMetadata.class);
    return PageableExecutionUtils.getPage(
        users, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), UserMetadata.class));
  }

  @Override
  public List<UserMetadata> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, UserMetadata.class);
  }

  @Override
  public List<String> findAllIds(Criteria criteria) {
    Query query = new Query(criteria);
    query.fields().include(UserMetadataKeys.userId);
    return mongoTemplate.find(query, UserMetadata.class).stream().map(UserMetadata::getUserId).collect(toList());
  }

  @Override
  public UserMetadata updateFirst(String userId, Update update) {
    Query query = new Query(Criteria.where(UserMetadataKeys.userId).is(userId));
    return mongoTemplate.findAndModify(query, update, UserMetadata.class);
  }

  public long insertAllIgnoringDuplicates(List<UserMetadata> userMetadata) {
    try {
      if (isEmpty(userMetadata)) {
        return 0;
      }
      return mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, UserMetadata.class, "userMetadata")
          .insert(userMetadata)
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
  public CloseableIterator<UserMetadata> stream(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.stream(query, UserMetadata.class);
  }
}
