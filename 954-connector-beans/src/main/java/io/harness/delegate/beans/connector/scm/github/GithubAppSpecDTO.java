/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.github;

import io.harness.encryption.SecretRefData;
import io.harness.secret.SecretReference;
import io.harness.validation.OneOfField;

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
@ApiModel("GithubAppSpec")
@OneOfField(fields = {"installationId", "installationIdRef"})
@OneOfField(fields = {"applicationId", "applicationIdRef"})
@Schema(name = "GithubAppSpec",
    description = "This contains details of the Github API access credentials Specs such as references of private key")
public class GithubAppSpecDTO implements GithubApiAccessSpecDTO {
  String installationId;
  String applicationId;
  @SecretReference @ApiModelProperty(dataType = "string") SecretRefData installationIdRef;
  @SecretReference @ApiModelProperty(dataType = "string") SecretRefData applicationIdRef;
  @ApiModelProperty(dataType = "string") @NotNull @SecretReference SecretRefData privateKeyRef;
}
