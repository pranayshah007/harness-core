package io.harness.cdng.infra.beans;

import lombok.experimental.SuperBuilder;

@SuperBuilder
public abstract class InfrastructureOutcomeAbstract {
  public String infraIdentifier;
  public String infraName;
}
