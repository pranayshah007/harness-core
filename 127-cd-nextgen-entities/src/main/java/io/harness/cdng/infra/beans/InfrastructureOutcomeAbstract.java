/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.beans;

import io.harness.cdng.infra.Connector;

import io.swagger.annotations.ApiModelProperty;
import java.util.Map;
import lombok.Data;
import lombok.Getter;

@Data
public abstract class InfrastructureOutcomeAbstract implements InfrastructureOutcome {
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  public String infraIdentifier;

  /***
   * Deprecating this field after introducing more intuitive field name which is with par with other entity's
   * expressions like <+env.name> and <+service.name><
   */
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  @Deprecated
  public String infraName;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) public String name;
  @ApiModelProperty(hidden = true) public Boolean skipInstances;

  private Connector connector;

  Map<String, String> tags;
}
