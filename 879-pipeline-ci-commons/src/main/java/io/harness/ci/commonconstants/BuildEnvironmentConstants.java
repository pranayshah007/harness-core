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
  public static final String CI = "CI";
  public static final String DRONE = "DRONE";
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

  public static final String DRONE_REPO_VISIBILITY = "DRONE_REPO_VISIBILITY";
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
  public static final String DRONE_BUILD_CREATED = "DRONE_BUILD_CREATED";
  public static final String DRONE_BUILD_FINISHED = "DRONE_BUILD_FINISHED";
  public static final String DRONE_BUILD_PARENT = "DRONE_BUILD_PARENT";
  public static final String DRONE_BUILD_STARTED = "DRONE_BUILD_STARTED";
  public static final String DRONE_BUILD_STATUS = "DRONE_BUILD_STATUS";
  public static final String DRONE_BUILD_TRIGGER = "DRONE_BUILD_TRIGGER";
  public static final String DRONE_PULL_REQUEST = "DRONE_PULL_REQUEST";
  public static final String DRONE_BUILD_EVENT = "DRONE_BUILD_EVENT";
  public static final String DRONE_CALVER = "DRONE_CALVER";
  public static final String DRONE_CALVER_SHORT = "DRONE_CALVER_SHORT";
  public static final String DRONE_CALVER_MAJOR_MINOR = "DRONE_CALVER_MAJOR_MINOR";
  public static final String DRONE_CALVER_MAJOR = "DRONE_CALVER_MAJOR";
  public static final String DRONE_CALVER_MINOR = "DRONE_CALVER_MINOR";
  public static final String DRONE_CALVER_MICRO = "DRONE_CALVER_MICRO";
  public static final String DRONE_CALVER_MODIFIER = "DRONE_CALVER_MODIFIER";

  public static final String DRONE_TAG = "DRONE_TAG";
  public static final String DRONE_DEPLOY_TO = "DRONE_DEPLOY_TO";
  public static final String DRONE_DEPLOY_ID = "DRONE_DEPLOY_ID";
  public static final String DRONE_FAILED_STAGES = "DRONE_FAILED_STAGES";
  public static final String DRONE_FAILED_STEPS = "DRONE_FAILED_STEPS";
  public static final String DRONE_SEMVER = "DRONE_SEMVER";
  public static final String DRONE_SEMVER_BUILD = "DRONE_SEMVER_BUILD";
  public static final String DRONE_SEMVER_ERROR = "DRONE_SEMVER_ERROR";
  public static final String DRONE_SEMVER_MAJOR = "DRONE_SEMVER_MAJOR";
  public static final String DRONE_SEMVER_MINOR = "DRONE_SEMVER_MINOR";
  public static final String DRONE_SEMVER_PATCH = "DRONE_SEMVER_PATCH";
  public static final String DRONE_SEMVER_PRERELEASE = "DRONE_SEMVER_PRERELEASE";
  public static final String DRONE_SEMVER_SHORT = "DRONE_SEMVER_SHORT";
  public static final String DRONE_STAGE_ARCH = "DRONE_STAGE_ARCH";
  public static final String DRONE_STAGE_DEPENDS_ON = "DRONE_STAGE_DEPENDS_ON";
  public static final String DRONE_STAGE_FINISHED = "DRONE_STAGE_FINISHED";
  public static final String DRONE_STAGE_KIND = "DRONE_STAGE_KIND";
  public static final String DRONE_STAGE_MACHINE = "DRONE_STAGE_MACHINE";
  public static final String DRONE_STAGE_NAME = "DRONE_STAGE_NAME";
  public static final String DRONE_STAGE_NUMBER = "DRONE_STAGE_NUMBER";
  public static final String DRONE_STAGE_OS = "DRONE_STAGE_OS";
  public static final String DRONE_STAGE_STARTED = "DRONE_STAGE_STARTED";
  public static final String DRONE_STAGE_STATUS = "DRONE_STAGE_STATUS";
  public static final String DRONE_STAGE_TYPE = "DRONE_STAGE_TYPE";
  public static final String DRONE_STAGE_VARIANT = "DRONE_STAGE_VARIANT";
  public static final String DRONE_STEP_NAME = "DRONE_STEP_NAME";
  public static final String DRONE_STEP_NUMBER = "DRONE_STEP_NUMBER";
  public static final String DRONE_SYSTEM_HOST = "DRONE_SYSTEM_HOST";
  public static final String DRONE_SYSTEM_HOSTNAME = "DRONE_SYSTEM_HOSTNAME";
  public static final String DRONE_SYSTEM_PROTO = "DRONE_SYSTEM_PROTO";
  public static final String DRONE_SYSTEM_VERSION = "DRONE_SYSTEM_VERSION";
  public static final String DRONE_WORKSPACE = "DRONE_WORKSPACE";
  public static final String DRONE_NETRC_MACHINE = "DRONE_NETRC_MACHINE";
  public static final String DRONE_NETRC_USERNAME = "DRONE_NETRC_USERNAME";
  public static final String DRONE_NETRC_PORT = "DRONE_NETRC_PORT";
  public static final String DRONE_NETRC_PASSWORD = "DRONE_NETRC_PASSWORD";
  public static final String SSH_KEY = "SSH_KEY";
  public static final String DRONE_AWS_REGION = "DRONE_AWS_REGION";
  public static final String DRONE_CARD_PATH = "DRONE_CARD_PATH";

  public static final String CI_REPO = "CI_REPO";
  public static final String CI_REPO_NAME = "CI_REPO_NAME";
  public static final String CI_REPO_LINK = "CI_REPO_LINK";
  public static final String CI_REPO_REMOTE = "CI_REPO_REMOTE";
  public static final String CI_REMOTE_URL = "CI_REMOTE_URL";
  public static final String CI_REPO_PRIVATE = "CI_REPO_PRIVATE";
  public static final String CI_BUILD_NUMBER = "CI_BUILD_NUMBER";
  public static final String CI_PARENT_BUILD_NUMBER = "CI_PARENT_BUILD_NUMBER";
  public static final String CI_BUILD_CREATED = "CI_BUILD_CREATED";
  public static final String CI_BUILD_STARTED = "CI_BUILD_STARTED";
  public static final String CI_BUILD_FINISHED = "CI_BUILD_FINISHED";
  public static final String CI_BUILD_STATUS = "CI_BUILD_STATUS";
  public static final String CI_BUILD_EVENT = "CI_BUILD_EVENT";
  public static final String CI_BUILD_LINK = "CI_BUILD_LINK";
  public static final String CI_BUILD_TARGET = "CI_BUILD_TARGET";
  public static final String CI_COMMIT_SHA = "CI_COMMIT_SHA";
  public static final String CI_COMMIT_REF = "CI_COMMIT_REF";
  public static final String CI_COMMIT_BRANCH = "CI_COMMIT_BRANCH";
  public static final String CI_COMMIT_MESSAGE = "CI_COMMIT_MESSAGE";
  public static final String CI_COMMIT_AUTHOR = "CI_COMMIT_AUTHOR";
  public static final String CI_COMMIT_AUTHOR_NAME = "CI_COMMIT_AUTHOR_NAME";
  public static final String CI_COMMIT_AUTHOR_EMAIL = "CI_COMMIT_AUTHOR_EMAIL";
  public static final String CI_COMMIT_AUTHOR_AVATAR = "CI_COMMIT_AUTHOR_AVATAR";
}
