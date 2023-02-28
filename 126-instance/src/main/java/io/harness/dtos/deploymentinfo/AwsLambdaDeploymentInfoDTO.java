
/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dtos.deploymentinfo;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.util.InstanceSyncKey;

import software.wings.beans.Tag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Date;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@OwnedBy(CDP)
public class AwsLambdaDeploymentInfoDTO extends DeploymentInfoDTO {
  @NotNull private String functionName;
  @NotNull private String region;
  @NotNull private String infraStructureKey;
  @NotNull private String version;

  private Set<String> aliases;
  private Set<Tag> tags;
  private String functionArn;
  private String description;
  private String runtime;
  private String handler;
  private String source;
  private Date updatedTime;
  private Integer memorySize;
  private String artifactId;

  @Override
  public String getType() {
    return ServiceSpecType.AWS_LAMBDA;
  }

  @Override
  public String prepareInstanceSyncHandlerKey() {
    return InstanceSyncKey.builder().part(infraStructureKey).part(functionName).build().toString();
  }
}
