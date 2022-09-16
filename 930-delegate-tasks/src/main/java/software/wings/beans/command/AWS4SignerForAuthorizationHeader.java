/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SimpleTimeZone;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDC)
public class AWS4SignerForAuthorizationHeader {
  private static final String ISO_8601_BASIC_FORMAT = "yyyyMMdd'T'HHmmss'Z'";
  private static final String DATE_STRING_FORMAT = "yyyyMMdd";
  private static final String EMPTY_BODY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
  private static final String SCHEME = "AWS4";
  private static final String ALGORITHM = "HMAC-SHA256";
  private static final String TERMINATOR = "aws4_request";
  private static final String HMAC_SHA_256 = "HmacSHA256";
  private static final String SERVICE = "s3";
  private static final String HTTP_METHOD = "GET";

  private AWS4SignerForAuthorizationHeader() {
    throw new IllegalStateException("Utility class");
  }

  public static String getAWSV4AuthorizationHeader(
      URL endpointUrl, String regionName, String awsAccessKey, String awsSecretKey, Date now, String awsToken) {
    // for a simple GET, we have no body so supply the precomputed 'empty' hash
    Map<String, String> headers = new HashMap<>();
    headers.put("x-amz-content-sha256", EMPTY_BODY_SHA256);

    // first get the date and time for the subsequent request, and convert
    // to ISO 8601 format for use in signature generation
    SimpleDateFormat dateTimeFormat = new SimpleDateFormat(ISO_8601_BASIC_FORMAT);
    dateTimeFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
    String dateTimeStamp = dateTimeFormat.format(now);
    SimpleDateFormat dateStampFormat = new SimpleDateFormat(DATE_STRING_FORMAT);
    dateStampFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));

    // update the headers with required 'x-amz-date' and 'host' values
    headers.put("x-amz-date", dateTimeStamp);

    String hostHeader = endpointUrl.getHost();
    int port = endpointUrl.getPort();
    if (port > -1) {
      hostHeader = hostHeader.concat(":" + port);
    }
    headers.put("Host", hostHeader);

    if (isNotEmpty(awsToken)) {
      headers.put("X-Amz-Security-Token", awsToken);
    }

    // canonicalize the headers; we need the set of header names as well as the
    // names and values to go into the signature process
    String canonicalizedHeaderNames = getCanonicalizeHeaderNames(headers);
    String canonicalizedHeaders = getCanonicalizedHeaderString(headers);

    // canonicalize the various components of the request
    String canonicalRequest = getCanonicalRequest(endpointUrl, canonicalizedHeaderNames, canonicalizedHeaders);
    log.info("Step 1: Create canonical request done...");

    // construct the string to be signed
    String dateStamp = dateStampFormat.format(now);
    String scope = dateStamp + "/" + regionName + "/" + SERVICE + "/" + TERMINATOR;
    String stringToSign = getStringToSign(dateTimeStamp, scope, canonicalRequest);
    log.info("Step 2: Create string to sign done...");

    // compute the signing key
    byte[] kSecret = (SCHEME + awsSecretKey).getBytes(UTF_8);
    byte[] kDate = sign(dateStamp, kSecret, HMAC_SHA_256);
    byte[] kRegion = sign(regionName, kDate, HMAC_SHA_256);
    byte[] kService = sign(SERVICE, kRegion, HMAC_SHA_256);
    byte[] kSigning = sign(TERMINATOR, kService, HMAC_SHA_256);
    byte[] signature = sign(stringToSign, kSigning, HMAC_SHA_256);
    log.info("Step 3: Calculate signature done...");

    String credentialsAuthorizationHeader = "Credential=" + awsAccessKey + "/" + scope;
    String signedHeadersAuthorizationHeader = "SignedHeaders=" + canonicalizedHeaderNames;
    String signatureAuthorizationHeader = "Signature=" + toHex(signature);

    String authorizationHeader = SCHEME + "-" + ALGORITHM + " " + credentialsAuthorizationHeader + ", "
        + signedHeadersAuthorizationHeader + ", " + signatureAuthorizationHeader;
    log.info("Step 4: Create authorization header done...");
    return authorizationHeader;
  }

  /**
   * Returns the canonical collection of header names that will be included in
   * the signature. For AWS4, all header names must be included in the process
   * in sorted canonicalized order.
   */
  private static String getCanonicalizeHeaderNames(Map<String, String> headers) {
    List<String> sortedHeaders = new ArrayList<>();
    sortedHeaders.addAll(headers.keySet());
    Collections.sort(sortedHeaders, String.CASE_INSENSITIVE_ORDER);

    StringBuilder buffer = new StringBuilder();
    for (String header : sortedHeaders) {
      if (buffer.length() > 0) {
        buffer.append(';');
      }
      buffer.append(header.toLowerCase());
    }

    return buffer.toString();
  }

  /**
   * Computes the canonical headers with values for the request. For AWS4, all
   * headers must be included in the signing process.
   */
  private static String getCanonicalizedHeaderString(Map<String, String> headers) {
    if (isEmpty(headers)) {
      return "";
    }

    // step1: sort the headers by case-insensitive order
    List<String> sortedHeaders = new ArrayList<>();
    sortedHeaders.addAll(headers.keySet());
    Collections.sort(sortedHeaders, String.CASE_INSENSITIVE_ORDER);

    // step2: form the canonical header:value entries in sorted order.
    // Multiple white spaces in the values should be compressed to a single
    // space.
    StringBuilder buffer = new StringBuilder();
    for (String key : sortedHeaders) {
      String header = headers.get(key);
      if (header != null) {
        buffer.append(key.toLowerCase().replaceAll("\\s+", " ")).append(':').append(header.replaceAll("\\s+", " "));
        buffer.append('\n');
      }
    }

    return buffer.toString();
  }

  /**
   * Returns the canonical request string to go into the signer process; this
   consists of several canonical sub-parts.
   * @return
   */
  private static String getCanonicalRequest(
      URL endpoint, String canonicalizedHeaderNames, String canonicalizedHeaders) {
    return HTTP_METHOD + "\n" + getEndpointWithCanonicalizedResourcePath(endpoint, false) + "\n"
        + ""
        + "\n" + canonicalizedHeaders + "\n" + canonicalizedHeaderNames + "\n" + EMPTY_BODY_SHA256;
  }

  /**
   * Returns the canonicalized resource path for the service endpoint.
   * @param endpoint Url endpoint
   * @param withEndpoint if true, return endpoint concatenated with canonicalized resource path
   */
  public static String getEndpointWithCanonicalizedResourcePath(URL endpoint, boolean withEndpoint) {
    if (endpoint == null) {
      return "/";
    }
    String path = endpoint.getPath();
    if (isEmpty(path)) {
      return "/";
    }

    String encodedPath = urlEncode(path, true);
    String baseUrl = endpoint.getProtocol() + "://" + endpoint.getAuthority();
    if (isNotEmpty(encodedPath) && encodedPath.charAt(0) == '/') {
      return withEndpoint ? baseUrl + encodedPath : encodedPath;
    } else {
      return withEndpoint ? baseUrl + "/".concat(encodedPath) : "/".concat(encodedPath);
    }
  }

  private static String getStringToSign(String dateTime, String scope, String canonicalRequest) {
    return SCHEME + "-" + ALGORITHM + "\n" + dateTime + "\n" + scope + "\n" + toHex(hash(canonicalRequest));
  }

  private static byte[] sign(String stringData, byte[] key, String algorithm) {
    try {
      byte[] data = stringData.getBytes(UTF_8);
      Mac mac = Mac.getInstance(algorithm);
      mac.init(new SecretKeySpec(key, algorithm));
      return mac.doFinal(data);
    } catch (Exception e) {
      throw new InvalidRequestException("Unable to calculate a request signature: " + e.getMessage(), e);
    }
  }

  private static byte[] hash(String text) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(text.getBytes(UTF_8));
      return md.digest();
    } catch (Exception e) {
      throw new InvalidRequestException("Unable to compute hash while signing request: " + e.getMessage(), e);
    }
  }

  private static String toHex(byte[] data) {
    StringBuilder sb = new StringBuilder(data.length * 2);
    for (int i = 0; i < data.length; i++) {
      String hex = Integer.toHexString(data[i]);
      if (hex.length() == 1) {
        // Append leading zero.
        sb.append('0');
      } else if (hex.length() == 8) {
        // Remove ff prefix from negative numbers.
        hex = hex.substring(6);
      }
      sb.append(hex);
    }
    return sb.toString().toLowerCase(Locale.getDefault());
  }

  private static String urlEncode(String url, boolean keepPathSlash) {
    String encoded;
    try {
      encoded = URLEncoder.encode(url, "UTF-8").replace("+", "%20");
    } catch (UnsupportedEncodingException e) {
      throw new InvalidRequestException("UTF-8 encoding is not supported.", e);
    }
    if (keepPathSlash) {
      encoded = encoded.replace("%2F", "/");
    }
    return encoded;
  }
}
