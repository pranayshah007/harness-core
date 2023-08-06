/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.git;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;

import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITOPS})
@Value
@Builder
public class NGGitOpsResponse implements DelegateTaskNotifyResponseData {
  String prLink;
  int prNumber;
  String commitId;
  String ref;
  TaskStatus taskStatus;
  String errorMessage;
  UnitProgressData unitProgressData;
  @NonFinal @Setter DelegateMetaInfo delegateMetaInfo;
  boolean isRevertPR;
}
