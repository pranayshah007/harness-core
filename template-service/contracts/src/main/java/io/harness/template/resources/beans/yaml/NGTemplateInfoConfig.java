/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.resources.beans.yaml;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.jackson.JsonNodeUtils;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.yaml.ParameterField;
import io.harness.template.resources.beans.NGTemplateConstants;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDC)
@Data
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("ngTemplateInfoConfig")
public class NGTemplateInfoConfig {
  @JsonProperty("__uuid")
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @NotNull @EntityIdentifier @Pattern(regexp = NGRegexValidatorConstants.IDENTIFIER_PATTERN) String identifier;
  @NotNull @EntityName @Pattern(regexp = NGRegexValidatorConstants.NAME_PATTERN) String name;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> description;
  Map<String, String> tags;
  @NotNull @Pattern(regexp = NGRegexValidatorConstants.VERSION_LABEL_PATTERN) String versionLabel;

  @NotNull TemplateEntityType type;
  JsonNode spec;

  String orgIdentifier;
  String projectIdentifier;

  String icon;

  List<NGVariable> variables;

  @Nullable
  public String fetchChildType() {
    if (getSpec() == null) {
      return null;
    }
    if (type == TemplateEntityType.STEPGROUP_TEMPLATE) {
      return JsonNodeUtils.getString(getSpec(), NGTemplateConstants.STAGE_TYPE);
    }
    return JsonNodeUtils.getString(getSpec(), NGTemplateConstants.TYPE);
  }
}