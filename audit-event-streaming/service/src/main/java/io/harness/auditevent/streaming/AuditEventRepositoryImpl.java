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
import java.util.Collections;
import java.util.List;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditEventRepositoryImpl implements AuditEventRepository {
  @Autowired private MongoTemplate mongoTemplate;
  @Override
  public List<AuditEvent> loadAuditEvents() {
    FindIterable<Document> auditEvents = mongoTemplate.getCollection("auditEvents").find().batchSize(1000);
    MongoCursor<Document> cursor = auditEvents.cursor();
    return Collections.EMPTY_LIST;
  }
}
