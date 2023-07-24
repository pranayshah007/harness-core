/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;
import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitsync.common.beans.EntityWithCount;
import io.harness.gitsync.common.beans.EntityWithCount.EntityWithCountKeys;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
import org.springframework.data.mongodb.core.aggregation.CountOperation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.repository.support.PageableExecutionUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@UtilityClass
@OwnedBy(DX)
public class EntityDistinctElementHelper {
  public <T> Page<T> getDistinctElementPage(
      MongoTemplate mongoTemplate, Criteria criteria, Pageable pageable, String distinctKey, Class<T> entityClass) {
    return getDistinctElementPage(mongoTemplate, criteria, pageable, entityClass, distinctKey);
  }

  public <T> Page<T> getDistinctElementPage(
      MongoTemplate mongoTemplate, Criteria criteria, Pageable pageable, Class<T> entityClass, String... distinctKeys) {
    final MatchOperation matchStage = Aggregation.match(criteria);
    final GroupOperation groupOperation =
        Aggregation.group(distinctKeys).first(Aggregation.ROOT).as(EntityWithCountKeys.object);
    final GroupOperation distinctGroupStage = Aggregation.group(distinctKeys);
    final CountOperation totalStage = Aggregation.count().as(EntityWithCountKeys.total);

    // Create a Collation object with the desired collation options
    Collation collation = Collation.of(Locale.ENGLISH).strength(2);

    // Create AggregationOptions and set the collation
    AggregationOptions aggregationOptions = AggregationOptions.builder().collation(collation).build();
    final Aggregation aggregationForEntity =
        Aggregation
            .newAggregation(matchStage, groupOperation,
                Aggregation.skip(pageable.getPageNumber() * pageable.getPageSize()),
                Aggregation.limit(pageable.getPageSize()))
            .withOptions(aggregationOptions);

    final Aggregation aggregationForCount = Aggregation.newAggregation(matchStage, distinctGroupStage, totalStage);
    final String collectionName = mongoTemplate.getCollectionName(entityClass);

    final List<EntityWithCount> mappedResults =
        mongoTemplate.aggregate(aggregationForEntity, collectionName, EntityWithCount.class).getMappedResults();

    return (Page<T>) PageableExecutionUtils.getPage(
        mappedResults.stream().map(EntityWithCount::getObject).collect(Collectors.toList()), pageable,
        ()
            -> mongoTemplate.aggregate(aggregationForCount, collectionName, EntityWithCount.class)
                   .getMappedResults()
                   .get(0)
                   .getTotal());
  }
}
