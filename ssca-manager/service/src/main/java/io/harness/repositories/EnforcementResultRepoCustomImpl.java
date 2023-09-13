/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ssca.entities.EnforcementResultEntity;

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

@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor = @__({ @Inject }))
public class EnforcementResultRepoCustomImpl implements EnforcementResultRepoCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public Page<EnforcementResultEntity> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<EnforcementResultEntity> enforcementResultEntities = mongoTemplate.find(query, EnforcementResultEntity.class);
    return PageableExecutionUtils.getPage(enforcementResultEntities, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), EnforcementResultEntity.class));
  }
}
