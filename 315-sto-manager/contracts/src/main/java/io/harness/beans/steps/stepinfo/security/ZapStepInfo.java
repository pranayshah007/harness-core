/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo.security;

import static io.harness.annotations.dev.HarnessTeam.STO;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.security.shared.STOGenericStepInfo;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlInstance;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlZapToolData;
import io.harness.yaml.sto.variables.STOYamlZapConfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonTypeName("Zap")
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("zapStepInfo")
@OwnedBy(STO)
@RecasterAlias("io.harness.beans.steps.stepinfo.security.ZapStepInfo")
public class ZapStepInfo extends STOGenericStepInfo {
  @JsonProperty protected STOYamlInstance instance;

  @NotNull
  @ApiModelProperty(dataType = "io.harness.yaml.sto.variables.STOYamlZapConfig")
  @Field(name = "zapConfig")
  protected STOYamlZapConfig config;

  @JsonProperty protected STOYamlZapToolData tool;
}
