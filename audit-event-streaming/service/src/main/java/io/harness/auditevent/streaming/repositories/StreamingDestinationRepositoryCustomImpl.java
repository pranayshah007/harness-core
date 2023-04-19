/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.repositories;

import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination.StreamingDestinationKeys;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class StreamingDestinationRepositoryCustomImpl implements StreamingDestinationRepositoryCustom {
  private final MongoTemplate template;

  @Autowired
  public StreamingDestinationRepositoryCustomImpl(MongoTemplate template) {
    this.template = template;
  }

  @Override
  public List<StreamingDestination> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    return template.find(query, StreamingDestination.class);
  }

  @Override
  public List<String> findDistinctAccounts(Criteria criteria) {
    Query query = new Query(criteria);
    return template.findDistinct(
        query, StreamingDestinationKeys.accountIdentifier, StreamingDestination.class, String.class);
  }
}
