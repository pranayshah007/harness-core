/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.user.custom;

import static io.harness.NGCommonEntityConstants.MONGODB_ID;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.util.MongoDbErrorCodes.isDuplicateKeyCode;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.user.entities.UserMembership;
import io.harness.ng.core.user.entities.UserMembership.UserMembershipKeys;
import io.harness.ng.core.user.entities.UsersPerAccountCount;
import io.harness.ng.core.user.entities.UsersPerAccountCount.UsersPerAccountCountKeys;

import com.google.inject.Inject;
import com.mongodb.MongoBulkWriteException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;
import org.springframework.data.util.CloseableIterator;

@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor = @__({ @Inject }))
@OwnedBy(PL)
@Slf4j
public class UserMembershipRepositoryCustomImpl implements UserMembershipRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public UserMembership findOne(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.findOne(query, UserMembership.class);
  }

  @Override
  public Page<UserMembership> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<UserMembership> userMemberships = mongoTemplate.find(query, UserMembership.class);
    return PageableExecutionUtils.getPage(
        userMemberships, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), UserMembership.class));
  }

  @Override
  public List<UserMembership> findAllWithCriteria(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, UserMembership.class);
  }

  @Override
  public Page<String> findAllUserIds(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    query.fields().include(UserMembershipKeys.userId);
    List<UserMembership> userMemberships = mongoTemplate.find(query, UserMembership.class);
    List<String> userIds = userMemberships.stream().map(UserMembership::getUserId).collect(Collectors.toList());
    return PageableExecutionUtils.getPage(
        userIds, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), UserMembership.class));
  }

  @Override
  public UserMembership update(String userId, Update update) {
    Criteria criteria = Criteria.where(UserMembershipKeys.userId).is(userId);
    Query query = new Query(criteria);
    return mongoTemplate.findAndModify(query, update, new FindAndModifyOptions().returnNew(true), UserMembership.class);
  }

  public long insertAllIgnoringDuplicates(List<UserMembership> userMemberships) {
    try {
      if (isEmpty(userMemberships)) {
        return 0;
      }
      return mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, UserMembership.class, "userMembershipsV2")
          .insert(userMemberships)
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
  public CloseableIterator<UserMembership> stream(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.stream(query, UserMembership.class);
  }

  @Override
  public long count(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.count(query, UserMembership.class);
  }

  @Override
  public Map<String, Integer> getUserCountPerAccount(List<String> accountIdentifiers) {
    Criteria criteria = Criteria.where(UserMembershipKeys.ACCOUNT_IDENTIFIER_KEY).in(accountIdentifiers);
    MatchOperation matchStage = Aggregation.match(criteria);

    GroupOperation groupBy =
        group(UserMembershipKeys.ACCOUNT_IDENTIFIER_KEY).addToSet(UserMembershipKeys.userId).as("distinctUsers");

    ProjectionOperation projectionStage = project()
                                              .and(MONGODB_ID)
                                              .as(UsersPerAccountCountKeys.accountIdentifier)
                                              .andExpression("{ $size: '$distinctUsers' }")
                                              .as(UsersPerAccountCountKeys.userCount);

    Map<String, Integer> result = new HashMap<>();
    aggregate(newAggregation(matchStage, groupBy, projectionStage), UsersPerAccountCount.class)
        .getMappedResults()
        .forEach(usersPerAccountCount
            -> result.put(usersPerAccountCount.getAccountIdentifier(), usersPerAccountCount.getUserCount()));

    return result;
  }

  private <T> AggregationResults<T> aggregate(Aggregation aggregation, Class<T> classToFillResultIn) {
    return mongoTemplate.aggregate(aggregation, UserMembership.class, classToFillResultIn);
  }
}
