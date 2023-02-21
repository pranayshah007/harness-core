/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cistatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketOnPremMergeResponse {
  @JsonProperty("id") public Integer id;
  @JsonProperty("version") public Integer version;
  @JsonProperty("title") public String title;
  @JsonProperty("state") public String state;
  @JsonProperty("properties") public Properties properties;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Properties {
    @JsonProperty("mergeCommit") public MergeCommit mergeCommit;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class MergeCommit {
    @JsonProperty("displayId") public String displayId;
    @JsonProperty("id") public String id;
  }
}
