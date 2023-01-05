/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.errorhandling;

import dev.morphia.annotations.Transient;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "GitToHarnessErrorDetailsKeys")
public class GitToHarnessErrorDetails implements GitSyncErrorDetails {
  private String gitCommitId;
  private Long commitTime;
  private String yamlContent;
  private String commitMessage;
  @Transient private LatestErrorDetailForFile latestErrorDetailForFile;
  private List<String> previousCommitIdsWithError;
  private List<GitSyncError> previousErrors;

  @Data
  @Builder
  private static class LatestErrorDetailForFile {
    private String gitCommitId;
    private String changeType;
    private String failureReason;
  }

  public void populateCommitWithLatestErrorDetails(GitSyncError gitSyncError) {
    this.setLatestErrorDetailForFile(
        latestErrorDetailForFile.builder()
            .gitCommitId(((GitToHarnessErrorDetails) gitSyncError.getAdditionalErrorDetails()).getGitCommitId())
            .changeType(gitSyncError.getChangeType())
            .failureReason(gitSyncError.getFailureReason())
            .build());
  }
}
