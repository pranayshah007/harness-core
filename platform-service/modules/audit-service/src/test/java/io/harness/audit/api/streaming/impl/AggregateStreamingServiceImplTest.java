/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.api.streaming.impl;

import static io.harness.audit.remote.v1.api.streaming.StreamingDestinationsApiUtilsTest.RANDOM_STRING_CHAR_COUNT_10;
import static io.harness.beans.SortOrder.Builder.aSortOrder;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.REETIKA;
import static io.harness.spec.server.audit.v1.model.StreamingDestinationStatus.ACTIVE;
import static io.harness.spec.server.audit.v1.model.StreamingDestinationStatus.INACTIVE;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.data.domain.Sort.Direction.DESC;

import io.harness.CategoryTest;
import io.harness.audit.api.streaming.StreamingService;
import io.harness.audit.entities.streaming.AwsS3StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination.StreamingDestinationKeys;
import io.harness.audit.entities.streaming.StreamingDestinationFilterProperties;
import io.harness.audit.mapper.streaming.StreamingDestinationMapper;
import io.harness.audit.remote.v1.api.streaming.StreamingDestinationsApiUtils;
import io.harness.audit.repositories.streaming.StreamingBatchRepository;
import io.harness.audit.repositories.streaming.StreamingDestinationRepository;
import io.harness.auditevent.streaming.beans.BatchStatus;
import io.harness.auditevent.streaming.dto.StreamingBatchDTO;
import io.harness.auditevent.streaming.dto.StreamingBatchDTO.StreamingBatchDTOKeys;
import io.harness.beans.SortOrder.OrderType;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.spec.server.audit.v1.model.FailureInfoCard;
import io.harness.spec.server.audit.v1.model.LastStreamedCard;
import io.harness.spec.server.audit.v1.model.StatusWiseCount;
import io.harness.spec.server.audit.v1.model.StreamingDestinationAggregateDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationCards;
import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO;
import io.harness.spec.server.audit.v1.model.StreamingDestinationStatus;
import io.harness.utils.PageUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import retrofit2.Call;
import retrofit2.Response;

public class AggregateStreamingServiceImplTest extends CategoryTest {
  public static final String ACCOUNT_IDENTIFIER = randomAlphabetic(10);
  @Mock private StreamingDestinationRepository streamingDestinationRepository;
  @Mock private StreamingBatchRepository streamingBatchRepository;
  private AggregateStreamingServiceImpl aggregateStreamingService;
  @Mock private StreamingService streamingService;
  @Mock private ConnectorResourceClient connectorResourceClient;
  @Mock private StreamingDestinationMapper streamingDestinationMapper;
  @Captor private ArgumentCaptor<Criteria> criteriaArgumentCaptor;
  @Captor private ArgumentCaptor<Sort> sortArgumentCaptor;
  @Mock private StreamingDestinationsApiUtils streamingDestinationsApiUtils;
  private String connectorRef;
  private String connectorId;
  @Mock Call call;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    connectorId = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    connectorRef = "account." + connectorId;
    this.aggregateStreamingService =
        new AggregateStreamingServiceImpl(streamingDestinationRepository, streamingBatchRepository, streamingService,
            connectorResourceClient, streamingDestinationMapper, streamingDestinationsApiUtils);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetStreamingDestinationCards() {
    long now = System.currentTimeMillis();
    StatusWiseCount activeStatusCount = new StatusWiseCount().count(1).status(ACTIVE);
    StatusWiseCount inactiveStatusCount = new StatusWiseCount().count(2).status(INACTIVE);
    when(streamingDestinationRepository.countByStatus(any()))
        .thenReturn(List.of(activeStatusCount, inactiveStatusCount));
    when(streamingBatchRepository.findOne(any(), any()))
        .thenReturn(StreamingBatchDTO.builder().accountIdentifier(ACCOUNT_IDENTIFIER).lastStreamedAt(now).build());
    when(streamingBatchRepository.count(any())).thenReturn(1L);
    StreamingDestinationCards cards = aggregateStreamingService.getStreamingDestinationCards(ACCOUNT_IDENTIFIER);
    assertThat(cards).isNotNull();
    assertThat(cards.getCountByStatusCard())
        .isNotNull()
        .hasSize(2)
        .containsExactlyInAnyOrder(activeStatusCount, inactiveStatusCount);
    LastStreamedCard expectedLastStreamedCard = new LastStreamedCard().lastStreamedAt(now);
    assertThat(cards.getLastStreamedCard()).isNotNull().isEqualToComparingFieldByField(expectedLastStreamedCard);
    FailureInfoCard expectedFailureInfoCard = new FailureInfoCard().count(1L);
    assertThat(cards.getFailureInfoCard()).isNotNull().isEqualToComparingFieldByField(expectedFailureInfoCard);

    verify(streamingDestinationRepository, times(1)).countByStatus(criteriaArgumentCaptor.capture());
    assertCountByStatusCriteria(criteriaArgumentCaptor.getValue());

    verify(streamingBatchRepository, times(1)).findOne(criteriaArgumentCaptor.capture(), sortArgumentCaptor.capture());
    assetLastStreamedCriteriaAndSort(criteriaArgumentCaptor.getValue(), sortArgumentCaptor.getValue());

    verify(streamingBatchRepository, times(1)).count(criteriaArgumentCaptor.capture());
    assertFailureCountCriteria(criteriaArgumentCaptor.getValue());
  }

  private void assertFailureCountCriteria(Criteria criteria) {
    Document document = criteria.getCriteriaObject();
    assertThat(document).isNotEmpty().containsExactlyInAnyOrderEntriesOf(
        Map.ofEntries(Map.entry(StreamingDestinationKeys.accountIdentifier, ACCOUNT_IDENTIFIER),
            Map.entry(StreamingBatchDTOKeys.status, BatchStatus.FAILED)));
  }

  private void assetLastStreamedCriteriaAndSort(Criteria criteria, Sort sort) {
    Document document = criteria.getCriteriaObject();
    assertThat(document).isNotEmpty().containsExactlyInAnyOrderEntriesOf(
        Map.ofEntries(Map.entry(StreamingDestinationKeys.accountIdentifier, ACCOUNT_IDENTIFIER),
            Map.entry(StreamingBatchDTOKeys.status, BatchStatus.SUCCESS)));
    assertThat(sort.get()).hasSize(1);
    assertThat(sort.getOrderFor(StreamingBatchDTOKeys.createdAt)).isNotNull();
    assertThat(sort.getOrderFor(StreamingBatchDTOKeys.createdAt).getDirection()).isEqualTo(DESC);
  }

  private void assertCountByStatusCriteria(Criteria criteria) {
    Document document = criteria.getCriteriaObject();
    assertThat(document).isNotEmpty().containsExactlyInAnyOrderEntriesOf(
        Map.ofEntries(Map.entry(StreamingDestinationKeys.accountIdentifier, ACCOUNT_IDENTIFIER)));
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void testAggregatedList() throws IOException {
    long now = System.currentTimeMillis();
    String searchTerm = randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10);
    StreamingDestinationStatus statusEnum = ACTIVE;
    int page = 0;
    int limit = 10;
    Pageable pageable = PageUtils.getPageRequest(new PageRequest(page, limit,
        List.of(aSortOrder().withField(StreamingDestinationKeys.lastModifiedDate, OrderType.DESC).build())));
    StreamingDestinationFilterProperties filterProperties =
        StreamingDestinationFilterProperties.builder().searchTerm(searchTerm).status(statusEnum).build();
    when(call.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            Optional.of(ConnectorDTO.builder()
                            .connectorInfo(ConnectorInfoDTO.builder().name(connectorId).identifier(connectorId).build())
                            .build()))));
    when(connectorResourceClient.get(any(), any(), any(), any())).thenReturn(call);
    when(streamingDestinationMapper.toDTO(any()))
        .thenReturn(new StreamingDestinationDTO().identifier("sd1").connectorRef(connectorRef));
    when(streamingService.list(ACCOUNT_IDENTIFIER, pageable, filterProperties))
        .thenReturn(new PageImpl<>(List.of(AwsS3StreamingDestination.builder()
                                               .accountIdentifier(ACCOUNT_IDENTIFIER)
                                               .identifier("sd1")
                                               .connectorRef(connectorRef)
                                               .build())));
    when(streamingBatchRepository.findOne(any(), any()))
        .thenReturn(StreamingBatchDTO.builder().accountIdentifier(ACCOUNT_IDENTIFIER).lastStreamedAt(now).build());
    Page<StreamingDestinationAggregateDTO> streamingDestinationsPage =
        aggregateStreamingService.getAggregatedList(ACCOUNT_IDENTIFIER, pageable, filterProperties);

    verify(streamingBatchRepository, times(1)).findOne(criteriaArgumentCaptor.capture(), sortArgumentCaptor.capture());

    verify(connectorResourceClient, times(1)).get(connectorId, ACCOUNT_IDENTIFIER, null, null);
    assertThat(streamingDestinationsPage).isNotEmpty();
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testAggregatedListWhenConnectorNotFound() {
    Pageable pageable = PageUtils.getPageRequest(new PageRequest(
        0, 10, List.of(aSortOrder().withField(StreamingDestinationKeys.lastModifiedDate, OrderType.DESC).build())));
    StreamingDestinationFilterProperties filterProperties =
        StreamingDestinationFilterProperties.builder()
            .searchTerm(randomAlphabetic(RANDOM_STRING_CHAR_COUNT_10))
            .status(ACTIVE)
            .build();

    when(connectorResourceClient.get(any(), any(), any(), any()))
        .thenThrow(new InvalidRequestException(
            String.format("Connector with identifier [%s] in project [null], org [null] not found", connectorId)));
    when(streamingDestinationMapper.toDTO(any()))
        .thenReturn(new StreamingDestinationDTO().identifier("sd1").connectorRef(connectorRef));
    when(streamingService.list(ACCOUNT_IDENTIFIER, pageable, filterProperties))
        .thenReturn(new PageImpl<>(List.of(AwsS3StreamingDestination.builder()
                                               .accountIdentifier(ACCOUNT_IDENTIFIER)
                                               .identifier("sd1")
                                               .connectorRef(connectorRef)
                                               .build())));
    when(streamingBatchRepository.findOne(any(), any()))
        .thenReturn(StreamingBatchDTO.builder()
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .lastStreamedAt(System.currentTimeMillis())
                        .build());
    Page<StreamingDestinationAggregateDTO> streamingDestinationsPage =
        aggregateStreamingService.getAggregatedList(ACCOUNT_IDENTIFIER, pageable, filterProperties);

    verify(streamingBatchRepository, times(1)).findOne(any(), any());
    verify(connectorResourceClient, times(1)).get(connectorId, ACCOUNT_IDENTIFIER, null, null);
    assertThat(streamingDestinationsPage.stream().findFirst().get().getConnectorInfo()).isNull();
    assertThat(streamingDestinationsPage).isNotEmpty();
  }
}
