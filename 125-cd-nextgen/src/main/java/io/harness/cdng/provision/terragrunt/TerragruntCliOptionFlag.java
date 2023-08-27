/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.yaml.TerragruntCommandFlagType;
import io.harness.pms.yaml.ParameterField;

import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"flag"})
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("terragruntCliOptionFlag")
@RecasterAlias("io.harness.cdng.provision.terragrunt.TerragruntCliOptionFlag")
@OwnedBy(CDP)
public class TerragruntCliOptionFlag {
  @NotNull TerragruntCommandFlagType commandType;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> flag;

  @Builder
  public TerragruntCliOptionFlag(TerragruntCommandFlagType commandType, ParameterField<String> flag) {
    this.commandType = commandType;
    this.flag = flag;
  }
}
