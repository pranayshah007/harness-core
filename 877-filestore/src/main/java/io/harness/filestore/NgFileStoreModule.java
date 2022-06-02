/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filestore.dto.mapper.FilesFilterPropertiesMapper;
import io.harness.filestore.service.FileActivityService;
import io.harness.filestore.service.FileFailsafeService;
import io.harness.filestore.service.FileStoreService;
import io.harness.filestore.service.FileStructureService;
import io.harness.filestore.service.impl.FileActivityServiceImpl;
import io.harness.filestore.service.impl.FileFailsafeServiceImpl;
import io.harness.filestore.service.impl.FileStoreServiceImpl;
import io.harness.filestore.service.impl.FileStructureServiceImpl;
import io.harness.filter.FilterType;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.persistence.HPersistence;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class NgFileStoreModule extends AbstractModule {
  private static final AtomicReference<NgFileStoreModule> instanceRef = new AtomicReference<>();

  private NgFileStoreModule() {}

  public static NgFileStoreModule getInstance() {
    if (instanceRef.get() == null) {
      instanceRef.compareAndSet(null, new NgFileStoreModule());
    }

    return instanceRef.get();
  }

  @Override
  protected void configure() {
    registerRequiredBindings();

    bind(FileStoreService.class).to(FileStoreServiceImpl.class);
    bind(FileFailsafeService.class).to(FileFailsafeServiceImpl.class);
    bind(FileActivityService.class).to(FileActivityServiceImpl.class);
    bind(FileStructureService.class).to(FileStructureServiceImpl.class);

    MapBinder<String, FilterPropertiesMapper> filterPropertiesMapper =
        MapBinder.newMapBinder(binder(), String.class, FilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.FILESTORE.toString()).to(FilesFilterPropertiesMapper.class);
  }

  private void registerRequiredBindings() {
    requireBinding(HPersistence.class);
  }
}
