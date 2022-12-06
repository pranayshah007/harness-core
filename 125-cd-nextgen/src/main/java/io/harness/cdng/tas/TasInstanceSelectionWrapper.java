package io.harness.cdng.tas;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.NGInstanceUnitType;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.lang.reflect.Parameter;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDP)
@Data
@RecasterAlias("io.harness.cdng.tas.TasInstanceSelectionWrapper")
public class TasInstanceSelectionWrapper {
  @NotNull TasInstanceUnitType type;
  @NotNull ParameterField<String> value;
}
