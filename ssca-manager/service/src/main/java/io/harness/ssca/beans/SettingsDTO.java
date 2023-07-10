/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.beans;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SettingsDTO {
  String orchestrationID;
  String pipelineIdentifier;
  String projectIdentifier;
  String orgIdentifier;
  String sequenceID;
  String accountID;
  String artifactID;
  Tool tool;
  String format;
  String artifactURL;

  @Data
  @Builder
  public static class Tool {
    String version;
    String name;
    String vendor;
  }
}
