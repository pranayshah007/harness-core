/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.dtos;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.GetBatchFileRequestIdentifier;
import io.harness.gitsync.common.beans.RequestIdentifier;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.PL)
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class ScmGetBatchFileRequestIdentifier implements RequestIdentifier<String> {
  String identifier;

  @Override
  public String getIdentifier() {
    return identifier;
  }

  public static ScmGetBatchFileRequestIdentifier fromGetBatchFileRequestIdentifier(
      GetBatchFileRequestIdentifier getBatchFileRequestIdentifier) {
    return ScmGetBatchFileRequestIdentifier.builder().identifier(getBatchFileRequestIdentifier.getIdentifier()).build();
  }
}
