/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc;

import com.google.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemObjectGenerator;
import org.bouncycastle.util.io.pem.PemWriter;

@Slf4j
@Singleton
public class RSAKeysUtils {
  public static final String PRIVATE_KEY = "PRIVATE KEY";
  public static final String PUBLIC_KEY = "PUBLIC KEY";

  public KeyPair generateKeyPair() {
    Security.addProvider(new BouncyCastleProvider());
    KeyPairGenerator keyPairGenerator = null;
    try {
      keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
    } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
      throw new RuntimeException(e);
    }
    keyPairGenerator.initialize(2048);
    return keyPairGenerator.generateKeyPair();
  }

  public RSAKeyPairPEM generateKeyPairPEM() {
    KeyPair keyPair = generateKeyPair();

    PublicKey publicKey = keyPair.getPublic();
    PrivateKey privateKey = keyPair.getPrivate();

    String privateKeyPEM = convertToPem(PRIVATE_KEY, privateKey);
    String publicKeyPEM = convertToPem(PUBLIC_KEY, publicKey);
    return RSAKeyPairPEM.builder().privateKeyPem(privateKeyPEM).publicKeyPem(publicKeyPEM).build();
  }

  public RSAKey readPemFile(String pemFile) {
    Reader reader = new StringReader(pemFile);
    PEMParser pemParser = new PEMParser(reader);
    Object pemObject = null;
    try {
      pemObject = pemParser.readObject();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    if (pemObject instanceof SubjectPublicKeyInfo) {
      try {
        return (RSAPublicKey) new JcaPEMKeyConverter().getPublicKey((SubjectPublicKeyInfo) pemObject);
      } catch (PEMException e) {
        throw new RuntimeException(e);
      }
    } else if (pemObject instanceof PrivateKeyInfo) {
      try {
        return (RSAPrivateKey) new JcaPEMKeyConverter().getPrivateKey((PrivateKeyInfo) pemObject);
      } catch (PEMException e) {
        throw new RuntimeException(e);
      }
    } else {
      log.error("Invalid PEM file format");
      throw new RuntimeException("Invalid PEM file format");
    }
  }

  public String convertToPem(String keyType, Key key) {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    PemWriter pemWriter = new PemWriter(new OutputStreamWriter(byteArrayOutputStream));
    PemObjectGenerator pemObjectGenerator = new PemObject(keyType, key.getEncoded());
    try {
      pemWriter.writeObject(pemObjectGenerator);
      pemWriter.flush();
      pemWriter.close();
      byteArrayOutputStream.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return byteArrayOutputStream.toString();
  }
}
