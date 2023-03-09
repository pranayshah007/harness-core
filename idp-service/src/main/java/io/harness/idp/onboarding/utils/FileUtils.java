/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.utils.YamlPipelineUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

@OwnedBy(HarnessTeam.IDP)
public class FileUtils {
  private FileUtils() {}

  public static void createDirectories(String... dirs) {
    for (String dir : dirs) {
      try {
        Files.createDirectories(Path.of(dir));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static void writeObjectAsYamlInFile(Object object, String filePath) {
    try {
      String yaml = YamlPipelineUtils.writeString(object);
      Path file = Paths.get(filePath);
      Files.write(file, Collections.singletonList(yaml), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
