/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.serverless;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
public class ServerlessAwsLambdaCloudFormationSchema {
  @JsonProperty("Description") private String description;
  @JsonProperty("Resources") private Map<String, Resource> resources;

  @Data
  @Builder
  public static class Resource {
    @JsonProperty("Type") private String type;
    @JsonProperty("Properties") private Map<String, Object> properties;
  }
}
