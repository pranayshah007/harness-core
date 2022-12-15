/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.LogCallback;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
public class HelmCommandData {
  private boolean isRepoConfigNull;
  @Default private boolean isHelmCmdFlagsNull = true;
  private String workingDir;
  private String commandFlags;
  private String kubeConfigLocation;
  private String releaseName;
  private String repoName;
  private HelmVersion helmVersion;
  @Default Map<HelmSubCommandType, String> valueMap = new HashMap<>();
  private List<String> yamlFiles;
  private LogCallback logCallback;
  private String namespace;
  private Integer prevReleaseVersion;
  private Integer newReleaseVersion;
  private long timeOutInMillis;
  // field below unique to HelmRollbackCommandRequest
  private Integer rollBackVersion;
  // these 3 fields are relevant only for CG
  private String chartUrl;
  private String chartVersion;
  private String chartName;
  private String gcpKeyPath;
}
