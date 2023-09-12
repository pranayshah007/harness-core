/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.slsa.beans.verification.source;

import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;

import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.SSCA)
public class SlsaDockerSourceSpec implements SlsaVerificationSourceSpec {
  @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> connector;
  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> image_path;
  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> tag;
}
