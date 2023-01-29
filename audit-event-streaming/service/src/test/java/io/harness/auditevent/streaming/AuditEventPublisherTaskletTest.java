/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.auditevent.streaming;

import static io.harness.auditevent.streaming.AuditEventStreamingConstants.ACCOUNT_IDENTIFIER_PARAMETER_KEY;
import static io.harness.auditevent.streaming.AuditEventStreamingConstants.JOB_START_TIME_PARAMETER_KEY;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.spec.server.audit.v1.model.StreamingDestinationDTO.StatusEnum.ACTIVE;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.audit.entities.streaming.AwsS3StreamingDestination;
import io.harness.audit.entities.streaming.StreamingDestinationFilterProperties;
import io.harness.auditevent.streaming.services.AuditEventStreamingService;
import io.harness.auditevent.streaming.services.StreamingDestinationService;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;

public class AuditEventPublisherTaskletTest extends CategoryTest {
  @Mock private StreamingDestinationService streamingDestinationService;
  @Mock private AuditEventStreamingService auditEventStreamingService;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) private ChunkContext chunkContext;
  ArgumentCaptor<StreamingDestinationFilterProperties> filterPropertiesArgumentCaptor =
      ArgumentCaptor.forClass(StreamingDestinationFilterProperties.class);

  private AuditEventPublisherTasklet auditEventPublisherTasklet;
  public static final String ACCOUNT_IDENTIFIER = randomAlphabetic(10);
  public static final Long JOB_START_TIME = System.currentTimeMillis();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Map<String, JobParameter> jobParameterMap = new HashMap<>();
    jobParameterMap.put(ACCOUNT_IDENTIFIER_PARAMETER_KEY, new JobParameter(ACCOUNT_IDENTIFIER));
    jobParameterMap.put(JOB_START_TIME_PARAMETER_KEY, new JobParameter(JOB_START_TIME));
    when(chunkContext.getStepContext().getStepExecution().getJobParameters())
        .thenReturn(new JobParameters(jobParameterMap));
    this.auditEventPublisherTasklet =
        new AuditEventPublisherTasklet(streamingDestinationService, auditEventStreamingService);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testExecute() throws Exception {
    AwsS3StreamingDestination awsS3StreamingDestination = AwsS3StreamingDestination.builder().build();
    when(streamingDestinationService.list(eq(ACCOUNT_IDENTIFIER), any()))
        .thenReturn(List.of(awsS3StreamingDestination));
    RepeatStatus status = auditEventPublisherTasklet.execute(null, chunkContext);
    assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    verify(streamingDestinationService, times(1))
        .list(eq(ACCOUNT_IDENTIFIER), filterPropertiesArgumentCaptor.capture());
    assertThat(filterPropertiesArgumentCaptor.getValue().getStatus()).isEqualTo(ACTIVE);
    verify(auditEventStreamingService, times(1))
        .stream(awsS3StreamingDestination, chunkContext.getStepContext().getStepExecution().getJobParameters());
  }
}
