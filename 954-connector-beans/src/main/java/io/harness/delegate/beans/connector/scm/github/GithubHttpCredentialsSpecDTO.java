/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.github;

import static io.harness.delegate.beans.connector.scm.github.GithubConnectorConstants.GITHUB_APP;
import static io.harness.delegate.beans.connector.scm.github.GithubConnectorConstants.OAUTH;
import static io.harness.delegate.beans.connector.scm.github.GithubConnectorConstants.USERNAME_AND_PASSWORD;
import static io.harness.delegate.beans.connector.scm.github.GithubConnectorConstants.USERNAME_AND_TOKEN;

import io.harness.beans.DecryptableEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = GithubUsernamePasswordDTO.class, name = USERNAME_AND_PASSWORD)
  , @JsonSubTypes.Type(value = GithubUsernameTokenDTO.class, name = USERNAME_AND_TOKEN),
      @JsonSubTypes.Type(value = GithubOauthDTO.class, name = OAUTH),
      @JsonSubTypes.Type(value = GithubAppDTO.class, name = GITHUB_APP)
})
@Schema(name = "GithubHttpCredentialsSpec",
    description =
        "This is a interface for details of the Github credentials Specs such as references of username and password")
public interface GithubHttpCredentialsSpecDTO extends DecryptableEntity {}
