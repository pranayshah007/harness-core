/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.bitbucket;

import io.harness.delegate.beans.connector.scm.GitConfigConstants;
import io.harness.delegate.beans.connector.scm.bitbucket.outcome.BitbucketCredentialsOutcomeDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "BitbucketCredentials", description = "This is a interface for details of the Bitbucket credentials")
@JsonSubTypes({
  @JsonSubTypes.Type(value = BitbucketHttpCredentialsDTO.class, name = GitConfigConstants.HTTP)
  , @JsonSubTypes.Type(value = BitbucketSshCredentialsDTO.class, name = GitConfigConstants.SSH)
})
public interface BitbucketCredentialsDTO {
  default BitbucketCredentialsOutcomeDTO toOutcome() {
    return null;
  }
}
