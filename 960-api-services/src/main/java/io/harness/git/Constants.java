/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.git;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDP)
public interface Constants {
  String GIT_YAML_LOG_PREFIX = "GIT_YAML_LOG_ENTRY: ";
  String GIT_TERRAFORM_LOG_PREFIX = "GIT_TERRAFORM_LOG_ENTRY: ";
  String GIT_TERRAGRUNT_LOG_PREFIX = "GIT_TERRAGRUNT_LOG_ENTRY: ";
  String GIT_TRIGGER_LOG_PREFIX = "GIT_TRIGGER_LOG_PREFIX: ";
  String GIT_DEFAULT_LOG_PREFIX = "GIT_DEFAULT_LOG_PREFIX: ";
  String GIT_HELM_LOG_PREFIX = "GIT_HELM_LOG_ENTRY: ";
  String GIT_SETUP_RBAC_PREFIX = "GIT_SETUP_RBAC_PREFIX: ";
  String REPOSITORY = "./repository";
  String GIT_REPO_BASE_DIR = "./repository/${REPO_TYPE}/${ACCOUNT_ID}/${CONNECTOR_ID}/${REPO_NAME}/${REPO_URL_HASH}";
  String REPOSITORY_GIT_FILE_DOWNLOADS = "./repository/gitFileDownloads";
  String REPOSITORY_GIT_LOCK_DIR = "./repository/gitFileDownloads/.locks";
  String REPOSITORY_GIT_LOCK_FIlE = REPOSITORY_GIT_LOCK_DIR + "/lock_%s";
  String REPOSITORY_GIT_FILE_DOWNLOADS_ACCOUNT = "./repository/gitFileDownloads/{ACCOUNT_ID}";
  String REPOSITORY_GIT_FILE_DOWNLOADS_BASE = "./repository/gitFileDownloads/{ACCOUNT_ID}/{CONNECTOR_ID}";
  String REPOSITORY_GIT_FILE_DOWNLOADS_REPO_BASE_DIR = REPOSITORY_GIT_FILE_DOWNLOADS_BASE + "/{REPO_NAME}";
  String REPOSITORY_GIT_FILE_DOWNLOADS_REPO_DIR = REPOSITORY_GIT_FILE_DOWNLOADS_REPO_BASE_DIR + "/{REPO_URL_HASH}";
  String PATH_DELIMITER = "/";
  String HARNESS_IO_KEY_ = "Harness.io";
  String HARNESS_SUPPORT_EMAIL_KEY = "support@harness.io";
  String COMMIT_MESSAGE = "Harness IO Git Sync.\n";
  String EXCEPTION_STRING = "Exception: ";
  String DEFAULT_FETCH_IDENTIFIER = "__DEFAULT";
}
