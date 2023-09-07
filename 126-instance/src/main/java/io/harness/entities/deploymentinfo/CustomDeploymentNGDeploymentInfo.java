/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities.deploymentinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.codehaus.commons.nullanalysis.NotNull;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.DX)
public class CustomDeploymentNGDeploymentInfo extends DeploymentInfo {
  @NotNull private String infratructureKey;
  private String instanceFetchScript;
  private String scriptOutput;
  private Set<String> tags;
  private String artifactName;
  private String artifactSourceName;
  private String artifactStreamId;
  private String artifactBuildNum;
}
