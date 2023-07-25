/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

public enum GithubHttpAuthenticationType {
  @JsonProperty(GithubConnectorConstants.USERNAME_AND_PASSWORD)
  USERNAME_AND_PASSWORD(GithubConnectorConstants.USERNAME_AND_PASSWORD),
  @JsonProperty(GithubConnectorConstants.USERNAME_AND_TOKEN)
  USERNAME_AND_TOKEN(GithubConnectorConstants.USERNAME_AND_TOKEN),
  @JsonProperty(GithubConnectorConstants.OAUTH) OAUTH(GithubConnectorConstants.OAUTH),
  @JsonProperty(GithubConnectorConstants.GITHUB_APP) GITHUB_APP(GithubConnectorConstants.GITHUB_APP);

  private final String displayName;

  GithubHttpAuthenticationType(String displayName) {
    this.displayName = displayName;
  }

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
