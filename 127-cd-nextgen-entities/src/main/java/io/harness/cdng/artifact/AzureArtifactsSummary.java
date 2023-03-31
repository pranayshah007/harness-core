/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.artifacts.ArtifactSourceConstants;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;

@OwnedBy(CDC)
@Data
@Builder
@JsonTypeName(ArtifactSourceConstants.AZURE_ARTIFACTS_NAME)
@RecasterAlias("io.harness.ngpipeline.pipeline.executions.beans.AzureArtifactsSummary")
public class AzureArtifactsSummary implements ArtifactSummary {
  /**
   * PackageName
   */
  String packageName;

  /**
   * Package ID
   */
  String packageId;

  /**
   * Version
   */
  String version;

  /**
   * Tag
   */
  String tag;

  @Override
  public String getDisplayName() {
    return packageName + ":" + version;
  }

  @Override
  public String getType() {
    return ArtifactSourceConstants.AZURE_ARTIFACTS_NAME;
  }
}
