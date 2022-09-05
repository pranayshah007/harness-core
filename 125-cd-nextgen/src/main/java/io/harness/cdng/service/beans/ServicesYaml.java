package io.harness.cdng.service.beans;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;

import io.harness.annotation.RecasterAlias;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

@Value
@Builder
@RecasterAlias("io.harness.cdng.service.beans.Services")
public class ServicesYaml {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @VariableExpression(skipVariableExpression = true)
  @ApiModelProperty(dataType = SwaggerConstants.SERVICE_YAML_LIST_CLASSPATH)
  @YamlSchemaTypes(value = {expression})
  ParameterField<List<ServiceYamlV2>> values;

  ServicesMetadata metadata;
}
