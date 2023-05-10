/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.reports;

import static software.wings.graphql.datafetcher.billing.CloudBillingHelper.unified;
import static software.wings.security.UserThreadLocal.userGuard;

import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.mail.CEMailNotificationService;
import io.harness.batch.processing.shard.AccountShardService;
import io.harness.beans.EmbeddedUser;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.views.entities.CEReportSchedule;
import io.harness.ccm.views.service.impl.CEReportScheduleServiceImpl;
import io.harness.ccm.views.service.impl.CEReportTemplateBuilderServiceImpl;

import software.wings.beans.User;
import software.wings.graphql.datafetcher.billing.CloudBillingHelper;
import software.wings.persistence.mail.EmailData;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.google.inject.Singleton;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@Slf4j
public class ScheduledReportServiceImpl {
  @Autowired private CEReportScheduleServiceImpl ceReportScheduleService;
  @Autowired private CEReportTemplateBuilderServiceImpl ceReportTemplateBuilderService;
  @Autowired private AccountShardService accountShardService;
  @Autowired private BatchMainConfig config;
  @Autowired private CEMailNotificationService emailNotificationService;
  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Autowired private BigQueryService bigQueryService;
  @Autowired private CloudBillingHelper cloudBillingHelper;

  // TODO: CE_VIEW_URL, MAIL_SUBJECT to be finalized
  private static final String CE_VIEW_URL = "PERSPECTIVE_URL";
  private static final String MAIL_SUBJECT = "Your Continous Efficiency scheduled report is here";
  private static final String URL = "url";
  private int scheduleCount;

  public void generateAndSendScheduledReport() {
    scheduleCount = 0;
    if (!config.getReportScheduleConfig().isEnabled()) {
      log.warn("Batch job for ce scheduled report is disabled!");
      return;
    }
    // TODO: Change info to debug post integration with UI
    List<String> accountIds = accountShardService.getCeEnabledAccountIds();
    log.info("ceEnabledAccounts ids list {}", accountIds);
    Date jobTime = new Date();
    log.info("jobTime {}", jobTime.toInstant());

    accountIds.forEach(accountId -> {
      // N mongo calls for N accounts
      List<CEReportSchedule> schedules = ceReportScheduleService.getAllMatchingSchedules(accountId, jobTime);
      log.info("Found schedules(total {}) : {} for account {}", schedules.size(), schedules, accountId);
      scheduleCount += schedules.size();
      schedules.forEach(schedule -> {
        for (String viewId : schedule.getViewsId()) {
          try {
            sendMail(accountId, schedule.getRecipients(), viewId, schedule.getUuid());
            setNextExecution(accountId, schedule);
          } catch (Exception e) {
            log.error("Error in generateAndSendScheduledReport for viewId {}, accountId {}, reportId {}", viewId,
                accountId, schedule.getUuid(), e);
          }
        }
      });
    });
    log.info("A total of {} reportSchedules were processed", scheduleCount);
  }

  private void sendMail(String accountId, String[] recipients, String viewId, String reportId) {
    Map<String, String> templateModel =
        ceReportTemplateBuilderService.getTemplatePlaceholders(accountId, viewId, reportId, bigQueryService.get(),
            cloudBillingHelper.getCloudProviderTableName(
                config.getBillingDataPipelineConfig().getGcpProjectId(), accountId, unified));
    String viewUrl = "";
    try {
      viewUrl = emailNotificationService.buildAbsoluteUrl(templateModel.get(CE_VIEW_URL));
      viewUrl = viewUrl.replace("%25", "%");
    } catch (URISyntaxException e) {
      log.error("Error in forming View URL for Scheduled Report", e);
    }
    templateModel.put(URL, viewUrl);
    EmailData emailData = EmailData.builder()
                              .to(Arrays.asList(recipients))
                              .templateName(config.getReportScheduleConfig().getTemplateName())
                              .templateModel(templateModel)
                              .accountId(accountId)
                              .build();
    emailData.setCc(Collections.emptyList());
    emailData.setSubject(MAIL_SUBJECT);
    emailData.setRetries(0);
    // TODO: This opens new connection every time. Makes things slow. Modify this to use connection pools with sessions.
    emailNotificationService.send(emailData, Base64.getDecoder().decode(templateModel.get("CHART")));
  }

  public void setNextExecution(String accountId, CEReportSchedule schedule) {
    User userData = getUserData(schedule);
    if (null != userData) {
      // Set this in threadlocal so that downstream update in Mongo works fine
      try (UserThreadLocal.Guard guard = userGuard(userData)) {
        ceReportScheduleService.updateNextExecution(accountId, schedule);
      }
    } else {
      ceReportScheduleService.updateNextExecution(accountId, schedule);
    }
  }

  private User getUserData(CEReportSchedule schedule) {
    try {
      // This is needed to keep lastUpdatedBy same as before.
      // Get existing user uuid from the record in collection. This can be null.
      EmbeddedUser euser = schedule.getLastUpdatedBy();
      // Get User record for this uuid.
      if (euser != null) {
        return cloudToHarnessMappingService.getUser(euser.getUuid());
      }
    } catch (Exception ex) {
      log.error("Exception getting user", ex);
    }
    return null;
  }
}
