/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.ipallowlist.custom;

import io.harness.ipallowlist.entity.IPAllowlistEntity;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;

@Slf4j
public class IPAllowlistRepositoryCustomImpl implements IPAllowlistRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Inject
  public IPAllowlistRepositoryCustomImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Page<IPAllowlistEntity> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<IPAllowlistEntity> ipAllowlistEntities = mongoTemplate.find(query, IPAllowlistEntity.class);
    return PageableExecutionUtils.getPage(ipAllowlistEntities, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), IPAllowlistEntity.class));
  }
}
