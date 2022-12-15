/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.utility;

import io.harness.azure.AzureEnvironmentType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.AzureAuthenticationException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.network.Http;

import com.azure.core.credential.TokenRequestContext;
import com.azure.core.http.ProxyOptions;
import com.azure.core.http.okhttp.implementation.ProxyAuthenticator;
import com.azure.core.http.policy.FixedDelayOptions;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.RetryOptions;
import com.azure.core.http.policy.RetryPolicy;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.Region;
import com.azure.core.management.profile.AzureProfile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.bind.DatatypeConverter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Authenticator;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;

@UtilityClass
@Slf4j
public class AzureUtils {
  public final List<String> AZURE_GOV_REGIONS_NAMES =
      Arrays.asList(Region.GOV_US_VIRGINIA.name(), Region.GOV_US_IOWA.name(), Region.GOV_US_ARIZONA.name(),
          Region.GOV_US_TEXAS.name(), Region.GOV_US_DOD_EAST.name(), Region.GOV_US_DOD_CENTRAL.name());

  private static String SCOPE_SUFFIX = ".default";

  static String BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";
  static String END_PRIVATE_KEY = "-----END PRIVATE KEY-----";
  static String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
  static String END_CERTIFICATE = "-----END CERTIFICATE-----";

  public String convertToScope(String endpoint) {
    if (EmptyPredicate.isNotEmpty(endpoint)) {
      if (endpoint.charAt(endpoint.length() - 1) != '/') {
        return String.format("%s/%s", endpoint, SCOPE_SUFFIX);
      }
      return String.format("%s%s", endpoint, SCOPE_SUFFIX);
    }
    return null;
  }

  public final AzureEnvironment getAzureEnvironment(AzureEnvironmentType azureEnvironmentType) {
    if (azureEnvironmentType == null) {
      return AzureEnvironment.AZURE;
    }

    switch (azureEnvironmentType) {
      case AZURE_US_GOVERNMENT:
        return AzureEnvironment.AZURE_US_GOVERNMENT;

      case AZURE:
      default:
        return AzureEnvironment.AZURE;
    }
  }

  public String getCertificateThumbprintBase64Encoded(byte[] pem) {
    String errMsg;
    try {
      InputStream is = new ByteArrayInputStream(getCertificate(pem, true).getBytes());
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      Certificate cert = cf.generateCertificate(is);
      RSAPublicKey publicKey = (RSAPublicKey) cert.getPublicKey();

      String certThumbprintInHex =
          DatatypeConverter.printHexBinary(MessageDigest.getInstance("SHA-1").digest(cert.getEncoded()));

      byte[] decodedThumbprint = DatatypeConverter.parseHexBinary(certThumbprintInHex);
      return new String(java.util.Base64.getUrlEncoder().encode(decodedThumbprint));
    } catch (NoSuchAlgorithmException | CertificateException e) {
      errMsg = e.getMessage();
      log.error(errMsg);
    }
    throw NestedExceptionUtils.hintWithExplanationException(
        "Fail to retrieve certificate from Azure connector PEM file.",
        "Please check if the PEM file configured with Azure connector is proper.",
        new AzureAuthenticationException(errMsg));
  }

  public RSAPrivateKey getPrivateKeyFromPEMFile(byte[] pem) {
    String errMsg;
    try {
      String privateKeyPEM = getPrivateKey(pem, false);
      byte[] encoded = Base64.decodeBase64(privateKeyPEM);
      KeyFactory kf = null;

      kf = KeyFactory.getInstance("RSA");
      PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
      return (RSAPrivateKey) kf.generatePrivate(keySpec);

    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      errMsg = e.getMessage();
      log.error(errMsg);
    }

    throw NestedExceptionUtils.hintWithExplanationException(
        "Failed to retrieve private key from Azure connector PEM file.",
        "Please check if the PEM file configured with Azure connector is proper.",
        new AzureAuthenticationException(errMsg));
  }

  public String getCertificate(byte[] pem, boolean withWrapperText) {
    try {
      return extract(pem, withWrapperText, BEGIN_CERTIFICATE, END_CERTIFICATE);
    } catch (Exception e) {
      log.error(e.getMessage());
    }

    throw NestedExceptionUtils.hintWithExplanationException(
        "Failed to retrieve certificate part from Azure connector PEM file.",
        "Please check if the PEM file configured with Azure connector is proper.",
        new AzureAuthenticationException("PEM file provided for Azure connector is not valid!"));
  }

  public String getPrivateKey(byte[] pem, boolean withWrapperText) {
    try {
      return extract(pem, withWrapperText, BEGIN_PRIVATE_KEY, END_PRIVATE_KEY);
    } catch (Exception e) {
      log.error(e.getMessage());
    }

    throw NestedExceptionUtils.hintWithExplanationException(
        "Failed to retrieve private key from Azure connector PEM file.",
        "Please check if the PEM file configured with Azure connector is proper.",
        new AzureAuthenticationException("PEM file provided for Azure connector is not valid!"));
  }

  protected String extract(byte[] data, boolean withWrapperText, String startPoint, String endPoint) throws Exception {
    String fullFile = new String(data);

    if (EmptyPredicate.isNotEmpty(fullFile)) {
      int startIndex = fullFile.indexOf(startPoint);
      int endIndex = fullFile.indexOf(endPoint);

      if (startIndex > -1 && endIndex > -1) {
        if (withWrapperText) {
          return fullFile.substring(startIndex, endIndex + endPoint.length());
        }

        return fullFile.substring(startIndex + startPoint.length(), endIndex);
      }
    }

    throw new Exception("Failed to parse provided data.");
  }

  public static HttpLogDetailLevel getAzureLogLevel(Logger logger) {
    if (logger.isTraceEnabled()) {
      return HttpLogDetailLevel.BODY_AND_HEADERS;
    }

    if (logger.isDebugEnabled()) {
      return HttpLogDetailLevel.BASIC;
    }

    return HttpLogDetailLevel.NONE;
  }

  public AzureProfile getAzureProfile(AzureEnvironment environment) {
    return getAzureProfile(null, null, environment);
  }

  public AzureProfile getAzureProfile(String tenantId, String subscriptionId, AzureEnvironment environment) {
    return new AzureProfile(tenantId, subscriptionId, environment);
  }

  public Proxy getProxyForRestClient(String url) {
    Proxy proxy = Http.checkAndGetNonProxyIfApplicable(url);
    if (proxy != null) {
      return proxy;
    }
    URL proxyUrl = getProxyUrl();
    if (proxyUrl != null) {
      return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyUrl.getHost(), proxyUrl.getPort()));
    }
    return null;
  }

  public Authenticator getProxyAuthenticatorForRestClient() {
    String proxyUsername = System.getenv("PROXY_USER");
    String proxyPassword = System.getenv("PROXY_PASSWORD");
    if (EmptyPredicate.isNotEmpty(proxyUsername) && EmptyPredicate.isNotEmpty(proxyPassword)) {
      return new ProxyAuthenticator(proxyUsername, proxyPassword);
    }
    return null;
  }

  public ProxySelector getProxySelectorForRestClient() {
    String nonProxyHostsConfig = System.getenv("NO_PROXY");
    List<String> nonProxyHosts = Arrays.stream(nonProxyHostsConfig.split("|")).collect(Collectors.toList());

    return new ProxySelector() {
      @Override
      public List<Proxy> select(URI uri) {
        final List<Proxy> proxyList = new ArrayList<>(1);
        final String host = uri.getHost();
        if (nonProxyHosts.contains(host)) {
          proxyList.add(Proxy.NO_PROXY);
        } else {
          proxyList.add(getProxyForRestClient(host));
        }
        return proxyList;
      }

      @Override
      public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Connection to proxy failed.", "Please check your proxy parameters.", ioe);
      }
    };
  }

  public ProxySelector getProxySelectorForRestClient2() {
    return new ProxySelector() {
      @Override
      public List<Proxy> select(URI url) {
        return Arrays.asList(Http.checkAndGetNonProxyIfApplicable(url.getHost()));
      }

      @Override
      public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Connection to proxy failed.", "Please check your proxy parameters.", ioe);
      }
    };
  }

  public ProxyOptions getProxyOptions() {
    URL proxyUrl = getProxyUrl();
    String nonProxyHosts = System.getenv("NO_PROXY");
    if (proxyUrl != null) {
      ProxyOptions proxyOptions =
          new ProxyOptions(ProxyOptions.Type.HTTP, new InetSocketAddress(proxyUrl.getHost(), proxyUrl.getPort()));

      proxyOptions.setNonProxyHosts(EmptyPredicate.isEmpty(nonProxyHosts) ? null : nonProxyHosts);

      String proxyUsername = System.getenv("PROXY_USER");
      String proxyPassword = System.getenv("PROXY_PASSWORD");
      if (EmptyPredicate.isNotEmpty(proxyUsername) && EmptyPredicate.isNotEmpty(proxyPassword)) {
        proxyOptions.setCredentials(proxyUsername, proxyPassword);
      }
      return proxyOptions;
    }
    return null;
  }

  public URL getProxyUrl() {
    String httpProxy = System.getenv("HTTP_PROXY");
    String httpsProxy = System.getenv("HTTPS_PROXY");
    if (EmptyPredicate.isEmpty(httpProxy) && EmptyPredicate.isEmpty(httpsProxy)) {
      return null;
    }

    String actualProxy = EmptyPredicate.isNotEmpty(httpsProxy) ? httpsProxy : httpProxy;

    try {
      return new URL(actualProxy);
    } catch (MalformedURLException e) {
      log.error("HTTP_PROXY/HTTPS_PROXY wrongly configured.", e);
      return null;
    }
  }

  public TokenRequestContext getTokenRequestContext(String[] resourceToScopes) {
    TokenRequestContext tokenRequestContext = new TokenRequestContext();
    tokenRequestContext.addScopes(resourceToScopes);
    return tokenRequestContext;
  }

  public FixedDelayOptions getDefaultDelayOptions() {
    return getFixedDelayOptions(0, Duration.ofSeconds(1));
  }

  public FixedDelayOptions getFixedDelayOptions(int maxRetries, Duration delay) {
    return new FixedDelayOptions(maxRetries, delay);
  }

  public RetryOptions getRetryOptions(FixedDelayOptions delayOptions) {
    return new RetryOptions(delayOptions);
  }

  public RetryPolicy getRetryPolicy(RetryOptions retryOptions) {
    return new RetryPolicy(retryOptions);
  }
}
