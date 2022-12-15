/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryption;

import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptionType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SecretRefParsedData {
  public static final String SECRET_REFERENCE_EXPRESSION_DELIMITER = ":";
  public static final String SECRET_REFERENCE_DATA_ROOT_PREFIX = "//";

  private String secretManagerIdentifier;
  private String relativePath;
  private EncryptionType encryptionType;
  private AdditionalMetadata additionalMetadata;
}
