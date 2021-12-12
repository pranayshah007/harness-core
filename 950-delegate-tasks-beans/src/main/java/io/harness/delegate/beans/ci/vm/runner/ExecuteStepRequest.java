package io.harness.delegate.beans.ci.vm.runner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExecuteStepRequest {
  @JsonProperty("ip_address") String ipAddress;
  @JsonProperty("pool_id") String poolId;
  @JsonProperty("start_step_request") Config config;

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Config {
    @JsonProperty("id") String id;
    @JsonProperty("detach") boolean detach;
    @JsonProperty("envs") Map<String, String> envs;
    @JsonProperty("name") String name;
    @JsonProperty("log_key") String logKey;
    @JsonProperty("secrets") List<String> secrets;
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
    @JsonProperty("user") String user;
    @JsonProperty("volumes") List<VolumeMount> volumeMounts;
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
    @JsonProperty("run_only_selected_tests") boolean runOnlySelectedTests;
    @JsonProperty("test_annotations") String testAnnotations;
  }
}