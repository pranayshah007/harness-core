/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.executor.taskloader;

import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.exception.WingsException;
import io.harness.serializer.KryoSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Deserialize task data from mounted volume
 */
@UtilityClass
@Slf4j
public class TaskPackageReader {
  public static DelegateTaskPackage readTask(String taskDataPath, KryoSerializer kryoSerializer) {
    try {
      var data = Files.readAllBytes(Path.of(taskDataPath));
      return (DelegateTaskPackage) kryoSerializer.asObject(data);
    } catch (IOException e) {
      log.error("Deserialize task package error", e);
      // TODO: define exceptions
      throw new WingsException(e);
    }
  }
}
