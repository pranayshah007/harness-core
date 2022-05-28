/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.vm.runner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExecuteStepRequest {
  @JsonProperty("correlation_id") String correlationID;
  @JsonProperty("ip_address") String ipAddress;
  @JsonProperty("pool_id") String poolId;
  @JsonProperty("start_step_request") Config config;

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Config {
    @JsonProperty("id") String id;
    @JsonProperty("detach") boolean detach;
    @JsonProperty("environment") Map<String, String> envs;
    @JsonProperty("name") String name;
    @JsonProperty("log_key") String logKey;
    @JsonProperty("secrets") @Singular List<String> secrets;
    @JsonProperty("working_dir") String workingDir;
    @JsonProperty("kind") String kind;
    @JsonProperty("run") RunConfig runConfig;
    @JsonProperty("run_test") RunTestConfig runTestConfig;
    @JsonProperty("output_vars") List<String> outputVars;
    @JsonProperty("test_report") TestReport testReport;
    @JsonProperty("timeout") long timeout;
    @JsonProperty("image") String image;
    @JsonProperty("pull") String pull;
    @JsonProperty("privileged") boolean privileged;
    @JsonProperty("port_bindings") Map<String, String> portBindings;
    @JsonProperty("user") String user;
    @JsonProperty("volumes") List<VolumeMount> volumeMounts;
    @JsonProperty("auth") ImageAuth imageAuth;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ImageAuth {
    @JsonProperty("address") String address;
    @JsonProperty("username") String username;
    @JsonProperty("password") String password;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TestReport {
    @JsonProperty("kind") String kind;
    @JsonProperty("junit") JunitReport junitReport;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class JunitReport {
    @JsonProperty("paths") List<String> paths;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class VolumeMount {
    @JsonProperty("name") String name;
    @JsonProperty("path") String path;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class RunConfig {
    @JsonProperty("commands") List<String> command;
    @JsonProperty("entrypoint") List<String> entrypoint;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class RunTestConfig {
    @JsonProperty("args") String args;
    @JsonProperty("entrypoint") List<String> entrypoint;
    @JsonProperty("pre_command") String preCommand;
    @JsonProperty("post_command") String postCommand;
    @JsonProperty("build_tool") String buildTool;
    @JsonProperty("language") String language;
    @JsonProperty("packages") String packages;
    @JsonProperty("namespaces") String namespaces;
    @JsonProperty("run_only_selected_tests") boolean runOnlySelectedTests;
    @JsonProperty("test_annotations") String testAnnotations;
    @JsonProperty("build_environment") String buildEnvironment;
    @JsonProperty("framework_version") String frameworkVersion;
  }
}
