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

@Value
@Builder
public class EcsGitFetchRunTaskResponse implements DelegateTaskNotifyResponseData {
  FetchFilesResult ecsTaskDefinitionFetchFilesResult;
  FetchFilesResult ecsRunTaskDefinitionRequestFetchFilesResult;

  TaskStatus taskStatus;
  String errorMessage;
  UnitProgressData unitProgressData;
  @NonFinal @Setter DelegateMetaInfo delegateMetaInfo;
}
