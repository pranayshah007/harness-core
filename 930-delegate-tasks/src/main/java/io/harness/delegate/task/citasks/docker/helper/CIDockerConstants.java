/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.docker.helper;

public class CIDockerConstants {
  public static final String RUNNER_URL = "http://127.0.0.1:3000/";
  public static final String RUNNER_URL_ENV = "RUNNER_URL";
  public static final int RUNNER_CONNECT_TIMEOUT_SECS = 1;
  public static final String RUN_STEP_KIND = "Run";
  //public static final String RUNTEST_STEP_KIND = "RunTest";
  //public static final String JUNIT_REPORT_KIND = "Junit";
  public static final String DOCKER_NETWORK_ID = "drone_docker";
  //public static final String WORKDIR_VOLUME_NAME = "_workspace";
  //public static final String WORKDIR_VOLUME_ID = "drone";
  public static final String DRONE_REMOTE_URL = "DRONE_REMOTE_URL";
  public static final String DRONE_COMMIT_SHA = "DRONE_COMMIT_SHA";
  public static final String DRONE_SOURCE_BRANCH = "DRONE_SOURCE_BRANCH";
  public static final String DRONE_TARGET_BRANCH = "DRONE_TARGET_BRANCH";
  public static final String DRONE_COMMIT_BRANCH = "DRONE_COMMIT_BRANCH";
  public static final String DRONE_COMMIT_LINK = "DRONE_COMMIT_LINK";
  //public static final String DOCKER_REGISTRY_V2 = "https://index.docker.io/v2/";
  //public static final String DOCKER_REGISTRY_V1 = "https://index.docker.io/v1/";
}
