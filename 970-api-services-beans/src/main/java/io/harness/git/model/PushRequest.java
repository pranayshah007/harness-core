/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.git.model;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITOPS})
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PushRequest extends GitBaseRequest {
  private boolean pushOnlyIfHeadSeen;
  private boolean forcePush;

  public static PushRequest mapFromRevertAndPushRequest(RevertAndPushRequest parent) {
    return PushRequest.builder()
        .accountId(parent.getAccountId())
        .branch(parent.getBranch())
        .authRequest(parent.getAuthRequest())
        .commitId(parent.getCommitId())
        .connectorId(parent.getConnectorId())
        .disableUserGitConfig(parent.getDisableUserGitConfig())
        .connectorId(parent.getConnectorId())
        .repoType(parent.getRepoType())
        .pushOnlyIfHeadSeen(parent.isPushOnlyIfHeadSeen())
        .forcePush(parent.isForcePush())
        .repoUrl(parent.getRepoUrl())
        .build();
  }
}
