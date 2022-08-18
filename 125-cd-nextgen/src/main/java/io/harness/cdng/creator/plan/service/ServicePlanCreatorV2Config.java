package io.harness.cdng.creator.plan.service;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceUseFromStageV2;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
public class ServicePlanCreatorV2Config {
  @JsonProperty("__uuid")
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  //  @YamlSchemaTypes(runtime)
  ParameterField<String> identifier;

  String name;
  String description;
  Map<String, String> tags;
  Boolean gitOpsEnabled;
  ServiceUseFromStageV2 useFromStage;

  ServiceDefinition serviceDefinition;

  Map<String, Object> inputs;
}
