/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

variable "metric_list_1" {
  default = [
    "artifacts_collection_count",
    "audits_count",
    "audits_files_count",
    "audits_files_average_size",
    "whitelist_count",
    "delegates_total_count",
    "delegates_connected_count",
    "delegate_tasks_count",
    "delegate_tasks_avg_broadcast_count",
    "delegate_tasks_started_count",
    "delegate_tasks_queued_count",
    "delegate_tasks_finished_count",
    "delegate_tasks_sync_count",
    "delegate_tasks_async_count",
    "notify_responses_count",
    "notify_queues_count",
    "execution_queues_count",
    "wait_instances_count",
    "collector_queues_count",
    "delay_queues_count",
    "deployment_queues_count",
    "deployment_timeseries_queues_count",
    "email_queues_count",
    "instance_change_queues_count",
    "instance_queues_count"
  ]
}

variable "metric_list_2" {
  default = [
    "kms_transition_queues_count",
    "prune_queues_count",
    "generic_queues_count",
    "instance_count",
    "alerts_count",
    "alerts_open_count",
    "perpetualtask_count",
    "perpetualtask_unassigned_count",
    "workflow_executions_count",
    "workflow_executions_document_size",
    "state_execution_instances_count",
    "state_execution_instances_document_size",
    "sweeping_output_instances_count",
    "sweeping_output_instances_document_size",
    "barrier_instances_count",
    "resource_constraint_instances_count",
    "resource_secret_usage_logs_count",
    "workflow_running_count",
    "yamlchangeset_count",
    "yamlchangeset_completed_count",
    "yamlchangeset_queued_count",
    "yamlchangeset_running_count",
    "yamlchangeset_failed_count",
    "yamlchangeset_skipped_count",
    "gitcommits_count",
  ]
}

variable "metric_list_3" {
  default = [
    "gitcommits_completed_count",
    "gitcommits_queued_count",
    "gitcommits_running_count",
    "gitcommits_failed_count"
  ]
}

data "template_file" "widget_template_1" {
  count    = length(var.metric_list_1)
  template = file("${path.module}/templates/widget.tpl")
  vars = {
    widget_title = title(replace(element(var.metric_list_1, count.index), "_", " "))
    metric_type = "custom.googleapis.com/user/${local.name_prefix}_${element(var.metric_list_1, count.index)}"
  }
}

data "template_file" "widget_template_2" {
  count    = length(var.metric_list_2)
  template = file("${path.module}/templates/widget.tpl")
  vars = {
    widget_title = title(replace(element(var.metric_list_2, count.index), "_", " "))
    metric_type = "custom.googleapis.com/user/${local.name_prefix}_${element(var.metric_list_2, count.index)}"
  }
}

data "template_file" "widget_template_3" {
  count    = length(var.metric_list_3)
  template = file("${path.module}/templates/widget.tpl")
  vars = {
    widget_title = title(replace(element(var.metric_list_3, count.index), "_", " "))
    metric_type = "custom.googleapis.com/user/${local.name_prefix}_${element(var.metric_list_3, count.index)}"
  }
}
