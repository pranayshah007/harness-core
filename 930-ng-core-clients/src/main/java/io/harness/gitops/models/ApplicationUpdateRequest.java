/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gitops.models;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(GITOPS)
public class ApplicationUpdateRequest {
  Application application;

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Application {
    @JsonProperty("spec") ApplicationSpec applicationSpec;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ApplicationSpec {
    String project;
    @JsonProperty("source") AppSource applicationSource;
    @JsonProperty("destination") AppDestination applicationDestination;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class AppSource {
    String repoURL;
    String path;
    String targetRevision;
    @JsonProperty("helm") HelmSource helmSource;
    @JsonProperty("kustomize") KustomizeSource kustomizeSource; // not used at the moment
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class HelmSource {
    List<String> valueFiles;
    List<HelmSourceParameters> parameters;
    List<HelmSourceFileParameters> fileParameters; // TODO: got permission denied need to test and add later
    String values; // not used since we can edit these values via "parameters" field
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class HelmSourceParameters {
    String name;
    String value;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class HelmSourceFileParameters {
    String name;
    String path;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class KustomizeSource {
    String namePrefix;
    String nameSuffix;
    List<String> images;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class AppDestination {
    @JsonProperty("server") String clusterURL;
    String namespace;
  }
}
