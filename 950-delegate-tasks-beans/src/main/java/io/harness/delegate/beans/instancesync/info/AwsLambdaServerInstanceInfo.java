/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync.info;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;

import software.wings.beans.Tag;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Date;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CDP)
@JsonTypeName("AwsLambdaServerInstanceInfo")
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class AwsLambdaServerInstanceInfo extends ServerInstanceInfo {
  private String functionName;
  private String version;
  private String region;
  private Set<String> aliases;
  private Set<Tag> tags;
  private String functionArn;
  private String description;
  private String runtime;
  private String handler;
  private String infrastructureKey;
  private String source;
  private Date updatedTime;
  private Integer memorySize;
  private String artifactId;
}
