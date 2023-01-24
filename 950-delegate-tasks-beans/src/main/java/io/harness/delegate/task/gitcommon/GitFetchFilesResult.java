package io.harness.delegate.task.gitcommon;

import io.harness.git.model.CommitResult;
import io.harness.git.model.GitFile;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GitFetchFilesResult {
  CommitResult commitResult;
  List<GitFile> files;
  String manifestType;
  String identifier;
}
