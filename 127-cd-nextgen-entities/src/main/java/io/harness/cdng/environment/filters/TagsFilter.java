/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.environment.filters;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@JsonTypeName("tags")
@TypeAlias("TagsFilter")
@RecasterAlias("io.harness.cdng.environment.filters.TagsFilter")
@OwnedBy(HarnessTeam.CDC)
public class TagsFilter implements FilterSpec {
  @NotNull
  @ApiModelProperty(required = true, dataType = SwaggerConstants.FILTERS_MATCHTYPE_ENUM_CLASSPATH)
  @YamlSchemaTypes(runtime)
  private ParameterField<MatchType> matchType;

  @NotNull
  @ApiModelProperty(required = true, dataType = SwaggerConstants.STRING_MAP_CLASSPATH)
  @YamlSchemaTypes(runtime)
  private ParameterField<Map<String, String>> tags;
}
