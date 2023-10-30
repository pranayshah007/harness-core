/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.SSCA;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ssca.entities.CdInstanceSummary;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;

@OwnedBy(SSCA)
@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor = @__({ @Inject }))
public class CdInstanceSummaryRepoCustomImpl implements CdInstanceSummaryRepoCustom {
  @Inject MongoTemplate mongoTemplate;

  @Override
  public CdInstanceSummary findOne(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.findOne(query, CdInstanceSummary.class);
  }

  @Override
  public List<CdInstanceSummary> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, CdInstanceSummary.class);
  }

  @Override
  public Page<CdInstanceSummary> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<CdInstanceSummary> cdInstanceSummaries = mongoTemplate.find(query, CdInstanceSummary.class);
    return PageableExecutionUtils.getPage(cdInstanceSummaries, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), CdInstanceSummary.class));
  }
}
