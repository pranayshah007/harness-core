/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.github.outcome;

import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.connector.scm.GitConfigConstants;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = GithubHttpCredentialsOutcomeDTO.class, name = GitConfigConstants.HTTP)
  , @JsonSubTypes.Type(value = GithubSshCredentialsOutcomeDTO.class, name = GitConfigConstants.SSH)
})
public interface GithubCredentialsOutcomeDTO extends DecryptableEntity {}
