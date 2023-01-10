package io.harness.delegate.task.gitcommon;

import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.git.TaskStatus;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.List;

@Value
@Builder
public class GitTaskNGResponse implements DelegateTaskNotifyResponseData {
    List<GitFetchFilesResult> gitFetchFilesResults;
    TaskStatus taskStatus;
    String errorMessage;
    UnitProgressData unitProgressData;
    @NonFinal @Setter DelegateMetaInfo delegateMetaInfo;
}