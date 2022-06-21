/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans.build;

import io.harness.annotation.RecasterAlias;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias("buildStatusUpdateParameter")
@RecasterAlias("io.harness.ngpipeline.status.BuildStatusUpdateParameter")
public class BuildStatusUpdateParameter implements BuildUpdateParameters {
  @Override
  public BuildUpdateType getBuildUpdateType() {
    return BuildUpdateType.STATUS;
  }
  private String label;
  private String title;
  private String desc;
  private String state;
  private String buildNumber;
  private String sha;
  private String identifier;
  private String name;
  private String connectorIdentifier;
  private String repoName;
}
