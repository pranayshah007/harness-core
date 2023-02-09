/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities.deploymentinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import software.wings.beans.Tag;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
public class AwsLambdaDeploymentInfo extends DeploymentInfo {
  @NotNull private String version;
  @NotNull private String functionName;
  @NotNull private String region;
  @NotNull private String infraStructureKey;
  @NotNull private String functionArn;
  @NotNull private List<String> aliases;
  @NotNull private List<Tag> tags;
  @NotNull private String artifactId;
}
