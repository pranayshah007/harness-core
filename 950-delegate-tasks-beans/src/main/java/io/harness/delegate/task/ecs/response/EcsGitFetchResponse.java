/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ecs.response;

import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.git.model.FetchFilesResult;

import java.util.List;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
public class EcsGitFetchResponse implements DelegateTaskNotifyResponseData {
  FetchFilesResult ecsTaskDefinitionFetchFilesResult;
  FetchFilesResult ecsServiceDefinitionFetchFilesResult;
  List<FetchFilesResult> ecsScalableTargetFetchFilesResults;
  List<FetchFilesResult> ecsScalingPolicyFetchFilesResults;

  TaskStatus taskStatus;
  String errorMessage;
  UnitProgressData unitProgressData;
  @NonFinal @Setter DelegateMetaInfo delegateMetaInfo;
}
