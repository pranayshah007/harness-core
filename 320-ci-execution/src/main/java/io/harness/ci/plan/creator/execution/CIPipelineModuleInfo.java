/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plan.creator.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.pipeline.executions.beans.CIWebhookInfoDTO;
import io.harness.pms.sdk.execution.beans.PipelineModuleInfo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CI)
public class CIPipelineModuleInfo implements PipelineModuleInfo {
  private CIWebhookInfoDTO ciExecutionInfoDTO;
  private String branch;
  private String repoName;
  private String triggerRepoName;
  private String tag;
  private String prNumber;
  private String buildType;
  private Boolean isPrivateRepo;
}
