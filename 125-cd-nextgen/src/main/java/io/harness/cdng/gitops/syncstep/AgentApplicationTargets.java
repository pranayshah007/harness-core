package io.harness.cdng.gitops.syncstep;

import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import io.swagger.annotations.ApiModelProperty;

import java.util.List;

public class AgentApplicationTargets {

    @YamlSchemaTypes(value = {runtime})
    @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
    public ParameterField<String> agentId;

    @YamlSchemaTypes(value = {runtime})
    @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
    public ParameterField<List<String>> applicationName;
}
