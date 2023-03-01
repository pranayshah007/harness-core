/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.v3.oas.annotations.media.Schema;

@OwnedBy(CDC)
@Schema(name = "JiraAuthCredentials", description = "This contains details of credentials for Jira Authentication")
@JsonSubTypes({
  @JsonSubTypes.Type(value = JiraUserNamePasswordDTO.class, name = JiraConstants.USERNAME_PASSWORD)
  , @JsonSubTypes.Type(value = JiraPATDTO.class, name = JiraConstants.PAT)
})
public interface JiraAuthCredentialsDTO extends DecryptableEntity {
  void validate();
}
