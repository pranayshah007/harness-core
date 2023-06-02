/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.services.impl;

import static io.harness.audit.entities.streaming.StreamingDestination.StreamingDestinationKeys;
import static io.harness.spec.server.audit.v1.model.StreamingDestinationStatus.ACTIVE;
import static io.harness.spec.server.audit.v1.model.StreamingDestinationStatus.INACTIVE;

import io.harness.NGResourceFilterConstants;
import io.harness.audit.client.remote.streaming.StreamingDestinationClient;
import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestinationFilterProperties;
import io.harness.auditevent.streaming.mappers.StreamingDestinationMapper;
import io.harness.auditevent.streaming.repositories.StreamingDestinationRepository;
import io.harness.auditevent.streaming.services.StreamingDestinationService;
import io.harness.exception.UnexpectedException;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO;

import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StreamingDestinationServiceImpl implements StreamingDestinationService {
  private final StreamingDestinationRepository streamingDestinationRepository;
  private final StreamingDestinationMapper streamingDestinationMapper;
  private final StreamingDestinationClient streamingDestinationClient;

  public StreamingDestinationServiceImpl(StreamingDestinationRepository streamingDestinationRepository,
      StreamingDestinationMapper streamingDestinationMapper, StreamingDestinationClient streamingDestinationClient) {
    this.streamingDestinationRepository = streamingDestinationRepository;
    this.streamingDestinationMapper = streamingDestinationMapper;
    this.streamingDestinationClient = streamingDestinationClient;
  }

  @Override
  public List<StreamingDestination> list(
      String accountIdentifier, StreamingDestinationFilterProperties filterProperties) {
    Criteria criteria = getCriteriaForStreamingDestinationList(accountIdentifier, filterProperties);
    return streamingDestinationRepository.findAll(criteria);
  }

  @Override
  public List<String> distinctAccounts() {
    Criteria criteria = Criteria.where(StreamingDestinationKeys.status).is(ACTIVE);
    return streamingDestinationRepository.findDistinctAccounts(criteria);
  }

  private Criteria getCriteriaForStreamingDestinationList(
      String accountIdentifier, StreamingDestinationFilterProperties filterProperties) {
    Criteria criteria = Criteria.where(StreamingDestinationKeys.accountIdentifier).is(accountIdentifier);
    if (null != filterProperties.getStatus()) {
      criteria.and(StreamingDestinationKeys.status).is(filterProperties.getStatus());
    }
    if (StringUtils.isNotEmpty(filterProperties.getSearchTerm())) {
      criteria.orOperator(
          Criteria.where(StreamingDestinationKeys.name)
              .regex(filterProperties.getSearchTerm(), NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          Criteria.where(StreamingDestinationKeys.identifier)
              .regex(filterProperties.getSearchTerm(), NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
    }
    return criteria;
  }

  public void disableStreamingDestination(StreamingDestination streamingDestination) {
    String identifier = streamingDestination.getIdentifier();
    String accountIdentifier = streamingDestination.getAccountIdentifier();
    try {
      StreamingDestinationDTO streamingDestinationDTO =
          streamingDestinationMapper.toStreamingDestinationDTO(streamingDestination);
      streamingDestinationDTO.setStatus(INACTIVE);
      streamingDestinationClient.updateStreamingDestination(identifier, streamingDestinationDTO, accountIdentifier)
          .execute();
    } catch (IOException exception) {
      String errorMessage =
          String.format("Error disabling streaming destination. [streamingDestination = %s] [accountIdentifier = %s]",
              identifier, accountIdentifier);
      log.error(errorMessage, exception);
      throw new UnexpectedException(errorMessage, exception);
    }
  }
}
