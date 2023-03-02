/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.util.io.pem.PemObject;

@UtilityClass
@Slf4j
public class PemReader {
  private static final Pattern CERT_HEADER =
      Pattern.compile("-+BEGIN\\s[^-\\r\\n]*CERTIFICATE[^-\\r\\n]*-+(?:\\s|\\r|\\n)+");
  private static final Pattern CERT_FOOTER =
      Pattern.compile("-+END\\s[^-\\r\\n]*CERTIFICATE[^-\\r\\n]*-+(?:\\s|\\r|\\n)*");
  private static final Pattern BODY = Pattern.compile("[a-z0-9+/=][a-z0-9+/=\\r\\n]*", Pattern.CASE_INSENSITIVE);

  // THE CALLER IS RESPONSIBLE TO CLOSE THE STREAM
  public static X509Certificate[] getCertificates(InputStream is) throws CertificateException {
    final ByteArrayInputStream[] certs = readCertificates(is);

    CertificateFactory cf = CertificateFactory.getInstance("X.509");
    X509Certificate[] x509Certs = new X509Certificate[certs.length];

    for (int i = 0; i < certs.length; i++) {
      x509Certs[i] = (X509Certificate) cf.generateCertificate(certs[i]);
    }

    return x509Certs;
  }

  static ByteArrayInputStream[] readCertificates(InputStream is) throws CertificateException {
    String content;
    try {
      content = readContent(is);
    } catch (IOException e) {
      throw new CertificateException("failed to read certificate input stream", e);
    }

    List<ByteArrayInputStream> certs = new ArrayList<>();
    Matcher m = CERT_HEADER.matcher(content);
    int start = 0;
    for (;;) {
      if (!m.find(start)) {
        break;
      }
      m.usePattern(BODY);
      if (!m.find()) {
        break;
      }

      final byte[] bytes = Base64.decodeBase64(m.group(0));
      m.usePattern(CERT_FOOTER);
      if (!m.find()) {
        // CERTIFICATE IS INCOMPLETE.
        break;
      }
      certs.add(new ByteArrayInputStream(bytes));

      start = m.end();
      m.usePattern(CERT_HEADER);
    }

    if (certs.isEmpty()) {
      throw new CertificateException("no certificates in input stream");
    }

    return certs.toArray(new ByteArrayInputStream[0]);
  }

  private static String readContent(InputStream in) throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buf = new byte[8192];
      for (;;) {
        int ret = in.read(buf);
        if (ret < 0) {
          break;
        }
        out.write(buf, 0, ret);
      }
      return out.toString();
    }
  }

  public static PrivateKey readPrivateKey(InputStream is)
      throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
    try (Reader reader = new InputStreamReader(is);
         org.bouncycastle.util.io.pem.PemReader pemReader = new org.bouncycastle.util.io.pem.PemReader(reader)) {
      final PemObject pemObject = pemReader.readPemObject();
      final byte[] content = pemObject.getContent();
      final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(content);

      final KeyFactory factory = KeyFactory.getInstance("RSA");
      return factory.generatePrivate(keySpec);
    }
  }
}
