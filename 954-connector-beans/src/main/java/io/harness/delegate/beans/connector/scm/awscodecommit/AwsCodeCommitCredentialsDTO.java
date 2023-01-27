/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.awscodecommit;

import io.harness.delegate.beans.connector.scm.awscodecommit.outcome.AwsCodeCommitCredentialsOutcomeDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = AwsCodeCommitHttpsCredentialsDTO.class, name = AwsCodeCommitConnectorConstants.HTTPS)
})
@Schema(
    name = "AwsCodeCommitCredentials", description = "This interface for details of the AWS Code Commit credentials")
public interface AwsCodeCommitCredentialsDTO {
  default AwsCodeCommitCredentialsOutcomeDTO toOutcome() {
    return null;
  }
}
