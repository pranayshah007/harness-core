/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.git.model;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@SuperBuilder
@ToString(exclude = {"authRequest"})
public class GitBaseRequest {
  private String repoUrl;
  private String branch;
  private String commitId;

  private AuthRequest authRequest;

  private String connectorId;
  @NotEmpty private String accountId;
  private GitRepositoryType repoType;
  private Boolean disableUserGitConfig;

  private boolean unsureOrNonExistentBranch;

  public boolean useBranch() {
    return isNotEmpty(branch);
  }

  public boolean useCommitId() {
    return isNotEmpty(commitId);
  }
}
