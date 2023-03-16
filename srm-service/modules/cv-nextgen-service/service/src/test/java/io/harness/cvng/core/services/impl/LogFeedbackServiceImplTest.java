/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.NAVEEN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.core.beans.LogFeedback;
import io.harness.cvng.core.beans.LogFeedback.LogFeedbackBuilder;
import io.harness.cvng.core.beans.LogFeedbackHistory;
import io.harness.cvng.core.beans.params.ProjectPathParams;
import io.harness.cvng.core.services.api.LogFeedbackService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.UserPrincipal;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class LogFeedbackServiceImplTest extends CvNextGenTestBase {
  @Inject private LogFeedbackService logFeedbackService;
  @Inject private VerificationTaskService verificationTaskService;
  private ProjectPathParams projectPathParams;

  @Before
  public void setup() {
    projectPathParams = ProjectPathParams.builder()
                            .projectIdentifier(UUID.randomUUID().toString())
                            .orgIdentifier(UUID.randomUUID().toString())
                            .accountIdentifier(UUID.randomUUID().toString())
                            .build();
    UserPrincipal userPrincipal =
        new UserPrincipal("test", "test@harness.io", "test", projectPathParams.getAccountIdentifier());
    SecurityContextBuilder.setContext(userPrincipal);
    verificationTaskService.createDeploymentVerificationTask(
        projectPathParams.getAccountIdentifier(), "", "abcd", new HashMap<>());
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testCreate_withGet() {
    LogFeedback logFeedback = LogFeedback.builder()
                                  .environmentIdentifier("env1")
                                  .serviceIdentifier("svc1")
                                  .sampleMessage("pre-deployment - host1 log2")
                                  .feedbackScore(LogFeedback.FeedbackScore.HIGH_RISK)
                                  .description("feedback as high risk")
                                  .verificationJobInstanceId("abcd")
                                  .build();
    LogFeedback createLogFeedback = logFeedbackService.create(projectPathParams, logFeedback);

    LogFeedback getLogFeedback = logFeedbackService.get(projectPathParams, createLogFeedback.getFeedbackId());

    List<LogFeedbackHistory> logFeedbackHistoryList =
        logFeedbackService.history(projectPathParams, createLogFeedback.getFeedbackId());
    assertThat(logFeedbackHistoryList.size()).isEqualTo(1);
    LogFeedbackHistory logFeedbackHistory = logFeedbackHistoryList.get(0);
    assertThat(logFeedbackHistory.getCreatedBy()).isEqualTo("test@harness.io");
    assertThat(logFeedbackHistory.getUpdatedBy()).isNull();
    assertThat(getLogFeedback.getFeedbackId()).isEqualTo(createLogFeedback.getFeedbackId());
    assertThat(getLogFeedback.getFeedbackScore()).isEqualTo(logFeedback.getFeedbackScore());
    assertThat(getLogFeedback.getDescription()).isEqualTo(logFeedback.getDescription());
    assertThat(getLogFeedback.getServiceIdentifier()).isEqualTo(logFeedback.getServiceIdentifier());
    assertThat(getLogFeedback.getEnvironmentIdentifier()).isEqualTo(logFeedback.getEnvironmentIdentifier());
    assertThat(getLogFeedback.getSampleMessage()).isEqualTo(logFeedback.getSampleMessage());
    assertThat(getLogFeedback.getCreatedBy()).isEqualTo("test");
    assertThat(getLogFeedback.getUpdatedby()).isNull();
    assertThat(getLogFeedback.getCreatedAt()).isNotNull();
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testUpdateFeedbackScore_withGet() {
    ProjectPathParams projectPathParams = ProjectPathParams.builder()
                                              .projectIdentifier(UUID.randomUUID().toString())
                                              .orgIdentifier(UUID.randomUUID().toString())
                                              .accountIdentifier(UUID.randomUUID().toString())
                                              .build();

    LogFeedbackBuilder logFeedbackBuilder = LogFeedback.builder()
                                                .environmentIdentifier("env1")
                                                .serviceIdentifier("svc1")
                                                .sampleMessage("pre-deployment - host1 log2")
                                                .feedbackScore(LogFeedback.FeedbackScore.HIGH_RISK)
                                                .description("feedback as high risk");

    LogFeedback logFeedback = logFeedbackService.create(projectPathParams, logFeedbackBuilder.build());

    LogFeedback updateLogFeedback =
        logFeedbackBuilder.feedbackScore(LogFeedback.FeedbackScore.NO_RISK_CONSIDER_FREQUENCY).build();
    logFeedbackService.update(projectPathParams, logFeedback.getFeedbackId(), updateLogFeedback);

    LogFeedback updatedLogFeedback = logFeedbackService.get(projectPathParams, logFeedback.getFeedbackId());
    assertThat(updatedLogFeedback.getFeedbackId()).isEqualTo(logFeedback.getFeedbackId());
    assert updateLogFeedback != null;
    assertThat(updatedLogFeedback.getFeedbackScore()).isEqualTo(updateLogFeedback.getFeedbackScore());
    assertThat(updatedLogFeedback.getDescription()).isEqualTo(updateLogFeedback.getDescription());
    assertThat(updatedLogFeedback.getServiceIdentifier()).isEqualTo(updateLogFeedback.getServiceIdentifier());
    assertThat(updatedLogFeedback.getEnvironmentIdentifier()).isEqualTo(updateLogFeedback.getEnvironmentIdentifier());
    assertThat(updatedLogFeedback.getSampleMessage()).isEqualTo(updateLogFeedback.getSampleMessage());
    assertThat(updatedLogFeedback.getCreatedBy()).isEqualTo("test");
    assertThat(updatedLogFeedback.getUpdatedby()).isEqualTo("test");
    assertThat(updatedLogFeedback.getCreatedAt()).isNotNull();
    assertThat(updatedLogFeedback.getUpdatedAt()).isNotNull();
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testUpdateFeedbackDescription_withGet() {
    ProjectPathParams projectPathParams = ProjectPathParams.builder()
                                              .projectIdentifier(UUID.randomUUID().toString())
                                              .orgIdentifier(UUID.randomUUID().toString())
                                              .accountIdentifier(UUID.randomUUID().toString())
                                              .build();

    LogFeedbackBuilder logFeedbackBuilder = LogFeedback.builder()
                                                .environmentIdentifier("env1")
                                                .serviceIdentifier("svc1")
                                                .sampleMessage("pre-deployment - host1 log2")
                                                .feedbackScore(LogFeedback.FeedbackScore.HIGH_RISK)
                                                .description("feedback as high risk");

    LogFeedback logFeedback = logFeedbackService.create(projectPathParams, logFeedbackBuilder.build());

    LogFeedback updateLogFeedback = logFeedbackBuilder.description("updated feedback").build();
    logFeedbackService.update(projectPathParams, logFeedback.getFeedbackId(), updateLogFeedback);

    LogFeedback updatedLogFeedback = logFeedbackService.get(projectPathParams, logFeedback.getFeedbackId());
    assertThat(updatedLogFeedback.getFeedbackId()).isEqualTo(logFeedback.getFeedbackId());
    assert updateLogFeedback != null;
    assertThat(updatedLogFeedback.getFeedbackScore()).isEqualTo(updateLogFeedback.getFeedbackScore());
    assertThat(updatedLogFeedback.getDescription()).isEqualTo(updateLogFeedback.getDescription());
    assertThat(updatedLogFeedback.getServiceIdentifier()).isEqualTo(updateLogFeedback.getServiceIdentifier());
    assertThat(updatedLogFeedback.getEnvironmentIdentifier()).isEqualTo(updateLogFeedback.getEnvironmentIdentifier());
    assertThat(updatedLogFeedback.getSampleMessage()).isEqualTo(updateLogFeedback.getSampleMessage());
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testUpdateFeedbackSampleMessage_withGet() {
    ProjectPathParams projectPathParams = ProjectPathParams.builder()
                                              .projectIdentifier(UUID.randomUUID().toString())
                                              .orgIdentifier(UUID.randomUUID().toString())
                                              .accountIdentifier(UUID.randomUUID().toString())
                                              .build();

    LogFeedbackBuilder logFeedbackBuilder = LogFeedback.builder()
                                                .environmentIdentifier("env1")
                                                .serviceIdentifier("svc1")
                                                .sampleMessage("pre-deployment - host1 log2")
                                                .feedbackScore(LogFeedback.FeedbackScore.HIGH_RISK)
                                                .description("feedback as high risk");

    LogFeedback logFeedback = logFeedbackService.create(projectPathParams, logFeedbackBuilder.build());

    LogFeedback updateLogFeedback = logFeedbackBuilder.sampleMessage("updated sample message").build();
    logFeedbackService.update(projectPathParams, logFeedback.getFeedbackId(), updateLogFeedback);

    LogFeedback updatedLogFeedback = logFeedbackService.get(projectPathParams, logFeedback.getFeedbackId());

    List<LogFeedbackHistory> logFeedbackHistoryList =
        logFeedbackService.history(projectPathParams, logFeedback.getFeedbackId());
    assertThat(logFeedbackHistoryList.size()).isEqualTo(2);
    LogFeedbackHistory logFeedbackHistory1 = logFeedbackHistoryList.get(0);
    assertThat(logFeedbackHistory1.getCreatedBy()).isEqualTo("test@harness.io");
    assertThat(logFeedbackHistory1.getUpdatedBy()).isNull();

    LogFeedbackHistory logFeedbackHistory2 = logFeedbackHistoryList.get(1);
    assertThat(logFeedbackHistory2.getCreatedBy()).isNull();
    assertThat(logFeedbackHistory2.getUpdatedBy()).isEqualTo("test@harness.io");
    assertThat(updatedLogFeedback.getFeedbackId()).isEqualTo(logFeedback.getFeedbackId());
    assert updateLogFeedback != null;
    assertThat(updatedLogFeedback.getFeedbackScore()).isEqualTo(updateLogFeedback.getFeedbackScore());
    assertThat(updatedLogFeedback.getDescription()).isEqualTo(updateLogFeedback.getDescription());
    assertThat(updatedLogFeedback.getServiceIdentifier()).isEqualTo(updateLogFeedback.getServiceIdentifier());
    assertThat(updatedLogFeedback.getEnvironmentIdentifier()).isEqualTo(updateLogFeedback.getEnvironmentIdentifier());
    assertThat(updatedLogFeedback.getSampleMessage()).isEqualTo("pre-deployment - host1 log2");
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testDelete_withGet() {
    ProjectPathParams projectPathParams = ProjectPathParams.builder()
                                              .projectIdentifier(UUID.randomUUID().toString())
                                              .orgIdentifier(UUID.randomUUID().toString())
                                              .accountIdentifier(UUID.randomUUID().toString())
                                              .build();

    LogFeedbackBuilder logFeedbackBuilder = LogFeedback.builder()
                                                .environmentIdentifier("env1")
                                                .serviceIdentifier("svc1")
                                                .sampleMessage("pre-deployment - host1 log2")
                                                .feedbackScore(LogFeedback.FeedbackScore.HIGH_RISK)
                                                .description("feedback as high risk");

    LogFeedback logFeedback = logFeedbackService.create(projectPathParams, logFeedbackBuilder.build());

    boolean isDeleted = logFeedbackService.delete(projectPathParams, logFeedback.getFeedbackId());
    assert isDeleted;

    LogFeedback updatedLogFeedback = logFeedbackService.get(projectPathParams, logFeedback.getFeedbackId());
    assert updatedLogFeedback == null;
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testMultipleUpdateHistory_withGet() {
    ProjectPathParams projectParams = ProjectPathParams.builder()
                                          .projectIdentifier(UUID.randomUUID().toString())
                                          .orgIdentifier(UUID.randomUUID().toString())
                                          .accountIdentifier(UUID.randomUUID().toString())
                                          .build();

    LogFeedbackBuilder logFeedbackBuilder = LogFeedback.builder()
                                                .environmentIdentifier("env1")
                                                .serviceIdentifier("svc1")
                                                .sampleMessage("pre-deployment - host1 log2")
                                                .feedbackScore(LogFeedback.FeedbackScore.HIGH_RISK)
                                                .description("feedback as high risk");

    LogFeedback logFeedback = logFeedbackService.create(projectParams, logFeedbackBuilder.build());

    LogFeedback updateLogFeedback = logFeedbackBuilder.sampleMessage("updated sample message").build();
    logFeedbackService.update(projectParams, logFeedback.getFeedbackId(), updateLogFeedback);

    updateLogFeedback = logFeedbackBuilder.sampleMessage("next updated sample message").build();
    logFeedbackService.update(projectParams, logFeedback.getFeedbackId(), updateLogFeedback);

    List<LogFeedbackHistory> logFeedbackHistoryList =
        logFeedbackService.history(projectParams, logFeedback.getFeedbackId());
    assertThat(logFeedbackHistoryList.size()).isEqualTo(3);

    LogFeedbackHistory logFeedbackHistory1 = logFeedbackHistoryList.get(0);
    assertThat(logFeedbackHistory1.getCreatedBy()).isEqualTo("test@harness.io");
    assertThat(logFeedbackHistory1.getUpdatedBy()).isNull();

    LogFeedbackHistory logFeedbackHistory2 = logFeedbackHistoryList.get(1);
    assertThat(logFeedbackHistory2.getCreatedBy()).isNull();
    assertThat(logFeedbackHistory2.getUpdatedBy()).isEqualTo("test@harness.io");

    LogFeedbackHistory logFeedbackHistory3 = logFeedbackHistoryList.get(2);
    assertThat(logFeedbackHistory3.getCreatedBy()).isNull();
    assertThat(logFeedbackHistory3.getUpdatedBy()).isEqualTo("test@harness.io");
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testLogFeedbackList_withGet() {
    ProjectPathParams projectPathParams = ProjectPathParams.builder()
                                              .projectIdentifier(UUID.randomUUID().toString())
                                              .orgIdentifier(UUID.randomUUID().toString())
                                              .accountIdentifier(UUID.randomUUID().toString())
                                              .build();

    LogFeedbackBuilder logFeedbackBuilder1 = LogFeedback.builder()
                                                 .environmentIdentifier("env1")
                                                 .serviceIdentifier("svc1")
                                                 .sampleMessage("pre-deployment - host1 log2")
                                                 .feedbackScore(LogFeedback.FeedbackScore.HIGH_RISK)
                                                 .description("feedback as high risk");
    logFeedbackService.create(projectPathParams, logFeedbackBuilder1.build());

    LogFeedbackBuilder logFeedbackBuilder2 = LogFeedback.builder()
                                                 .environmentIdentifier("env1")
                                                 .serviceIdentifier("svc1")
                                                 .sampleMessage("pre-deployment - host1 log1")
                                                 .feedbackScore(LogFeedback.FeedbackScore.MEDIUM_RISK)
                                                 .description("medium Risk");
    logFeedbackService.create(projectPathParams, logFeedbackBuilder2.build());

    LogFeedbackBuilder logFeedbackBuilder3 = LogFeedback.builder()
                                                 .environmentIdentifier("env1")
                                                 .serviceIdentifier("svc1")
                                                 .sampleMessage("pre-deployment - host1 log3")
                                                 .feedbackScore(LogFeedback.FeedbackScore.MEDIUM_RISK)
                                                 .description("medium Risk");
    logFeedbackService.create(projectPathParams, logFeedbackBuilder3.build());

    List<LogFeedback> logFeedbackList = logFeedbackService.list("env1", "svc1");
    assertThat(logFeedbackList.size()).isEqualTo(3);
  }
}
