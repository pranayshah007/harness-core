/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.eventsframework;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import java.time.Duration;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_TRIGGERS, HarnessModuleComponent.CDS_PIPELINE})
@UtilityClass
@OwnedBy(PL)
public final class EventsFrameworkConstants {
  public static final String DUMMY_REDIS_URL = "dummyRedisUrl";

  public static final String ENTITY_CRUD = "entity_crud";
  public static final String SETUP_USAGE = "setup_usage";
  public static final String ENTITY_ACTIVITY = "entity_activity";
  public static final String HARNESS_TO_GIT_PUSH = "harness_to_git_push";
  public static final String WEBHOOK_REQUEST_PAYLOAD_DETAILS = "webhook_request_payload_data";
  public static final String WEBHOOK_EVENTS_STREAM = "webhook_events_stream";
  public static final String POLLING_EVENTS_STREAM = "polling_events_stream";
  public static final String TRIGGER_EXECUTION_EVENTS_STREAM = "trigger_execution_events_stream";
  public static final String GIT_PUSH_EVENT_STREAM = "git_push_event_stream";
  public static final String GIT_PR_EVENT_STREAM = "git_pr_event_stream";
  public static final String WEBHOOK_PUSH_EVENT = "WebhookPushEvent";
  public static final String WEBHOOK_BRANCH_HOOK_EVENT = "WebhookBranchHookEvent";
  public static final String WEBHOOK_EVENT = "WebhookEvent";
  public static final String GIT_BRANCH_HOOK_EVENT_STREAM = "git_branch_hook_event_stream";
  public static final String USERMEMBERSHIP = "usermembership";
  public static final String ORCHESTRATION_LOG = "orchestration_log";
  public static final String NG_ACCOUNT_SETUP = "ng_account_setup";
  public static final String INSTANCE_SYNC_PERPETUAL_TASK_RESPONSE_STREAM = "instance_sync_perpetual_task_stream";
  public static final String INSTANCE_STATS = "instance_stats";
  public static final String GIT_FULL_SYNC_STREAM = "full_sync_stream";
  public static final String OBSERVER_EVENT_CHANNEL = "observer_event_channel";
  public static final String GIT_SYNC_ENTITY_STREAM = "git_sync_entity_stream";

  // created for git sdk, dont use outside sdk.
  public static final String GIT_CONFIG_STREAM = "git_config_stream";
  public static final String SAML_AUTHORIZATION_ASSERTION = "saml_authorization_assertion";
  public static final String LDAP_GROUP_SYNC = "ldap_group_sync";

  public static final String DUMMY_TOPIC_NAME = "dummy_topic_name";
  public static final String DUMMY_GROUP_NAME = "dummy_group_name";
  public static final String DUMMY_NAME = "dummy_name";

  // Pipeline Service Events
  public static final String PIPELINE_ORCHESTRATION_EVENT_TOPIC = "pipeline_orchestration";
  public static final int PIPELINE_ORCHESTRATION_EVENT_BATCH_SIZE = 40;
  public static final int PIPELINE_ORCHESTRATION_EVENT_MAX_TOPIC_SIZE = 5000;

  public static final String PIPELINE_SDK_RESPONSE_EVENT_TOPIC = "pipeline_sdk_response";
  public static final int PIPELINE_SDK_RESPONSE_EVENT_MAX_TOPIC_SIZE = 5000;
  public static final int SDK_RESPONSE_EVENT_BATCH_SIZE = 20;

  public static final String INITIATE_NODE_EVENT_TOPIC = "pipeline_initiate_node";
  public static final int INITIATE_NODE_EVENT_BATCH_SIZE = 10;
  public static final int INITIATE_NODE_EVENT_MAX_TOPIC_SIZE = 5000;

  public static final String PIPELINE_PARTIAL_PLAN_RESPONSE = "pipeline_partial_plan_response";
  public static final int PIPELINE_PARTIAL_PLAN_RESPONSE_EVENT_MAX_TOPIC_SIZE = 5000;
  public static final int PARTIAL_PLAN_EVENT_BATCH_SIZE = 20;

  public static final String PIPELINE_INTERRUPT_TOPIC = "pipeline_interrupt";
  public static final int PIPELINE_INTERRUPT_BATCH_SIZE = 20;
  public static final int PIPELINE_INTERRUPT_EVENT_MAX_TOPIC_SIZE = 1000;

  public static final String PIPELINE_FACILITATOR_EVENT_TOPIC = "pipeline_node_facilitation";
  public static final int PIPELINE_FACILITATOR_EVENT_BATCH_SIZE = 20;
  public static final int PIPELINE_FACILITATOR_EVENT_MAX_TOPIC_SIZE = 5000;

  public static final String PIPELINE_NODE_START_EVENT_TOPIC = "pipeline_node_start";
  public static final int PIPELINE_NODE_START_EVENT_BATCH_SIZE = 20;
  public static final int PIPELINE_NODE_START_EVENT_MAX_TOPIC_SIZE = 5000;

  public static final String PIPELINE_PROGRESS_EVENT_TOPIC = "pipeline_node_progress";
  public static final int PIPELINE_PROGRESS_BATCH_SIZE = 20;
  public static final int PIPELINE_PROGRESS_MAX_TOPIC_SIZE = 5000;

  public static final String PIPELINE_NODE_ADVISE_EVENT_TOPIC = "pipeline_node_advise";
  public static final int PIPELINE_NODE_ADVISE_BATCH_SIZE = 20;
  public static final int PIPELINE_NODE_ADVISE_MAX_TOPIC_SIZE = 5000;

  public static final String PIPELINE_NODE_RESUME_EVENT_TOPIC = "pipeline_node_resume";
  public static final int PIPELINE_NODE_RESUME_BATCH_SIZE = 20;
  public static final int PIPELINE_NODE_RESUME_MAX_TOPIC_SIZE = 5000;

  public static final String PIPELINE_EXECUTION_SUMMARY_REDIS_EVENT_CONSUMER =
      "PIPELINE_EXECUTION_SUMMARY_REDIS_EVENT_CONSUMER";
  public static final String MODULE_LICENSES_REDIS_EVENT_CONSUMER = "MODULE_LICENSES_REDIS_EVENT_CONSUMER";
  public static final String PIPELINE_EXECUTION_SUMMARY_REDIS_EVENT_CONSUMER_CD =
      "PIPELINE_EXECUTION_SUMMARY_REDIS_EVENT_CONSUMER_CD";
  public static final String PIPELINE_EXECUTION_SUMMARY_SNAPSHOT_REDIS_EVENT_CONSUMER =
      "PIPELINE_EXECUTION_SUMMARY_SNAPSHOT_REDIS_EVENT_CONSUMER";
  public static final String PIPELINE_EXECUTION_SUMMARY_CD_CONSUMER = "PIPELINE_EXECUTION_SUMMARY_CD_CONSUMER";

  public static final String APPLICATION_TIMESCALE_REDIS_CHANGE_EVENT_CONSUMER =
      "APPLICATION_TIMESCALE_REDIS_CHANGE_EVENT_CONSUMER";
  public static final String CDNG_ORCHESTRATION_EVENT_CONSUMER = "CDNG_ORCHESTRATION_EVENT_CONSUMER";

  public static final String START_PARTIAL_PLAN_CREATOR_EVENT_TOPIC = "pipeline_start_plan";
  public static final int START_PARTIAL_PLAN_CREATOR_BATCH_SIZE = 20;
  public static final int START_PARTIAL_PLAN_CREATOR_MAX_TOPIC_SIZE = 5000;

  public static final String PLAN_NOTIFY_EVENT_PRODUCER = "plan_notify_event_producer";
  public static final String PLAN_NOTIFY_EVENT_TOPIC = "plan_notify_event";
  public static final int PLAN_NOTIFY_EVENT_BATCH_SIZE = 20;
  public static final int PLAN_NOTIFY_EVENT_MAX_TOPIC_SIZE = 5000;

  public static final String PMS_ORCHESTRATION_NOTIFY_EVENT = "pms_orchestration_notify_event";
  public static final String CI_ORCHESTRATION_NOTIFY_EVENT = "ci_orchestration_notify_event";
  public static final String STO_ORCHESTRATION_NOTIFY_EVENT = "sto_orchestration_notify_event";
  public static final int PMS_ORCHESTRATION_NOTIFY_EVENT_BATCH_SIZE = 20;
  public static final int PMS_ORCHESTRATION_NOTIFY_EVENT_MAX_TOPIC_SIZE = 5000;

  public static final String CD_DEPLOYMENT_EVENT = "cd_deployment_event";

  public static final int CD_DEPLOYMENT_EVENT_BATCH_SIZE = 1;
  public static final int CD_DEPLOYMENT_EVENT_MAX_TOPIC_SIZE = 5000;

  public static final String INTERNAL_CHANGE_EVENT_FF = "cf_svc_updates";
  public static final String INTERNAL_CHANGE_EVENT_CE = "chaos_change_events";

  public static final int INTERNAL_CHANGE_EVENT_FF_BATCH_SIZE = 1;
  public static final int INTERNAL_CHANGE_EVENT_CE_BATCH_SIZE = 1;

  public static final String SRM_STATEMACHINE_EVENT = "srm_statemachine_event";
  public static final int SRM_STATEMACHINE_EVENT_BATCH_SIZE = 1;
  public static final int SRM_STATEMACHINE_EVENT_MAX_TOPIC_SIZE = 5000;
  public static final String SRM_STATEMACHINE_LOCK = "srm_statemachine_lock";
  public static final int SRM_STATEMACHINE_LOCK_TIMEOUT = 10;
  public static final int SRM_STATEMACHINE_LOCK_WAIT_TIMEOUT = 5;

  public static final String CUSTOM_CHANGE_EVENT = "srm_custom_change";

  public static final int CUSTOM_CHANGE_EVENT_BATCH_SIZE = 1;
  public static final int CUSTOM_CHANGE_EVENT_MAX_TOPIC_SIZE = 5000;
  public static final int DEFAULT_TOPIC_SIZE = 10000;
  public static final int USER_MEMBERSHIP_TOPIC_SIZE = 100000;
  public static final int ENTITY_CRUD_MAX_TOPIC_SIZE = 100000;
  public static final int SETUP_USAGE_MAX_TOPIC_SIZE = 10000;
  public static final int ENTITY_ACTIVITY_MAX_TOPIC_SIZE = 10000;
  public static final int HARNESS_TO_GIT_PUSH_MAX_TOPIC_SIZE = 10000;
  public static final int WEBHOOK_REQUEST_PAYLOAD_DETAILS_MAX_TOPIC_SIZE = 10000;
  public static final int WEBHOOK_EVENTS_STREAM_MAX_TOPIC_SIZE = 10000;
  public static final int POLLING_EVENTS_STREAM_MAX_TOPIC_SIZE = 10000;
  public static final int TRIGGER_EXECUTION_EVENTS_STREAM_MAX_TOPIC_SIZE = 10000;
  public static final int GIT_PUSH_EVENT_STREAM_MAX_TOPIC_SIZE = 10000;
  public static final int GIT_PR_EVENT_STREAM_MAX_TOPIC_SIZE = 10000;
  public static final int GIT_BRANCH_HOOK_EVENT_STREAM_MAX_TOPIC_SIZE = 10000;
  public static final int GIT_CONFIG_STREAM_MAX_TOPIC_SIZE = 10000;
  public static final int FULL_SYNC_STREAM_MAX_TOPIC_SIZE = 10000;
  public static final int ORCHESTRATION_LOG_MAX_TOPIC_SIZE = 100000;

  public static final Duration DEFAULT_MAX_PROCESSING_TIME = Duration.ofSeconds(10);
  public static final Duration ENTITY_CRUD_MAX_PROCESSING_TIME = Duration.ofSeconds(20);
  public static final Duration NG_ACCOUNT_SETUP_MAX_PROCESSING_TIME = Duration.ofSeconds(10);
  public static final Duration WEBHOOK_EVENTS_STREAM_MAX_PROCESSING_TIME = Duration.ofSeconds(150);
  public static final Duration POLLING_EVENTS_STREAM_MAX_PROCESSING_TIME = Duration.ofSeconds(20);
  public static final Duration TRIGGER_EXECUTION_EVENTS_STREAM_MAX_PROCESSING_TIME = Duration.ofSeconds(20);
  public static final Duration GIT_PUSH_EVENT_STREAM_MAX_PROCESSING_TIME = Duration.ofSeconds(20);
  public static final Duration GIT_PR_EVENT_STREAM_MAX_PROCESSING_TIME = Duration.ofSeconds(20);
  public static final Duration GIT_BRANCH_HOOK_EVENT_STREAM_MAX_PROCESSING_TIME = Duration.ofSeconds(20);
  public static final Duration USERMEMBERSHIP_MAX_PROCESSING_TIME = Duration.ofMinutes(10);
  public static final Duration SETUP_USAGE_MAX_PROCESSING_TIME = Duration.ofSeconds(20);
  public static final Duration ENTITY_ACTIVITY_MAX_PROCESSING_TIME = Duration.ofSeconds(20);
  public static final Duration HARNESS_TO_GIT_PUSH_MAX_PROCESSING_TIME = Duration.ofSeconds(50);
  public static final Duration GIT_CONFIG_STREAM_PROCESSING_TIME = Duration.ofSeconds(20);
  public static final Duration FULL_SYNC_STREAM_PROCESSING_TIME = Duration.ofSeconds(20);
  public static final Duration ORCHESTRATION_LOG_MAX_PROCESSING_TIME = Duration.ofSeconds(30);
  public static final Duration PLAN_NOTIFY_EVENT_MAX_PROCESSING_TIME = Duration.ofSeconds(5);
  public static final Duration CD_DEPLOYMENT_EVENT_MAX_PROCESSING_TIME = Duration.ofSeconds(20);

  public static final Duration INTERNAL_CHANGE_EVENT_MAX_PROCESSING_TIME = Duration.ofSeconds(20);

  public static final Duration CUSTOM_CHANGE_EVENT_MAX_PROCESSING_TIME = Duration.ofSeconds(20);

  public static final int DEFAULT_READ_BATCH_SIZE = 50;
  public static final int ENTITY_CRUD_READ_BATCH_SIZE = 50;
  public static final int NG_ACCOUNT_SETUP_READ_BATCH_SIZE = 50;
  public static final int ORCHESTRATION_LOG_READ_BATCH_SIZE = 200;
  public static final int USERMEMBERSHIP_READ_BATCH_SIZE = 50;
  public static final int SETUP_USAGE_READ_BATCH_SIZE = 50;
  public static final int ENTITY_ACTIVITY_READ_BATCH_SIZE = 50;
  public static final int HARNESS_TO_GIT_PUSH_READ_BATCH_SIZE = 50;
  public static final int GIT_CONFIG_STREAM_READ_BATCH_SIZE = 50;
  public static final int FULL_SYNC_STREAM_READ_BATCH_SIZE = 20;
  public static final int WEBHOOK_EVENTS_STREAM_BATCH_SIZE = 20;
  public static final int POLLING_EVENTS_STREAM_BATCH_SIZE = 50;
  public static final int TRIGGER_EXECUTION_EVENTS_STREAM_BATCH_SIZE = 50;
  public static final int GIT_PUSH_EVENT_STREAM_BATCH_SIZE = 50;
  public static final int GIT_PR_EVENT_STREAM_BATCH_SIZE = 50;
  public static final int GIT_BRANCH_HOOK_EVENT_STREAM_BATCH_SIZE = 50;

  // Tracing Constants
  public static final String QUERY_ANALYSIS_TOPIC = "query_analysis";
  public static final int QUERY_ANALYSIS_TOPIC_SIZE = 100;

  // CG WaitNotify constants

  public static final String CG_NOTIFY_EVENT = "cg_notify_event";
  public static final int CG_NOTIFY_EVENT_TOPIC_SIZE = 100000;
  public static final int CG_NOTIFY_EVENT_BATCH_SIZE = 50;
  public static final Duration CG_NOTIFY_EVENT_MAX_PROCESSING_TIME = Duration.ofMinutes(10);

  public static final String CG_GENERAL_EVENT = "cg_general_event";
  public static final int CG_GENERAL_EVENT_TOPIC_SIZE = 100000;
  public static final int CG_GENERAL_EVENT_BATCH_SIZE = 50;
  public static final Duration CG_GENERAL_EVENT_MAX_PROCESSING_TIME = Duration.ofMinutes(10);
}
