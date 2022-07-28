/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.inputset.gitsync;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.beans.YamlDTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.Valid;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder
public class InputSetYamlDTO implements YamlDTO {
  @JsonProperty("inputSet") @Valid InputSetYamlInfoDTO inputSetInfo;
  @JsonProperty("overlayInputSet") @Valid OverlayInputSetYamlInfoDTO overlayInputSetInfo;
}
