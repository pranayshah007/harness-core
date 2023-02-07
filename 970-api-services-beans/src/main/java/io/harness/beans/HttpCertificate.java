/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import io.harness.encryption.Encrypted;

import software.wings.annotation.EncryptableSetting;
import software.wings.settings.SettingVariableTypes;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Http certificate used with encrypted content.
 */
@Getter
@Setter
@Builder
public class HttpCertificate implements EncryptableSetting {
  private String accountId;

  private String keyStoreType;

  /**
   * Trusted certificates for verifying the remote endpoint's certificate.
   *
   * Should contain an X.509 certificate collection in PEM format.
   */
  @Encrypted(fieldName = "cert") private char[] cert;
  private String encryptedCert;

  /**
   * A PKCS#8 private key file in PEM format.
   *
   * When private key is present the {@code cert} field is mandatory too to enable the mutual authentication.
   */
  @Encrypted(fieldName = "certKey") private char[] certKey;
  private String encryptedCertKey;

  @Override
  public SettingVariableTypes getSettingType() {
    return SettingVariableTypes.SECRET_FILE;
  }

  /**
   * Verify if certificate is set up for mutual authentication.
   */
  public boolean isMutual() {
    return cert != null && certKey != null;
  }
}
