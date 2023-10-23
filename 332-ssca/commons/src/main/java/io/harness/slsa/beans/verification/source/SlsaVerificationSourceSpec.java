/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.slsa.beans.verification.source;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonSubTypes;

@OwnedBy(HarnessTeam.SSCA)
@JsonSubTypes({
  @JsonSubTypes.Type(value = SlsaDockerSourceSpec.class, name = SlsaVerificationSourceConstants.DOCKER)
  , @JsonSubTypes.Type(value = SlsaGcrSourceSpec.class, name = SlsaVerificationSourceConstants.GCR)
})
public interface SlsaVerificationSourceSpec {}
