/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.file;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static software.wings.service.impl.FileServiceImpl.FILE_SERVICE_DATA_STORAGE_MODE;
import static software.wings.service.impl.GoogleCloudFileServiceImpl.FILE_SERVICE_CLUSTER_NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.file.dao.GcsHarnessFileMetadataDao;
import io.harness.file.dao.NgGcsHarnessFileMetadataDaoImpl;
import io.harness.persistence.HPersistence;

import software.wings.DataStorageMode;
import software.wings.service.impl.FileServiceImpl;
import software.wings.service.intfc.FileService;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.atomic.AtomicReference;

@OwnedBy(PL)
public class NGFileServiceModule extends AbstractModule {
  private static final AtomicReference<NGFileServiceModule> instanceRef = new AtomicReference<>();

  private final DataStorageMode dataStorageMode;
  private final String clusterName;

  private NGFileServiceModule(DataStorageMode dataStorageMode, String clusterName) {
    this.dataStorageMode = dataStorageMode;
    this.clusterName = clusterName;
  }

  public static NGFileServiceModule getInstance(DataStorageMode dataStorageMode, String clusterName) {
    if (instanceRef.get() == null) {
      instanceRef.compareAndSet(null, new NGFileServiceModule(dataStorageMode, clusterName));
    }

    return instanceRef.get();
  }

  @Override
  protected void configure() {
    registerRequiredBindings();
    bind(GcsHarnessFileMetadataDao.class).to(NgGcsHarnessFileMetadataDaoImpl.class);
    bind(FileService.class).to(FileServiceImpl.class);
  }

  @Provides
  @Singleton
  @Named(FILE_SERVICE_CLUSTER_NAME)
  protected String getClusterName() {
    return this.clusterName;
  }

  @Provides
  @Singleton
  @Named(FILE_SERVICE_DATA_STORAGE_MODE)
  protected DataStorageMode getDataStorageMode() {
    return this.dataStorageMode;
  }

  private void registerRequiredBindings() {
    requireBinding(HPersistence.class);
  }
}
