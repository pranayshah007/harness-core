/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.cloudformation;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.provision.cloudformation.CloudformationCreateStackStepConfigurationParameters.CloudformationCreateStackStepConfigurationParametersBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.validation.Validator;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.LinkedHashMap;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.provision.cloudformation.CloudformationCreateStackStepConfiguration")
public class CloudformationCreateStackStepConfiguration {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> stackName;
  @NotNull CloudformationTemplateFile templateFile;
  @JsonProperty("parameters") List<CloudformationParametersFileSpec> parametersFilesSpecs;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> connectorRef;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> region;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> roleArn;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<String>> capabilities;
  CloudformationTags tags;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<String>> skipOnStackStatuses;
  List<NGVariable> parameterOverrides;

  void validateParams() {
    Validator.notNullCheck("AWS connector is null", connectorRef);
    Validator.notNullCheck("AWS region is null", region);
  }

  public CloudformationCreateStackStepConfigurationParameters toStepParameters() {
    CloudformationCreateStackStepConfigurationParametersBuilder builder =
        CloudformationCreateStackStepConfigurationParameters.builder()
            .stackName(stackName)
            .templateFile(templateFile)
            .connectorRef(connectorRef)
            .region(region)
            .roleArn(roleArn)
            .capabilities(capabilities)
            .tags(tags)
            .skipOnStackStatuses(skipOnStackStatuses)
            .parameterOverrides(NGVariablesUtils.getMapOfVariables(parameterOverrides, 0L));

    LinkedHashMap<String, CloudformationParametersFileSpec> parameters = new LinkedHashMap<>();
    if (isNotEmpty(parametersFilesSpecs)) {
      parametersFilesSpecs.forEach(cloudformationParametersFileSpec
          -> parameters.put(cloudformationParametersFileSpec.getIdentifier(), cloudformationParametersFileSpec));
    }

    return builder.parameters(parameters).build();
  }
}
