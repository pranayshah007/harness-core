/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.services.impl;

import io.harness.NGResourceFilterConstants;
import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestinationFilterProperties;
import io.harness.auditevent.streaming.repositories.StreamingDestinationRepository;
import io.harness.auditevent.streaming.services.StreamingDestinationsService;

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

@Service
public class StreamingDestinationsServiceImpl implements StreamingDestinationsService {
  private final StreamingDestinationRepository streamingDestinationRepository;

  @Autowired
  public StreamingDestinationsServiceImpl(StreamingDestinationRepository streamingDestinationRepository) {
    this.streamingDestinationRepository = streamingDestinationRepository;
  }

  @Override
  public List<StreamingDestination> list(
      String accountIdentifier, StreamingDestinationFilterProperties filterProperties) {
    Criteria criteria = getCriteriaForStreamingDestinationList(accountIdentifier, filterProperties);
    return streamingDestinationRepository.findAll(criteria);
  }

  private Criteria getCriteriaForStreamingDestinationList(
      String accountIdentifier, StreamingDestinationFilterProperties filterProperties) {
    Criteria criteria =
        Criteria.where(StreamingDestination.StreamingDestinationKeys.accountIdentifier).is(accountIdentifier);
    if (null != filterProperties.getStatus()) {
      criteria.and(StreamingDestination.StreamingDestinationKeys.status).is(filterProperties.getStatus());
    }
    if (StringUtils.isNotEmpty(filterProperties.getSearchTerm())) {
      criteria.orOperator(
          Criteria.where(StreamingDestination.StreamingDestinationKeys.name)
              .regex(filterProperties.getSearchTerm(), NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          Criteria.where(StreamingDestination.StreamingDestinationKeys.identifier)
              .regex(filterProperties.getSearchTerm(), NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
    }
    return criteria;
  }
}
