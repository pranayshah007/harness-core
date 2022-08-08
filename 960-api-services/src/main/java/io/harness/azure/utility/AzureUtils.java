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

import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
import java.util.Arrays;
import java.util.List;
import javax.xml.bind.DatatypeConverter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;

@UtilityClass
@Slf4j
public class AzureUtils {
  public final List<String> AZURE_GOV_REGIONS_NAMES =
      Arrays.asList(Region.GOV_US_VIRGINIA.name(), Region.GOV_US_IOWA.name(), Region.GOV_US_ARIZONA.name(),
          Region.GOV_US_TEXAS.name(), Region.GOV_US_DOD_EAST.name(), Region.GOV_US_DOD_CENTRAL.name());

  public static String AUTH_URL = "https://login.microsoftonline.com/";
  public static String AUTH_SCOPE = "https://management.core.windows.net/.default";

  static String BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";
  static String END_PRIVATE_KEY = "-----END PRIVATE KEY-----";
  static String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
  static String END_CERTIFICATE = "-----END CERTIFICATE-----";

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
}
