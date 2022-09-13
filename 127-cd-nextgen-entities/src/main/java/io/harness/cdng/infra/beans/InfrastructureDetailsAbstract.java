/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.beans;

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
