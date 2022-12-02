package io.harness.cdng.tas.beans;

import software.wings.beans.InstanceUnitType;

import lombok.Data;

@Data
public class TasInstanceResize {
  InstanceUnitType type;
  Integer value;
}
