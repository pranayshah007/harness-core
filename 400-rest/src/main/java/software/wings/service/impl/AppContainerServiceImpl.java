/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.delegate.beans.FileBucket.PLATFORMS;
import static io.harness.validation.Validator.notNullCheck;

import static com.google.common.collect.ImmutableMap.of;
import static java.lang.String.format;
import static org.atteo.evo.inflector.English.plural;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.delegate.beans.FileBucket;
import io.harness.exception.InvalidRequestException;
import io.harness.scheduler.PersistentScheduler;

import software.wings.beans.AppContainer;
import software.wings.beans.Service;
import software.wings.dl.WingsPersistence;
import software.wings.scheduler.PruneFileJob;
import software.wings.service.intfc.AppContainerService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.utils.FileType;
import software.wings.utils.FileTypeDetector;

import com.google.common.io.Files;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.executable.ValidateOnExecution;
import org.mongodb.morphia.mapping.Mapper;

/**
 * Created by anubhaw on 5/4/16.
 */
@ValidateOnExecution
@Singleton
public class AppContainerServiceImpl implements AppContainerService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private FileService fileService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private AppService appService;

  @Inject @Named("BackgroundJobScheduler") private PersistentScheduler jobScheduler;

  /**
   * {@inheritDoc}
   */
  @Override
  public PageResponse<AppContainer> list(PageRequest<AppContainer> request) {
    return wingsPersistence.query(AppContainer.class, request);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public AppContainer get(String accountId, String platformId) {
    return wingsPersistence.createQuery(AppContainer.class)
        .filter(AppContainer.ACCOUNT_ID_KEY2, accountId)
        .filter(Mapper.ID_KEY, platformId)
        .get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public AppContainer getByName(String accountId, String appContainerName) {
    return wingsPersistence.createQuery(AppContainer.class)
        .filter(AppContainer.ACCOUNT_ID_KEY2, accountId)
        .filter("name", appContainerName)
        .get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public AppContainer save(AppContainer appContainer, InputStream in, FileBucket fileBucket) {
    uploadAppContainerFile(appContainer, in, fileBucket);
    String appContainerKey = wingsPersistence.save(appContainer);
    return wingsPersistence.getWithAppId(AppContainer.class, appContainer.getAppId(), appContainerKey);
  }
  /**
   * {@inheritDoc}
   */
  @Override
  public AppContainer save(AppContainer appContainer) {
    String appContainerKey = wingsPersistence.save(appContainer);
    return wingsPersistence.getWithAppId(AppContainer.class, appContainer.getAppId(), appContainerKey);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public AppContainer update(AppContainer appContainer, InputStream in, FileBucket fileBucket) {
    AppContainer storedAppContainer = get(appContainer.getAccountId(), appContainer.getUuid());
    notNullCheck("App Stack", storedAppContainer);
    if (storedAppContainer.isSystemCreated()) {
      throw new InvalidRequestException("System created Application Stack can not be updated.");
    }
    if (newPlatformSoftwareBinaryUploaded(storedAppContainer, appContainer)) {
      uploadAppContainerFile(appContainer, in, fileBucket);
    }
    wingsPersistence.updateFields(AppContainer.class, appContainer.getUuid(),
        of("name", appContainer.getName(), "description", appContainer.getDescription(), "version",
            appContainer.getVersion(), "hardened", appContainer.isHardened()));
    return get(appContainer.getAccountId(), appContainer.getUuid());
  }

  @Override
  public AppContainer update(AppContainer appContainer) {
    AppContainer storedAppContainer = get(appContainer.getAccountId(), appContainer.getUuid());
    notNullCheck("App Stack", storedAppContainer);
    Map<String, Object> updatedFields = new HashMap<>();
    if (appContainer.getStackRootDirectory() != null) {
      updatedFields.put("stackRootDirectory", appContainer.getStackRootDirectory());
    }
    updatedFields.put("name", appContainer.getName());
    updatedFields.put("description", appContainer.getDescription());
    updatedFields.put("version", appContainer.getVersion());
    updatedFields.put("hardened", appContainer.isHardened());
    updatedFields.put("fileName", appContainer.getFileName());
    wingsPersistence.updateFields(AppContainer.class, appContainer.getUuid(), updatedFields);
    return get(appContainer.getAccountId(), appContainer.getUuid());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void delete(String accountId, String appContainerId) {
    AppContainer appContainer = get(accountId, appContainerId);
    if (appContainer == null) {
      return;
    }

    ensureAppContainerNotInUse(appContainerId);

    if (!appContainer.isSystemCreated() && appContainer.getFileUuid() != null) {
      PruneFileJob.addDefaultJob(jobScheduler, AppContainer.class, appContainerId, FileBucket.PLATFORMS);
    }

    wingsPersistence.delete(AppContainer.class, appContainerId);
  }

  private boolean newPlatformSoftwareBinaryUploaded(AppContainer storedAppContainer, AppContainer appContainer) {
    return !(appContainer.getChecksum() != null && appContainer.getChecksum().equals(storedAppContainer.getChecksum()));
  }

  private void ensureAppContainerNotInUse(String appContainerId) {
    List<Service> services = serviceResourceService
                                 .list(aPageRequest().addFilter("appContainer", Operator.EQ, appContainerId).build(),
                                     false, true, false, null)
                                 .getResponse();
    if (!services.isEmpty()) {
      throw new InvalidRequestException(
          format("Application Stack is in use by %d %s.", services.size(), plural("service", services.size())));
    }
  }

  private void uploadAppContainerFile(AppContainer appContainer, InputStream in, FileBucket fileBucket) {
    String fileId = fileService.saveFile(appContainer, in, fileBucket);
    appContainer.setFileUuid(fileId);

    File tempFile = new File(System.getProperty("java.io.tmpdir"), "appStack" + Thread.currentThread().getId());
    fileService.download(fileId, tempFile, fileBucket);

    try (BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(tempFile))) {
      FileType fileType = FileTypeDetector.detectType(bufferedInputStream);
      if (fileType == FileType.UNKNOWN) {
        throw new InvalidRequestException("Restricted file type. It must be ZIP or TAR.");
      }
      appContainer.setFileType(fileType);
      appContainer.setStackRootDirectory(fileType.getRoot(bufferedInputStream));
    } catch (InvalidRequestException e) {
      throw e;
    } catch (Exception e) {
      // do nothing
    }
  }

  @Override
  public File download(String accountId, String appContainerId) {
    AppContainer appContainer = get(accountId, appContainerId);
    File file = new File(Files.createTempDir(), appContainer.getFileName());
    fileService.download(appContainer.getFileUuid(), file, PLATFORMS);
    return file;
  }

  @Override
  public void deleteByAccountId(String accountId) {
    List<AppContainer> appContainerList =
        wingsPersistence.createQuery(AppContainer.class).filter(AppContainer.ACCOUNT_ID_KEY2, accountId).asList();
    for (AppContainer appContainer : appContainerList) {
      delete(accountId, appContainer.getUuid());
    }
  }
}
