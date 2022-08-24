/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.impl.NGConnectorSecretManagerServiceImpl;
import io.harness.connector.impl.NGVaultServiceImpl;
import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.connector.services.NGVaultService;
import io.harness.ng.core.accountsetting.services.NGAccountSettingService;
import io.harness.ng.core.accountsetting.services.NGAccountSettingServiceImpl;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.api.NGSecretActivityService;
import io.harness.ng.core.api.NGSecretManagerService;
import io.harness.ng.core.api.NGSecretServiceV2;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.api.impl.NGEncryptedDataServiceImpl;
import io.harness.ng.core.api.impl.NGSecretActivityServiceImpl;
import io.harness.ng.core.api.impl.NGSecretManagerServiceImpl;
import io.harness.ng.core.api.impl.NGSecretServiceV2Impl;
import io.harness.ng.core.api.impl.NGSecretsFileServiceImpl;
import io.harness.ng.core.api.impl.SecretCrudServiceImpl;
import io.harness.ng.core.dao.NGEncryptedDataDao;
import io.harness.ng.core.dao.impl.NGEncryptedDaoServiceImpl;
import io.harness.repositories.NGEncryptedDataRepository;
import io.harness.repositories.NGEncryptedDataRepositoryImpl;
import io.harness.secrets.SecretsFileService;

import software.wings.service.impl.security.NGEncryptorService;
import software.wings.service.impl.security.NGEncryptorServiceImpl;
import software.wings.service.intfc.FileService;

import com.google.inject.AbstractModule;

@OwnedBy(PL)
public class SecretManagementModule extends AbstractModule {
  @Override
  protected void configure() {
    registerRequiredBindings();
    bind(SecretsFileService.class).to(NGSecretsFileServiceImpl.class);
    bind(NGEncryptedDataDao.class).to(NGEncryptedDaoServiceImpl.class);
    bind(NGEncryptedDataRepository.class).to(NGEncryptedDataRepositoryImpl.class);
    bind(NGEncryptedDataService.class).to(NGEncryptedDataServiceImpl.class);
    bind(NGConnectorSecretManagerService.class).to(NGConnectorSecretManagerServiceImpl.class);
    bind(NGSecretManagerService.class).to(NGSecretManagerServiceImpl.class);
    bind(NGSecretServiceV2.class).to(NGSecretServiceV2Impl.class);
    bind(SecretCrudService.class).to(SecretCrudServiceImpl.class);
    bind(NGSecretActivityService.class).to(NGSecretActivityServiceImpl.class);
    bind(NGEncryptorService.class).to(NGEncryptorServiceImpl.class);
    bind(NGVaultService.class).to(NGVaultServiceImpl.class);
    bind(NGAccountSettingService.class).to(NGAccountSettingServiceImpl.class);
  }

  private void registerRequiredBindings() {
    requireBinding(FileService.class);
  }
}
