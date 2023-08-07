/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.genericgitconnector;

import io.harness.annotation.RecasterAlias;
import io.harness.delegate.beans.connector.scm.GitConfigConstants;
import io.harness.delegate.beans.connector.scm.genericgitconnector.outcome.GitAuthenticationOutcomeDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.outcome.GitHTTPAuthenticationOutcomeDTO;
import io.harness.encryption.SecretRefData;
import io.harness.secret.SecretReference;
import io.harness.validation.OneOfField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(GitConfigConstants.HTTP)
@OneOfField(fields = {"username", "usernameRef"})
@Schema(name = "GitAuthentication",
    description = "This contains details of the Generic Git authentication information used via HTTP connections")
@RecasterAlias("io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO")
public class GitHTTPAuthenticationDTO extends GitAuthenticationDTO {
  String username;
  @SecretReference @ApiModelProperty(dataType = "string") SecretRefData usernameRef;
  @ApiModelProperty(dataType = "string") @NotNull @SecretReference SecretRefData passwordRef;
  @Override
  public GitAuthenticationOutcomeDTO toOutcome() {
    return GitHTTPAuthenticationOutcomeDTO.builder()
        .username(this.username)
        .usernameRef(this.usernameRef)
        .passwordRef(this.passwordRef)
        .build();
  }
}
