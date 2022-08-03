/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.common;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDP)
public enum NGTaskType {
  DOCKER_ARTIFACT_TASK_NG,
  GCR_ARTIFACT_TASK_NG,
  ECR_ARTIFACT_TASK_NG,
  NEXUS_ARTIFACT_TASK_NG,
  ARTIFACTORY_ARTIFACT_TASK_NG,
  GIT_FETCH_NEXT_GEN_TASK,
  K8S_COMMAND_TASK_NG,
  K8S_COMMAND_TASK,
  JIRA_TASK_NG,
  HTTP_TASK_NG,
  EMAIL_TASK_NG,
  GCP_TASK,
  SERVICENOW_TASK_NG,
  ACR_ARTIFACT_TASK_NG,
  JENKINS_ARTIFACT_TASK_NG,
  GITHUB_PACKAGES_TASK_NG
}
