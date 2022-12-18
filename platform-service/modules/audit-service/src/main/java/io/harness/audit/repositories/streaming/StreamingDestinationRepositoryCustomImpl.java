/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.repositories.streaming;

import io.harness.audit.entities.streaming.StreamingDestination;

import com.google.inject.Inject;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;

public class StreamingDestinationRepositoryCustomImpl implements StreamingDestinationRepositoryCustom {
  private final MongoTemplate template;

  @Inject
  public StreamingDestinationRepositoryCustomImpl(MongoTemplate template) {
    this.template = template;
  }

  @Override
  public Page<StreamingDestination> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<StreamingDestination> streamingDestinations = template.find(query, StreamingDestination.class);
    return PageableExecutionUtils.getPage(streamingDestinations, pageable,
        () -> template.count(Query.of(query).limit(-1).skip(-1), StreamingDestination.class));
  }
}
