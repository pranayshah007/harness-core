/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.services.impl;

import static io.harness.audit.entities.AuditEvent.AuditEventKeys;
import static io.harness.auditevent.streaming.beans.BatchStatus.FAILED;
import static io.harness.auditevent.streaming.beans.BatchStatus.IN_PROGRESS;
import static io.harness.auditevent.streaming.beans.BatchStatus.READY;
import static io.harness.auditevent.streaming.beans.BatchStatus.SUCCESS;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.spec.server.audit.v1.model.StreamingDestinationSpecDTO.TypeEnum.AWS_S3;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.data.domain.Sort.Direction.DESC;

import io.harness.CategoryTest;
import io.harness.audit.entities.streaming.AwsS3StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.auditevent.streaming.AuditEventRepository;
import io.harness.auditevent.streaming.beans.BatchStatus;
import io.harness.auditevent.streaming.entities.StreamingBatch;
import io.harness.auditevent.streaming.entities.StreamingBatch.StreamingBatchKeys;
import io.harness.auditevent.streaming.repositories.StreamingBatchRepository;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.RandomUtils;
import org.bson.Document;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

public class StreamingBatchServiceImplTest extends CategoryTest {
  public static final int RANDOM_STRING_LENGTH = 10;
  @Mock private StreamingBatchRepository streamingBatchRepository;
  @Mock private AuditEventRepository auditEventRepository;
  StreamingBatchServiceImpl streamingBatchService;

  @Rule public ExpectedException expectedException = ExpectedException.none();

  ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
  ArgumentCaptor<Sort> sortArgumentCaptor = ArgumentCaptor.forClass(Sort.class);
  ArgumentCaptor<StreamingBatch> streamingBatchArgumentCaptor = ArgumentCaptor.forClass(StreamingBatch.class);

  private final String ACCOUNT_IDENTIFIER = randomAlphabetic(RANDOM_STRING_LENGTH);
  private final String STREAMING_DESTINATION_IDENTIFIER = randomAlphabetic(RANDOM_STRING_LENGTH);
  private final long MINUTES_10_IN_MILLS = 10 * 60 * 1000L;
  private final long MINUTES_15_IN_MILLS = 15 * 60 * 1000L;
  private final long MINUTES_30_IN_MILLS = 30 * 60 * 1000L;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    this.streamingBatchService = new StreamingBatchServiceImpl(streamingBatchRepository, auditEventRepository);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGet() {
    String streamingDestinationIdentifier = randomAlphabetic(RANDOM_STRING_LENGTH);
    Optional<StreamingBatch> streamingBatchOptional = Optional.of(StreamingBatch.builder().build());
    List<BatchStatus> batchStatusList = List.of(READY);
    when(streamingBatchRepository.findStreamingBatchByAccountIdentifierAndStreamingDestinationIdentifierAndStatusIn(
             ACCOUNT_IDENTIFIER, streamingDestinationIdentifier, batchStatusList))
        .thenReturn(streamingBatchOptional);
    Optional<StreamingBatch> streamingBatchOptionalReturned =
        streamingBatchService.get(ACCOUNT_IDENTIFIER, streamingDestinationIdentifier, batchStatusList);
    assertThat(streamingBatchOptionalReturned).isNotNull();
    assertThat(streamingBatchOptionalReturned).isEqualTo(streamingBatchOptional);
    verify(streamingBatchRepository, times(1))
        .findStreamingBatchByAccountIdentifierAndStreamingDestinationIdentifierAndStatusIn(
            ACCOUNT_IDENTIFIER, streamingDestinationIdentifier, batchStatusList);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetLatest() {
    when(streamingBatchRepository.findOne(any(), any())).thenReturn(StreamingBatch.builder().build());
    Optional<StreamingBatch> streamingBatchOptional =
        streamingBatchService.getLatest(ACCOUNT_IDENTIFIER, STREAMING_DESTINATION_IDENTIFIER);
    assertThat(streamingBatchOptional).isNotEmpty();
    verify(streamingBatchRepository, times(1)).findOne(criteriaArgumentCaptor.capture(), sortArgumentCaptor.capture());
    Sort sort = sortArgumentCaptor.getValue();
    assertThat(sort.get()).hasSize(1);
    assertThat(sort.getOrderFor(StreamingBatchKeys.createdAt)).isNotNull();
    assertThat(sort.getOrderFor(StreamingBatchKeys.createdAt).getDirection()).isEqualTo(DESC);
    Criteria criteria = criteriaArgumentCaptor.getValue();
    assertGetLatestCriteria(criteria);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testUpdate() {
    StreamingBatch streamingBatch = StreamingBatch.builder()
                                        .id(randomAlphabetic(RANDOM_STRING_LENGTH))
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .build();
    when(streamingBatchRepository.save(any())).thenReturn(streamingBatch);
    StreamingBatch streamingBatchReturned = streamingBatchService.update(ACCOUNT_IDENTIFIER, streamingBatch);
    assertThat(streamingBatchReturned).isNotNull();
    assertThat(streamingBatchReturned).isEqualTo(streamingBatch);
    verify(streamingBatchRepository, times(1)).save(streamingBatchArgumentCaptor.capture());
    assertThat(streamingBatchArgumentCaptor.getValue()).isEqualTo(streamingBatch);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testUpdate_forAccountIdentifierMismatch() {
    StreamingBatch streamingBatch = StreamingBatch.builder()
                                        .id(randomAlphabetic(RANDOM_STRING_LENGTH))
                                        .accountIdentifier(randomAlphabetic(RANDOM_STRING_LENGTH))
                                        .build();
    expectedException.expect(InvalidRequestException.class);
    expectedException.expectMessage(
        String.format("Account identifier mismatch. Passed: [%s] but expected [%s] for batch id: [%s]",
            ACCOUNT_IDENTIFIER, streamingBatch.getAccountIdentifier(), streamingBatch.getId()));
    streamingBatchService.update(ACCOUNT_IDENTIFIER, streamingBatch);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetLastStreamingBatch_whenNotInDB() {
    long now = System.currentTimeMillis();
    long numberOfRecords = RandomUtils.nextLong();
    StreamingDestination streamingDestination = getStreamingDestination(now);

    when(streamingBatchRepository.findOne(any(), any())).thenReturn(null);
    when(auditEventRepository.countAuditEvents(any())).thenReturn(numberOfRecords);

    StreamingBatch expectedStreamingBatch = getStreamingBatch(now, streamingDestination, READY);
    expectedStreamingBatch.setNumberOfRecords(numberOfRecords);

    streamingBatchService.getLastStreamingBatch(streamingDestination, now);

    verify(streamingBatchRepository, times(1)).save(streamingBatchArgumentCaptor.capture());
    StreamingBatch savedStreamingBatch = streamingBatchArgumentCaptor.getValue();
    assertThat(savedStreamingBatch).isEqualToComparingFieldByField(expectedStreamingBatch);

    verify(auditEventRepository, times(1)).countAuditEvents(criteriaArgumentCaptor.capture());
    assertAuditRecordsCountCriteria(expectedStreamingBatch, criteriaArgumentCaptor.getValue());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetLastStreamingBatch_whenStatusInProgress() {
    long now = System.currentTimeMillis();
    StreamingDestination streamingDestination = getStreamingDestination(now);
    StreamingBatch streamingBatch = getStreamingBatch(now, streamingDestination, IN_PROGRESS);
    when(streamingBatchRepository.findOne(any(), any())).thenReturn(streamingBatch);
    StreamingBatch streamingBatchReturned = streamingBatchService.getLastStreamingBatch(streamingDestination, now);
    verify(streamingBatchRepository, times(0)).save(streamingBatchArgumentCaptor.capture());
    assertThat(streamingBatchReturned).isEqualTo(streamingBatch);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetLastStreamingBatch_whenStatusReady() {
    long now = System.currentTimeMillis();
    StreamingDestination streamingDestination = getStreamingDestination(now);
    StreamingBatch streamingBatch = getStreamingBatch(now, streamingDestination, READY);
    when(streamingBatchRepository.findOne(any(), any())).thenReturn(streamingBatch);
    StreamingBatch streamingBatchReturned = streamingBatchService.getLastStreamingBatch(streamingDestination, now);
    verify(streamingBatchRepository, times(0)).save(streamingBatchArgumentCaptor.capture());
    assertThat(streamingBatchReturned).isEqualTo(streamingBatch);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetLastStreamingBatch_whenStatusFailed() {
    long now = System.currentTimeMillis();
    StreamingDestination streamingDestination = getStreamingDestination(now);
    StreamingBatch streamingBatch = getStreamingBatch(now, streamingDestination, FAILED);
    when(streamingBatchRepository.findOne(any(), any())).thenReturn(streamingBatch);
    StreamingBatch streamingBatchReturned = streamingBatchService.getLastStreamingBatch(streamingDestination, now);
    verify(streamingBatchRepository, times(0)).save(streamingBatchArgumentCaptor.capture());
    assertThat(streamingBatchReturned).isEqualTo(streamingBatch);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetLastStreamingBatch_whenStatusSuccess() {
    long now = System.currentTimeMillis();
    long previousBatchExecutionTime = now - MINUTES_30_IN_MILLS;
    long numberOfRecords = RandomUtils.nextLong();
    StreamingDestination streamingDestination = getStreamingDestination(previousBatchExecutionTime);
    StreamingBatch previousStreamingBatch =
        getStreamingBatch(previousBatchExecutionTime, streamingDestination, SUCCESS);
    previousStreamingBatch.setLastSuccessfulRecordTimestamp(previousBatchExecutionTime - MINUTES_15_IN_MILLS);
    when(streamingBatchRepository.findOne(any(), any())).thenReturn(previousStreamingBatch);
    when(auditEventRepository.countAuditEvents(any())).thenReturn(numberOfRecords);

    streamingBatchService.getLastStreamingBatch(streamingDestination, now);

    StreamingBatch expectedStreamingBatch = getStreamingBatch(now, streamingDestination, READY);
    expectedStreamingBatch.setStartTime(previousStreamingBatch.getEndTime());
    expectedStreamingBatch.setNumberOfRecords(numberOfRecords);

    verify(streamingBatchRepository, times(1)).save(streamingBatchArgumentCaptor.capture());
    StreamingBatch savedStreamingBatch = streamingBatchArgumentCaptor.getValue();
    assertThat(savedStreamingBatch).isEqualToComparingFieldByField(expectedStreamingBatch);

    verify(auditEventRepository, times(1)).countAuditEvents(criteriaArgumentCaptor.capture());
    assertAuditRecordsCountCriteria(expectedStreamingBatch, criteriaArgumentCaptor.getValue());
  }

  private StreamingDestination getStreamingDestination(long updatedAt) {
    StreamingDestination streamingDestination =
        AwsS3StreamingDestination.builder().bucket(randomAlphabetic(RANDOM_STRING_LENGTH)).build();
    streamingDestination.setIdentifier(STREAMING_DESTINATION_IDENTIFIER);
    streamingDestination.setAccountIdentifier(ACCOUNT_IDENTIFIER);
    streamingDestination.setType(AWS_S3);
    streamingDestination.setLastStatusChangedAt(updatedAt - MINUTES_10_IN_MILLS);
    streamingDestination.setCreatedAt(updatedAt - MINUTES_30_IN_MILLS);
    streamingDestination.setLastModifiedDate(updatedAt);
    return streamingDestination;
  }

  private StreamingBatch getStreamingBatch(long now, StreamingDestination streamingDestination, BatchStatus status) {
    return StreamingBatch.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .streamingDestinationIdentifier(STREAMING_DESTINATION_IDENTIFIER)
        .startTime(streamingDestination.getLastStatusChangedAt())
        .endTime(now)
        .status(status)
        .build();
  }

  private void assertGetLatestCriteria(Criteria criteria) {
    Document document = criteria.getCriteriaObject();
    int expectedSize = 2;
    assertThat(document).hasSize(expectedSize);
    assertThat(document).containsExactlyInAnyOrderEntriesOf(
        Map.ofEntries(Map.entry(StreamingBatchKeys.accountIdentifier, ACCOUNT_IDENTIFIER),
            Map.entry(StreamingBatchKeys.streamingDestinationIdentifier, STREAMING_DESTINATION_IDENTIFIER)));
  }

  private void assertAuditRecordsCountCriteria(StreamingBatch expectedStreamingBatch, Criteria criteria) {
    Document document = criteria.getCriteriaObject();
    assertThat(document).containsEntry(AuditEventKeys.ACCOUNT_IDENTIFIER_KEY, ACCOUNT_IDENTIFIER);
    assertThat(document).containsKey(AuditEventKeys.createdAt);
    Document createdAtDocument = (Document) document.get(AuditEventKeys.createdAt);
    assertThat(createdAtDocument)
        .containsExactlyInAnyOrderEntriesOf(Map.ofEntries(Map.entry("$gt", expectedStreamingBatch.getStartTime()),
            Map.entry("$lte", expectedStreamingBatch.getEndTime())));
  }
}
