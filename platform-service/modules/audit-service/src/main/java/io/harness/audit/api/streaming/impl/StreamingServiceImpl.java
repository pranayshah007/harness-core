/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.api.streaming.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.exception.WingsException.USER;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.api.streaming.StreamingService;
import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination.StreamingDestinationKeys;
import io.harness.audit.entities.streaming.StreamingDestinationFilterProperties;
import io.harness.audit.events.StreamingDestinationCreateEvent;
import io.harness.audit.events.StreamingDestinationDeleteEvent;
import io.harness.audit.events.StreamingDestinationUpdateEvent;
import io.harness.audit.mapper.streaming.StreamingDestinationMapper;
import io.harness.audit.repositories.streaming.StreamingDestinationRepository;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NoResultFoundException;
import io.harness.outbox.api.OutboxService;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO.StatusEnum;

import com.google.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
@Slf4j
public class StreamingServiceImpl implements StreamingService {
  private final StreamingDestinationMapper streamingDestinationMapper;
  private final StreamingDestinationRepository streamingDestinationRepository;
  OutboxService outboxService;
  TransactionTemplate transactionTemplate;

  @Inject
  public StreamingServiceImpl(StreamingDestinationMapper streamingDestinationMapper,
      StreamingDestinationRepository streamingDestinationRepository, OutboxService outboxService,
      TransactionTemplate transactionTemplate) {
    this.streamingDestinationMapper = streamingDestinationMapper;
    this.streamingDestinationRepository = streamingDestinationRepository;
    this.outboxService = outboxService;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public StreamingDestination create(String accountIdentifier, @Valid StreamingDestinationDTO streamingDestinationDTO) {
    StreamingDestination streamingDestination =
        streamingDestinationMapper.toStreamingDestinationEntity(accountIdentifier, streamingDestinationDTO);
    try {
      return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        StreamingDestination savedStreamingDestination = streamingDestinationRepository.save(streamingDestination);
        outboxService.save(new StreamingDestinationCreateEvent(savedStreamingDestination.getAccountIdentifier(),
            streamingDestinationMapper.toDTO(savedStreamingDestination)));
        return savedStreamingDestination;
      }));
    } catch (DuplicateKeyException exception) {
      String message = String.format(
          "Streaming destination with identifier [%s] already exists.", streamingDestinationDTO.getIdentifier());
      log.error(message, exception);
      throw new DuplicateFieldException(message);
    }
  }

  @Override
  public Page<StreamingDestination> list(
      String accountIdentifier, Pageable pageable, StreamingDestinationFilterProperties filterProperties) {
    Criteria criteria = getCriteriaForStreamingDestinationList(accountIdentifier, filterProperties);

    return streamingDestinationRepository.findAll(criteria, pageable);
  }

  @Override
  public StreamingDestination getStreamingDestination(String accountIdentifier, String identifier) {
    Optional<StreamingDestination> optionalStreamingDestination =
        streamingDestinationRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);

    if (optionalStreamingDestination.isEmpty()) {
      String message = String.format("Streaming destination with identifier [%s] not found.", identifier);
      log.error(message);
      throw NoResultFoundException.newBuilder()
          .code(ErrorCode.RESOURCE_NOT_FOUND)
          .message(message)
          .level(Level.ERROR)
          .reportTargets(USER)
          .build();
    }

    return optionalStreamingDestination.get();
  }

  @Override
  public boolean delete(String accountIdentifier, String identifier) {
    StreamingDestination streamingDestination = getStreamingDestination(accountIdentifier, identifier);
    if (streamingDestination.getStatus().equals(StatusEnum.ACTIVE)) {
      String message = String.format(
          "Streaming destination with identifier [%s] cannot be deleted because it is active.", identifier);
      log.error(message);
      throw new InvalidRequestException(message);
    }

    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      streamingDestinationRepository.deleteByCriteria(
          getCriteriaForStreamingDestination(accountIdentifier, identifier));
      outboxService.save(new StreamingDestinationDeleteEvent(
          streamingDestination.getAccountIdentifier(), streamingDestinationMapper.toDTO(streamingDestination)));
      return true;
    }));
  }

  @Override
  public StreamingDestination update(String streamingDestinationIdentifier,
      StreamingDestinationDTO streamingDestinationDTO, String accountIdentifier) {
    StreamingDestination currentStreamingDestination =
        getStreamingDestination(accountIdentifier, streamingDestinationIdentifier);
    validateUpdateRequest(streamingDestinationIdentifier, streamingDestinationDTO, currentStreamingDestination);

    return updateAndReturnStreamingDestination(streamingDestinationDTO, currentStreamingDestination, accountIdentifier);
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

  private Criteria getCriteriaForStreamingDestination(String accountIdentifier, String identifier) {
    return Criteria.where(StreamingDestinationKeys.accountIdentifier)
        .is(accountIdentifier)
        .and(StreamingDestinationKeys.identifier)
        .is(identifier);
  }

  private StreamingDestination updateAndReturnStreamingDestination(StreamingDestinationDTO newStreamingDestinationDTO,
      StreamingDestination currentStreamingDestination, String accountIdentifier) {
    StreamingDestination newStreamingDestination =
        streamingDestinationMapper.toStreamingDestinationEntity(accountIdentifier, newStreamingDestinationDTO);
    newStreamingDestination.setId(currentStreamingDestination.getId());
    newStreamingDestination.setCreatedAt(currentStreamingDestination.getCreatedAt());
    if (!currentStreamingDestination.getStatus().equals(newStreamingDestination.getStatus())) {
      newStreamingDestination.setLastStatusChangedAt(System.currentTimeMillis());
    } else {
      newStreamingDestination.setLastStatusChangedAt(currentStreamingDestination.getLastStatusChangedAt());
    }

    try {
      return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        StreamingDestination savedStreamingDestination = streamingDestinationRepository.save(newStreamingDestination);
        outboxService.save(new StreamingDestinationUpdateEvent(newStreamingDestination.getAccountIdentifier(),
            streamingDestinationMapper.toDTO(savedStreamingDestination),
            streamingDestinationMapper.toDTO(currentStreamingDestination)));
        return savedStreamingDestination;
      }));
    } catch (DuplicateKeyException exception) {
      String message = String.format(
          "Streaming destination with identifier [%s] already exists.", currentStreamingDestination.getIdentifier());
      log.error(message, exception);
      throw new DuplicateFieldException(message);
    }
  }

  private void validateUpdateRequest(String streamingDestinationIdentifier,
      StreamingDestinationDTO streamingDestinationDTO, StreamingDestination currentStreamingDestination) {
    checkEqualityOrThrow(streamingDestinationIdentifier, streamingDestinationDTO.getIdentifier(), "identifier");
    checkEqualityOrThrow(
        currentStreamingDestination.getConnectorRef(), streamingDestinationDTO.getConnectorRef(), "connectorRef");
    checkEqualityOrThrow(
        currentStreamingDestination.getType().name(), streamingDestinationDTO.getSpec().getType().name(), "type");
  }

  private void checkEqualityOrThrow(Object str1, Object str2, Object str3) {
    if (!Objects.equals(str1, str2)) {
      String message =
          String.format("Streaming destination with %s [%s] did not match with StreamingDestinationDTO %s [%s]", str3,
              str1, str3, str2);
      log.error(message);
      throw new InvalidRequestException(message);
    }
  }
}
