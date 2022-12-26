/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming;

import io.harness.audit.entities.AuditEvent;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class AuditEventRepositoryImpl implements AuditEventRepository {
  private final MongoTemplate mongoTemplate;
  private final BatchConfig batchConfig;

  public AuditEventRepositoryImpl(MongoTemplate mongoTemplate, BatchConfig batchConfig) {
    this.mongoTemplate = mongoTemplate;
    this.batchConfig = batchConfig;
  }

  @Override
  public List<AuditEvent> loadAuditEvents(Criteria criteria, Sort sort) {
    Query query =
        new Query(criteria).with(sort).cursorBatchSize(batchConfig.getCursorBatchSize()).limit(batchConfig.getLimit());
    return mongoTemplate.find(query, AuditEvent.class);
  }

  @Override
  public long countAuditEvents(Criteria criteria) {
    return mongoTemplate.count(new Query(criteria), AuditEvent.class);
  }
}
