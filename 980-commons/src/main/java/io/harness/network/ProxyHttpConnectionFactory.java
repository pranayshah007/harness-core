/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.network;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.eclipse.jgit.transport.http.apache.HttpClientConnectionFactory;

@Data
@Builder
@AllArgsConstructor
@OwnedBy(CI)
public class ProxyHttpConnectionFactory implements HttpConnectionFactory {
  @NotNull private String proxyHost;
  @NotNull private int proxyPort;

  @Override
  public HttpConnection create(URL url) throws IOException {
    return create(url, new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
  }

  @Override
  public HttpConnection create(URL url, Proxy proxy) throws IOException {
    return new HttpClientConnectionFactory().create(
        url, new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
  }
}
