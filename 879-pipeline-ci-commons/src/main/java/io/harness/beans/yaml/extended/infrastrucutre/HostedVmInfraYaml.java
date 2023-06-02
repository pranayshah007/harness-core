/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.infrastrucutre;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.platform.Platform;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("HostedVm")
@TypeAlias("HostedVmInfra")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml")
public class HostedVmInfraYaml implements Infrastructure {
  @Builder.Default @NotNull private Type type = Type.HOSTED_VM;
  @NotNull private HostedVmInfraSpec spec;
  @ApiModelProperty(hidden = true) String uuid;
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class HostedVmInfraSpec {
    @NotNull private ParameterField<Platform> platform;
    @ApiModelProperty(hidden = true) String uuid;
  }
}
