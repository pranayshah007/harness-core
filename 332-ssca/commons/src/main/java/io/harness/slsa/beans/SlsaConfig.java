/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.slsa.beans;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.ssca.beans.Attestation;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonTypeName("slsa")
@RecasterAlias("io.harness.slsa.beans.SlsaConfig")
@OwnedBy(HarnessTeam.SSCA)
public class SlsaConfig {
  @ApiModelProperty(hidden = true) String uuid;

  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  private ParameterField<Boolean> enabled;

  private Attestation attestation;
}
