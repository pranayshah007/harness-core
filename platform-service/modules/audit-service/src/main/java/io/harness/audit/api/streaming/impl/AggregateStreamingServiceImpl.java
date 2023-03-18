/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.api.streaming.impl;

import io.harness.audit.api.streaming.AggregateStreamingService;
import io.harness.audit.api.streaming.StreamingService;
import io.harness.audit.entities.streaming.StreamingDestination.StreamingDestinationKeys;
import io.harness.audit.entities.streaming.StreamingDestinationFilterProperties;
import io.harness.audit.mapper.streaming.StreamingDestinationMapper;
import io.harness.audit.remote.v1.api.streaming.StreamingDestinationsApiUtils;
import io.harness.audit.repositories.streaming.StreamingBatchRepository;
import io.harness.audit.repositories.streaming.StreamingDestinationRepository;
import io.harness.auditevent.streaming.beans.BatchFailureInfo;
import io.harness.auditevent.streaming.beans.BatchStatus;
import io.harness.auditevent.streaming.dto.StreamingBatchDTO;
import io.harness.auditevent.streaming.dto.StreamingBatchDTO.StreamingBatchDTOKeys;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.spec.server.audit.v1.model.Connector;
import io.harness.spec.server.audit.v1.model.FailureInfoCard;
import io.harness.spec.server.audit.v1.model.LastStreamedCard;
import io.harness.spec.server.audit.v1.model.StatusWiseCount;
import io.harness.spec.server.audit.v1.model.StreamingDestinationAggregateDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationCards;
import io.harness.spec.server.audit.v1.model.StreamingDetails;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import lombok.extern.log4j.Log4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@Log4j
public class AggregateStreamingServiceImpl implements AggregateStreamingService {
  private final StreamingDestinationRepository streamingDestinationRepository;
  private final StreamingBatchRepository streamingBatchRepository;
  private final StreamingService streamingService;
  private final ConnectorResourceClient connectorResourceClient;
  private final StreamingDestinationMapper streamingDestinationMapper;
  private final StreamingDestinationsApiUtils streamingDestinationsApiUtils;

  @Inject
  public AggregateStreamingServiceImpl(StreamingDestinationRepository streamingDestinationRepository,
      StreamingBatchRepository streamingBatchRepository, StreamingService streamingService,
      ConnectorResourceClient connectorResourceClient, StreamingDestinationMapper streamingDestinationMapper,
      StreamingDestinationsApiUtils streamingDestinationsApiUtils) {
    this.streamingDestinationRepository = streamingDestinationRepository;
    this.streamingBatchRepository = streamingBatchRepository;
    this.streamingService = streamingService;
    this.connectorResourceClient = connectorResourceClient;
    this.streamingDestinationMapper = streamingDestinationMapper;
    this.streamingDestinationsApiUtils = streamingDestinationsApiUtils;
  }

  @Override
  public StreamingDestinationCards getStreamingDestinationCards(String accountIdentifier) {
    StreamingDestinationCards streamingDestinationCards = new StreamingDestinationCards();

    List<StatusWiseCount> statusWiseCounts = getStatusWiseCounts(accountIdentifier);
    streamingDestinationCards.countByStatusCard(statusWiseCounts);

    Optional<StreamingBatchDTO> latestSuccessfulBatch = getLatestSuccessfulBatch(accountIdentifier);
    if (latestSuccessfulBatch.isPresent()) {
      LastStreamedCard lastStreamedCard = new LastStreamedCard();
      lastStreamedCard.lastStreamedAt(latestSuccessfulBatch.get().getLastStreamedAt());
      streamingDestinationCards.lastStreamedCard(lastStreamedCard);
    }

    long count = getFailedBatchCount(accountIdentifier);
    FailureInfoCard failureInfoCard = new FailureInfoCard();
    failureInfoCard.count(count);
    streamingDestinationCards.failureInfoCard(failureInfoCard);
    return streamingDestinationCards;
  }

  private List<StatusWiseCount> getStatusWiseCounts(String accountIdentifier) {
    return streamingDestinationRepository.countByStatus(
        Criteria.where(StreamingDestinationKeys.accountIdentifier).is(accountIdentifier));
  }

  private Optional<StreamingBatchDTO> getLatestSuccessfulBatch(String accountIdentifier) {
    Criteria criteria = Criteria.where(StreamingBatchDTOKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(StreamingBatchDTOKeys.status)
                            .is(BatchStatus.SUCCESS);
    Sort sort = Sort.by(Sort.Direction.DESC, StreamingBatchDTOKeys.createdAt);
    return Optional.ofNullable(streamingBatchRepository.findOne(criteria, sort));
  }

  private long getFailedBatchCount(String accountIdentifier) {
    Criteria criteria = Criteria.where(StreamingBatchDTOKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(StreamingBatchDTOKeys.status)
                            .is(BatchStatus.FAILED);
    return streamingBatchRepository.count(criteria);
  }

  private Optional<StreamingBatchDTO> getLatestBatch(String accountIdentifier, String streamingDestinationIdentifier) {
    Criteria criteria = Criteria.where(StreamingBatchDTOKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(StreamingBatchDTOKeys.streamingDestinationIdentifier)
                            .is(streamingDestinationIdentifier);
    Sort sort = Sort.by(Sort.Direction.DESC, StreamingBatchDTOKeys.createdAt);
    return Optional.ofNullable(streamingBatchRepository.findOne(criteria, sort));
  }

  private Optional<ConnectorDTO> getConnectorDTO(String connectorId, String accountIdentifier) {
    Optional<ConnectorDTO> connectorDTOOptional = Optional.empty();
    try {
      connectorDTOOptional =
          NGRestUtils.getResponse(connectorResourceClient.get(connectorId, accountIdentifier, null, null),
              "Could not get connector response for account: " + accountIdentifier + " after {} attempts.");
    } catch (Exception exception) {
      log.warn(String.format("Exception while fetching connector [%s]", connectorId), exception);
    }
    return connectorDTOOptional;
  }

  @Override
  public Page<StreamingDestinationAggregateDTO> getAggregatedList(
      String accountIdentifier, Pageable pageable, StreamingDestinationFilterProperties filterProperties) {
    return streamingService.list(accountIdentifier, pageable, filterProperties).map(streamingDestination -> {
      IdentifierRef connectorRef =
          IdentifierRefHelper.getIdentifierRef(streamingDestination.getConnectorRef(), accountIdentifier, null, null);
      Optional<ConnectorDTO> connectorDTO = getConnectorDTO(connectorRef.getIdentifier(), accountIdentifier);
      StreamingDestinationAggregateDTO streamingDestinationAggregateDTO = new StreamingDestinationAggregateDTO();
      streamingDestinationAggregateDTO.setStreamingDestination(streamingDestinationMapper.toDTO(streamingDestination));
      if (connectorDTO.isPresent()) {
        ConnectorInfoDTO connectorInfo = connectorDTO.get().getConnectorInfo();
        streamingDestinationAggregateDTO.setConnectorInfo(new Connector()
                                                              .name(connectorInfo.getName())
                                                              .identifier(connectorInfo.getIdentifier())
                                                              .description(connectorInfo.getDescription())
                                                              .tags(connectorInfo.getTags()));
      }

      Optional<StreamingBatchDTO> lastStreamedBatch =
          getLatestBatch(accountIdentifier, streamingDestination.getIdentifier());
      if (lastStreamedBatch.isPresent()) {
        StreamingBatchDTO streamedBatch = lastStreamedBatch.get();
        BatchFailureInfo batchFailureInfo = streamedBatch.getFailureInfo();
        streamingDestinationAggregateDTO.setStreamingDetails(
            new StreamingDetails()
                .lastStreamedAt(streamedBatch.getLastStreamedAt())
                .errorMessage(batchFailureInfo == null ? "" : batchFailureInfo.getMessage())
                .status(streamingDestinationsApiUtils.getStatusEnum(streamedBatch.getStatus())));
      }
      return streamingDestinationAggregateDTO;
    });
  }
}
