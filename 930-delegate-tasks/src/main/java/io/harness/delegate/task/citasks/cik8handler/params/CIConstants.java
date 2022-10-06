/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.cik8handler.params;

public final class CIConstants {
  private CIConstants() {
    // do nothing
  }

  public static final String MEMORY = "memory";
  public static final String MEMORY_FORMAT = "Mi";
  public static final String CPU = "cpu";
  public static final String CPU_FORMAT = "m";
  public static final String STORAGE = "storage";
  public static final String STORAGE_FORMAT = "Mi";
  public static final String PVC_READ_WRITE_ONCE = "ReadWriteOnce";
  public static final String RESTART_POLICY = "Never";
  public static final long STAGE_MAX_TTL_SECS = 86400L; // 1 day
  public static final long STAGE_MAX_TTL_SECS_HOSTED_FREE = 3600L; // 1 hour

  public static final long POD_MAX_TTL_SECS = 86400L; // 1 day
  public static final long POD_MAX_TTL_SECS_HOSTED_FREE = 3600L; // 1 hour
  public static final String POD_PENDING_PHASE = "Pending";
  public static final String POD_RUNNING_PHASE = "Running";
  public static final int POD_MAX_WAIT_UNTIL_READY_SECS = 8 * 60; // 8 minutes
  public static final int POD_WAIT_UNTIL_READY_SLEEP_SECS = 2; // Time to sleep in between pod status checks

  public static final String SECRET_VOLUME_NAME = "secrets";
  public static final int SECRET_FILE_MODE = 256;
  public static final String DEFAULT_SECRET_MOUNT_PATH = "/etc/secrets/";

  public static final String PLUGIN_DOCKER_IMAGE_NAME = "plugins/docker";
  public static final String PLUGIN_ECR_IMAGE_NAME = "plugins/ecr";
  public static final String PLUGIN_ACR_IMAGE_NAME = "plugins/acr";
  public static final String PLUGIN_GCR_IMAGE_NAME = "plugins/gcr";
  public static final String PLUGIN_HEROKU_IMAGE_NAME = "plugins/heroku";

  public static final String DOCKER_IMAGE_NAME = "docker";
  public static final String DIND_TAG_REGEX = "(.*)dind(.*)";
}
