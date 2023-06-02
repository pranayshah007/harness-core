/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.templates;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PL)
public enum PredefinedTemplate {
  EMAIL_TEST("templates/email_test.txt", "email_test"),
  SLACK_TEST("templates/slack_test.txt", "slack_test"),
  SLACK_VANILLA("templates/slack_vanilla.txt", "slack_vanilla"),
  PD_TEST("templates/pd_test.txt", "pd_test"),
  PD_VANILLA("templates/pd_vanilla.txt", "pd_vanilla"),
  MSTEAMS_TEST("templates/msteams_test.txt", "msteams_test"),
  EMAIL_INVITE("templates/email_invite.txt", "email_invite"),
  EMAIL_NOTIFY("templates/email_notify.txt", "email_notify"),
  EMAIL_VERIFY("templates/email_verify.txt", "default_email_verify"),
  SIGNUP_CONFIRMATION("templates/signup_confirmation.txt", "default_signup_confirmation"),
  EMAIL_TEST_WITH_USER("templates/email_test2.txt", "email_test2"),
  SLACK_TEST_WITH_USER("templates/slack_test2.txt", "slack_test2"),
  PD_TEST_WITH_USER("templates/pd_test2.txt", "pd_test2"),
  MSTEAMS_TEST_WITH_USER("templates/msteams_test2.txt", "msteams_test2"),
  PIPELINE_PLAIN_SLACK("notification_templates/pipeline/slack/plain_text.txt", "pms_pipeline_slack_plain"),
  PIPELINE_PLAIN_EMAIL("notification_templates/pipeline/email/plain_text.txt", "pms_pipeline_email_plain"),
  PIPELINE_PLAIN_PAGERDUTY("notification_templates/pipeline/pagerduty/plain_text.txt", "pms_pipeline_pagerduty_plain"),
  PIPELINE_PLAIN_MSTEAMS("notification_templates/pipeline/msteams/plain_text.txt", "pms_pipeline_msteams_plain"),
  STAGE_PLAIN_SLACK("notification_templates/stage/slack/plain_text.txt", "pms_stage_slack_plain"),
  STAGE_PLAIN_EMAIL("notification_templates/stage/email/plain_text.txt", "pms_stage_email_plain"),
  STAGE_PLAIN_PAGERDUTY("notification_templates/stage/pagerduty/plain_text.txt", "pms_stage_pagerduty_plain"),
  STAGE_PLAIN_MSTEAMS("notification_templates/stage/msteams/plain_text.txt", "pms_stage_msteams_plain"),
  STEP_PLAIN_SLACK("notification_templates/step/slack/plain_text.txt", "pms_step_slack_plain"),
  STEP_PLAIN_EMAIL("notification_templates/step/email/plain_text.txt", "pms_step_email_plain"),
  STEP_PLAIN_PAGERDUTY("notification_templates/step/pagerduty/plain_text.txt", "pms_step_pagerduty_plain"),
  STEP_PLAIN_MSTEAMS("notification_templates/step/msteams/plain_text.txt", "pms_step_msteams_plain"),
  HARNESS_APPROVAL_NOTIFICATION_SLACK("notification_templates/approval/slack/plain_text.txt", "harness_approval_slack"),
  HARNESS_APPROVAL_NOTIFICATION_EMAIL("notification_templates/approval/email/plain_text.txt", "harness_approval_email"),
  HARNESS_APPROVAL_EXECUTION_NOTIFICATION_SLACK(
      "notification_templates/approval/slack/plain_text_execution.txt", "harness_approval_execution_slack"),
  HARNESS_APPROVAL_EXECUTION_NOTIFICATION_EMAIL(
      "notification_templates/approval/email/plain_text_execution.txt", "harness_approval_execution_email"),
  CVNG_SLO_SIMPLE_PROJECT_SLACK(
      "cvng_notification_templates/cvng_slo_simple_project_slack.txt", "cvng_slo_simple_project_slack"),
  CVNG_SLO_SIMPLE_PROJECT_EMAIL(
      "cvng_notification_templates/cvng_slo_simple_project_email.txt", "cvng_slo_simple_project_email"),
  CVNG_SLO_SIMPLE_PROJECT_PAGERDUTY(
      "cvng_notification_templates/cvng_slo_simple_project_pagerduty.txt", "cvng_slo_simple_project_pagerduty"),
  CVNG_SLO_SIMPLE_PROJECT_MSTEAMS(
      "cvng_notification_templates/cvng_slo_simple_project_msteams.txt", "cvng_slo_simple_project_msteams"),

  CVNG_SLO_COMPOSITE_PROJECT_SLACK(
      "cvng_notification_templates/cvng_slo_composite_project_slack.txt", "cvng_slo_composite_project_slack"),
  CVNG_SLO_COMPOSITE_PROJECT_EMAIL(
      "cvng_notification_templates/cvng_slo_composite_project_email.txt", "cvng_slo_composite_project_email"),
  CVNG_SLO_COMPOSITE_PROJECT_PAGERDUTY(
      "cvng_notification_templates/cvng_slo_composite_project_pagerduty.txt", "cvng_slo_composite_project_pagerduty"),
  CVNG_SLO_COMPOSITE_PROJECT_MSTEAMS(
      "cvng_notification_templates/cvng_slo_composite_project_msteams.txt", "cvng_slo_composite_project_msteams"),
  CVNG_SLO_COMPOSITE_ACCOUNT_SLACK(
      "cvng_notification_templates/cvng_slo_composite_account_slack.txt", "cvng_slo_composite_account_slack"),
  CVNG_SLO_COMPOSITE_ACCOUNT_EMAIL(
      "cvng_notification_templates/cvng_slo_composite_account_email.txt", "cvng_slo_composite_account_email"),
  CVNG_SLO_COMPOSITE_ACCOUNT_PAGERDUTY(
      "cvng_notification_templates/cvng_slo_composite_account_pagerduty.txt", "cvng_slo_composite_account_pagerduty"),
  CVNG_SLO_COMPOSITE_ACCOUNT_MSTEAMS(
      "cvng_notification_templates/cvng_slo_composite_account_msteams.txt", "cvng_slo_composite_account_msteams"),
  CVNG_MONITOREDSERVICE_SLACK(
      "cvng_notification_templates/cvng_monitoredservice_slack.txt", "cvng_monitoredservice_slack"),
  CVNG_MONITOREDSERVICE_ET_SLACK(
      "cvng_notification_templates/cvng_monitoredservice_et_slack.txt", "cvng_monitoredservice_et_slack"),
  CVNG_MONITOREDSERVICE_EMAIL(
      "cvng_notification_templates/cvng_monitoredservice_email.txt", "cvng_monitoredservice_email"),
  CVNG_MONITOREDSERVICE_ET_EMAIL(
      "cvng_notification_templates/cvng_monitoredservice_et_email.txt", "cvng_monitoredservice_et_email"),
  CVNG_MONITOREDSERVICE_PAGERDUTY(
      "cvng_notification_templates/cvng_monitoredservice_pagerduty.txt", "cvng_monitoredservice_pagerduty"),
  CVNG_MONITOREDSERVICE_MSTEAMS(
      "cvng_notification_templates/cvng_monitoredservice_msteams.txt", "cvng_monitoredservice_msteams"),
  EMAIL_CCM_ANOMALY_ALERT("templates/email_ccm_anomaly_alert.txt", "email_ccm_anomaly_alert"),
  SLACK_CCM_ANOMALY_ALERT("templates/slack_ccm_anomaly_alert.txt", "slack_ccm_anomaly_alert"),
  EMAIL_CCM_BUDGET_ALERT("templates/email_ccm_budget_alert.txt", "email_ccm_budget_alert"),
  SLACK_CCM_BUDGET_ALERT("templates/slack_ccm_budget_alert.txt", "slack_ccm_budget_alert"),
  EMAIL_CCM_CLOUD_BILLING_DATA_READY("templates/email_ccm_cloud_data_ready.txt", "email_ccm_cloud_data_ready"),
  FREEZE_EMAIL_ALERT("notification_templates/email_test.txt", "freeze_email_alert"),
  FREEZE_SLACK_ALERT("notification_templates/slack_test.txt", "freeze_slack_alert"),
  FREEZE_PD_ALERT("notification_templates/pd_test.txt", "freeze_pagerduty_alert"),
  FREEZE_MSTEAMS_ALERT("notification_templates/msteams_test.txt", "freeze_msteams_alert"),
  EMAIL_SMP_LICENSE_ALERT("templates/email_smp_license_alert.txt", "email_smp_license_alert"),
  PIPELINE_REJECTED_EMAIL_ALERT(
      "notification_templates/pipeline_rejected_email_test.txt", "pipeline_rejected_email_alert"),
  PIPELINE_REJECTED_SLACK_ALERT(
      "notification_templates/pipeline_rejected_slack_test.txt", "pipeline_rejected_slack_alert"),
  PIPELINE_REJECTED_PD_ALERT(
      "notification_templates/pipeline_rejected_pd_test.txt", "pipeline_rejected_pagerduty_alert"),
  PIPELINE_REJECTED_MSTEAMS_ALERT(
      "notification_templates/pipeline_rejected_msteams_test.txt", "pipeline_rejected_msteams_alert"),
  HARNESS_APPROVAL_NOTIFICATION_MSTEAMS(
      "notification_templates/approval/msteams/plain_text.txt", "harness_approval_msteams"),
  HARNESS_APPROVAL_EXECUTION_NOTIFICATION_MSTEAMS(
      "notification_templates/approval/msteams/plain_text_execution.txt", "harness_approval_execution_msteams"),
  HARNESS_APPROVAL_ACTION_NOTIFICATION_SLACK(
      "notification_templates/approval/slack/plain_text_action.txt", "harness_approval_action_slack"),
  HARNESS_APPROVAL_ACTION_NOTIFICATION_EMAIL(
      "notification_templates/approval/email/plain_text_action.txt", "harness_approval_action_email"),
  HARNESS_APPROVAL_ACTION_NOTIFICATION_MSTEAMS(
      "notification_templates/approval/msteams/plain_text_action.txt", "harness_approval_action_msteams"),
  HARNESS_APPROVAL_ACTION_EXECUTION_NOTIFICATION_SLACK(
      "notification_templates/approval/slack/plain_text_action_execution.txt",
      "harness_approval_action_execution_slack"),
  HARNESS_APPROVAL_ACTION_EXECUTION_NOTIFICATION_EMAIL(
      "notification_templates/approval/email/plain_text_action_execution.txt",
      "harness_approval_action_execution_email"),
  HARNESS_APPROVAL_ACTION_EXECUTION_NOTIFICATION_MSTEAMS(
      "notification_templates/approval/msteams/plain_text_action_execution.txt",
      "harness_approval_action_execution_msteams");

  private String path;
  private String identifier;

  PredefinedTemplate(String path, String identifier) {
    this.path = path;
    this.identifier = identifier;
  }

  public String getPath() {
    return path;
  }

  public String getIdentifier() {
    return identifier;
  }
}
