package io.harness.cdng.infra.beans;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;

public abstract class InfrastructureDetailsAbstract {
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  public String infraIdentifier;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) public String infraName;

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
}
