/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.repositories;

import io.harness.auditevent.streaming.entities.StreamingBatch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class StreamingBatchRepositoryCustomImpl implements StreamingBatchRepositoryCustom {
  private final MongoTemplate template;

  @Autowired
  public StreamingBatchRepositoryCustomImpl(MongoTemplate template) {
    this.template = template;
  }

  @Override
  public StreamingBatch findOne(Criteria criteria, Sort sort) {
    Query query = new Query(criteria).with(sort);
    return template.findOne(query, StreamingBatch.class);
  }
}
