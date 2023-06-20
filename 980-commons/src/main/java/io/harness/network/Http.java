/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.network;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.startsWith;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.KeyValuePair;
import io.harness.security.AllTrustingX509TrustManager;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.http.HttpHost;

@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class Http {
  public static final OkHttpClient DEFAULT_OKHTTP_CLIENT =
      Http.getOkHttpClientWithProxyAuthSetup().connectionPool(new ConnectionPool()).build();

  private static UrlValidator urlValidator =
      new UrlValidator(new String[] {"http", "https"}, UrlValidator.ALLOW_LOCAL_URLS);
  public static ConnectionPool connectionPool = new ConnectionPool(0, 5, TimeUnit.MINUTES);

  private static TrustManager[] trustAllCerts = getTrustManagers();
  private static final SSLContext sc = createSslContext();

  public static boolean connectableHost(String host, int port, int timeout) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), timeout);
      return true;
    } catch (IOException ignored) {
      // Do nothing
    }
    return false; // Either timeout or unreachable or failed DNS lookup.
  }

  public static boolean connectableHost(String host, int port) {
    return connectableHost(host, port, 5000); // 5 sec timeout
  }

  public static boolean connectableHost(String urlString) {
    try {
      URL url = new URL(urlString);
      return connectableHost(url.getHost(), url.getPort() < 0 ? 80 : url.getPort());
    } catch (MalformedURLException e) {
      log.error("", e);
    }
    return false;
  }

  LoadingCache<String, Integer> responseCodeForValidation =
      CacheBuilder.newBuilder()
          .maximumSize(1000)
          .expireAfterWrite(1, TimeUnit.MINUTES)
          .build(new CacheLoader<String, Integer>() {
            @Override
            public Integer load(String url) throws IOException {
              log.info("Testing connectivity");

              // Create a trust manager that does not validate certificate chains
              // Install the all-trusting trust manager
              HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
              // Create all-trusting host name verifier
              HostnameVerifier allHostsValid = (s, sslSession) -> true;
              // Install the all-trusting host verifier
              HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
              HttpURLConnection connection = getHttpsURLConnection(url);
              try {
                // Changed to GET as some providers like artifactory SAAS is not
                // accepting HEAD requests
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                int responseCode = connection.getResponseCode();
                log.info("Returned code {}", responseCode);
                return responseCode;
              } finally {
                if (connection != null) {
                  connection.disconnect();
                }
              }
            }
          });

  LoadingCache<HttpURLHeaderInfo, Integer> responseCodeForValidationWithoutFollowingRedirect =
      CacheBuilder.newBuilder()
          .maximumSize(1000)
          .expireAfterWrite(5, TimeUnit.MINUTES)
          .build(new CacheLoader<HttpURLHeaderInfo, Integer>() {
            @Override
            public Integer load(HttpURLHeaderInfo httpURLHeaderInfo) throws IOException {
              log.info("Testing connectivity using headers without follow redirect");

              // Create a trust manager that does not validate certificate chains
              // Install the all-trusting trust manager
              HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
              // Create all-trusting host name verifier
              HostnameVerifier allHostsValid = (s, sslSession) -> true;
              // Install the all-trusting host verifier
              HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
              HttpURLConnection connection = getHttpsURLConnection(httpURLHeaderInfo.getUrl());
              try {
                // Changed to GET as some providers like artifactory SAAS is not
                // accepting HEAD requests
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(false);

                // set headers
                if (isNotEmpty(httpURLHeaderInfo.getHeaders())) {
                  for (KeyValuePair header : httpURLHeaderInfo.getHeaders()) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                  }
                }
                int responseCode = connection.getResponseCode();
                log.info("Returned code {}", responseCode);
                return responseCode;
              } finally {
                if (connection != null) {
                  connection.disconnect();
                }
              }
            }
          });

  LoadingCache<HttpURLHeaderInfo, Integer> responseCodeForValidationWithHeaders =
      CacheBuilder.newBuilder()
          .maximumSize(1000)
          .expireAfterWrite(5, TimeUnit.MINUTES)
          .build(new CacheLoader<HttpURLHeaderInfo, Integer>() {
            @Override
            public Integer load(HttpURLHeaderInfo httpURLHeaderInfo) throws IOException {
              log.info("Testing connectivity using headers");

              // Create a trust manager that does not validate certificate chains
              // Install the all-trusting trust manager
              HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
              // Create all-trusting host name verifier
              HostnameVerifier allHostsValid = (s, sslSession) -> true;
              // Install the all-trusting host verifier
              HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
              HttpURLConnection connection = getHttpsURLConnection(httpURLHeaderInfo.getUrl());
              try {
                // Changed to GET as some providers like artifactory SAAS is not
                // accepting HEAD requests
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                // set headers
                for (KeyValuePair header : httpURLHeaderInfo.getHeaders()) {
                  connection.setRequestProperty(header.getKey(), header.getValue());
                }
                int responseCode = connection.getResponseCode();
                log.info("Returned code {}", responseCode);
                return responseCode;
              } finally {
                if (connection != null) {
                  connection.disconnect();
                }
              }
            }
          });

  LoadingCache<String, Integer> jenkinsResponseCodeForValidation =
      CacheBuilder.newBuilder()
          .maximumSize(100)
          .expireAfterWrite(2, TimeUnit.MINUTES)
          .build(new CacheLoader<String, Integer>() {
            @Override
            public Integer load(String url) throws IOException {
              log.info("Testing connectivity");
              // Create a trust manager that does not validate certificate chains
              // Install the all-trusting trust manager
              HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
              // Create all-trusting host name verifier
              HostnameVerifier allHostsValid = (s, sslSession) -> true;
              // Install the all-trusting host verifier
              HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
              HttpURLConnection connection = getHttpsURLConnection(url);
              try {
                connection.setRequestMethod("GET");
                // Set increased timeout since Jenkins servers might be slow
                connection.setConnectTimeout(150000);
                connection.setReadTimeout(150000);
                int responseCode = connection.getResponseCode();
                log.info("Returned code {}", responseCode);
                return responseCode;
              } finally {
                connection.disconnect();
              }
            }
          });

  public static boolean checkResponseCode(int responseCode, boolean ignoreResponseCode) {
    if (ignoreResponseCode) {
      return responseCode < 500;
    }
    return responseCode != 400;
  }

  public static boolean connectableHttpUrl(String url, boolean ignoreResponseCode) {
    try (UrlLogContext ignore = new UrlLogContext(url, OVERRIDE_ERROR)) {
      try {
        return checkResponseCode(responseCodeForValidation.get(url), ignoreResponseCode);
      } catch (Exception e) {
        log.info("Could not connect: {}", e.getMessage());
      }
    }
    return false;
  }

  public static boolean connectableHttpUrlWithoutFollowingRedirect(
      String url, List<KeyValuePair> headers, boolean ignoreResponseCode) {
    try (UrlLogContext ignore = new UrlLogContext(url, OVERRIDE_ERROR)) {
      try {
        return checkResponseCode(responseCodeForValidationWithoutFollowingRedirect.get(
                                     HttpURLHeaderInfo.builder().url(url).headers(headers).build()),
            ignoreResponseCode);
      } catch (Exception e) {
        log.info("Could not connect: {}", e.getMessage());
      }
    }
    return false;
  }

  public static boolean connectableHttpUrlWithoutFollowingRedirect(String url) {
    return connectableHttpUrlWithoutFollowingRedirect(url, null, false);
  }

  public static boolean connectableHttpUrlWithHeaders(
      String url, List<KeyValuePair> headers, boolean ignoreResponseCode) {
    try (UrlLogContext ignore = new UrlLogContext(url, OVERRIDE_ERROR)) {
      try {
        return checkResponseCode(
            responseCodeForValidationWithHeaders.get(HttpURLHeaderInfo.builder().url(url).headers(headers).build()),
            ignoreResponseCode);
      } catch (Exception e) {
        log.info("Could not connect: {}", e.getMessage());
      }
    }
    return false;
  }

  public static boolean connectableJenkinsHttpUrl(String url) {
    try (UrlLogContext ignore = new UrlLogContext(url, OVERRIDE_ERROR)) {
      try {
        return checkResponseCode(jenkinsResponseCodeForValidation.get(url), false);
      } catch (Exception e) {
        log.info("Could not connect: {}", e.getMessage());
      }
    }
    return false;
  }

  @VisibleForTesting
  static HttpURLConnection getHttpsURLConnection(String url) throws IOException {
    HttpURLConnection connection;
    if (shouldUseNonProxy(url)) {
      connection = (HttpURLConnection) new URL(url).openConnection(Proxy.NO_PROXY);
    } else {
      connection = (HttpURLConnection) new URL(url).openConnection();
    }
    return connection;
  }

  private static SSLContext createSslContext() {
    try {
      SSLContext sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
      return sslContext;
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      log.error("Error while initializing the SSL context", e);
    }
    return null;
  }

  public static SSLContext getSslContext() {
    return sc;
  }

  public static TrustManager[] getTrustManagers() {
    return new TrustManager[] {new AllTrustingX509TrustManager()};
  }

  public static OkHttpClient getUnsafeOkHttpClient(String url) {
    return getUnsafeOkHttpClient(url, 15, 15);
  }

  public static synchronized OkHttpClient getUnsafeOkHttpClient(
      String url, long connectTimeOutSeconds, long readTimeOutSeconds) {
    return getUnsafeOkHttpClientBuilder(url, connectTimeOutSeconds, readTimeOutSeconds).build();
  }

  public static synchronized OkHttpClient.Builder getUnsafeOkHttpClientBuilder(
      String url, long connectTimeOutSeconds, long readTimeOutSeconds) {
    return getUnsafeOkHttpClientBuilder(url, connectTimeOutSeconds, readTimeOutSeconds, getSslContext());
  }

  public static synchronized OkHttpClient.Builder getUnsafeOkHttpClientBuilder(
      String url, long connectTimeOutSeconds, long readTimeOutSeconds, SSLContext sslContext) {
    try {
      OkHttpClient.Builder builder =
          getOkHttpClientBuilder()
              .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) getTrustManagers()[0])
              .hostnameVerifier(new NoopHostnameVerifier())
              .connectTimeout(connectTimeOutSeconds, TimeUnit.SECONDS)
              .readTimeout(readTimeOutSeconds, TimeUnit.SECONDS);

      Proxy proxy = checkAndGetNonProxyIfApplicable(url);
      if (proxy != null) {
        builder.proxy(proxy);
      }

      return builder;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public OkHttpClient.Builder getSafeOkHttpClientBuilder(
      String url, long connectTimeOutSeconds, long readTimeOutSeconds) {
    try {
      OkHttpClient.Builder builder = Http.getOkHttpClientBuilder()
                                         .hostnameVerifier(new NoopHostnameVerifier())
                                         .connectTimeout(connectTimeOutSeconds, TimeUnit.SECONDS)
                                         .readTimeout(readTimeOutSeconds, TimeUnit.SECONDS);

      Proxy proxy = Http.checkAndGetNonProxyIfApplicable(url);
      if (proxy != null) {
        builder.proxy(proxy);
      }
      return builder;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public OkHttpClient getOkHttpClient(String url, boolean isCertValidationRequired) {
    return getOkHttpClientBuilder(url, isCertValidationRequired).build();
  }

  public OkHttpClient.Builder getOkHttpClientBuilder(String url, boolean isCertValidationRequired) {
    return getOkHttpClientBuilder(url, 15, 15, isCertValidationRequired);
  }

  public OkHttpClient.Builder getOkHttpClientBuilder(
      String url, long connectTimeOutSeconds, long readTimeOutSeconds, boolean isCertValidationRequired) {
    if (isCertValidationRequired) {
      return getSafeOkHttpClientBuilder(url, connectTimeOutSeconds, readTimeOutSeconds);
    } else {
      return getUnsafeOkHttpClientBuilder(url, connectTimeOutSeconds, readTimeOutSeconds);
    }
  }

  public static boolean validUrl(String url) {
    return urlValidator.isValid(url);
  }

  public static HttpHost getHttpProxyHost() {
    String proxyHost = null;
    int proxyPort = -1;
    String proxyScheme = System.getProperty("proxyScheme");

    if ("http".equalsIgnoreCase(proxyScheme)) {
      String httpProxyHost = System.getProperty("http.proxyHost");
      String httpProxyPort = System.getProperty("http.proxyPort");
      if (isNotEmpty(httpProxyHost)) {
        proxyHost = httpProxyHost;
        if (isNotEmpty(httpProxyPort)) {
          proxyPort = Integer.parseInt(httpProxyPort);
        }
        proxyScheme = "http";
      }
    } else { // https by default
      String httpsProxyHost = System.getProperty("https.proxyHost");
      String httpsProxyPort = System.getProperty("https.proxyPort");
      if (isNotEmpty(httpsProxyHost)) {
        proxyHost = httpsProxyHost;
        if (isNotEmpty(httpsProxyHost)) {
          proxyPort = Integer.parseInt(httpsProxyPort);
        }
        proxyScheme = "https";
      }
    }
    return isNotEmpty(proxyHost) ? new HttpHost(proxyHost, proxyPort, proxyScheme) : null;
  }

  public static HttpHost getHttpProxyHost(String url) {
    if (shouldUseNonProxy(url, System.getProperty("http.nonProxyHosts"))) {
      return null;
    }

    return getHttpProxyHost();
  }

  public static boolean shouldUseNonProxy(String url) {
    return shouldUseNonProxy(url, System.getProperty("http.nonProxyHosts"));
  }

  /**
   * Sets the HTTP proxy that will be used by connections created by this client. This takes precedence over
   * proxySelector, which is only honored when this proxy is null (which it is by default). To disable proxy use
   * completely, call setProxy(Proxy.NO_PROXY)
   */
  public static Proxy checkAndGetNonProxyIfApplicable(String url) {
    return shouldUseNonProxy(url) ? Proxy.NO_PROXY : null;
  }
  /**
   * as per Oracle doc,
   * http.nonProxyHosts:a list of hosts that should be reached directly, bypassing the proxy. This is a list of patterns
   * separated by '|'. The patterns may start or end with a '*' for wildcards. Any host matching one of these patterns
   * will be reached through a direct connection instead of through a proxy.
   *
   * Currently, we only support suffix format (and not prefix). e.g. localhost, *localhost
   * e.g. *localhost
   */
  public static boolean shouldUseNonProxy(String url, String nonProxyConfigString) {
    if (isNotBlank(url) && isNotBlank(nonProxyConfigString)) {
      String domain = getDomain(url);

      if (Splitter.on("|")
              .splitToList(nonProxyConfigString)
              .stream()
              .anyMatch(suffix -> checkPattern(suffix, domain))) {
        return true;
      }
    }
    return false;
  }

  public static String getDomain(String url) {
    try {
      URI uri = getNormalizedURI(url);
      return uri.getHost();
    } catch (Exception e) {
      log.warn("Bad URI syntax", e);
      return null;
    }
  }

  public static String getBaseUrl(String url) {
    try {
      URI uri = getNormalizedURI(url);
      String scheme = uri.getScheme();
      String hostName = uri.getHost();
      int port = uri.getPort();
      if (port <= 0 || (StringUtils.equals("https", scheme) && port == 443)
          || (StringUtils.equals("http", scheme) && port == 80)) {
        return scheme + "://" + hostName + "/";
      }
      return scheme + "://" + hostName + ":" + port + "/";
    } catch (Exception e) {
      log.warn("Bad URI syntax", e);
      return null;
    }
  }

  public static String getDomainWithPort(String url) {
    try {
      URI uri = getNormalizedURI(url);
      String hostName = uri.getHost();
      if (isNotEmpty(hostName) && uri.getPort() > 0) {
        hostName += ":" + uri.getPort();
      }
      return hostName;
    } catch (Exception e) {
      log.warn("Bad URI syntax", e);
      return null;
    }
  }

  public static String joinHostPort(String host, String port) {
    if (host.indexOf(':') >= 0) {
      // Host is an IPv6
      return "[" + host + "]:" + port;
    }
    return host + ":" + port;
  }

  private static URI getNormalizedURI(String url) throws URISyntaxException {
    if (!startsWith(url, "http")) {
      return new URI("http://" + url);
    }
    return new URI(url);
  }

  private static boolean checkPattern(String pattern, String domain) {
    if (pattern.charAt(0) == '*') { // remove *, *.jenkins.com to .jenkins.com
      pattern = pattern.substring(1);
    }

    return domain.toLowerCase().endsWith(pattern.toLowerCase());
  }

  public static OkHttpClient.Builder getOkHttpClientWithNoProxyValueSet(String url) {
    return getOkHttpClientBuilder().proxy(checkAndGetNonProxyIfApplicable(url));
  }

  public static OkHttpClient.Builder getOkHttpClientBuilder() {
    return getOkHttpClientWithProxyAuthSetup().connectionPool(connectionPool);
  }

  public static OkHttpClient.Builder getOkHttpClientBuilderWithReadtimeOut(int timeout, TimeUnit timeUnit) {
    return getOkHttpClientBuilder().readTimeout(timeout, timeUnit);
  }

  public static OkHttpClient.Builder getOkHttpClientWithProxyAuthSetup() {
    OkHttpClient.Builder builder = new OkHttpClient.Builder().hostnameVerifier(new NoopHostnameVerifier());

    String user = getProxyUserName();
    if (isNotEmpty(user)) {
      log.info("###Using proxy Auth");
      String password = getProxyPassword();
      builder.proxyAuthenticator((route, response) -> {
        if (response == null || response.code() == 407) {
          return null;
        }
        String credential = Credentials.basic(user, password);
        return response.request().newBuilder().header("Proxy-Authorization", credential).build();
      });
    }

    return builder;
  }

  private static String getProxyPrefix() {
    return "http" + ("http".equalsIgnoreCase(getProxyScheme()) ? "" : "s") + ".";
  }

  public static String getProxyUserName() {
    return System.getProperty(getProxyPrefix() + "proxyUser");
  }

  // Need to add url encoding here
  public static String getProxyPassword() {
    if (isNotEmpty(getProxyUserName())) {
      return System.getProperty(getProxyPrefix() + "proxyPassword");
    }
    return null;
  }

  public static String getProxyScheme() {
    String proxyScheme = System.getProperty("proxyScheme");
    if (isNotEmpty(proxyScheme)) {
      return proxyScheme;
    }

    return "http";
  }

  // Need to add url encoding here
  public static String getProxyHostName() {
    return System.getProperty(getProxyPrefix() + "proxyHost");
  }

  // Need to add url encoding here
  public static String getProxyPort() {
    return System.getProperty(getProxyPrefix() + "proxyPort");
  }

  public static String getResponseStringFromUrl(String url, int connectTimeoutSeconds, int readTimeoutSeconds)
      throws IOException {
    return getResponseFromUrl(url, connectTimeoutSeconds, readTimeoutSeconds).body().string();
  }

  public static InputStream getResponseStreamFromUrl(String url, int connectTimeoutSeconds, int readTimeoutSeconds)
      throws IOException {
    return getResponseFromUrl(url, connectTimeoutSeconds, readTimeoutSeconds).body().byteStream();
  }

  private static Response getResponseFromUrl(String url, int connectTimeoutSeconds, int readTimeoutSeconds)
      throws IOException {
    return getUnsafeOkHttpClient(url, connectTimeoutSeconds, readTimeoutSeconds)
        .newCall(new Request.Builder().url(url).build())
        .execute();
  }
}
