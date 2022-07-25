package io.harness.delegate.task.ecs.response;

import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.git.model.FetchFilesResult;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.ArrayList;
import java.util.List;

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
  @NonFinal
  @Setter
  DelegateMetaInfo delegateMetaInfo;
}
