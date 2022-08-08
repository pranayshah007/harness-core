/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.secretmanagerclient;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import software.wings.settings.SettingVariableTypes;

@OwnedBy(PL)
public enum SecretType {
  SecretFile,
  SecretText,
  SSHKey,
  WinRmCredentials;

  public static SettingVariableTypes toSettingVariableType(SecretType secretType) {
    if (secretType == null) {
      return null;
    }
    switch (secretType) {
      case SecretFile:
        return SettingVariableTypes.CONFIG_FILE;
      case SecretText:
        return SettingVariableTypes.SECRET_TEXT;
      case SSHKey:
        return SettingVariableTypes.SSH_SESSION_CONFIG;
      case WinRmCredentials:
        return SettingVariableTypes.WINRM_SESSION_CONFIG;
      default:
        throw new IllegalArgumentException("SecretType " + secretType + " cannot be converted to settingVariableType");
    }
  }

  public static SecretType fromSettingVariableType(SettingVariableTypes type) {
    if (type == null) {
      return null;
    }
    switch (type) {
      case SECRET_TEXT:
        return SecretType.SecretText;
      case CONFIG_FILE:
        return SecretType.SecretFile;
      case SSH_SESSION_CONFIG:
        return SecretType.SSHKey;
      default:
        throw new IllegalArgumentException("Setting variable type " + type + " cannot be converted to SecretType");
    }
  }
}
