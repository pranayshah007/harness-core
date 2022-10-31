/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.genericgitconnector.outcome;

import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.connector.scm.GitConfigConstants;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = GitHTTPAuthenticationOutcomeDTO.class, name = GitConfigConstants.HTTP)
  , @JsonSubTypes.Type(value = GitSSHAuthenticationOutcomeDTO.class, name = GitConfigConstants.SSH)
})
@Schema(name = "GitAuthentication",
    description = "This is a interface for details of the Generic Git authentication information")
public abstract class GitAuthenticationOutcomeDTO implements DecryptableEntity {}
