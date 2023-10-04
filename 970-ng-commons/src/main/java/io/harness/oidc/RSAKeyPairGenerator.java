/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemObjectGenerator;
import org.bouncycastle.util.io.pem.PemWriter;

public class RSAKeyPairGenerator {
  public KeyPair generateKeyPair() {
    Security.addProvider(new BouncyCastleProvider());
    KeyPairGenerator keyPairGenerator = null;
    try {
      keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    } catch (NoSuchProviderException e) {
      throw new RuntimeException(e);
    }
    keyPairGenerator.initialize(2048);
    return keyPairGenerator.generateKeyPair();
  }

  public RSAKeyPairPEM generateKeyPairPEM() {
    KeyPair keyPair = generateKeyPair();
    PublicKey publicKey = keyPair.getPublic();
    PrivateKey privateKey = keyPair.getPrivate();

    try {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      PemWriter pemWriter = new PemWriter(new OutputStreamWriter(byteArrayOutputStream));
      PemObjectGenerator pemObjectGenerator = new PemObject("PRIVATE KEY", privateKey.getEncoded());
      pemWriter.writeObject(pemObjectGenerator);
      pemWriter.flush();
      String privateKeyPEM = byteArrayOutputStream.toString();

      byteArrayOutputStream = new ByteArrayOutputStream();
      pemWriter = new PemWriter(new OutputStreamWriter(byteArrayOutputStream));

      pemObjectGenerator = new PemObject("PUBLIC KEY", publicKey.getEncoded());
      pemWriter.writeObject(pemObjectGenerator);
      pemWriter.flush();
      String publicKeyPEM = byteArrayOutputStream.toString();
      pemWriter.close();
      byteArrayOutputStream.close();
      return RSAKeyPairPEM.builder().privateKeyPem(privateKeyPEM).publicKeyPem(publicKeyPEM).build();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
