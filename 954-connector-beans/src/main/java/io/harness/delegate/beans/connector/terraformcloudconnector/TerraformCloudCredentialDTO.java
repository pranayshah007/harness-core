/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.terraformcloudconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.terraformcloudconnector.outcome.TerraformCloudCredentialOutcomeDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("TerraformCloudCredential")
@Schema(name = "TerraformCloudCredential", description = "This contains Terraform Cloud ConnectorDisconnectHandler credentials")
public class TerraformCloudCredentialDTO {
  @NotNull @JsonProperty("type") TerraformCloudCredentialType type;
  @JsonProperty("spec")
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @Valid
  TerraformCloudCredentialSpecDTO spec;

  @Builder
  public TerraformCloudCredentialDTO(TerraformCloudCredentialType type, TerraformCloudCredentialSpecDTO spec) {
    this.type = type;
    this.spec = spec;
  }

  public TerraformCloudCredentialOutcomeDTO toOutcome() {
    return TerraformCloudCredentialOutcomeDTO.builder().type(this.type).spec(spec.toOutcome()).build();
  }
}
