/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities.deploymentinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.Tag;

import java.util.Date;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
public class AwsLambdaDeploymentInfo extends DeploymentInfo {
  @NotNull private String version;
  @NotNull private String functionName;
  @NotNull private String region;
  @NotNull private String infraStructureKey;
  private String functionArn;
  private Set<String> aliases;
  private Set<Tag> tags;
  private String artifactId;
  private String description;
  private String runtime;
  private String handler;
  private String source;
  private Date updatedTime;
  private Integer memorySize;
}
