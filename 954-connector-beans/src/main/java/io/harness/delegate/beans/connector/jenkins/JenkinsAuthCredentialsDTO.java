/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.jenkins;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.v3.oas.annotations.media.Schema;

@OwnedBy(CDC)
@Schema(
    name = "JenkinsAuthCredentialsDTO", description = "This contains details of credentials for Docker Authentication")
@JsonSubTypes({
  @JsonSubTypes.Type(value = JenkinsUserNamePasswordDTO.class, name = JenkinsConstant.USERNAME_PASSWORD)
  , @JsonSubTypes.Type(value = JenkinsBearerTokenDTO.class, name = JenkinsConstant.BEARER_TOKEN)
})
public interface JenkinsAuthCredentialsDTO extends DecryptableEntity {}
