/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.cyberark;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.CYBERARK_OPERATION_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.exception.WingsException;
import io.harness.network.Http;

import software.wings.beans.CyberArkConfig;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@UtilityClass
@Slf4j
@OwnedBy(PL)
public class CyberArkRestClientFactory {
  private static final String TEMPORARY_KEY_PASSWORD = "changeit";
  private static final String KEYSTORE_CLIENT_CERT = "client-cert";
  private static final String KEYSTORE_CLIENT_KEY = "client-key";

  // Client certificate
  private static final String PEM_CERTIFICATE_START = "-----BEGIN CERTIFICATE-----";
  private static final String PEM_CERTIFICATE_END = "-----END CERTIFICATE-----";
  // PKCS#8 format
  private static final String PEM_PRIVATE_KEY_START = "-----BEGIN PRIVATE KEY-----";
  private static final String PEM_PRIVATE_KEY_END = "-----END PRIVATE KEY-----";
  // PKCS#1 format
  private static final String PEM_RSA_PRIVATE_KEY_START = "-----BEGIN RSA PRIVATE KEY-----";
  private static final String PEM_RSA_PRIVATE_KEY_END = "-----END RSA PRIVATE KEY-----";

  private static ObjectMapper objectMapper = new ObjectMapper();
  private static HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
  static {
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    loggingInterceptor.setLevel(Level.NONE);
  }

  public static CyberArkRestClient create(CyberArkConfig cyberArkConfig) {
    OkHttpClient httpClient;
    if (EmptyPredicate.isEmpty(cyberArkConfig.getClientCertificate())) {
      if (cyberArkConfig.isCertValidationRequired()) {
        httpClient = Http.getSafeOkHttpClientBuilder(cyberArkConfig.getCyberArkUrl(), 10, 10)
                         .addInterceptor(loggingInterceptor)
                         .build();
      } else {
        httpClient = getOkHttpClient(cyberArkConfig);
      }
    } else {
      httpClient = getOkHttpClientWithClientCertificate(cyberArkConfig);
    }

    final Retrofit retrofit = new Retrofit.Builder()
                                  .baseUrl(cyberArkConfig.getCyberArkUrl())
                                  .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                                  .client(httpClient)
                                  .build();

    return retrofit.create(CyberArkRestClient.class);
  }

  private static OkHttpClient getOkHttpClient(CyberArkConfig cyberArkConfig) {
    return Http.getUnsafeOkHttpClientBuilder(cyberArkConfig.getCyberArkUrl(), 10, 10)
        .addInterceptor(loggingInterceptor)
        .build();
  }

  private static OkHttpClient getOkHttpClientWithClientCertificate(CyberArkConfig cyberArkConfig) {
    try {
      String cyberArkClientCertificatePem = cyberArkConfig.getClientCertificate();
      KeyStore keyStore = getKeyStore(cyberArkClientCertificatePem);

      KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(keyStore, TEMPORARY_KEY_PASSWORD.toCharArray());

      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(keyManagerFactory.getKeyManagers(), Http.getTrustManagers(), new SecureRandom());

      return Http.getUnsafeOkHttpClientBuilder(cyberArkConfig.getCyberArkUrl(), 10, 10, sslContext)
          .addInterceptor(loggingInterceptor)
          .build();
    } catch (GeneralSecurityException | IOException e) {
      throw new SecretManagementDelegateException(CYBERARK_OPERATION_ERROR,
          "Failed to create http client to communicate with CyberArk", e, WingsException.USER);
    }
  }

  public static boolean validateClientCertificate(String cyberArkPem) {
    try {
      // Should not result in exception when use it to initialize a key store.
      getKeyStore(cyberArkPem);
      return true;
    } catch (Exception e) {
      log.error("Invalid client certificate", e);
      return false;
    }
  }

  private static KeyStore getKeyStore(String cyberArkPem) throws IOException, GeneralSecurityException {
    Certificate clientCertificate = loadCertificate(extractCertificatePem(cyberArkPem));
    PrivateKey privateKey = loadPrivateKey(extractPrivateKeyPem(cyberArkPem));
    // Certificate caCertificate = loadCertificate(cyberArkPem);

    KeyStore keyStore = KeyStore.getInstance("JKS");
    keyStore.load(null, TEMPORARY_KEY_PASSWORD.toCharArray());
    // keyStore.setCertificateEntry("ca-cert", caCertificate);
    keyStore.setCertificateEntry(KEYSTORE_CLIENT_CERT, clientCertificate);
    keyStore.setKeyEntry(
        KEYSTORE_CLIENT_KEY, privateKey, TEMPORARY_KEY_PASSWORD.toCharArray(), new Certificate[] {clientCertificate});
    return keyStore;
  }

  private static Certificate loadCertificate(String certificatePem) throws IOException, GeneralSecurityException {
    CertificateFactory certificateFactory = CertificateFactory.getInstance("X509");
    final byte[] content = readPemContent(certificatePem);
    return certificateFactory.generateCertificate(new ByteArrayInputStream(content));
  }

  private static PrivateKey loadPrivateKey(String privateKeyPem) throws GeneralSecurityException {
    return pemLoadPrivateKeyPkcs1OrPkcs8Encoded(privateKeyPem);
  }

  private static byte[] readPemContent(String pem) throws IOException {
    final byte[] content;
    StringReader stringReaderForPem = new StringReader(pem);
    try (PemReader pemReader = new PemReader(stringReaderForPem)) {
      final PemObject pemObject = pemReader.readPemObject();
      content = pemObject.getContent();
    }
    return content;
  }

  private static String extractCertificatePem(String cyberArkPem) {
    int startIndex = cyberArkPem.indexOf(PEM_CERTIFICATE_START);
    int endIndex = cyberArkPem.indexOf(PEM_CERTIFICATE_END) + PEM_CERTIFICATE_END.length();
    return cyberArkPem.substring(startIndex, endIndex);
  }

  private static String extractPrivateKeyPem(String cyberArkPem) {
    int startIndex = cyberArkPem.indexOf(PEM_PRIVATE_KEY_START);
    int endIndex = cyberArkPem.indexOf(PEM_PRIVATE_KEY_END) + PEM_PRIVATE_KEY_END.length();
    return cyberArkPem.substring(startIndex, endIndex);
  }

  private static PrivateKey pemLoadPrivateKeyPkcs1OrPkcs8Encoded(String privateKeyPem) throws GeneralSecurityException {
    if (privateKeyPem.contains(PEM_PRIVATE_KEY_START)) { // PKCS#8 format
      privateKeyPem = privateKeyPem.replace(PEM_PRIVATE_KEY_START, "").replace(PEM_PRIVATE_KEY_END, "");
      privateKeyPem = privateKeyPem.replaceAll("\\s", "");

      byte[] pkcs8EncodedKey = Base64.getDecoder().decode(privateKeyPem);
      KeyFactory factory = KeyFactory.getInstance("RSA");
      return factory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8EncodedKey));
    }

    // PKCS#1 format need special handling, see following SO article for more details if it really need to be supported.
    // https://stackoverflow.com/questions/7216969/getting-rsa-private-key-from-pem-base64-encoded-private-key-file
    throw new SecretManagementDelegateException(
        CYBERARK_OPERATION_ERROR, "Private key format is not supported.", WingsException.USER);
  }
}
