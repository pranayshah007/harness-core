/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.dtos;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.userprofile.commons.SCMType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@JsonTypeName("GITLAB")
@SuperBuilder
@OwnedBy(HarnessTeam.PIPELINE)
public class GitlabSCMRequestDTO extends UserSourceCodeManagerRequestDTO {
  @JsonProperty("authentication") GitlabAuthenticationDTO authentication;

  @Override
  public SCMType getType() {
    return SCMType.GITLAB;
  }
}
