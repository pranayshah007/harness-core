/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PipelineExecutionSummaryKeys {
  public final String uuid = "uuid";
  public final String planExecutionId = "planExecutionId";
  public final String name = "name";
  public final String status = "status";
  public final String moduleInfo = "moduleInfo";
  public final String accountId = "accountId";
  public final String orgIdentifier = "orgIdentifier";
  public final String projectIdentifier = "projectIdentifier";
  public final String pipelineIdentifier = "pipelineIdentifier";
  public final String runSequence = "runSequence";
  public final String executionTriggerInfo = "executionTriggerInfo";
  public final String startTs = "startTs";
  public final String endTs = "endTs";
  public final String triggerType = "triggerType";
  public final String triggeredBy = "triggeredBy";
  public final String ciExecutionInfoDTO = "ciExecutionInfoDTO";
  public final String repoName = "repoName";
  public final String branch = "branch";
  public final String commits = "commits";
  public final String message = "message";
  public final String pullRequest = "pullRequest";
  public final String sourceBranch = "sourceBranch";
  public final String commitMessage = "message";
  public final String commitId = "id";
  public final String author = "author";
  public final String avatar = "avatar";
  public final String event = "event";
  public final String executionTriggerInfoIdentifier = "identifier";
}
