/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets.setupusage;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.encryption.common.EncryptionType;

import lombok.Builder;
import lombok.Data;

@OwnedBy(PL)
@Data
@Builder
public class EncryptionDetail implements Comparable<EncryptionDetail> {
  private EncryptionType encryptionType;
  private String secretManagerName;

  @Override
  public int compareTo(EncryptionDetail o) {
    return encryptionType.compareTo(o.encryptionType);
  }
}
