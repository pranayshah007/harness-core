/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.utils.YamlPipelineUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class FileUtils {
  private FileUtils() {}

  public static void createDirectories(String... dirs) {
    for (String dir : dirs) {
      try {
        Files.createDirectories(Path.of(dir));
      } catch (IOException e) {
        throw new UnexpectedException("Error while creating directories");
      }
    }
  }

  public static void writeObjectAsYamlInFile(Object object, String filePath) {
    try {
      String yaml = YamlPipelineUtils.writeString(object);
      Path file = Paths.get(filePath);
      Files.write(file, Collections.singletonList(yaml), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UnexpectedException("Error writing object as yaml in file");
    }
  }

  public static void cleanUpDirectories(String... dirs) {
    for (String dir : dirs) {
      try {
        org.apache.commons.io.FileUtils.deleteDirectory(new File(dir));
      } catch (IOException e) {
        log.error("Error in cleaning up directories. Exception = {}", e.getMessage(), e);
        throw new UnexpectedException("Error while cleaning up directories");
      }
    }
  }
}
