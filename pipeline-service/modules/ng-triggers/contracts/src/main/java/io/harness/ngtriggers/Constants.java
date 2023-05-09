/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PIPELINE)
public interface Constants {
  String PR = "PR";
  String PUSH = "PUSH";

  // Webhook Triggers
  String AZURE_REPO = "AzureRepo";
  String GITHUB_REPO = "Github";
  String GITLAB_REPO = "Gitlab";
  String BITBUCKET_REPO = "Bitbucket";
  String AWS_CODECOMMIT_REPO = "AwsCodeCommit";
  String CUSTOM_REPO = "Custom";

  // Artifact Trigger

  String DOCKER_REGISTRY = "DockerRegistry";
  String NEXUS3_REGISTRY = "Nexus3Registry";
  String NEXUS2_REGISTRY = "Nexus2Registry";
  String ARTIFACTORY_REGISTRY = "ArtifactoryRegistry";
  String GCR = "Gcr";
  String ECR = "Ecr";
  String ACR = "Acr";
  String JENKINS = "Jenkins";
  String BAMBOO = "Bamboo";
  String AMAZON_S3 = "AmazonS3";
  String CUSTOM_ARTIFACT = "CustomArtifact";
  String GOOGLE_ARTIFACT_REGISTRY = "GoogleArtifactRegistry";
  String GITHUB_PACKAGES = "GithubPackageRegistry";
  String AZURE_ARTIFACTS = "AzureArtifacts";
  String AMI = "AmazonMachineImage";
  String GOOGLE_CLOUD_STORAGE = "GoogleCloudStorage";

  // Manifest Triggers
  String HELM_CHART = "HelmChart";

  // Cron
  String CRON = "Cron";

  String PULL_REQUEST_EVENT_TYPE = "PullRequest";
  String MERGE_REQUEST_EVENT_TYPE = "MergeRequest";
  String PUSH_EVENT_TYPE = "Push";
  String ISSUE_COMMENT_EVENT_TYPE = "IssueComment";
  String RELEASE_EVENT_TYPE = "Release";
  String MR_COMMENT_EVENT_TYPE = "MRComment";
  String PR_COMMENT_EVENT_TYPE = "PRComment";

  String TRIGGER_EXECUTION_TAG_TAG_VALUE_DELIMITER = ":";
  String TRIGGER_REF_DELIMITER = "/";
  String TRIGGER_REF = "triggerRef";
  String EVENT_CORRELATION_ID = "eventCorrelationId";

  String BRANCH = "branch";
  String TARGET_BRANCH = "targetBranch";
  String SOURCE_BRANCH = "sourceBranch";
  String EVENT = "event";
  String PR_NUMBER = "prNumber";
  String COMMIT_SHA = "commitSha";
  String BASE_COMMIT_SHA = "baseCommitSha";
  String TYPE = "type";
  String PAYLOAD = "payload";
  String EVENT_PAYLOAD = "eventPayload";
  String HEADER = "header";
  String REPO_URL = "repoUrl";
  String GIT_USER = "gitUser";
  String TAG = "tag";
  String PR_TITLE = "prTitle";
  String SOURCE_TYPE = "sourceType";
  String ARTIFACT_EXPR = "artifact";
  String MANIFEST_EXPR = "manifest";
  String MANIFEST_VERSION_EXPR = "version";
  String ARTIFACT_BUILD_EXPR = "build";
  String ARTIFACT_METADATA_EXPR = "metadata";

  String WEBHOOK_TYPE = "Webhook";
  String SCHEDULED_TYPE = "Scheduled";
  String CUSTOM_TYPE = "Custom";
  String ARTIFACT_TYPE = "Artifact";
  String MANIFEST_TYPE = "Manifest";

  String GITHUB_REPO_EXPR_VAL = "Github";
  String GITLAB_REPO_EXPR_VAL = "Gitlab";
  String BITBUCKET_REPO_EXPR_VAL = "Bitbucket";
  String CUSTOM_REPO_EXPR_VAL = "Custom";
  String AWS_CODECOMMIT_REPO_EXPR_VAL = "AwsCodeCommit";

  String CHANGED_FILES = "changedFiles";
  String TRIGGER_ERROR_LOG = "TRIGGER_ERROR_LOG: ";
  String TRIGGER_INFO_LOG = "TRIGGER_INFO_LOG: ";
  String TRIGGER_PAYLOAD_COMMITS = "<+trigger.payload.commits>";
  String TRIGGER_PAYLOAD_BRANCH = "<+trigger.payload.branch>";
  String COMMIT_FILE_ADDED = "added";
  String COMMIT_FILE_MODIFIED = "modified";
  String COMMIT_FILE_REMOVED = "removed";

  String GITHUB_LOWER_CASE = "github";
  String GITLAB_LOWER_CASE = "gitlab";
  String BITBUCKET_LOWER_CASE = "bitbucket";

  String TRIGGER_KEY = "triggerIdentifier";
  String WEBHOOK_TOKEN = "webhookToken";

  String DOT_GIT = ".git";
  String MANIFEST = "Manifest";
  String ARTIFACT = "Artifact";
  String ARTIFACT_REF = "artifactRef";
  String MANIFEST_REF = "manifestRef";

  String MANIFEST_VERSION = "<+trigger.manifest.version>";
  String ARTIFACT_VERSION = "<+trigger.artifact.build>";
  String TRIGGER_CATEGORY = "Category of this Trigger.";
  String TRIGGER_TYPE_LIST_BY_CATEGORY = "List of Trigger types corresponding to a specific category.";
  String TRIGGER_CATALOGUE_LIST = "List of Trigger category and Trigger types corresponding to a specific category.";
  String TRIGGERS_MANDATE_GITHUB_AUTHENTICATION = "mandate_webhook_secrets_for_github_triggers";
  String MANDATE_GITHUB_AUTHENTICATION_TRUE_VALUE = "true";
  String PIPELINE_INPUTS_VALIDATION_ERROR = "PipelineInputsErrorMetadataV2";
  String MANDATE_CUSTOM_WEBHOOK_AUTHORIZATION = "mandate_custom_webhook_authorization";
  String MANDATE_CUSTOM_WEBHOOK_TRUE_VALUE = "true";
  String ENABLE_NODE_EXECUTION_AUDIT_EVENTS = "enable_node_execution_audit_events";
  String ENABLE_NODE_EXECUTION_AUDIT_EVENTS_TRUE_VALUE = "true";
  String API_SAMPLE_TRIGGER_YAML = "trigger:\n"
      + "  name: Trigger\n"
      + "  identifier: Trigger\n"
      + "  enabled: true\n"
      + "  orgIdentifier: default\n"
      + "  projectIdentifier: Terraform_Provider\n"
      + "  pipelineIdentifier: Terraform_NG_Acc_Tests_With_Notifications\n"
      + "  source:\n"
      + "    type: Scheduled\n"
      + "    spec:\n"
      + "      type: Cron\n"
      + "      spec:\n"
      + "        expression: 0 8,20 * * *\n"
      + "  inputYaml: |\n"
      + "    pipeline:\n"
      + "      identifier: Terraform_NG_Acc_Tests_With_Notifications\n"
      + "      properties:\n"
      + "        ci:\n"
      + "          codebase:\n"
      + "            build:\n"
      + "              type: branch\n"
      + "              spec:\n"
      + "                branch: main";
}
