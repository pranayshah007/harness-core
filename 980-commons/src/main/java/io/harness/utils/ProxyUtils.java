/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.net.URL;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ProxyUtils {
  public String getProxyHost(String urlString) {
    if (isEmpty(urlString)) {
      return null;
    }
    try {
      URL url = new URL(urlString);
      return url.getHost();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public Integer getProxyPort(String urlString) {
    if (isEmpty(urlString)) {
      return null;
    }
    try {
      URL url = new URL(urlString);
      return url.getPort();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
}
