/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.config.beans.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Getter;

@OwnedBy(HarnessTeam.IDP)
@Getter
@Builder
public class AppConfigDTO {
  private String accountIdentifier;
  private Long createdAt;
  private Long lastModifiedAt;
  private boolean isDeleted;
  private long deletedAt;
}
