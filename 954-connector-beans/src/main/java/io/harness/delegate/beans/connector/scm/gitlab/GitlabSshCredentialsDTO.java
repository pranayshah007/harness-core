/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.gitlab;

import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.connector.scm.gitlab.outcome.GitlabCredentialsOutcomeDTO;
import io.harness.delegate.beans.connector.scm.gitlab.outcome.GitlabSshCredentialsOutcomeDTO;
import io.harness.encryption.SecretRefData;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("GitlabSshCredentials")
@Schema(name = "GitlabSshCredentials",
    description = "This contains details of the Gitlab credentials used via SSH connections")
public class GitlabSshCredentialsDTO implements GitlabCredentialsDTO, DecryptableEntity {
  @NotNull @SecretReference @ApiModelProperty(dataType = "string") SecretRefData sshKeyRef;
  @Override
  public GitlabCredentialsOutcomeDTO toOutcome() {
    return GitlabSshCredentialsOutcomeDTO.builder().sshKeyRef(this.sshKeyRef).build();
  }
}
