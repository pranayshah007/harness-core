/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo.V1;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.stepinfo.TestStepInfo;
import io.harness.beans.yaml.extended.volumes.V1.Volume;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

@ApiModel(subTypes = {ScriptStepInfo.class, PluginStepInfoV1.class, TestStepInfo.class, BackgroundStepInfoV1.class})
@OwnedBy(CI)
public abstract class CIAbstractStepInfo implements CIStepInfo {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  protected String uuid;

  protected ContainerResource resources;
  public ContainerResource getResources() {
    return this.resources;
  }

  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.volumes.V1.Volume", hidden = true)
  protected ParameterField<List<Volume>> volumes;
  public ParameterField<List<Volume>> getVolumes() {
    if (this.volumes.getValue() == null) {
      this.volumes.setValue(Collections.emptyList());
    }
    return this.volumes;
  }
}
