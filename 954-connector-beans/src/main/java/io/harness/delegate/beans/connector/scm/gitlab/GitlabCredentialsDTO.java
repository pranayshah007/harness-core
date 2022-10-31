/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.gitlab;

import io.harness.delegate.beans.connector.scm.GitConfigConstants;
import io.harness.delegate.beans.connector.scm.gitlab.outcome.GitlabCredentialsOutcomeDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = GitlabHttpCredentialsDTO.class, name = GitConfigConstants.HTTP)
  , @JsonSubTypes.Type(value = GitlabSshCredentialsDTO.class, name = GitConfigConstants.SSH)
})
@Schema(name = "GitlabCredentials", description = "This is a interface for details of the Gitlab credentials")
public interface GitlabCredentialsDTO {
  default GitlabCredentialsOutcomeDTO toOutcome() {
    return null;
  }
}
