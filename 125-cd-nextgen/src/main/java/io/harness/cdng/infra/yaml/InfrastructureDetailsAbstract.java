package io.harness.cdng.infra.yaml;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;

public abstract class InfrastructureDetailsAbstract {
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  public String infraIdentifier;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) public String infraName;

  @ApiModelProperty(hidden = true) public Boolean skipInstances;

  public String getInfraIdentifier() {
    return infraIdentifier;
  }
  public String getInfraName() {
    return infraName;
  }
  public void setInfraIdentifier(String infraIdentifier) {
    this.infraIdentifier = infraIdentifier;
  }
  public void setInfraName(String infraName) {
    this.infraName = infraName;
  }

  public Boolean getSkipInstances() {
    return skipInstances;
  }
  public void setSkipInstances(boolean skipInstances) {
    this.skipInstances = skipInstances;
  }
}