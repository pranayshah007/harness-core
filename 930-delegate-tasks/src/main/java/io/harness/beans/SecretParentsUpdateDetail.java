/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import java.util.Set;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@OwnedBy(PL)
@RequiredArgsConstructor
@Value
@Getter
public class SecretParentsUpdateDetail {
  @NonNull String secretId;
  @NonNull Set<EncryptedDataParent> parentsToAdd;
  @NonNull Set<EncryptedDataParent> parentsToRemove;
}
