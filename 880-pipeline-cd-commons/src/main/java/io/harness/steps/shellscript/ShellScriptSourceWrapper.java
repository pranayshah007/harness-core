/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.shellscript;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.YamlNode;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@OwnedBy(CDC)
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@RecasterAlias("io.harness.steps.shellscript.ShellScriptSourceWrapper")
public class ShellScriptSourceWrapper {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;
  @NotNull String type;
  @NotNull
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  ShellScriptBaseSource spec;

  @Builder
  public ShellScriptSourceWrapper(String type, ShellScriptBaseSource spec) {
    this.type = type;
    this.spec = spec;
  }

  @JsonIgnore
  public List<String> fetchFileRefs() {
    if (spec instanceof HarnessFileStoreSource) {
      HarnessFileStoreSource harnessFileStoreSource = (HarnessFileStoreSource) spec;
      if (harnessFileStoreSource.getFile() != null && harnessFileStoreSource.getFile().getValue() != null) {
        return List.of(harnessFileStoreSource.getFile().getValue());
      }
    }
    return Collections.emptyList();
  }
}
