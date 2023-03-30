/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class FileUtils {
  public static String readFile(String dir, String fileName, String ext) {
    ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    String file = dir + fileName + ext;
    try (InputStream inputStream = classLoader.getResourceAsStream(file)) {
      if (inputStream == null) {
        return null;
      }
      return IOUtils.toString(inputStream, UTF_8);
    } catch (IOException e) {
      String errMessage = "Error occurred while reading file: " + file;
      log.error(errMessage, e);
      throw new InvalidRequestException(errMessage);
    }
  }
}
