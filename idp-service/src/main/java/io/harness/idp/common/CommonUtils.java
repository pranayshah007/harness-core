/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;
import static io.harness.idp.common.Constants.LOCAL_ENV;
import static io.harness.idp.common.Constants.LOCAL_HOST;
import static io.harness.idp.common.Constants.PRE_QA_ENV;
import static io.harness.idp.common.Constants.PRE_QA_HOST;
import static io.harness.idp.common.Constants.PROD_HOST;
import static io.harness.idp.common.Constants.QA_ENV;
import static io.harness.idp.common.Constants.QA_HOST;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.IDP)
public class CommonUtils {
  public static String removeAccountFromIdentifier(String identifier) {
    String[] arrOfStr = identifier.split("[.]");
    if (arrOfStr.length == 2 && arrOfStr[0].equals("account")) {
      return arrOfStr[1];
    }
    return arrOfStr[0];
  }

  public static String readFileFromClassPath(String filename) {
    ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename, e);
    }
  }

  public static Set<String> addGlobalAccountIdentifierAlong(String accountIdentifier) {
    return new HashSet<>(Arrays.asList(accountIdentifier, GLOBAL_ACCOUNT_ID));
  }

  public Object findObjectByName(Map<String, Object> map, String targetName) {
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      if (entry.getKey().equals(targetName)) {
        return entry.getValue();
      }

      if (entry.getValue() instanceof Map) {
        Object nestedResult = findObjectByName((Map<String, Object>) entry.getValue(), targetName);
        if (nestedResult != null) {
          return nestedResult;
        }
      }
    }
    return null;
  }

  public static String getHarnessHostForEnv(String env) {
    switch (env) {
      case QA_ENV:
        return QA_HOST;
      case PRE_QA_ENV:
        return PRE_QA_HOST;
      case LOCAL_ENV:
        return LOCAL_HOST;
      default:
        return PROD_HOST;
    }
  }

  public static String removeTrailingSlash(String str) {
    if (str.endsWith("/")) {
      str = str.substring(0, str.length() - 1);
    }
    return str;
  }

  public static String removeLeadingSlash(String str) {
    if (str.startsWith("/")) {
      str = str.substring(1);
    }
    return str;
  }
}
