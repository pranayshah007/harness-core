/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo.security;

import static io.harness.annotations.dev.HarnessTeam.STO;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.security.shared.STOGenericStepInfo;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlAuth;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlBlackduckToolData;
import io.harness.beans.steps.stepinfo.security.shared.STOYamlImage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.TypeAlias;

@Data
@EqualsAndHashCode(callSuper = false)
@JsonTypeName("BlackDuck")
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("blackDuckStepInfo")
@OwnedBy(STO)
@RecasterAlias("io.harness.beans.steps.stepinfo.security.BlackDuckStepInfo")
public class BlackDuckStepInfo extends STOGenericStepInfo {
  @NotNull @JsonProperty protected STOYamlAuth auth;

  @JsonProperty protected STOYamlImage image;

  @JsonProperty("tool") protected STOYamlBlackduckToolData tool;
}
