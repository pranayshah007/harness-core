/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.runner.taskloader;

import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.runner.config.Configuration;
import io.harness.exception.WingsException;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

/**
 * Deserialize task data from mounted volume
 */
@Slf4j
public class TaskPackageReaderImpl implements TaskPackageReader {
  @Inject private Configuration configuration;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer kryoSerializer;
  @Override
  public DelegateTaskPackage readTask() {
    final File taskDataFile = new File(configuration.getTaskFilePath());
    try {
      return (DelegateTaskPackage) kryoSerializer.asObject(FileUtils.readFileToByteArray(taskDataFile));
    } catch (IOException e) {
      log.error("Deserialize task package error", e);
      // TODO: define exceptions
      throw new WingsException(e);
    }
  }
}
