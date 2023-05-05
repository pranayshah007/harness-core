/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo.security.shared;

import static io.harness.annotations.dev.HarnessTeam.STO;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.SecurityStepInfo;
import io.harness.sto.STOStepType;
import io.harness.yaml.sto.variables.STOYamlScanMode;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.Locale;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("stoGenericStepInfo")
@OwnedBy(STO)
@RecasterAlias("io.harness.beans.steps.stepinfo.security.STOGenericStepInfo")
public class STOGenericStepInfo extends SecurityStepInfo {
  @ApiModelProperty(hidden = true)
  public String getProductName() {
    return this.getTypeName().toLowerCase(Locale.ROOT);
  }

  @ApiModelProperty(hidden = true)
  public STOStepType getSTOStepType() {
    return STOStepType.lookupByName(this.getTypeName());
  }

  @NotNull @ApiModelProperty(dataType = "io.harness.yaml.sto.variables.STOYamlScanMode") protected STOYamlScanMode mode;

  @NotNull @JsonProperty protected STOYamlTarget target;

  @JsonProperty protected STOYamlIngestion ingestion;

  @JsonProperty protected STOYamlAdvancedSettings advanced;
}
