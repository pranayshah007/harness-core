/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.ci;

import io.harness.delegate.beans.ci.pod.ConnectorDetails;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class CIBuildStatusPushParameters extends CIBuildPushParameters {
  private String title;
  private String desc;
  private String state;

  @Builder
  public CIBuildStatusPushParameters(String buildNumber, String detailsUrl, String repo, String owner, String sha,
      String prNumber, String identifier, String target_url, String key, String installId, String appId, String title,
      String desc, String state, String token, String userName, GitSCMType gitSCMType,
      ConnectorDetails connectorDetails) {
    super(buildNumber, detailsUrl, repo, owner, sha, prNumber, identifier, target_url, key, installId, appId, token,
        userName, gitSCMType, connectorDetails);
    this.title = title;
    this.desc = desc;
    this.state = state;
    this.commandType = CIBuildPushTaskType.STATUS;
  }

  @Override
  public CIBuildPushTaskType getCommandType() {
    return commandType;
  }
}
