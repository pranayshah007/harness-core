/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.SwaggerConstants.INTEGER_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.beans.ConstructorProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;

@Data
@JsonTypeName("GCSUpload")
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("uploadToGCSStepInfo")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.stepinfo.UploadToGCSStepInfo")
public class UploadToGCSStepInfo implements PluginCompatibleStep, WithConnectorRef {
  @VariableExpression(skipVariableExpression = true) public static final int DEFAULT_RETRY = 1;
  @VariableExpression(skipVariableExpression = true) public static final int DEFAULT_TIMEOUT = 60 * 60 * 2; // 2 hour
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;
  @JsonIgnore
  public static final TypeInfo typeInfo = TypeInfo.builder().stepInfoType(CIStepInfoType.UPLOAD_GCS).build();
  @JsonIgnore
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(CIStepInfoType.UPLOAD_GCS.getDisplayName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String identifier;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String name;
  @VariableExpression(skipVariableExpression = true) @Min(MIN_RETRY) @Max(MAX_RETRY) private int retry;

  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> connectorRef;
  private ContainerResource resources;
  @YamlSchemaTypes({string}) @ApiModelProperty(dataType = INTEGER_CLASSPATH) private ParameterField<Integer> runAsUser;

  // plugin settings
  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> bucket;
  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> sourcePath;
  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> target;

  @Builder
  @ConstructorProperties(
      {"identifier", "name", "retry", "connectorRef", "resources", "bucket", "sourcePath", "target", "runAsUser"})
  public UploadToGCSStepInfo(String identifier, String name, Integer retry, ParameterField<String> connectorRef,
      ContainerResource resources, ParameterField<String> bucket, ParameterField<String> sourcePath,
      ParameterField<String> target, ParameterField<Integer> runAsUser) {
    this.identifier = identifier;
    this.name = name;
    this.retry = Optional.ofNullable(retry).orElse(DEFAULT_RETRY);
    this.connectorRef = connectorRef;
    this.resources = resources;
    this.bucket = bucket;
    this.sourcePath = sourcePath;
    this.target = target;
    this.runAsUser = runAsUser;
  }

  @Override
  public TypeInfo getNonYamlInfo() {
    return typeInfo;
  }

  @Override
  public StepType getStepType() {
    return STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }
}
