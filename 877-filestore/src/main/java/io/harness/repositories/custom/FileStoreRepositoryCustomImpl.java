/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.custom;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filestore.entities.NGFile;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;

@OwnedBy(CDP)
@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor = @__({ @Inject }))
public class FileStoreRepositoryCustomImpl implements FileStoreRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public <T> AggregationResults<T> aggregate(Aggregation aggregation, Class<T> classToFillResultIn) {
    return mongoTemplate.aggregate(aggregation, NGFile.class, classToFillResultIn);
  }

  @Override
  public List<NGFile> findAllAndSort(Criteria criteria, Sort sortBy) {
    Query query = new Query(criteria).with(sortBy);
    return mongoTemplate.find(query, NGFile.class);
  }

  @Override
  public Page<NGFile> findAllAndSort(Criteria criteria, Sort sortBy, Pageable pageable) {
    Query query = new Query(criteria).with(sortBy).with(pageable);
    List<NGFile> ngFiles = mongoTemplate.find(query, NGFile.class);
    return PageableExecutionUtils.getPage(
        ngFiles, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), NGFile.class));
  }

  @Override
  public Page<NGFile> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<NGFile> organizations = mongoTemplate.find(query, NGFile.class);
    return PageableExecutionUtils.getPage(
        organizations, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), NGFile.class));
  }
}
