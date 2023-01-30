/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.api.streaming.impl;

import static io.harness.audit.entities.streaming.StreamingDestination.StreamingDestinationKeys.accountIdentifier;
import static io.harness.auditevent.streaming.dto.StreamingBatchDTO.StreamingBatchDTOKeys.createdAt;
import static io.harness.auditevent.streaming.dto.StreamingBatchDTO.StreamingBatchDTOKeys.status;
import static io.harness.rule.OwnerRule.NISHANT;
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
import io.harness.audit.repositories.streaming.StreamingBatchRepository;
import io.harness.audit.repositories.streaming.StreamingDestinationRepository;
import io.harness.auditevent.streaming.beans.BatchStatus;
import io.harness.auditevent.streaming.dto.StreamingBatchDTO;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.audit.v1.model.FailureInfoCard;
import io.harness.spec.server.audit.v1.model.LastStreamedCard;
import io.harness.spec.server.audit.v1.model.StatusWiseCount;
import io.harness.spec.server.audit.v1.model.StreamingDestinationCards;

import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

public class AggregateStreamingServiceImplTest extends CategoryTest {
  public static final String ACCOUNT_IDENTIFIER = randomAlphabetic(10);
  @Mock private StreamingDestinationRepository streamingDestinationRepository;
  @Mock private StreamingBatchRepository streamingBatchRepository;
  private AggregateStreamingServiceImpl aggregateStreamingService;

  @Captor private ArgumentCaptor<Criteria> criteriaArgumentCaptor;
  @Captor private ArgumentCaptor<Sort> sortArgumentCaptor;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    this.aggregateStreamingService =
        new AggregateStreamingServiceImpl(streamingDestinationRepository, streamingBatchRepository);
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
        Map.ofEntries(Map.entry(accountIdentifier, ACCOUNT_IDENTIFIER), Map.entry(status, BatchStatus.FAILED)));
  }

  private void assetLastStreamedCriteriaAndSort(Criteria criteria, Sort sort) {
    Document document = criteria.getCriteriaObject();
    assertThat(document).isNotEmpty().containsExactlyInAnyOrderEntriesOf(
        Map.ofEntries(Map.entry(accountIdentifier, ACCOUNT_IDENTIFIER), Map.entry(status, BatchStatus.SUCCESS)));
    assertThat(sort.get()).hasSize(1);
    assertThat(sort.getOrderFor(createdAt)).isNotNull();
    assertThat(sort.getOrderFor(createdAt).getDirection()).isEqualTo(DESC);
  }

  private void assertCountByStatusCriteria(Criteria criteria) {
    Document document = criteria.getCriteriaObject();
    assertThat(document).isNotEmpty().containsExactlyInAnyOrderEntriesOf(
        Map.ofEntries(Map.entry(accountIdentifier, ACCOUNT_IDENTIFIER)));
  }
}
