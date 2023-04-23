/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.commonconstants;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CI)
public class BuildEnvironmentConstants {
  public static final String DRONE_BUILD_NUMBER = "DRONE_BUILD_NUMBER";

  public static final String DRONE_REPO = "DRONE_REPO";
  public static final String DRONE_REPO_SCM = "DRONE_REPO_SCM";
  public static final String DRONE_REPO_OWNER = "DRONE_REPO_OWNER";
  public static final String DRONE_REPO_NAMESPACE = "DRONE_REPO_NAMESPACE";
  public static final String DRONE_REPO_NAME = "DRONE_REPO_NAME";
  public static final String DRONE_REPO_LINK = "DRONE_REPO_LINK";
  public static final String DRONE_REPO_BRANCH = "DRONE_REPO_BRANCH";
  public static final String DRONE_REMOTE_URL = "DRONE_REMOTE_URL";
  public static final String DRONE_GIT_HTTP_URL = "DRONE_GIT_HTTP_URL";
  public static final String DRONE_GIT_SSH_URL = "DRONE_GIT_SSH_URL";
  public static final String DRONE_REPO_PRIVATE = "DRONE_REPO_PRIVATE";

  public static final String DRONE_BRANCH = "DRONE_BRANCH";
  public static final String DRONE_SOURCE_BRANCH = "DRONE_SOURCE_BRANCH";
  public static final String DRONE_TARGET_BRANCH = "DRONE_TARGET_BRANCH";
  public static final String DRONE_COMMIT = "DRONE_COMMIT";
  public static final String DRONE_COMMIT_SHA = "DRONE_COMMIT_SHA";
  public static final String DRONE_COMMIT_BEFORE = "DRONE_COMMIT_BEFORE";
  public static final String DRONE_COMMIT_AFTER = "DRONE_COMMIT_AFTER";
  public static final String DRONE_COMMIT_REF = "DRONE_COMMIT_REF";
  public static final String DRONE_COMMIT_BRANCH = "DRONE_COMMIT_BRANCH";
  public static final String DRONE_COMMIT_LINK = "DRONE_COMMIT_LINK";
  public static final String DRONE_COMMIT_MESSAGE = "DRONE_COMMIT_MESSAGE";
  public static final String DRONE_COMMIT_AUTHOR = "DRONE_COMMIT_AUTHOR";
  public static final String DRONE_BUILD_LINK = "DRONE_BUILD_LINK";
  public static final String DRONE_PULL_REQUEST_TITLE = "DRONE_PULL_REQUEST_TITLE";
  public static final String DRONE_COMMIT_AUTHOR_EMAIL = "DRONE_COMMIT_AUTHOR_EMAIL";
  public static final String DRONE_COMMIT_AUTHOR_AVATAR = "DRONE_COMMIT_AUTHOR_AVATAR";
  public static final String DRONE_COMMIT_AUTHOR_NAME = "DRONE_COMMIT_AUTHOR_NAME";
  public static final String DRONE_BUILD_ACTION = "DRONE_BUILD_ACTION";
  public static final String DRONE_BUILD_EVENT = "DRONE_BUILD_EVENT";

  public static final String DRONE_TAG = "DRONE_TAG";
  public static final String DRONE_NETRC_MACHINE = "DRONE_NETRC_MACHINE";
  public static final String DRONE_NETRC_USERNAME = "DRONE_NETRC_USERNAME";
  public static final String DRONE_NETRC_PORT = "DRONE_NETRC_PORT";
  public static final String DRONE_NETRC_PASSWORD = "DRONE_NETRC_PASSWORD";
  public static final String SSH_KEY = "SSH_KEY";
  public static final String DRONE_AWS_REGION = "DRONE_AWS_REGION";
}
