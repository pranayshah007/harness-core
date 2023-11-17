/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s.trafficrouting;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
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
  @JsonSubTypes.Type(value = K8sTrafficRoutingURIRuleSpec.class, name = K8sTrafficRoutingConst.URI)
  , @JsonSubTypes.Type(value = K8sTrafficRoutingMethodRuleSpec.class, name = K8sTrafficRoutingConst.METHOD),
      @JsonSubTypes.Type(value = K8sTrafficRoutingHeaderRuleSpec.class, name = K8sTrafficRoutingConst.HEADER),
      @JsonSubTypes.Type(value = K8sTrafficRoutingPortRuleSpec.class, name = K8sTrafficRoutingConst.PORT),
      @JsonSubTypes.Type(value = K8sTrafficRoutingSchemaRuleSpec.class, name = K8sTrafficRoutingConst.SCHEME),
      @JsonSubTypes.Type(value = K8sTrafficRoutingAuthorityRuleSpec.class, name = K8sTrafficRoutingConst.AUTHORITY)
})
@FieldDefaults(level = AccessLevel.PROTECTED)
@SuperBuilder
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_K8S})
public abstract class K8sTrafficRoutingRuleSpec {
  @Getter @NotEmpty @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> name;
}
