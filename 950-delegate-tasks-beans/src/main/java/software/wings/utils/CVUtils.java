/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

public class CVUtils {
  public static String appendPathToBaseUrl(String baseUrl, String path) {
    if (baseUrl.charAt(baseUrl.length() - 1) != '/') {
      baseUrl += '/';
    }
    if (path.length() > 0 && path.charAt(0) == '/') {
      path = path.substring(1);
    }
    return baseUrl + path;
  }
}
