/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.repositories;

import static io.harness.NGCommonEntityConstants.MONGODB_ID;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.mongo.MongoConfig.NO_LIMIT;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.entities.AuditEvent;
import io.harness.audit.entities.AuditEvent.AuditEventKeys;
import io.harness.audit.entities.DistinctProjectsPerAccount;
import io.harness.audit.entities.DistinctProjectsPerAccount.DistinctProjectsPerAccountKeys;
import io.harness.mongo.helper.AnalyticsMongoTemplateHolder;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;

@OwnedBy(PL)
public class AuditRepositoryCustomImpl implements AuditRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  private final MongoTemplate secondaryMongoTemplate;
  private final MongoTemplate analyticsMongoTemplate;

  @Inject
  public AuditRepositoryCustomImpl(MongoTemplate mongoTemplate,
      SecondaryMongoTemplateHolder secondaryMongoTemplateHolder,
      AnalyticsMongoTemplateHolder analyticsMongoTemplateHolder) {
    this.mongoTemplate = mongoTemplate;
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
    this.analyticsMongoTemplate = analyticsMongoTemplateHolder.getAnalyticsMongoTemplate();
  }

  @Override
  public Page<AuditEvent> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<AuditEvent> auditEvents = secondaryMongoTemplate.find(query, AuditEvent.class);
    return PageableExecutionUtils.getPage(auditEvents, pageable,
        () -> secondaryMongoTemplate.count(Query.of(query).limit(-1).skip(-1), AuditEvent.class));
  }

  @Override
  public void delete(Criteria criteria) {
    Query query = new Query(criteria);
    mongoTemplate.remove(query, AuditEvent.class);
  }

  @Override
  public List<String> fetchDistinctAccountIdentifiers() {
    Query query = new Query().limit(NO_LIMIT);
    return secondaryMongoTemplate.findDistinct(
        query, AuditEventKeys.ACCOUNT_IDENTIFIER_KEY, AuditEvent.class, String.class);
  }

  @Override
  public AuditEvent get(Criteria criteria) {
    Query query = new Query(criteria);
    return secondaryMongoTemplate.findOne(query, AuditEvent.class);
  }

  @Override
  public Map<String, Integer> getUniqueProjectCountPerAccountId(List<String> accountIds, long startTime, long endTime) {
    List<Criteria> criteriaList = new ArrayList<>();

    criteriaList.add(Criteria.where(AuditEventKeys.ACCOUNT_IDENTIFIER_KEY).in(accountIds));
    criteriaList.add(Criteria.where(AuditEventKeys.timestamp).gte(Instant.ofEpochMilli(startTime)));
    criteriaList.add(Criteria.where(AuditEventKeys.timestamp).lte(Instant.ofEpochMilli(endTime)));

    MatchOperation matchStage = Aggregation.match(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));

    GroupOperation groupBy = group(AuditEventKeys.ACCOUNT_IDENTIFIER_KEY)
                                 .addToSet(AuditEventKeys.PROJECT_IDENTIFIER_KEY)
                                 .as("distinctProjects");

    ProjectionOperation projectionStage = project()
                                              .and(MONGODB_ID)
                                              .as(DistinctProjectsPerAccountKeys.accountIdentifier)
                                              .andExpression("{ $size: '$distinctProjects' }")
                                              .as(DistinctProjectsPerAccountKeys.distinctProjectCount);

    Map<String, Integer> result = new HashMap<>();

    aggregate(newAggregation(matchStage, groupBy, projectionStage), DistinctProjectsPerAccount.class)
        .getMappedResults()
        .forEach(uniqueProjectCountPerAccountId
            -> result.put(uniqueProjectCountPerAccountId.getAccountIdentifier(),
                uniqueProjectCountPerAccountId.getDistinctProjectCount()));

    return result;
  }

  public <T> AggregationResults<T> aggregate(Aggregation aggregation, Class<T> classToFillResultIn) {
    return analyticsMongoTemplate.aggregate(aggregation, AuditEvent.class, classToFillResultIn);
  }
}
