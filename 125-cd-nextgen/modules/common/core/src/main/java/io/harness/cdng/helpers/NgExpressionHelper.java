/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.helpers;

import io.harness.account.AccountClient;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.BaseUrls;
import io.harness.remote.client.CGRestUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.URL;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class NgExpressionHelper {
  @Inject private AccountClient accountClient;
  @Inject BaseUrls baseUrls;

  public String getBaseUrl(String accountId) {
    String defaultBaseUrl = baseUrls.getNextGenUiUrl();
    if (defaultBaseUrl.endsWith("/")) {
      defaultBaseUrl = defaultBaseUrl.substring(0, defaultBaseUrl.length() - 1);
    }
    String vanityUrl = getVanityUrl(accountId);
    if (EmptyPredicate.isEmpty(vanityUrl)) {
      return defaultBaseUrl;
    }
    String newBaseUrl = vanityUrl;
    if (vanityUrl.endsWith("/")) {
      newBaseUrl = vanityUrl.substring(0, vanityUrl.length() - 1);
    }
    try {
      URL url = new URL(defaultBaseUrl);
      String hostUrl = String.format("%s://%s", url.getProtocol(), url.getHost());
      return newBaseUrl + defaultBaseUrl.substring(hostUrl.length());
    } catch (Exception e) {
      log.warn("There was error while generating vanity URL", e);
      return defaultBaseUrl;
    }
  }

  public String getVanityUrl(String accountId) {
    return CGRestUtils.getResponse(accountClient.getVanityUrl(accountId));
  }
}
