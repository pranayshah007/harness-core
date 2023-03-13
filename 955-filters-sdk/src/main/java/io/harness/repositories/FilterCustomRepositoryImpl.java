/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.filter.entity.Filter;

import com.mongodb.client.result.DeleteResult;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;

@HarnessRepo
@OwnedBy(DX)
public class FilterCustomRepositoryImpl implements FilterCustomRepository {
  private final MongoTemplate mongoTemplate;

  @Autowired
  public FilterCustomRepositoryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Page<Filter> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    query.collation(Collation.of("en").strength(Collation.ComparisonLevel.secondary()));
    List<Filter> filters = mongoTemplate.find(query, Filter.class);
    return PageableExecutionUtils.getPage(
        filters, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Filter.class));
  }
  @Override
  public DeleteResult deleteAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.remove(query, Filter.class);
  }
}
