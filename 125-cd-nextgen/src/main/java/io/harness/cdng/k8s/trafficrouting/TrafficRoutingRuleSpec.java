/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s.trafficrouting;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = TrafficRoutingURIRuleSpec.class, name = TrafficRoutingConst.URI)
  , @JsonSubTypes.Type(value = TrafficRoutingMethodRuleSpec.class, name = TrafficRoutingConst.METHOD),
      @JsonSubTypes.Type(value = TrafficRoutingHeaderRuleSpec.class, name = TrafficRoutingConst.HEADER),
      @JsonSubTypes.Type(value = TrafficRoutingPortRuleSpec.class, name = TrafficRoutingConst.PORT),
      @JsonSubTypes.Type(value = TrafficRoutingSchemeRuleSpec.class, name = TrafficRoutingConst.SCHEME),
      @JsonSubTypes.Type(value = TrafficRoutingAuthorityRuleSpec.class, name = TrafficRoutingConst.AUTHORITY)
})
@FieldDefaults(level = AccessLevel.PROTECTED)
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public abstract class TrafficRoutingRuleSpec {
  @Getter @NotEmpty @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> name;
}
