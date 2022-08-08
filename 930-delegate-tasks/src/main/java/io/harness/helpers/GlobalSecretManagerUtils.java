/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helpers;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.secretmanagerclient.NGSecretManagerMetadata;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class GlobalSecretManagerUtils {
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";

  public static boolean isNgHarnessSecretManager(NGSecretManagerMetadata ngSecretManagerMetadata) {
    return ngSecretManagerMetadata != null
        && (Boolean.TRUE.equals(ngSecretManagerMetadata.getHarnessManaged())
            || HARNESS_SECRET_MANAGER_IDENTIFIER.equals(ngSecretManagerMetadata.getIdentifier()));
  }
}
