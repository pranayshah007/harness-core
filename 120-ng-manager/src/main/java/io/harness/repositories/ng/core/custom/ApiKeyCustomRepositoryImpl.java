/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.ng.core.custom;

import static io.harness.NGCommonEntityConstants.MONGODB_ID;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.beans.CountByKeyAggregationResult;
import io.harness.ng.core.beans.CountByKeyAggregationResult.CountByKeyAggregationResultKeys;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.entities.ApiKey;
import io.harness.ng.core.entities.ApiKey.ApiKeyKeys;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;

@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor = @__({ @Inject }))
public class ApiKeyCustomRepositoryImpl implements ApiKeyCustomRepository {
  private final MongoTemplate mongoTemplate;

  @Override
  public Page<ApiKey> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<ApiKey> apiKeys = mongoTemplate.find(query, ApiKey.class);
    return PageableExecutionUtils.getPage(
        apiKeys, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), ApiKey.class));
  }

  @Override
  public <T> AggregationResults<T> aggregate(Aggregation aggregation, Class<T> classToFillResultIn) {
    return mongoTemplate.aggregate(aggregation, ApiKey.class, classToFillResultIn);
  }

  @Override
  public Map<String, Integer> getApiKeysPerParentIdentifier(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ApiKeyType apiKeyType, List<String> parentIdentifiers) {
    Criteria criteria = Criteria.where(ApiKeyKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(ApiKeyKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(ApiKeyKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(ApiKeyKeys.apiKeyType)
                            .is(apiKeyType.name());
    if (isNotEmpty(parentIdentifiers)) {
      criteria.and(ApiKeyKeys.parentIdentifier).in(parentIdentifiers);
    }
    MatchOperation matchStage = Aggregation.match(criteria);
    GroupOperation groupByParentStage =
        group(ApiKeyKeys.parentIdentifier).count().as(CountByKeyAggregationResultKeys.count);
    ProjectionOperation projectionStage = project()
                                              .and(MONGODB_ID)
                                              .as(CountByKeyAggregationResultKeys.key)
                                              .andInclude(CountByKeyAggregationResultKeys.count);
    Map<String, Integer> result = new HashMap<>();
    aggregate(newAggregation(matchStage, groupByParentStage, projectionStage), CountByKeyAggregationResult.class)
        .getMappedResults()
        .forEach(countByKeyAggregationResult
            -> result.put(countByKeyAggregationResult.getKey(), countByKeyAggregationResult.getCount()));
    return result;
  }

  @Override
  public DeleteResult deleteAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.remove(query, ApiKey.class);
  }
}
