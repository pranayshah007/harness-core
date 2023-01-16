/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.NAVEEN;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.core.beans.LogFeedback;
import io.harness.cvng.core.beans.LogFeedbackHistory;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.LogFeedbackService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class LogFeedbackServiceImplTest extends CvNextGenTestBase {
  @Inject private LogFeedbackService logFeedbackService;

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testCreate_withGet() {
    ProjectParams projectParams = ProjectParams.builder()
                                      .projectIdentifier(UUID.randomUUID().toString())
                                      .orgIdentifier(UUID.randomUUID().toString())
                                      .accountIdentifier(UUID.randomUUID().toString())
                                      .build();

    LogFeedback logFeedback = LogFeedback.builder()
                                  .environmentIdentifier("env1")
                                  .serviceIdentifier("svc1")
                                  .sampleMessage("pre-deployment - host1 log2")
                                  .feedbackScore(LogFeedback.FeedbackScore.HIGH_RISK)
                                  .description("feedback as high risk")
                                  .build();

    LogFeedback createLogFeedback = logFeedbackService.create(projectParams, "user.id@harness.io", logFeedback);

    LogFeedback getLogFeedback = logFeedbackService.get(projectParams, createLogFeedback.getFeedbackId());

    List<LogFeedbackHistory> logFeedbackHistoryList =
        logFeedbackService.history(projectParams, createLogFeedback.getFeedbackId());
    assertThat(logFeedbackHistoryList.size()).isEqualTo(1);
    LogFeedbackHistory logFeedbackHistory = logFeedbackHistoryList.get(0);
    assertThat(logFeedbackHistory.getCreatedBy()).isEqualTo("user.id@harness.io");
    assertThat(logFeedbackHistory.getUpdatedBy()).isEqualTo(null);
    assertThat(getLogFeedback.getFeedbackId()).isEqualTo(createLogFeedback.getFeedbackId());
    assertThat(getLogFeedback.getFeedbackScore()).isEqualTo(logFeedback.getFeedbackScore());
    assertThat(getLogFeedback.getDescription()).isEqualTo(logFeedback.getDescription());
    assertThat(getLogFeedback.getServiceIdentifier()).isEqualTo(logFeedback.getServiceIdentifier());
    assertThat(getLogFeedback.getEnvironmentIdentifier()).isEqualTo(logFeedback.getEnvironmentIdentifier());
    assertThat(getLogFeedback.getSampleMessage()).isEqualTo(logFeedback.getSampleMessage());
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testUpdateFeedbackScore_withGet() {
    ProjectParams projectParams = ProjectParams.builder()
                                      .projectIdentifier(UUID.randomUUID().toString())
                                      .orgIdentifier(UUID.randomUUID().toString())
                                      .accountIdentifier(UUID.randomUUID().toString())
                                      .build();

    LogFeedback.LogFeedbackBuilder logFeedbackBuilder = LogFeedback.builder()
                                                            .environmentIdentifier("env1")
                                                            .serviceIdentifier("svc1")
                                                            .sampleMessage("pre-deployment - host1 log2")
                                                            .feedbackScore(LogFeedback.FeedbackScore.HIGH_RISK)
                                                            .description("feedback as high risk");

    LogFeedback logFeedback =
        logFeedbackService.create(projectParams, "user.id@harness.io", logFeedbackBuilder.build());

    LogFeedback updateLogFeedback =
        logFeedbackBuilder.feedbackScore(LogFeedback.FeedbackScore.NO_RISK_CONSIDER_FREQUENCY).build();
    logFeedbackService.update(projectParams, "user.id@harness.io", logFeedback.getFeedbackId(), updateLogFeedback);

    LogFeedback updatedLogFeedback = logFeedbackService.get(projectParams, logFeedback.getFeedbackId());
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
  public void testUpdateFeedbackDescription_withGet() {
    ProjectParams projectParams = ProjectParams.builder()
                                      .projectIdentifier(UUID.randomUUID().toString())
                                      .orgIdentifier(UUID.randomUUID().toString())
                                      .accountIdentifier(UUID.randomUUID().toString())
                                      .build();

    LogFeedback.LogFeedbackBuilder logFeedbackBuilder = LogFeedback.builder()
                                                            .environmentIdentifier("env1")
                                                            .serviceIdentifier("svc1")
                                                            .sampleMessage("pre-deployment - host1 log2")
                                                            .feedbackScore(LogFeedback.FeedbackScore.HIGH_RISK)
                                                            .description("feedback as high risk");

    LogFeedback logFeedback =
        logFeedbackService.create(projectParams, "user.id@harness.io", logFeedbackBuilder.build());

    LogFeedback updateLogFeedback = logFeedbackBuilder.description("updated feedback").build();
    logFeedbackService.update(projectParams, "user.id@harness.io", logFeedback.getFeedbackId(), updateLogFeedback);

    LogFeedback updatedLogFeedback = logFeedbackService.get(projectParams, logFeedback.getFeedbackId());
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
    ProjectParams projectParams = ProjectParams.builder()
                                      .projectIdentifier(UUID.randomUUID().toString())
                                      .orgIdentifier(UUID.randomUUID().toString())
                                      .accountIdentifier(UUID.randomUUID().toString())
                                      .build();

    LogFeedback.LogFeedbackBuilder logFeedbackBuilder = LogFeedback.builder()
                                                            .environmentIdentifier("env1")
                                                            .serviceIdentifier("svc1")
                                                            .sampleMessage("pre-deployment - host1 log2")
                                                            .feedbackScore(LogFeedback.FeedbackScore.HIGH_RISK)
                                                            .description("feedback as high risk");

    LogFeedback logFeedback =
        logFeedbackService.create(projectParams, "user.id@harness.io", logFeedbackBuilder.build());

    LogFeedback updateLogFeedback = logFeedbackBuilder.sampleMessage("updated sample message").build();
    logFeedbackService.update(projectParams, "newuser.id@harness.io", logFeedback.getFeedbackId(), updateLogFeedback);

    LogFeedback updatedLogFeedback = logFeedbackService.get(projectParams, logFeedback.getFeedbackId());

    List<LogFeedbackHistory> logFeedbackHistoryList =
        logFeedbackService.history(projectParams, logFeedback.getFeedbackId());
    assertThat(logFeedbackHistoryList.size()).isEqualTo(2);
    LogFeedbackHistory logFeedbackHistory1 = logFeedbackHistoryList.get(0);
    assertThat(logFeedbackHistory1.getCreatedBy()).isEqualTo("user.id@harness.io");
    assertThat(logFeedbackHistory1.getUpdatedBy()).isNull();

    LogFeedbackHistory logFeedbackHistory2 = logFeedbackHistoryList.get(1);
    assertThat(logFeedbackHistory2.getCreatedBy()).isNull();
    assertThat(logFeedbackHistory2.getUpdatedBy()).isEqualTo("newuser.id@harness.io");
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
    ProjectParams projectParams = ProjectParams.builder()
                                      .projectIdentifier(UUID.randomUUID().toString())
                                      .orgIdentifier(UUID.randomUUID().toString())
                                      .accountIdentifier(UUID.randomUUID().toString())
                                      .build();

    LogFeedback.LogFeedbackBuilder logFeedbackBuilder = LogFeedback.builder()
                                                            .environmentIdentifier("env1")
                                                            .serviceIdentifier("svc1")
                                                            .sampleMessage("pre-deployment - host1 log2")
                                                            .feedbackScore(LogFeedback.FeedbackScore.HIGH_RISK)
                                                            .description("feedback as high risk");

    LogFeedback logFeedback =
        logFeedbackService.create(projectParams, "user.id@harness.io", logFeedbackBuilder.build());

    boolean isDeleted = logFeedbackService.delete(projectParams, "user.id@harness.io", logFeedback.getFeedbackId());
    assert isDeleted;

    LogFeedback updatedLogFeedback = logFeedbackService.get(projectParams, logFeedback.getFeedbackId());
    assert updatedLogFeedback == null;
  }

  @Test
  @Owner(developers = NAVEEN)
  @Category(UnitTests.class)
  public void testMultipleUpdateHistory_withGet() {
    ProjectParams projectParams = ProjectParams.builder()
                                      .projectIdentifier(UUID.randomUUID().toString())
                                      .orgIdentifier(UUID.randomUUID().toString())
                                      .accountIdentifier(UUID.randomUUID().toString())
                                      .build();

    LogFeedback.LogFeedbackBuilder logFeedbackBuilder = LogFeedback.builder()
                                                            .environmentIdentifier("env1")
                                                            .serviceIdentifier("svc1")
                                                            .sampleMessage("pre-deployment - host1 log2")
                                                            .feedbackScore(LogFeedback.FeedbackScore.HIGH_RISK)
                                                            .description("feedback as high risk");

    LogFeedback logFeedback =
        logFeedbackService.create(projectParams, "user.id@harness.io", logFeedbackBuilder.build());

    LogFeedback updateLogFeedback = logFeedbackBuilder.sampleMessage("updated sample message").build();
    logFeedbackService.update(projectParams, "newuser.id@harness.io", logFeedback.getFeedbackId(), updateLogFeedback);

    updateLogFeedback = logFeedbackBuilder.sampleMessage("next updated sample message").build();
    logFeedbackService.update(projectParams, "newuser2.id@harness.io", logFeedback.getFeedbackId(), updateLogFeedback);

    List<LogFeedbackHistory> logFeedbackHistoryList =
        logFeedbackService.history(projectParams, logFeedback.getFeedbackId());
    assertThat(logFeedbackHistoryList.size()).isEqualTo(3);

    LogFeedbackHistory logFeedbackHistory1 = logFeedbackHistoryList.get(0);
    assertThat(logFeedbackHistory1.getCreatedBy()).isEqualTo("user.id@harness.io");
    assertThat(logFeedbackHistory1.getUpdatedBy()).isNull();

    LogFeedbackHistory logFeedbackHistory2 = logFeedbackHistoryList.get(1);
    assertThat(logFeedbackHistory2.getCreatedBy()).isNull();
    assertThat(logFeedbackHistory2.getUpdatedBy()).isEqualTo("newuser.id@harness.io");

    LogFeedbackHistory logFeedbackHistory3 = logFeedbackHistoryList.get(2);
    assertThat(logFeedbackHistory3.getCreatedBy()).isNull();
    assertThat(logFeedbackHistory3.getUpdatedBy()).isEqualTo("newuser2.id@harness.io");
  }
}
