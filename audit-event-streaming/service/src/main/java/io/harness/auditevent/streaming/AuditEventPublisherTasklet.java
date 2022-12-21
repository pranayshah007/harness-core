/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class AuditEventPublisherTasklet implements Tasklet {
  @Autowired private MongoTemplate mongoTemplate;
  @Override
  public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) throws Exception {
    FindIterable<Document> auditEvents = mongoTemplate.getCollection("auditEvents").find().batchSize(1000);
    MongoCursor<Document> cursor = auditEvents.cursor();
    while (cursor.hasNext()) {
      log.info(cursor.next().toJson());
    }
    return null;
  }
}
