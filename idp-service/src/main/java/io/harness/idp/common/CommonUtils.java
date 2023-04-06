/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class CommonUtils {
  public static String removeAccountFromIdentifier(String identifier) {
    String[] arrOfStr = identifier.split("[.]");
    if (arrOfStr.length == 2 && arrOfStr[0].equals("account")) {
      return arrOfStr[1];
    }
    return arrOfStr[0];
  }
}
