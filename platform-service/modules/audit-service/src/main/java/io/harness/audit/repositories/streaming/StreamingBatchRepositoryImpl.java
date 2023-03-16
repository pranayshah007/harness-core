/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.repositories.streaming;

import io.harness.auditevent.streaming.dto.StreamingBatchDTO;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class StreamingBatchRepositoryImpl implements StreamingBatchRepository {
  public static final String STREAMING_BATCHES_COLLECTION = "streamingBatches";
  private final MongoTemplate template;

  @Inject
  public StreamingBatchRepositoryImpl(MongoTemplate template) {
    this.template = template;
  }

  @Override
  public StreamingBatchDTO findOne(Criteria criteria, Sort sort) {
    final List<StreamingBatchDTO> streamingBatchDTOList = new ArrayList<>();
    Query query = new Query(criteria).with(sort).limit(1);
    template.executeQuery(query, STREAMING_BATCHES_COLLECTION,
        document -> streamingBatchDTOList.add(template.getConverter().read(StreamingBatchDTO.class, document)));
    return !streamingBatchDTOList.isEmpty() ? streamingBatchDTOList.get(0) : null;
  }

  @Override
  public Long count(Criteria criteria) {
    Query query = new Query(criteria);
    return template.count(query, STREAMING_BATCHES_COLLECTION);
  }
}
