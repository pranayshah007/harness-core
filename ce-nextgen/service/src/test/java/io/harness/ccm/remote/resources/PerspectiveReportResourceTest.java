/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.audittrails.events.ReportCreateEvent;
import io.harness.ccm.audittrails.events.ReportDeleteEvent;
import io.harness.ccm.audittrails.events.ReportUpdateEvent;
import io.harness.ccm.remote.resources.perspectives.PerspectiveReportResource;
import io.harness.ccm.views.entities.CEReportSchedule;
import io.harness.ccm.views.service.CEReportScheduleService;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;

import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@RunWith(MockitoJUnitRunner.class)
public class PerspectiveReportResourceTest extends CategoryTest {
  private CEReportScheduleService ceReportScheduleService = mock(CEReportScheduleService.class);
  private TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
  private OutboxService outboxService = mock(OutboxService.class);
  @Mock private TelemetryReporter telemetryReporter;
  private PerspectiveReportResource perspectiveReportResource;

  private final String ACCOUNT_ID = "ACCOUNT_ID";
  private final String NAME = "REPORT_NAME";
  private final String REPORT_ID = "REPORT_ID";
  private final String[] RECIPIENTS = {"user1@harness.io"};
  private final String[] VIEWS_ID = {"ceviewsid123"};
  private final String USER_CRON = "* 30 12 * * *"; // 12.30PM daily
  private final String[] RECIPIENTS2 = {"user2@harness.io"};
  @Rule public TemporaryFolder tempFolder = new TemporaryFolder(new File("/tmp"));
  private CEReportSchedule reportSchedule;
  private CEReportSchedule reportScheduleDTO;

  @Captor private ArgumentCaptor<ReportCreateEvent> reportCreateEventArgumentCaptor;
  @Captor private ArgumentCaptor<ReportDeleteEvent> reportDeleteEventArgumentCaptor;
  @Captor private ArgumentCaptor<ReportUpdateEvent> reportUpdateEventArgumentCaptor;

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    reportSchedule = CEReportSchedule.builder()
                         .accountId(ACCOUNT_ID)
                         .viewsId(VIEWS_ID)
                         .recipients(RECIPIENTS)
                         .description("")
                         .userCron(USER_CRON)
                         .name(NAME)
                         .uuid(REPORT_ID)
                         .enabled(true)
                         .build();
    when(ceReportScheduleService.get(REPORT_ID, ACCOUNT_ID)).thenReturn(reportSchedule);
    perspectiveReportResource =
        new PerspectiveReportResource(ceReportScheduleService, telemetryReporter, transactionTemplate, outboxService);
    reportScheduleDTO = reportSchedule.toDTO();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testGet() {
    perspectiveReportResource.getReportSetting(null, REPORT_ID, ACCOUNT_ID);
    verify(ceReportScheduleService).get(REPORT_ID, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testCreateReportSetting() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    perspectiveReportResource.createReportSetting(ACCOUNT_ID, reportSchedule);
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(reportCreateEventArgumentCaptor.capture());
    ReportCreateEvent reportCreateEvent = reportCreateEventArgumentCaptor.getValue();
    assertThat(reportScheduleDTO).isEqualTo(reportCreateEvent.getReportDTO());
    verify(ceReportScheduleService).createReportSetting(ACCOUNT_ID, reportSchedule);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testModifyRecipients() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    reportSchedule.setRecipients(RECIPIENTS2);
    reportScheduleDTO = reportSchedule.toDTO();
    perspectiveReportResource.updateReportSetting(ACCOUNT_ID, reportSchedule);
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(reportUpdateEventArgumentCaptor.capture());
    ReportUpdateEvent reportUpdateEvent = reportUpdateEventArgumentCaptor.getValue();
    assertThat(reportScheduleDTO).isEqualTo(reportUpdateEvent.getReportDTO());
    verify(ceReportScheduleService).update(ACCOUNT_ID, reportSchedule);
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testDeleteReportSetting() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    perspectiveReportResource.deleteReportSetting(REPORT_ID, null, ACCOUNT_ID);
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(reportDeleteEventArgumentCaptor.capture());
    ReportDeleteEvent reportDeleteEvent = reportDeleteEventArgumentCaptor.getValue();
    assertThat(reportScheduleDTO).isEqualTo(reportDeleteEvent.getReportDTO());
    verify(ceReportScheduleService).delete(REPORT_ID, ACCOUNT_ID);
  }
}
