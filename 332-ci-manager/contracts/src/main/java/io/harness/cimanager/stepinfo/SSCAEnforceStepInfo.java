/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.validator.EntityIdentifier;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.variables.OutputNGVariable;
import io.harness.yaml.extended.ci.container.ContainerResource;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.beans.ConstructorProperties;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.SwaggerConstants.*;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

@Data
@NoArgsConstructor
@JsonTypeName("SSCAEnforce")
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("SSCAEnforceStepInfo")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.stepinfo.SSCAEnforceStepInfo")
public class SSCAEnforceStepInfo  implements io.harness.beans.plugin.compatible.PluginCompatibleStep, WithConnectorRef {

    @JsonProperty(YamlNode.UUID_FIELD_NAME)
    @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
    @ApiModelProperty(hidden = true)
    private String uuid;

    @VariableExpression(skipVariableExpression = true) public static final int DEFAULT_RETRY = 1;

    @JsonIgnore
    public static final TypeInfo typeInfo = TypeInfo.builder().stepInfoType(CIStepInfoType.SSCAEnforce).build();

    @JsonIgnore
    public static final StepType STEP_TYPE = StepType.newBuilder()
            .setType(CIStepInfoType.SSCAEnforce.getDisplayName())
            .setStepCategory(StepCategory.STEP)
            .build();

    @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
    @ApiModelProperty(hidden = true)
    @NotNull
    @EntityIdentifier
    protected String identifier;
    @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) protected String name;
    @VariableExpression(skipVariableExpression = true) @Min(MIN_RETRY) @Max(MAX_RETRY) protected int retry;

    @VariableExpression(skipVariableExpression = true)
    @YamlSchemaTypes(value = {string})
    protected ParameterField<Map<String, JsonNode>> settings;

    @YamlSchemaTypes(value = {runtime})
    @VariableExpression(skipVariableExpression = true)
    @ApiModelProperty(dataType = "[Lio.harness.yaml.core.variables.OutputNGVariable;")
    protected ParameterField<List<OutputNGVariable>> outputVariables;

    @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH)
    protected ParameterField<String> connectorRef;
    protected ContainerResource resources;
    @YamlSchemaTypes(value = {string})
    @ApiModelProperty(dataType = STRING_MAP_CLASSPATH)
    private ParameterField<String> sbomFormat;
    @YamlSchemaTypes(value = {string})
    @ApiModelProperty(dataType = STRING_MAP_CLASSPATH)
    private ParameterField<String> sbomGenerationTool;
    @YamlSchemaTypes(value = {string})
    @ApiModelProperty(dataType = STRING_MAP_CLASSPATH)
    private ParameterField<Map<String, String>> envVariables;
    @YamlSchemaTypes(value = {string})
    @ApiModelProperty(dataType = STRING_MAP_CLASSPATH)
    private ParameterField<String> sbomSource;
    @YamlSchemaTypes(value = {string})
    @ApiModelProperty(dataType = STRING_MAP_CLASSPATH)
    private ParameterField<String> sbomDestination;
    @YamlSchemaTypes({runtime})
    @ApiModelProperty(dataType = BOOLEAN_CLASSPATH)
    protected ParameterField<Boolean> privileged;
    @YamlSchemaTypes({string})
    @ApiModelProperty(dataType = INTEGER_CLASSPATH)
    protected ParameterField<Integer> runAsUser;
    @YamlSchemaTypes({runtime})
    @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.ImagePullPolicy")
    protected ParameterField<ImagePullPolicy> imagePullPolicy;

    @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> bucket;
    @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> context;
    @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> dockerfile;


    @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> target;
    @VariableExpression(skipVariableExpression = true) protected static List<OutputNGVariable> defaultOutputVariables;
    static {
        defaultOutputVariables = Arrays.asList(OutputNGVariable.builder().name("JOB_ID").build());
    }

    @Builder
    @ConstructorProperties({"identifier", "name", "retry", "settings", "resources", "outputVariables", "runAsUser",
            "privileged", "imagePullPolicy","dockerfile",
            "context","sbomFormat","sbomGenerationTool","envVariables","sbomSource","sbomDestination","connectorRef","bucket","target"})
    public SSCAEnforceStepInfo(String identifier, String name, Integer retry, ParameterField<Map<String, JsonNode>> settings,
                                   ContainerResource resources, ParameterField<List<OutputNGVariable>> outputVariables,
                                   ParameterField<Integer> runAsUser, ParameterField<Boolean> privileged,
                                   ParameterField<ImagePullPolicy> imagePullPolicy,
                                   ParameterField<String> dockerfile,
                                   ParameterField<String> context,
                                   ParameterField<String> sbomFormat,
                                   ParameterField<String> sbomGenerationTool,
                                   ParameterField<Map<String, String>> envVariables,
                                   ParameterField<String> sbomSource,
                                   ParameterField<String> sbomDestination,
                                   ParameterField<String> connectorRef,
                                   ParameterField<String> bucket,
                                   ParameterField<String> target) {
        this.identifier = identifier;
        this.name = name;
        this.retry = Optional.ofNullable(retry).orElse(DEFAULT_RETRY);
        this.settings = settings;
        this.resources = resources;
        this.outputVariables = outputVariables;

        this.runAsUser = runAsUser;
        this.privileged = privileged;
        this.imagePullPolicy = imagePullPolicy;
        this.dockerfile = dockerfile;
        this.context = context;
        this.connectorRef = connectorRef;
        this.sbomFormat = sbomFormat;
        this.sbomGenerationTool = sbomGenerationTool;
        this.envVariables = envVariables;
        this.sbomSource = sbomSource;
        this.sbomDestination = sbomDestination;
        this.bucket = bucket;
        this.target = target;
    }

    @Override
    public TypeInfo getNonYamlInfo() {
        return typeInfo;
    }

    @Override
    public ParameterField<Integer> getRunAsUser() {
        return null;
    }

    private String getTypeName() {
        return this.getClass().getAnnotation(JsonTypeName.class).value();
    }

    @Override
    public StepType getStepType() {
        return StepType.newBuilder().setType(getTypeName()).setStepCategory(StepCategory.STEP).build();
    }

    @Override
    public String getFacilitatorType() {
        return OrchestrationFacilitatorType.ASYNC;
    }

    public ParameterField<List<OutputNGVariable>> getOutputVariables() {
        return ParameterField.createValueField(
                Stream
                        .concat(defaultOutputVariables.stream(),
                                (CollectionUtils.emptyIfNull((List<OutputNGVariable>) outputVariables.fetchFinalValue())).stream())
                        .collect(Collectors.toSet())
                        .stream()
                        .collect(Collectors.toList()));
    }

    @Override
    public Map<String, ParameterField<String>> extractConnectorRefs() {
        Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
        connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
        return connectorRefMap;
    }
}
