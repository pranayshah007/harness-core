package io.harness.delegate.task.gitcommon;

import io.harness.git.model.CommitResult;
import io.harness.git.model.GitFile;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class GitFetchFilesResult {
    CommitResult commitResult;
    List<GitFile> files;
    String manifestType;
    String identifier;
}
