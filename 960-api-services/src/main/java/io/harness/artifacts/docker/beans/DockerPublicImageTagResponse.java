/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifacts.docker.beans;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(CDC)
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DockerPublicImageTagResponse {
  @Getter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Result {
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Image {
      private long size;
      private String architecture;
      private String variant;
      private String features;
      private String os;
      @JsonProperty("os_version") private String osVersion;
      @JsonProperty("os_features") private String osFeatures;
    }

    private String name;
    private String id;
    private int repository;
    private int creator;
    private boolean v2;
    @JsonProperty("last_updater") private int lastUpdater;
    @JsonProperty("full_size") private String fullSize;
    @JsonProperty("last_updated") private String lastUpdated;
    private List<Image> images;
  }

  private Integer count;
  private String next;
  private String previous;
  private List<Result> results;
}
