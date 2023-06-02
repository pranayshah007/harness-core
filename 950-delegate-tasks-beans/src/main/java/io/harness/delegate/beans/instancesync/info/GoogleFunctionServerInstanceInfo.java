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

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CDP)
@JsonTypeName("GoogleCloudFunctionsServerInstanceInfo")
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class GoogleFunctionServerInstanceInfo extends ServerInstanceInfo {
  private String revision;
  private String functionName;
  private String project;
  private String region;

  private String source;
  private long updatedTime;
  private String memorySize;
  private String runTime;

  private String infraStructureKey;
  private String environmentType;
}
