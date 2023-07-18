/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.beans.tools;

import io.harness.pms.yaml.ParameterField;
import io.harness.ssca.beans.tools.blackduck.BlackduckSbomOrchestration;
import io.harness.ssca.beans.tools.syft.SyftSbomOrchestration;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.annotations.ApiModelProperty;

@JsonSubTypes({
  @JsonSubTypes.Type(value = SyftSbomOrchestration.class, name = SbomToolConstants.SYFT)
  , @JsonSubTypes.Type(value = BlackduckSbomOrchestration.class, name = SbomToolConstants.BLACKDUCK)
})
public interface SbomOrchestrationSpec {}
