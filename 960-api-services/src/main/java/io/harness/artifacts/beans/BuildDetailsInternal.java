/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifacts.beans;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.delegate.beans.artifact.ArtifactFileMetadataInternal;

import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Value
@Builder
public class BuildDetailsInternal {
  @UtilityClass
  public static final class BuildDetailsInternalMetadataKeys {
    public static final String image = "image";
    public static final String tag = "tag";
  }

  String number;
  String revision;
  String description;
  String artifactPath;
  String buildUrl;
  String buildDisplayName;
  String buildFullDisplayName;
  String artifactFileSize;
  String uiDisplayName;
  Date imagePushedAt;
  BuildStatus status;
  Map<String, String> metadata;
  Map<String, String> buildParameters;
  Map<String, String> labels;
  ArtifactMetaInfo artifactMetaInfo;
  List<ArtifactFileMetadataInternal> artifactFileMetadataList;

  public enum BuildStatus {
    FAILURE("Failure"),
    UNSTABLE("Unstable"),
    SUCCESS("Success");

    BuildStatus(String displayName) {
      this.displayName = displayName;
    }

    private String displayName;

    public String getDisplayName() {
      return displayName;
    }
  }
}
