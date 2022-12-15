/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.api.streaming.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.api.streaming.StreamingService;
import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination.StreamingDestinationKeys;
import io.harness.audit.entities.streaming.StreamingDestinationFilterProperties;
import io.harness.audit.mapper.streaming.StreamingDestinationMapper;
import io.harness.audit.repositories.streaming.StreamingDestinationRepository;
import io.harness.exception.DuplicateFieldException;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO;

import com.google.inject.Inject;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(PL)
@Slf4j
public class StreamingServiceImpl implements StreamingService {
  private final StreamingDestinationMapper streamingDestinationMapper;
  private final StreamingDestinationRepository streamingDestinationRepository;

  @Inject
  public StreamingServiceImpl(StreamingDestinationMapper streamingDestinationMapper,
      StreamingDestinationRepository streamingDestinationRepository) {
    this.streamingDestinationMapper = streamingDestinationMapper;
    this.streamingDestinationRepository = streamingDestinationRepository;
  }

  @Override
  public StreamingDestination create(String accountIdentifier, @Valid StreamingDestinationDTO streamingDestinationDTO) {
    StreamingDestination streamingDestination =
        streamingDestinationMapper.toStreamingDestinationEntity(accountIdentifier, streamingDestinationDTO);
    try {
      return streamingDestinationRepository.save(streamingDestination);
    } catch (DuplicateKeyException exception) {
      String message = String.format(
          "Streaming destination with identifier [%s] already exists.", streamingDestinationDTO.getSlug());
      log.warn(message, exception);
      throw new DuplicateFieldException(message);
    }
  }

  @Override
  public Page<StreamingDestination> list(
      String accountIdentifier, Pageable pageable, StreamingDestinationFilterProperties filterProperties) {
    Criteria criteria = getCriteriaForStreamingDestinationList(accountIdentifier, filterProperties);

    return streamingDestinationRepository.findAll(criteria, pageable);
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
}
