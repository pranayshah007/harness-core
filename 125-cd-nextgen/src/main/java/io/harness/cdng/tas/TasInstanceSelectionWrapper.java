package io.harness.cdng.tas;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.NGInstanceUnitType;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

@OwnedBy(CDP)
@Data
@RecasterAlias("io.harness.cdng.tas.TasInstanceSelectionWrapper")
public class TasInstanceSelectionWrapper {
  TasInstanceUnitType type;
  Integer value;
}
