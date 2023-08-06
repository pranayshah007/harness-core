/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.cloudformation;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.YamlNode;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
@RecasterAlias("io.harness.cdng.provision.cloudformation.CloudformationTemplateFile")
public class CloudformationTemplateFile {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;
  @NotNull String type;

  @NotNull
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  CloudformationTemplateFileSpec spec;

  @Builder
  public CloudformationTemplateFile(String type, CloudformationTemplateFileSpec spec) {
    this.type = type;
    this.spec = spec;
  }
}
