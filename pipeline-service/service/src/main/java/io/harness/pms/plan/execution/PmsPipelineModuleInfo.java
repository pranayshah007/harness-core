/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import io.harness.pms.sdk.execution.beans.PipelineModuleInfo;

import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
public class PmsPipelineModuleInfo implements PipelineModuleInfo {
  @Singular Set<String> approvalStageNames;
  boolean hasApprovalStage;
}
