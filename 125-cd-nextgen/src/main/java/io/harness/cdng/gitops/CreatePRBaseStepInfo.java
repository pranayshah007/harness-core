package io.harness.cdng.gitops;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.SkipAutoEvaluation;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeAlias("CreatePRBaseStepInfo")
@FieldNameConstants(innerTypeName = "CreatePRBaseStepInfoKeys")
public class CreatePRBaseStepInfo {
  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;
  ParameterField<Map<String, String>> stringMap;
  @Wither
  @JsonProperty("store")
  @ApiModelProperty(dataType = "io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper")
  @SkipAutoEvaluation
  ParameterField<StoreConfigWrapper> store;

  @JsonProperty("commitMessage")
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @SkipAutoEvaluation
  ParameterField<String> commitMessage;

  @JsonProperty("targetBranch")
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @SkipAutoEvaluation
  ParameterField<String> targetBranch;

  @JsonProperty("isNewBranch")
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  @SkipAutoEvaluation
  ParameterField<Boolean> isNewBranch;

  @JsonProperty("prTitle")
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @SkipAutoEvaluation
  ParameterField<String> prTitle;
}
