/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto.secrets;

import io.harness.annotation.RecasterAlias;
import io.harness.beans.DecryptableEntity;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.models.SSHCredentialSpec;
import io.harness.ng.core.models.SSHPasswordCredential;
import io.harness.secret.SecretReference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("Password")
@JsonIgnoreProperties(ignoreUnknown = true)
@RecasterAlias("io.harness.ng.core.dto.secrets.SSHPasswordCredentialDTO")
public class SSHPasswordCredentialDTO extends SSHCredentialSpecDTO implements DecryptableEntity {
  @NotNull private String userName;
  @ApiModelProperty(dataType = "string") @NotNull @SecretReference private SecretRefData password;

  @Override
  public SSHCredentialSpec toEntity() {
    return SSHPasswordCredential.builder().userName(getUserName()).password(getPassword()).build();
  }
}
