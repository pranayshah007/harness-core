/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.repositories;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.entities.AuditEvent;
import io.harness.audit.entities.AuditEvent.AuditEventKeys;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;

import com.google.inject.Inject;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;

@OwnedBy(PL)
public class AuditRepositoryCustomImpl implements AuditRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  private final MongoTemplate secondaryMongoTemplate;

  @Inject
  public AuditRepositoryCustomImpl(
      MongoTemplate mongoTemplate, SecondaryMongoTemplateHolder secondaryMongoTemplateHolder) {
    this.mongoTemplate = mongoTemplate;
    this.secondaryMongoTemplate = secondaryMongoTemplateHolder.getSecondaryMongoTemplate();
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
    Query query = new Query();
    return secondaryMongoTemplate.findDistinct(
        query, AuditEventKeys.ACCOUNT_IDENTIFIER_KEY, AuditEvent.class, String.class);
  }

  @Override
  public AuditEvent get(Criteria criteria) {
    Query query = new Query(criteria);
    return secondaryMongoTemplate.findOne(query, AuditEvent.class);
  }
}
