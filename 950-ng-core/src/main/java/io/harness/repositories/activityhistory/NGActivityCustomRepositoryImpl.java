/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.activityhistory;

import static io.harness.ng.core.activityhistory.NGActivityConstants.ACTIVITY_COLLECTION_NAME;

import io.harness.annotation.HarnessRepo;
import io.harness.ng.core.activityhistory.entity.NGActivity;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;

@HarnessRepo
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class NGActivityCustomRepositoryImpl implements NGActivityCustomRepository {
  private final MongoTemplate mongoTemplate;

  public Page<NGActivity> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<NGActivity> activities = mongoTemplate.find(query, NGActivity.class);
    return PageableExecutionUtils.getPage(
        activities, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), NGActivity.class));
  }

  @Override
  public <T> AggregationResults<T> aggregate(Aggregation aggregation, Class<T> classToFillResultIn) {
    return mongoTemplate.aggregate(aggregation, ACTIVITY_COLLECTION_NAME, classToFillResultIn);
  }

  @Override
  public List<String> findDistinctEntityTypes(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.findDistinct(
        query, NGActivity.ActivityHistoryEntityKeys.referredByEntityType, NGActivity.class, String.class);
  }
}
