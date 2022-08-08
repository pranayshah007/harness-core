/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static com.google.common.collect.ImmutableMap.of;

import io.harness.beans.PageRequest;
import io.harness.delegate.beans.FileBucket;
import io.harness.logging.Misc;
import io.harness.stream.BoundedInputStream;

import software.wings.beans.SystemCatalog;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.SystemCatalogService;
import software.wings.utils.FileType;
import software.wings.utils.FileTypeDetector;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import javax.validation.executable.ValidateOnExecution;

/**
 * Created by sgurubelli on 5/23/17.
 */
@ValidateOnExecution
@Singleton
public class SystemCatalogSeviceImpl implements SystemCatalogService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private FileService fileService;
  /**
   * {@inheritDoc}
   */
  @Override
  public SystemCatalog save(SystemCatalog systemCatalog, String url, FileBucket fileBucket, long size) {
    uploadSystemCatalogFile(systemCatalog, url, fileBucket, size);
    String systemCatalogKey = wingsPersistence.save(systemCatalog);
    return wingsPersistence.getWithAppId(SystemCatalog.class, systemCatalog.getAppId(), systemCatalogKey);
  }

  @Override
  public SystemCatalog update(SystemCatalog systemCatalog, String url, FileBucket fileBucket, long size) {
    SystemCatalog storedSystemCatalog = get(systemCatalog.getUuid());
    notNullCheck("System Catalog", storedSystemCatalog);
    if (newPlatformSoftwareBinaryUploaded(storedSystemCatalog, systemCatalog)) {
      uploadSystemCatalogFile(systemCatalog, url, fileBucket, size);
    }
    wingsPersistence.updateFields(SystemCatalog.class, systemCatalog.getUuid(),
        of("name", systemCatalog.getName(), "notes", systemCatalog.getNotes(), "version", systemCatalog.getVersion(),
            "hardened", systemCatalog.isHardened(), "stackRootDirectory", systemCatalog.getStackRootDirectory()));
    return get(systemCatalog.getUuid());
  }

  private boolean uploadSystemCatalogFile(SystemCatalog systemCatalog, String url, FileBucket fileBucket, long size) {
    BufferedInputStream in = new BufferedInputStream(BoundedInputStream.createBoundedStreamForUrl(url, size));

    String fileId = fileService.saveFile(systemCatalog, in, fileBucket);
    systemCatalog.setFileUuid(fileId);
    systemCatalog.setAppId(GLOBAL_APP_ID);

    File tempFile = new File(
        System.getProperty("java.io.tmpdir"), systemCatalog.getCatalogType().name() + Thread.currentThread().getId());
    fileService.download(fileId, tempFile, fileBucket);

    Misc.ignoreException(() -> {
      try (BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(tempFile))) {
        FileType fileType = FileTypeDetector.detectType(bufferedInputStream);
        systemCatalog.setFileType(fileType);
        systemCatalog.setStackRootDirectory(fileType.getRoot(bufferedInputStream));
      }
    });

    return tempFile.delete();
  }

  @Override
  public List<SystemCatalog> list(PageRequest<SystemCatalog> pageRequest) {
    return wingsPersistence.query(SystemCatalog.class, pageRequest).getResponse();
  }

  @Override
  public SystemCatalog get(String systemCatalogId) {
    return wingsPersistence.get(SystemCatalog.class, systemCatalogId);
  }

  private boolean newPlatformSoftwareBinaryUploaded(SystemCatalog storedSystemCatalog, SystemCatalog systemCatalog) {
    return !(
        systemCatalog.getChecksum() != null && systemCatalog.getChecksum().equals(storedSystemCatalog.getChecksum()));
  }
}
