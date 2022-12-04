package io.harness.cdng.tas;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.integer;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;

@Data
@JsonTypeName("Count")
@RecasterAlias("io.harness.cdng.tas.CountInstanceSelection")
@OwnedBy(HarnessTeam.CDP)
public class CountInstanceSelection implements TasInstanceSelectionBase {
  @YamlSchemaTypes({string, integer}) ParameterField<Integer> count;
}
