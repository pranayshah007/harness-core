/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.beans.dto;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import io.harness.app.beans.entities.CIBuildAuthor;
import io.harness.app.beans.entities.CIBuildBranchHook;
import io.harness.app.beans.entities.CIBuildPRHook;
import io.harness.app.beans.entities.CIBuildPipeline;
import io.harness.ci.pipeline.executions.beans.CIBuildReleaseHook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_EMPTY)
public class CIBuildResponseDTO {
  private Long id;
  private String status;
  private String errorMessage;
  private long startTime;
  private long endTime;
  private CIBuildPipeline pipeline;
  private String triggerType;
  private String event;
  private CIBuildAuthor author;
  private CIBuildBranchHook branch;
  private CIBuildPRHook pullRequest;
  private CIBuildReleaseHook release;
}
