/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.interceptor;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
@OwnedBy(DX)
public class GitSyncConstants {
  public static final String DEFAULT = "__default__";
  public static final String COMMIT_MSG = "Harness.io Git Commit";
  public static final String DEFAULT_USER_NAME = "Harness_user";
  public static final String DEFAULT_USER_EMAIL_ID = "user@harness.io";
  public static final String FOLDER_PATH = ".harness/";
  public static final String TRUE_VALUE = "true";
  public static final String GIT_CLIENT_ENABLED_SETTING = "enable_git_commands";
  public static final String ALLOW_DIFFERENT_REPO_FOR_PIPELINE_AND_INPUT_SETS =
      "allow_different_repo_for_pipeline_and_input_sets";
  public static final String ENFORCE_GIT_EXPERIENCE = "enforce_git_experience";
  public static final String DEFAULT_CONNECTOR_FOR_GIT_EXPERIENCE = "default_connector_for_git_experience";
  public static final String DEFAULT_STORE_TYPE_FOR_ENTITIES = "default_store_type_for_entities";
  public static final String REPO_ALLOWLIST_FOR_GIT_EXPERIENCE = "git_experience_repo_allowlist";
}
