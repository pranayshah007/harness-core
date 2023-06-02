/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming.services.impl;

import static io.harness.audit.entities.AuditEvent.AuditEventKeys;
import static io.harness.auditevent.streaming.AuditEventStreamingConstants.ACCOUNT_IDENTIFIER_PARAMETER_KEY;
import static io.harness.auditevent.streaming.AuditEventStreamingConstants.AWS_S3_STREAMING_PUBLISHER;
import static io.harness.auditevent.streaming.AuditEventStreamingConstants.JOB_START_TIME_PARAMETER_KEY;
import static io.harness.auditevent.streaming.beans.BatchStatus.IN_PROGRESS;
import static io.harness.auditevent.streaming.beans.BatchStatus.READY;
import static io.harness.auditevent.streaming.entities.StreamingBatch.StreamingBatchKeys;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.spec.server.audit.v1.model.StreamingDestinationSpecDTO.TypeEnum.AWS_S3;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.audit.entities.AuditEvent;
import io.harness.audit.entities.streaming.AwsS3StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.audit.streaming.outgoing.OutgoingAuditMessage;
import io.harness.auditevent.streaming.AuditEventRepository;
import io.harness.auditevent.streaming.BatchConfig;
import io.harness.auditevent.streaming.beans.BatchStatus;
import io.harness.auditevent.streaming.beans.PublishResponse;
import io.harness.auditevent.streaming.beans.PublishResponseStatus;
import io.harness.auditevent.streaming.entities.StreamingBatch;
import io.harness.auditevent.streaming.publishers.StreamingPublisher;
import io.harness.auditevent.streaming.publishers.impl.AwsS3StreamingPublisher;
import io.harness.auditevent.streaming.services.BatchProcessorService;
import io.harness.auditevent.streaming.services.StreamingBatchService;
import io.harness.auditevent.streaming.services.StreamingDestinationService;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.mongodb.client.MongoCursor;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

public class AuditEventStreamingServiceImplTest extends CategoryTest {
  public static final int MINUTES_15_IN_MILLS = 15 * 60 * 1000;
  public static final int MINUTES_10_IN_MILLS = 10 * 60 * 1000;
  public static final int MINUTES_30_IN_MILLS = 30 * 60 * 1000;
  public static final int RANDOM_STRING_LENGTH = 10;
  @Mock private BatchProcessorService batchProcessorService;
  @Mock private StreamingBatchService streamingBatchService;
  @Mock private StreamingDestinationService streamingDestinationService;
  @Mock private AuditEventRepository auditEventRepository;
  @Mock private AwsS3StreamingPublisher awsS3StreamingPublisher;
  @Mock private BatchConfig batchConfig;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) private MongoTemplate template;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) private MongoCursor<Document> mongoCursor;
  private Map<String, StreamingPublisher> streamingPublisherMap;
  private AuditEventStreamingServiceImpl auditEventStreamingService;

  ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
  ArgumentCaptor<StreamingBatch> streamingBatchArgumentCaptor = ArgumentCaptor.forClass(StreamingBatch.class);
  ArgumentCaptor<StreamingDestination> streamingDestinationArgumentCaptor =
      ArgumentCaptor.forClass(StreamingDestination.class);

  private static final String ACCOUNT_IDENTIFIER = randomAlphabetic(RANDOM_STRING_LENGTH);

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    streamingPublisherMap = Map.of(AWS_S3_STREAMING_PUBLISHER, awsS3StreamingPublisher);
    this.auditEventStreamingService = new AuditEventStreamingServiceImpl(batchProcessorService, streamingBatchService,
        streamingDestinationService, auditEventRepository, batchConfig, template, streamingPublisherMap);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testStream_whenStatusInProgress() {
    long now = System.currentTimeMillis();
    StreamingDestination streamingDestination = getStreamingDestination();
    StreamingBatch streamingBatch = getStreamingBatch(streamingDestination, IN_PROGRESS, now);
    JobParameters jobParameters = getJobParameters();
    when(streamingBatchService.getLastStreamingBatch(
             streamingDestination, jobParameters.getLong(JOB_START_TIME_PARAMETER_KEY)))
        .thenReturn(streamingBatch);
    StreamingBatch streamingBatchAsReturned = auditEventStreamingService.stream(streamingDestination, jobParameters);

    assertThat(streamingBatchAsReturned).isEqualToComparingFieldByField(streamingBatch);

    verify(streamingBatchService, times(1))
        .getLastStreamingBatch(streamingDestination, jobParameters.getLong(JOB_START_TIME_PARAMETER_KEY));
    verify(auditEventRepository, times(0)).loadAuditEvents(any(), any());
    verify(streamingBatchService, times(0)).update(ACCOUNT_IDENTIFIER, streamingBatch);
    verify(batchProcessorService, times(0)).processAuditEvent(eq(streamingBatch), any());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testStream_whenStatusIsReadyAndNoNewAuditRecords() {
    long now = System.currentTimeMillis();
    StreamingDestination streamingDestination = getStreamingDestination();
    StreamingBatch streamingBatch = getStreamingBatch(streamingDestination, READY, now);
    JobParameters jobParameters = getJobParameters();
    when(streamingBatchService.getLastStreamingBatch(
             streamingDestination, jobParameters.getLong(JOB_START_TIME_PARAMETER_KEY)))
        .thenReturn(streamingBatch);
    when(mongoCursor.hasNext()).thenReturn(false);
    when(auditEventRepository.loadAuditEvents(any(), any())).thenReturn(mongoCursor);
    when(streamingBatchService.update(any(), any())).thenReturn(streamingBatch);

    StreamingBatch streamingBatchAsReturned = auditEventStreamingService.stream(streamingDestination, jobParameters);

    assertThat(streamingBatchAsReturned).isNotNull();
    assertLoadAuditEventsCallAndCriteria(streamingBatch, streamingBatch.getStartTime());

    verify(streamingBatchService, times(1)).update(eq(ACCOUNT_IDENTIFIER), streamingBatchArgumentCaptor.capture());
    assertThat(streamingBatchArgumentCaptor.getValue().getStatus()).isEqualTo(BatchStatus.SUCCESS);
    assertThat(streamingBatchArgumentCaptor.getValue().getLastStreamedAt()).isGreaterThan(0);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testStream_whenStatusIsReadyAndNewAuditRecordsFound() {
    long now = System.currentTimeMillis();
    StreamingDestination streamingDestination = getStreamingDestination();
    StreamingBatch streamingBatch = getStreamingBatch(streamingDestination, READY, now);
    streamingBatch.setLastSuccessfulRecordTimestamp(streamingBatch.getStartTime() + MINUTES_10_IN_MILLS);
    long expectedStartTime = streamingBatch.getLastSuccessfulRecordTimestamp();
    JobParameters jobParameters = getJobParameters();
    Document document = new Document();
    when(streamingBatchService.getLastStreamingBatch(
             streamingDestination, jobParameters.getLong(JOB_START_TIME_PARAMETER_KEY)))
        .thenReturn(streamingBatch);
    when(mongoCursor.hasNext()).thenReturn(true, true, true, false);
    when(batchConfig.getLimit()).thenReturn(1);
    when(mongoCursor.next()).thenReturn(document);
    when(template.getConverter().read(AuditEvent.class, document))
        .thenReturn(AuditEvent.builder().createdAt(streamingBatch.getStartTime() + MINUTES_15_IN_MILLS).build());
    when(auditEventRepository.loadAuditEvents(any(), any())).thenReturn(mongoCursor);
    when(batchProcessorService.processAuditEvent(eq(streamingBatch), any()))
        .thenReturn(List.of(OutgoingAuditMessage.builder().build()));
    when(awsS3StreamingPublisher.publish(any(), any(), any()))
        .thenReturn(PublishResponse.builder().status(PublishResponseStatus.SUCCESS).build());
    when(streamingBatchService.update(any(), any())).thenReturn(streamingBatch);

    StreamingBatch streamingBatchAsReturned = auditEventStreamingService.stream(streamingDestination, jobParameters);

    assertThat(streamingBatchAsReturned).isNotNull();
    assertLoadAuditEventsCallAndCriteria(streamingBatch, expectedStartTime);

    int wantedNumberOfInvocations = 2;
    verify(streamingBatchService, times(wantedNumberOfInvocations))
        .update(eq(ACCOUNT_IDENTIFIER), streamingBatchArgumentCaptor.capture());
    StreamingBatch streamingBatchCaptured = streamingBatchArgumentCaptor.getValue();
    assertThat(streamingBatchCaptured.getStatus()).isEqualTo(BatchStatus.SUCCESS);
    assertThat(streamingBatchCaptured.getLastSuccessfulRecordTimestamp())
        .isEqualTo(streamingBatch.getStartTime() + MINUTES_15_IN_MILLS);
    assertThat(streamingBatchCaptured.getNumberOfRecordsPublished()).isEqualTo(1);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testStream_whenStatusReadyAndNewAuditRecordsPublishFailed() {
    long now = System.currentTimeMillis();
    StreamingDestination streamingDestination = getStreamingDestination();
    StreamingBatch streamingBatch = getStreamingBatch(streamingDestination, READY, now);
    streamingBatch.setLastSuccessfulRecordTimestamp(streamingBatch.getStartTime() + MINUTES_15_IN_MILLS);
    long expectedStartTime = streamingBatch.getLastSuccessfulRecordTimestamp();
    JobParameters jobParameters = getJobParameters();
    setupReturnsForAuditRecordsPublishFailureCase(streamingDestination, streamingBatch, jobParameters);

    StreamingBatch streamingBatchAsReturned = auditEventStreamingService.stream(streamingDestination, jobParameters);

    assertThat(streamingBatchAsReturned).isNotNull();
    assertLoadAuditEventsCallAndCriteria(streamingBatch, expectedStartTime);

    verify(streamingBatchService, times(1)).update(eq(ACCOUNT_IDENTIFIER), streamingBatchArgumentCaptor.capture());
    StreamingBatch streamingBatchExpected = getStreamingBatch(streamingDestination, BatchStatus.FAILED, now);
    streamingBatchExpected.setLastSuccessfulRecordTimestamp(streamingBatch.getStartTime() + MINUTES_15_IN_MILLS);
    StreamingBatch streamingBatchCaptured = streamingBatchArgumentCaptor.getValue();
    assertThat(streamingBatchCaptured)
        .isEqualToIgnoringGivenFields(streamingBatchExpected, StreamingBatchKeys.lastStreamedAt);
    assertThat(streamingBatchCaptured.getLastStreamedAt()).isNotNull();
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testStream_whenStatusFailedAndNewAuditRecordsPublishFailed() {
    long now = System.currentTimeMillis();
    StreamingDestination streamingDestination = getStreamingDestination();
    StreamingBatch streamingBatch = getStreamingBatch(streamingDestination, BatchStatus.FAILED, now);
    streamingBatch.setLastSuccessfulRecordTimestamp(streamingBatch.getStartTime() + MINUTES_15_IN_MILLS);
    long expectedStartTime = streamingBatch.getLastSuccessfulRecordTimestamp();
    JobParameters jobParameters = getJobParameters();
    setupReturnsForAuditRecordsPublishFailureCase(streamingDestination, streamingBatch, jobParameters);
    when(batchConfig.getMaxRetries()).thenReturn(2);

    StreamingBatch streamingBatchAsReturned = auditEventStreamingService.stream(streamingDestination, jobParameters);

    assertThat(streamingBatchAsReturned).isNotNull();
    assertLoadAuditEventsCallAndCriteria(streamingBatch, expectedStartTime);

    verify(streamingBatchService, times(1)).update(eq(ACCOUNT_IDENTIFIER), streamingBatchArgumentCaptor.capture());
    StreamingBatch streamingBatchExpected = getStreamingBatch(streamingDestination, BatchStatus.FAILED, now);
    streamingBatchExpected.setLastSuccessfulRecordTimestamp(streamingBatch.getStartTime() + MINUTES_15_IN_MILLS);
    streamingBatchExpected.setRetryCount(1);
    StreamingBatch streamingBatchCaptured = streamingBatchArgumentCaptor.getValue();
    assertThat(streamingBatchCaptured)
        .isEqualToIgnoringGivenFields(streamingBatchExpected, StreamingBatchKeys.lastStreamedAt);
    assertThat(streamingBatchCaptured.getLastStreamedAt()).isNotNull();
  }

  private void setupReturnsForAuditRecordsPublishFailureCase(
      StreamingDestination streamingDestination, StreamingBatch streamingBatch, JobParameters jobParameters) {
    when(streamingBatchService.getLastStreamingBatch(
             streamingDestination, jobParameters.getLong(JOB_START_TIME_PARAMETER_KEY)))
        .thenReturn(streamingBatch);
    Document document = new Document();
    when(mongoCursor.hasNext()).thenReturn(true);
    when(mongoCursor.next()).thenReturn(document);
    when(template.getConverter().read(AuditEvent.class, document))
        .thenReturn(AuditEvent.builder().createdAt(streamingBatch.getStartTime() + MINUTES_10_IN_MILLS).build());
    when(auditEventRepository.loadAuditEvents(any(), any())).thenReturn(mongoCursor);
    when(batchProcessorService.processAuditEvent(eq(streamingBatch), any()))
        .thenReturn(List.of(OutgoingAuditMessage.builder().build()));
    when(awsS3StreamingPublisher.publish(any(), any(), any()))
        .thenReturn(PublishResponse.builder().status(PublishResponseStatus.FAILED).build());
    when(streamingBatchService.update(any(), any())).thenReturn(streamingBatch);
  }

  private void assertLoadAuditEventsCallAndCriteria(StreamingBatch streamingBatch, long expectedStartTime) {
    verify(auditEventRepository, times(1)).loadAuditEvents(criteriaArgumentCaptor.capture(), any());
    Criteria criteria = criteriaArgumentCaptor.getValue();
    Document document = criteria.getCriteriaObject();
    assertThat(document).containsEntry(AuditEventKeys.ACCOUNT_IDENTIFIER_KEY, ACCOUNT_IDENTIFIER);
    assertThat(document).containsKey(AuditEventKeys.createdAt);
    Document createdAtDocument = (Document) document.get(AuditEventKeys.createdAt);
    assertThat(createdAtDocument)
        .containsAllEntriesOf(
            Map.ofEntries(Map.entry("$gt", expectedStartTime), Map.entry("$lte", streamingBatch.getEndTime())));
  }

  private StreamingDestination getStreamingDestination() {
    String streamingDestinationIdentifier = randomAlphabetic(RANDOM_STRING_LENGTH);
    StreamingDestination streamingDestination =
        AwsS3StreamingDestination.builder().bucket(randomAlphabetic(RANDOM_STRING_LENGTH)).build();
    streamingDestination.setIdentifier(streamingDestinationIdentifier);
    streamingDestination.setAccountIdentifier(ACCOUNT_IDENTIFIER);
    streamingDestination.setType(AWS_S3);
    return streamingDestination;
  }

  private JobParameters getJobParameters() {
    Long timestamp = System.currentTimeMillis();
    Map<String, JobParameter> parameters = Map.of(JOB_START_TIME_PARAMETER_KEY, new JobParameter(timestamp),
        ACCOUNT_IDENTIFIER_PARAMETER_KEY, new JobParameter(ACCOUNT_IDENTIFIER));
    return new JobParameters(parameters);
  }

  private StreamingBatch getStreamingBatch(
      StreamingDestination streamingDestination, BatchStatus status, long currentTime) {
    long startTime = currentTime - MINUTES_30_IN_MILLS;
    return StreamingBatch.builder()
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .streamingDestinationIdentifier(streamingDestination.getIdentifier())
        .startTime(startTime)
        .endTime(currentTime)
        .status(status)
        .build();
  }
}
