/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.yaml;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
public class GitFilesBetweenCommitsRequest extends GitCommandRequest {
  private String newCommitId;
  private String oldCommitId;
  private String gitConnectorId;
  private boolean useGitFetchCommandTimeout;

  public GitFilesBetweenCommitsRequest() {
    super(GitCommandType.FILES_BETWEEN_COMMITS);
  }

  public GitFilesBetweenCommitsRequest(
      String newCommitId, String oldCommitId, String gitConnectorId, boolean useGitFetchCommandTimeout) {
    super(GitCommandType.FILES_BETWEEN_COMMITS);
    this.newCommitId = newCommitId;
    this.oldCommitId = oldCommitId;
    this.gitConnectorId = gitConnectorId;
    this.useGitFetchCommandTimeout = useGitFetchCommandTimeout;
  }
}
