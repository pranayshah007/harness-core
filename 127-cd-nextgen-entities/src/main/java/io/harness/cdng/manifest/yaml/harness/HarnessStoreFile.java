/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml.harness;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Wither;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDP)
@Data
@Builder
@RecasterAlias("io.harness.cdng.manifest.yaml.harness.HarnessStoreFile")
public class HarnessStoreFile {
  @NotNull
  @NotEmpty
  @Wither
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @JsonProperty("ref")
  private ParameterField<String> ref;

  @NotNull
  @NotEmpty
  @Wither
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @JsonProperty("path")
  private ParameterField<String> path;

  @NotNull
  @Wither
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  @JsonProperty("isEncrypted")
  private ParameterField<Boolean> isEncrypted;
}
