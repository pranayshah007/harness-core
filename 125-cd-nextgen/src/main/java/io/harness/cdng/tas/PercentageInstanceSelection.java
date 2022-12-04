package io.harness.cdng.tas;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.number;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;

@OwnedBy(CDP)
@Data
@JsonTypeName("Percentage")
@RecasterAlias("io.harness.cdng.tas.PercentageInstanceSelection")
public class PercentageInstanceSelection implements TasInstanceSelectionBase {
  @YamlSchemaTypes({string, number}) ParameterField<String> percentage;
}
