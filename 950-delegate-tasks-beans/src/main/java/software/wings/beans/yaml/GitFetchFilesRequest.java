/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.yaml;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@OwnedBy(HarnessTeam.DX)
@TargetModule(HarnessModule._970_API_SERVICES_BEANS)
public class GitFetchFilesRequest extends GitCommandRequest {
  private String commitId;
  private List<String> filePaths;
  private String branch;
  private String gitConnectorId;
  private boolean useBranch;
  private List<String> fileExtensions;
  private boolean recursive;
  private String identifier;

  public GitFetchFilesRequest() {
    super(GitCommandType.FETCH_FILES);
  }

  public GitFetchFilesRequest(
      String commitId, List<String> filePaths, String branch, String gitConnectorId, boolean useBranch) {
    super(GitCommandType.FETCH_FILES);
    this.commitId = commitId;
    this.filePaths = filePaths;
    this.branch = branch;
    this.gitConnectorId = gitConnectorId;
    this.useBranch = useBranch;
  }

  public GitFetchFilesRequest(String commitId, List<String> filePaths, String branch, String gitConnectorId,
      boolean useBranch, List<String> fileExtensions, boolean recursive) {
    super(GitCommandType.FETCH_FILES);
    this.commitId = commitId;
    this.filePaths = filePaths;
    this.branch = branch;
    this.gitConnectorId = gitConnectorId;
    this.useBranch = useBranch;
    this.fileExtensions = fileExtensions;
    this.recursive = recursive;
  }

  public GitFetchFilesRequest(String commitId, List<String> filePaths, String branch, String gitConnectorId,
      boolean useBranch, List<String> fileExtensions, boolean recursive, String identifier) {
    super(GitCommandType.FETCH_FILES);
    this.commitId = commitId;
    this.filePaths = filePaths;
    this.branch = branch;
    this.gitConnectorId = gitConnectorId;
    this.useBranch = useBranch;
    this.fileExtensions = fileExtensions;
    this.recursive = recursive;
    this.identifier = identifier;
  }
}
