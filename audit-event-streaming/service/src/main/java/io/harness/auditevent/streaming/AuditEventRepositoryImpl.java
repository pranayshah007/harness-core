/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming;

import io.harness.audit.entities.AuditEvent;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class AuditEventRepositoryImpl implements AuditEventRepository {
  private final MongoTemplate mongoTemplate;
  private final BatchConfig batchConfig;

  @Autowired
  public AuditEventRepositoryImpl(MongoTemplate mongoTemplate, BatchConfig batchConfig) {
    this.mongoTemplate = mongoTemplate;
    this.batchConfig = batchConfig;
  }

  @Override
  public MongoCursor<Document> loadAuditEvents(Criteria criteria, Bson sort) {
    FindIterable<Document> auditEvents = mongoTemplate.getCollection(mongoTemplate.getCollectionName(AuditEvent.class))
                                             .find(criteria.getCriteriaObject())
                                             .batchSize(batchConfig.getCursorBatchSize())
                                             .sort(sort);
    return auditEvents.cursor();
  }

  @Override
  public long countAuditEvents(Criteria criteria) {
    return mongoTemplate.count(new Query(criteria), AuditEvent.class);
  }
}
