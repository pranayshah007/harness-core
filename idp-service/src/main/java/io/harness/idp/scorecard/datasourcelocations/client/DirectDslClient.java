/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.client;

import io.harness.http.HttpHeaderConfig;

import java.util.List;
import javax.ws.rs.core.Response;

public class DirectDslClient implements DslClient {
  @Override
  public Response call(
      String accountIdentifier, String url, List<HttpHeaderConfig> headerList, String body, String method) {
    // Refer ProxyApiImpl for okhttpclient example
    return null;
  }
}
