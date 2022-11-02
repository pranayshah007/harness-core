/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static org.apache.commons.codec.binary.Base64.decodeBase64;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class UuidUtils {
  private UuidUtils() {}
  public static String base64StrToUuid(String str) throws BufferUnderflowException, BufferOverflowException {
    byte[] bytes = decodeBase64(str);
    ByteBuffer bb = ByteBuffer.wrap(bytes);
    UUID uuid = new UUID(bb.getLong(), bb.getLong());
    return uuid.toString();
  }

  public static boolean isValidUuidStr(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (Exception e) {
      log.info("{} is not a valid UUID", value);
    }
    return false;
  }
}
