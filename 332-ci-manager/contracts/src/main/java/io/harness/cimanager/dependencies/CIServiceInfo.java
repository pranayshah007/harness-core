/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.dependencies;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.SwaggerConstants.BOOLEAN_CLASSPATH;
import static io.harness.beans.SwaggerConstants.INTEGER_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_LIST_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_MAP_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;

/**
 *  This stores specification for integration test services.
 */

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("ciServiceInfo")
@RecasterAlias("io.harness.beans.dependencies.CIServiceInfo")
@OwnedBy(CI)
public class CIServiceInfo implements DependencySpecType {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;
  @JsonIgnore public static final CIDependencyType type = CIDependencyType.SERVICE;

  @Getter(onMethod = @__(@JsonIgnore)) @JsonIgnore @NotNull @EntityIdentifier private String identifier;
  @Getter(onMethod = @__(@JsonIgnore)) @JsonIgnore private String name;

  @Getter(onMethod = @__(@JsonIgnore)) @JsonIgnore private Integer grpcPort;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = STRING_MAP_CLASSPATH)
  private ParameterField<Map<String, String>> envVariables;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = STRING_LIST_CLASSPATH)
  private ParameterField<List<String>> entrypoint;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = STRING_LIST_CLASSPATH)
  private ParameterField<List<String>> args;

  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> image;
  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> connectorRef;
  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = BOOLEAN_CLASSPATH)
  private ParameterField<Boolean> privileged;
  @YamlSchemaTypes({string}) @ApiModelProperty(dataType = INTEGER_CLASSPATH) private ParameterField<Integer> runAsUser;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = STRING_MAP_CLASSPATH)
  private ParameterField<Map<String, String>> portBindings;
  private ContainerResource resources;
  @YamlSchemaTypes({expression})
  @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.ImagePullPolicy")
  private ParameterField<ImagePullPolicy> imagePullPolicy;

  @JsonIgnore
  public CIDependencyType getType() {
    return type;
  }
}
