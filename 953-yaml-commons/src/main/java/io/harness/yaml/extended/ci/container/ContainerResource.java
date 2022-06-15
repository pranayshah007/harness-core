/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.extended.ci.container;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.number;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.Optional;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;

@Data
@TypeAlias("resource")
@OwnedBy(CI)
public class ContainerResource {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;
  @NotNull Limits limits;

  @Builder
  @JsonCreator
  public ContainerResource(@JsonProperty("limits") Limits limits) {
    this.limits = Optional.ofNullable(limits).orElse(Limits.builder().build());
  }

  @Data
  @TypeAlias("resource_limits")
  public static class Limits {
    @JsonProperty(YamlNode.UUID_FIELD_NAME)
    @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
    @ApiModelProperty(hidden = true)
    String uuid;
    @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) private ParameterField<String> memory;
    @YamlSchemaTypes(value = {number})
    @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
    @Min(0)
    private ParameterField<String> cpu;

    @Builder
    @JsonCreator
    public Limits(
        @JsonProperty("memory") ParameterField<String> memory, @JsonProperty("cpu") ParameterField<String> cpu) {
      this.memory = memory;
      this.cpu = cpu;
    }
  }
}
