/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonTypeName("ARTIFACT")
public class ArtifactStreamAllowedValueYaml implements AllowedValueYaml {
  private String artifactServerName;
  private String artifactStreamName;
  private String artifactStreamType;
  private String type;

  @Builder
  public ArtifactStreamAllowedValueYaml(
      String artifactServerName, String artifactStreamName, String artifactStreamType, String type) {
    this.artifactServerName = artifactServerName;
    this.artifactStreamName = artifactStreamName;
    this.artifactStreamType = artifactStreamType;
    this.type = type;
  }

  @Data
  @NoArgsConstructor
  public static final class Yaml {
    private String artifactServerName;
    private String artifactStreamName;
    private String artifactStreamType;
    private String type;

    @Builder
    public Yaml(String artifactServerName, String artifactStreamName, String artifactStreamType, String type) {
      this.artifactServerName = artifactServerName;
      this.artifactStreamName = artifactStreamName;
      this.artifactStreamType = artifactStreamType;
      this.type = type;
    }
  }
}
