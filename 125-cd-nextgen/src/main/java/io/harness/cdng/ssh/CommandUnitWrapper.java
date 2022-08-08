/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.cdng.ssh.CommandUnitSpecType.SCRIPT;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.pms.yaml.YamlNode;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.ssh.CommandUnitWrapper")
public class CommandUnitWrapper {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;
  @NotNull @EntityIdentifier String identifier;
  @NotNull String type;
  @NotNull String name;
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true) CommandUnitBaseSpec spec;

  @JsonIgnore
  public boolean isScript() {
    return SCRIPT.equals(type);
  }
}
