/*
 * Copyright 2021 Harness Inc. All rights reserved.
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
public class SetupAbstractionKeys {
  public final String accountId = "accountId";
  public final String orgIdentifier = "orgIdentifier";
  public final String projectIdentifier = "projectIdentifier";
  public final String inputSetYaml = "inputSetYaml";
  public final String pipelineIdentifier = "pipelineIdentifier";
  public final String eventPayload = "eventPayload";
  public final String triggerInfo = "triggerInfo";
  public final String runSequence = "runSequence";
  public final String trigger = "trigger";
}
