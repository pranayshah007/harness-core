package io.harness.cdng.artifact.bean.yaml;

import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Wither;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageDefinition {
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> name;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> computeGallery;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> resourceGroup;
}
