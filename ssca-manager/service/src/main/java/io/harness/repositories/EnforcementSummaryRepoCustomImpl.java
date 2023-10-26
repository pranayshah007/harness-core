/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.SSCA;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ssca.beans.EnforcementSummaryDBO;
import io.harness.ssca.entities.EnforcementSummaryEntity;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(SSCA)
@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor = @__({ @Inject }))
public class EnforcementSummaryRepoCustomImpl implements EnforcementSummaryRepoCustom {
  @Inject MongoTemplate mongoTemplate;

  @Override
  public List<EnforcementSummaryEntity> findAll(Aggregation aggregation) {
    return mongoTemplate.aggregate(aggregation, EnforcementSummaryEntity.class, EnforcementSummaryDBO.class)
        .getMappedResults()
        .stream()
        .map(EnforcementSummaryDBO::getDocument)
        .collect(Collectors.toList());
  }

  @Override
  public List<EnforcementSummaryEntity> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, EnforcementSummaryEntity.class);
  }
}
