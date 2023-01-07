/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import dev.morphia.annotations.Transient;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Very simple hardcoded encryption package for encrypting user passwords in persistence.
 * Created by mike@ on 4/24/17.
 */
@OwnedBy(PL)
public class SimpleEncryption implements EncryptionInterface {
  @JsonIgnore public static final Charset CHARSET = Charsets.ISO_8859_1;
  @JsonIgnore private static final int AES_256_KEY_LENGTH = 32;

  // IV and KEY both need to be AES_256_KEY_LENGTH characters long.
  @JsonIgnore private static final byte[] IV = "EncryptionIV0*d&".getBytes(CHARSET);
  @JsonIgnore private static final char[] DEFAULT_KEY = "EncryptionKey2a@EncryptionKey2a@".toCharArray();
  @JsonIgnore @Transient private SecretKeyFactory FACTORY;

  @JsonIgnore @Transient private char[] key;
  private byte[] salt;
  @JsonIgnore @Transient private SecretKey secretKey;

  public SimpleEncryption() {
    this(DEFAULT_KEY, EncryptionUtils.generateSalt());
  }

  public SimpleEncryption(char[] key) {
    this(key, EncryptionUtils.generateSalt());
  }

  public SimpleEncryption(String keySource) {
    this(BaseEncoding.base64().encode(Hashing.sha256().hashString(keySource, CHARSET).asBytes()).toCharArray(),
        EncryptionUtils.generateSalt());
  }

  public SimpleEncryption(String keySource, byte[] salt) {
    this(BaseEncoding.base64().encode(Hashing.sha256().hashString(keySource, CHARSET).asBytes()).toCharArray(), salt);
  }

  public SimpleEncryption(char[] key, byte[] salt) {
    if (key.length > AES_256_KEY_LENGTH) {
      key = Arrays.copyOf(key, AES_256_KEY_LENGTH);
    }
    if (key.length != AES_256_KEY_LENGTH) {
      throw new WingsException("Key must be " + AES_256_KEY_LENGTH + " characters. Key is " + key.length);
    }
    this.key = key.clone();
    this.salt = salt == null ? null : salt.clone();
    this.secretKey = generateSecretKey(key, salt);
  }

  @Override
  @JsonIgnore
  public SecretKey getSecretKey() {
    return this.secretKey;
  }

  @Override
  public byte[] encrypt(byte[] content) {
    try {
      Cipher c = Cipher.getInstance("AES/CBC/PKCS5PADDING");
      c.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(IV));
      byte[] encrypted = c.doFinal(content);
      byte[] combined = new byte[salt.length + encrypted.length];
      System.arraycopy(salt, 0, combined, 0, salt.length);
      System.arraycopy(encrypted, 0, combined, salt.length, encrypted.length);
      return combined;
    } catch (InvalidKeyException e) {
      // Key must be AES_256_KEY_LENGTH ASCII characters. If the JCE Unlimited Strength jars aren't installed, this
      // won't work.
      throw new WingsException(ErrorCode.ENCRYPTION_NOT_CONFIGURED,
          "Encryption failed. Have you installed the JCE Unlimited Strength jar files?", e);
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidAlgorithmParameterException
        | IllegalBlockSizeException | BadPaddingException e) {
      throw new WingsException(ErrorCode.ENCRYPTION_NOT_CONFIGURED, "Encryption failed: ", e);
    }
  }

  public char[] encryptChars(char[] content) {
    if (content == null) {
      return null;
    }
    byte[] encrypted = this.encrypt(CHARSET.encode(CharBuffer.wrap(content)).array());
    return CHARSET.decode(ByteBuffer.wrap(encrypted)).array();
  }

  @Override
  public byte[] decrypt(byte[] encrypted) {
    try {
      byte[] newSalt = new byte[EncryptionUtils.DEFAULT_SALT_SIZE];
      byte[] inputBytes = new byte[encrypted.length - EncryptionUtils.DEFAULT_SALT_SIZE];
      System.arraycopy(encrypted, 0, newSalt, 0, newSalt.length);
      System.arraycopy(encrypted, newSalt.length, inputBytes, 0, inputBytes.length);
      this.secretKey = generateSecretKey(key, newSalt);
      Cipher c = Cipher.getInstance("AES/CBC/PKCS5PADDING");
      c.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(IV));
      return c.doFinal(inputBytes);
    } catch (InvalidKeyException e) {
      // Key must be AES_256_KEY_LENGTH ASCII characters. If the JCE Unlimited Strength jars aren't installed, this
      // won't work.

      throw new WingsException(ErrorCode.ENCRYPTION_NOT_CONFIGURED,
          "Decryption failed. Have you installed the JCE Unlimited Strength jar files?", e);
    } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidAlgorithmParameterException
        | IllegalBlockSizeException | BadPaddingException e) {
      throw new WingsException(ErrorCode.ENCRYPTION_NOT_CONFIGURED, "Decryption failed: ", e);
    }
  }

  public char[] decryptChars(char[] encrypted) {
    if (encrypted == null) {
      return null;
    }
    byte[] decrypted = this.decrypt(CHARSET.encode(CharBuffer.wrap(encrypted)).array());
    return CHARSET.decode(ByteBuffer.wrap(decrypted)).array();
  }

  private SecretKey generateSecretKey(char[] key, byte[] salt) {
    try {
      FACTORY = SecretKeyFactory.getInstance("PBEWithHmacSHA256AndAES_256");
      KeySpec spec = new PBEKeySpec(key, salt, 65536, 256);
      SecretKey tmp = FACTORY.generateSecret(spec);
      return new SecretKeySpec(tmp.getEncoded(), "AES");
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new WingsException("Encryption secret key generation failed: ", e);
    }
  }
}
