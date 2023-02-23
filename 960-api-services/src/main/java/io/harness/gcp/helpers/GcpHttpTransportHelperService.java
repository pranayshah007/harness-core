/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gcp.helpers;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.network.Http;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.auth.http.HttpTransportFactory;
import com.google.inject.Singleton;
import java.io.IOException;
import java.security.GeneralSecurityException;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;

@Singleton
@Slf4j
public class GcpHttpTransportHelperService {
  private static final String GOOGLE_APIS_HOST = "googleapis.com";

  public HttpTransport checkIfUseProxyAndGetHttpTransport() throws IOException, GeneralSecurityException {
    return Http.getProxyHostName() != null && !Http.shouldUseNonProxy(GOOGLE_APIS_HOST)
        ? getProxyConfiguredHttpTransport()
        : getDefaultTrustedHttpTransport();
  }

  public static HttpTransport getProxyConfiguredHttpTransport() {
    return getHttpTransportFactory().create();
  }

  private HttpTransport getDefaultTrustedHttpTransport() throws GeneralSecurityException, IOException {
    return GoogleNetHttpTransport.newTrustedTransport();
  }

  public static HttpTransportFactory getHttpTransportFactory() {
    String proxyHost = Http.getProxyHostName();
    int proxyPort = Integer.parseInt(Http.getProxyPort());
    String proxyUsername = Http.getProxyUserName();
    String proxyPassword = Http.getProxyPassword();
    HttpHost proxyHostDetails = new HttpHost(proxyHost, proxyPort);
    HttpRoutePlanner httpRoutePlanner = new DefaultProxyRoutePlanner(proxyHostDetails);

    HttpClientBuilder httpClient = ApacheHttpTransport.newDefaultHttpClientBuilder()
                                       .setRoutePlanner(httpRoutePlanner)
                                       .setProxyAuthenticationStrategy(ProxyAuthenticationStrategy.INSTANCE);

    if (isNotEmpty(proxyUsername) && isNotEmpty(proxyPassword)) {
      CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(new AuthScope(proxyHostDetails.getHostName(), proxyHostDetails.getPort()),
          new UsernamePasswordCredentials(proxyUsername, proxyPassword));
      httpClient.setDefaultCredentialsProvider(credentialsProvider);
    }

    final HttpTransport httpTransport = new ApacheHttpTransport(httpClient.build());
    return () -> httpTransport;
  }
}
