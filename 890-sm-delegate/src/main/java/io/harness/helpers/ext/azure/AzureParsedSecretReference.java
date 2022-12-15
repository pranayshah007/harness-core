/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helpers.ext.azure;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;

import com.google.common.base.Preconditions;
import lombok.Value;

@OwnedBy(PL)
@Value
public class AzureParsedSecretReference {
  public static final char SECRET_NAME_AND_VERSION_SEPARATOR = '/';

  String secretName;
  String secretVersion;

  public AzureParsedSecretReference(String secretPath) {
    Preconditions.checkState(isNotBlank(secretPath), "'secretPath' is blank");

    int separatorIndex = secretPath.indexOf(SECRET_NAME_AND_VERSION_SEPARATOR);
    if (separatorIndex != -1) {
      secretName = secretPath.substring(0, separatorIndex);
      secretVersion = secretPath.substring(separatorIndex + 1);
    } else {
      secretName = secretPath;
      secretVersion = "";
    }
  }
}
