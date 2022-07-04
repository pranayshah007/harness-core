/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.KeyManagerBuilderException;
import io.harness.filesystem.FileReader;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509KeyManager;
import javax.xml.bind.DatatypeConverter;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class X509KeyManagerBuilderTest extends CategoryTest {
  private static final String PKCS8_KEY_HEADER = "-----BEGIN PRIVATE KEY-----";
  private static final String PKCS8_KEY_BASE64 = "MIIBVAIBADANBgkqhkiG9w0BAQEFAASCAT4wggE6AgEAAkEApllh1fPR3Qwi/Uix\n"
      + "9HlkqIc0J4417hGXzfpGy8G8Jq0lnsajTnhwJL/sXCkrDJ9LXRgdO1AnskTg0/CX\n"
      + "EpyXCQIDAQABAkBixfwD0GyydjxobLvN0C7mqrFbM2PuYl/jHFLhNb4EsCHd470y\n"
      + "1vVThKaW0HNJ4XiOiZSv7bqWRQrNMO0c6XaJAiEA00AhMuVMYrLUwsiWr6go9WUt\n"
      + "AHFqMmQuyNSsYEKnPusCIQDJlk3Krb8YtFYd4hnNpjDSxxquT4vrhBy+hsmgVZRM\n"
      + "2wIhAL3nwbCCR3fWscNlFWlVr3Ri/uCOFFy2iQRLg6aJZNX/AiBhrRV2bmeYxdCw\n"
      + "XBzam3sutlDEQ0Dt1i7DVrYdnTnlXwIgQkS05a4cyO9ZRdOhMhYFCUaUpcmM8w3v\n"
      + "p88nNbXDh4Y=";
  private static final String PKCS8_KEY_FOOTER = "-----END PRIVATE KEY-----";

  private static final String FILE_PATH_CERT = "/some/file/path.crt";
  private static final String FILE_PATH_KEY = "/some/file/path.key";

  private static final String PEM_KEY_VALID = loadResource("/io/harness/security/certs/key-valid.pem");
  private static final String PEM_CERT_VALID = loadResource("/io/harness/security/certs/cert-valid.pem");
  private static final String PEM_CERT_CHAIN_VALID = loadResource("/io/harness/security/certs/cert-chain-valid.pem");

  private static final String CLIENT_CERT_ALIAS_0 =
      String.format(X509KeyManagerBuilder.CLIENT_CERTIFICATE_KEY_ENTRY_NAME_FORMAT, 0);
  private static final String CLIENT_CERT_ALIAS_1 =
      String.format(X509KeyManagerBuilder.CLIENT_CERTIFICATE_KEY_ENTRY_NAME_FORMAT, 1);

  private static String loadResource(String resourcePath) {
    try {
      return Resources.toString(X509KeyManagerBuilderTest.class.getResource(resourcePath), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      return "NOT FOUND";
    }
  }

  @Test
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testBuildWithoutClientCertificate() throws Exception {
    X509KeyManager manager = new X509KeyManagerBuilder().build();
    assertThat(manager).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileSmokeTest() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8)).thenReturn(PEM_KEY_VALID);
    InputStream stream = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(PEM_CERT_VALID).array());
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(stream);

    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);
    X509KeyManager manager = builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY).build();

    assertThat(manager).isNotNull();

    PrivateKey pk = manager.getPrivateKey(CLIENT_CERT_ALIAS_0);
    assertThat(pk).isNotNull();

    X509Certificate[] certs = manager.getCertificateChain(CLIENT_CERT_ALIAS_0);
    assertThat(certs).isNotNull();
    assertThat(certs.length).isEqualTo(1);
    assertThat(DatatypeConverter.printHexBinary(MessageDigest.getInstance("SHA-256").digest(certs[0].getEncoded())))
        .isEqualTo("32DD4D30482A06426B4BD498DBDE6774F4C17884BF42F0A9637021F224905F19");
  }

  @Test
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileWithCertChain() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8)).thenReturn(PEM_KEY_VALID);
    InputStream stream = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(PEM_CERT_CHAIN_VALID).array());
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(stream);

    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);
    X509KeyManager manager = builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY).build();

    assertThat(manager).isNotNull();

    PrivateKey pk = manager.getPrivateKey(CLIENT_CERT_ALIAS_0);
    assertThat(pk).isNotNull();

    X509Certificate[] certs = manager.getCertificateChain(CLIENT_CERT_ALIAS_0);
    assertThat(certs).isNotNull();
    assertThat(certs.length).isEqualTo(2);
    X509Certificate clientCert = certs[0];
    X509Certificate caCert = certs[1];
    assertThat(DatatypeConverter.printHexBinary(MessageDigest.getInstance("SHA-256").digest(clientCert.getEncoded())))
        .isEqualTo("32DD4D30482A06426B4BD498DBDE6774F4C17884BF42F0A9637021F224905F19");
    // ca cert
    assertThat(DatatypeConverter.printHexBinary(MessageDigest.getInstance("SHA-256").digest(caCert.getEncoded())))
        .isEqualTo("3B12A7C945BEE25E8350BB418D9D07CE199A1BBCA7EAED07E682BB48D46154F6");

    assertThat(clientCert.getIssuerDN().getName()).isEqualTo(caCert.getSubjectDN().getName());
  }

  @Test
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileTwoClientCerts() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);

    // Add first client cert
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8)).thenReturn(PEM_KEY_VALID);
    InputStream stream = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(PEM_CERT_VALID).array());
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(stream);
    builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY);

    // Add second client cert
    stream = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(PEM_CERT_CHAIN_VALID).array());
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(stream);
    builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY);

    X509KeyManager manager = builder.build();
    assertThat(manager).isNotNull();

    PrivateKey pk0 = manager.getPrivateKey(CLIENT_CERT_ALIAS_0);
    assertThat(pk0).isNotNull();

    PrivateKey pk1 = manager.getPrivateKey(CLIENT_CERT_ALIAS_1);
    assertThat(pk1).isNotNull();

    X509Certificate[] certs0 = manager.getCertificateChain(CLIENT_CERT_ALIAS_0);
    assertThat(certs0).isNotNull();
    assertThat(certs0.length).isEqualTo(1);

    X509Certificate[] certs1 = manager.getCertificateChain(CLIENT_CERT_ALIAS_1);
    assertThat(certs1).isNotNull();
    assertThat(certs1.length).isEqualTo(2);
  }

  @Test(expected = KeyManagerBuilderException.class)
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileWithInvalidCertPath() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8)).thenReturn(PEM_KEY_VALID);
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ))
        .thenThrow(new InvalidPathException(FILE_PATH_CERT, "Thrown by UT"));

    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);
    builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY).build();
  }

  @Test(expected = KeyManagerBuilderException.class)
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileWithInvalidCertNull() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8)).thenReturn(PEM_KEY_VALID);
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(null);

    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);
    builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY).build();
  }

  @Test(expected = KeyManagerBuilderException.class)
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileWithInvalidCertNotPEM() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8)).thenReturn(PEM_KEY_VALID);
    InputStream stream = new ByteArrayInputStream(StandardCharsets.UTF_8.encode("NOT A PEM").array());
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(stream);

    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);
    builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY).build();
  }

  @Test(expected = KeyManagerBuilderException.class)
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileWithInvalidKeyPath() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    InputStream stream = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(PEM_CERT_VALID).array());
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(stream);
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8))
        .thenThrow(new InvalidPathException(FILE_PATH_KEY, "Thrown by UT"));

    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);
    builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY).build();
  }

  @Test(expected = KeyManagerBuilderException.class)
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileWithInvalidKeyNull() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    InputStream stream = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(PEM_CERT_VALID).array());
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(stream);
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8)).thenReturn(null);

    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);
    builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY).build();
  }

  @Test(expected = KeyManagerBuilderException.class)
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileWithInvalidKeyEmpty() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    InputStream stream = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(PEM_CERT_VALID).array());
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(stream);
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8)).thenReturn("");

    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);
    builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY).build();
  }

  @Test(expected = KeyManagerBuilderException.class)
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileWithInvalidKeyNoHeader() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    InputStream stream = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(PEM_CERT_VALID).array());
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(stream);
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8))
        .thenReturn(StringUtils.joinWith("\n", PKCS8_KEY_BASE64, PKCS8_KEY_FOOTER));

    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);
    builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY).build();
  }

  @Test(expected = KeyManagerBuilderException.class)
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileWithInvalidKeyNoFooter() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    InputStream stream = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(PEM_CERT_VALID).array());
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(stream);
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8))
        .thenReturn(StringUtils.joinWith("\n", PKCS8_KEY_HEADER, PKCS8_KEY_BASE64));

    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);
    builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY).build();
  }

  @Test(expected = KeyManagerBuilderException.class)
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileWithInvalidKeyNoBase64() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    InputStream stream = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(PEM_CERT_VALID).array());
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(stream);
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8))
        .thenReturn(StringUtils.joinWith("\n", PKCS8_KEY_HEADER, PKCS8_KEY_FOOTER));

    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);
    builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY).build();
  }

  @Test(expected = KeyManagerBuilderException.class)
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileWithInvalidKeyBadHeader() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    InputStream stream = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(PEM_CERT_VALID).array());
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(stream);
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8))
        .thenReturn(StringUtils.joinWith("\n", "-----BEGIN KEY-----", PKCS8_KEY_BASE64, PKCS8_KEY_FOOTER));

    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);
    builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY).build();
  }

  @Test(expected = KeyManagerBuilderException.class)
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileWithInvalidKeyBadBase64() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    InputStream stream = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(PEM_CERT_VALID).array());
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(stream);
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8))
        .thenReturn(StringUtils.joinWith("\n", PKCS8_KEY_HEADER, "x", PKCS8_KEY_FOOTER));

    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);
    builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY).build();
  }

  @Test(expected = KeyManagerBuilderException.class)
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileWithInvalidKeyBadFooter() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    InputStream stream = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(PEM_CERT_VALID).array());
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(stream);
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8))
        .thenReturn(StringUtils.joinWith("\n", PKCS8_KEY_HEADER, PKCS8_KEY_BASE64, "-----END PRIVATE-----"));

    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);
    builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY).build();
  }

  @Test(expected = KeyManagerBuilderException.class)
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileWithInvalidKeyTooShort() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    InputStream stream = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(PEM_CERT_VALID).array());
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(stream);
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8))
        .thenReturn(StringUtils.joinWith("\n", PKCS8_KEY_HEADER,
            "MIHDAgEAMA0GCSqGSIb3DQEBAQUABIGuMIGrAgEAAiEAwsZNlBEGs1acZIWR2bGc\n"
                + "wG5hFQad5/nT/Q5aiupUSgMCAwEAAQIgBF2njYMYMFrnpIUt8MA/cDLFxMBz5RpX\n"
                + "/e3XhJSBmJkCEQDkpo40vTPktd/kQmHbLhfXAhEA2hJw0s+HQQRGUhkh6PQptQIQ\n"
                + "fXThvBBAQ/KDCWRe+vscewIRAMaOc9v+yZARbFXkPFP3vOECEQCEYbd3yvWKu+XU\n"
                + "OGjza/CG",
            PKCS8_KEY_FOOTER));

    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);
    builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY).build();
  }

  @Test
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileWithKeyNoNewLines() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    InputStream stream = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(PEM_CERT_VALID).array());
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(stream);
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8))
        .thenReturn(PKCS8_KEY_HEADER + PKCS8_KEY_BASE64 + PKCS8_KEY_FOOTER);

    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);
    builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY).build();
  }

  @Test
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileWithKeyManyNewLines() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    InputStream stream = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(PEM_CERT_VALID).array());
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(stream);
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8))
        .thenReturn(PKCS8_KEY_HEADER + "\n\n\n\n" + PKCS8_KEY_BASE64 + "\n\n\n\n" + PKCS8_KEY_FOOTER);

    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);
    builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY).build();
  }

  @Test
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileWithKeyWindowsNewLines() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    InputStream stream = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(PEM_CERT_VALID).array());
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(stream);
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8))
        .thenReturn(PKCS8_KEY_HEADER + "\r\n\r\n" + PKCS8_KEY_BASE64 + "\r\n\r\n" + PKCS8_KEY_FOOTER);

    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);
    builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY).build();
  }

  @Test
  @Owner(developers = OwnerRule.JOHANNES)
  @Category(UnitTests.class)
  public void testClientCertificateFromFileWithKeyManySpaces() throws Exception {
    FileReader mockFileReader = mock(FileReader.class);
    InputStream stream = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(PEM_CERT_VALID).array());
    when(mockFileReader.newInputStream(FILE_PATH_CERT, StandardOpenOption.READ)).thenReturn(stream);
    when(mockFileReader.getFileContent(FILE_PATH_KEY, StandardCharsets.UTF_8))
        .thenReturn("    \n   " + PKCS8_KEY_HEADER + "   \n    " + PKCS8_KEY_BASE64 + "   \n   " + PKCS8_KEY_FOOTER
            + "   \n   ");

    X509KeyManagerBuilder builder = new X509KeyManagerBuilder(mockFileReader);
    builder.withClientCertificateFromFile(FILE_PATH_CERT, FILE_PATH_KEY).build();
  }
}
