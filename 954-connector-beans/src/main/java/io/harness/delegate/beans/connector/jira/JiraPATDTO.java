/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.secret.SecretReference;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@OwnedBy(CDC)
@Data
@Builder
@Schema(name = "JiraPATPassword", description = "This entity contains the details of the Jira PAT")
public class JiraPATDTO implements JiraAuthCredentialsDTO {
  @ApiModelProperty(dataType = "string") @NotNull @SecretReference SecretRefData patRef;

  @Override
  public void validate() {
    if (patRef == null || patRef.isNull()) {
      throw new InvalidRequestException("Jira PAT cannot be empty");
    }
  }
}
