/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security.encryption.common;

import lombok.Getter;

public enum SecretManagerType {
  KMS,
  VAULT,
  CUSTOM,
  SSH;

  @Getter private final String name;

  SecretManagerType() {
    this.name = name();
  }
}
