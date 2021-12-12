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
public class SetupVmRequest {
  @JsonProperty("correlation_id") String correlationID;
  @JsonProperty("pool_id") String poolID;
  @JsonProperty("id") String id;
  @JsonProperty("setup_request") Config config;

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Config {
    @JsonProperty("envs") Map<String, String> envs;
    @JsonProperty("secrets") List<String> secrets;
    @JsonProperty("network") Network network;
    @JsonProperty("volumes") List<Volume> volumes;
    @JsonProperty("log_config") LogConfig logConfig;
    @JsonProperty("ti_config") TIConfig tiConfig;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class LogConfig {
    @JsonProperty("url") String url;
    @JsonProperty("token") String token;
    @JsonProperty("account_id") String accountID;
    @JsonProperty("indirect_upload") boolean indirectUpload;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class TIConfig {
    @JsonProperty("url") String url;
    @JsonProperty("token") String token;
    @JsonProperty("account_id") String accountID;
    @JsonProperty("org_id") String orgID;
    @JsonProperty("project_id") String projectID;
    @JsonProperty("pipeline_id") String pipelineID;
    @JsonProperty("stage_id") String stageID;
    @JsonProperty("build_id") String buildID;
    @JsonProperty("repo") String repo;
    @JsonProperty("sha") String sha;
    @JsonProperty("source_branch") String sourceBranch;
    @JsonProperty("target_branch") String targetBranch;
    @JsonProperty("commit_branch") String commitBranch;
    @JsonProperty("commit_link") String commitLink;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Network {
    @JsonProperty("id") String id;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Volume {
    @JsonProperty("host") HostVolume hostVolume;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class HostVolume {
    @JsonProperty("id") String id;
    @JsonProperty("name") String name;
    @JsonProperty("path") String path;
  }
}
