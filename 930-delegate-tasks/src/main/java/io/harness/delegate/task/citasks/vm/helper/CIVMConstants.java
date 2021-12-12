package io.harness.delegate.task.citasks.vm.helper;

public class CIVMConstants {
  public static final String RUNNER_URL = "http://127.0.0.1:3000/";
  public static final int RUNNER_CONNECT_TIMEOUT_SECS = 1;
  public static final String RUN_STEP_KIND = "Run";
  public static final String RUNTEST_STEP_KIND = "RunTest";
  public static final String JUNIT_REPORT_KIND = "Junit";
  public static final String NETWORK_ID = "drone";
  public static final String WORKDIR_VOLUME_NAME = "_workspace";
  public static final String WORKDIR_VOLUME_ID = "drone";
  public static final String DRONE_REMOTE_URL = "DRONE_REMOTE_URL";
  public static final String DRONE_COMMIT_SHA = "DRONE_COMMIT_SHA";
  public static final String DRONE_SOURCE_BRANCH = "DRONE_SOURCE_BRANCH";
  public static final String DRONE_TARGET_BRANCH = "DRONE_TARGET_BRANCH";
  public static final String DRONE_COMMIT_BRANCH = "DRONE_COMMIT_BRANCH";
  public static final String DRONE_COMMIT_LINK = "DRONE_COMMIT_LINK";
}
