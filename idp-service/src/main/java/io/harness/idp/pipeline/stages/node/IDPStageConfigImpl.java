/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.pipeline.stages.node;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.beans.dependencies.DependencyElement;
import io.harness.beans.yaml.extended.cache.Caching;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.platform.Platform;
import io.harness.beans.yaml.extended.runtime.Runtime;
import io.harness.idp.pipeline.stages.IDPStepSpecTypeConstants;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.schema.beans.SupportedPossibleFieldTypes;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

import java.util.List;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

@OwnedBy(HarnessTeam.IDP)
@Data
@Builder
@AllArgsConstructor
@JsonTypeName(IDPStepSpecTypeConstants.IDP_STAGE)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("IDPStage")
public class IDPStageConfigImpl implements IDPStageConfig{

    @JsonProperty(YamlNode.UUID_FIELD_NAME)
    @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
    @ApiModelProperty(hidden = true)
    String uuid;

    @YamlSchemaTypes(value = {string})
    @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
    ParameterField<List<String>> sharedPaths;

    ExecutionElementConfig execution;

    Infrastructure infrastructure;

    Runtime runtime;

    @YamlSchemaTypes(value = {SupportedPossibleFieldTypes.runtime})
    @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.platform.Platform")
    ParameterField<Platform> platform;

    @YamlSchemaTypes(value = {SupportedPossibleFieldTypes.runtime})
    @ApiModelProperty(dataType = "[Lio.harness.beans.dependencies.DependencyElement;")
    ParameterField<List<DependencyElement>> serviceDependencies;

    @YamlSchemaTypes(value = {SupportedPossibleFieldTypes.runtime})
    @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.cache.Caching")
    @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
    private Caching caching;
}
