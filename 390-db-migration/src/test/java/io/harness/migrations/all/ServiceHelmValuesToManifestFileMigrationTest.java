/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.rule.OwnerRule.ANSHUL;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentType;
import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.appmanifest.StoreType;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.utils.ArtifactType;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ServiceHelmValuesToManifestFileMigrationTest extends WingsBaseTest {
  @Inject private HPersistence persistence;
  @Inject private ApplicationManifestService applicationManifestService;
  @InjectMocks @Inject private ServiceHelmValuesToManifestFileMigration serviceHelmValuesToManifestFileMigration;

  @Mock private AppService appService;
  @Mock private AccountService accountService;
  @Mock private Application application;
  @Mock private Account account;

  private Service service;
  private static final String SERVICE_HELM_VALUE = "Service-helmValue";
  private static final String VALUES_YAML_KEY = "values.yaml";

  @Before
  public void setupMocks() {
    when(appService.get(APP_ID)).thenReturn(application);
    when(accountService.get(anyString())).thenReturn(account);
    persistence.save(anApplication().uuid(APP_ID).accountId(ACCOUNT_ID).build());

    service = Service.builder()
                  .uuid(SERVICE_ID)
                  .appId(APP_ID)
                  .name(SERVICE_NAME)
                  .helmValueYaml(SERVICE_HELM_VALUE)
                  .deploymentType(DeploymentType.HELM)
                  .artifactType(ArtifactType.DOCKER)
                  .build();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testMigrateHelmValuesInServices() {
    persistence.save(service);

    serviceHelmValuesToManifestFileMigration.migrate();

    ApplicationManifest appManifest =
        applicationManifestService.getByServiceId(APP_ID, SERVICE_ID, AppManifestKind.VALUES);
    ManifestFile manifestFile =
        applicationManifestService.getManifestFileByFileName(appManifest.getUuid(), VALUES_YAML_KEY);

    assertThat(appManifest.getAppId()).isEqualTo(APP_ID);
    assertThat(appManifest.getServiceId()).isEqualTo(SERVICE_ID);
    assertThat(appManifest.getStoreType()).isEqualTo(StoreType.Local);
    assertThat(appManifest.getKind()).isEqualTo(AppManifestKind.VALUES);
    assertThat(appManifest.getGitFileConfig()).isNull();

    assertThat(manifestFile.getFileContent()).isEqualTo(SERVICE_HELM_VALUE);
    assertThat(manifestFile.getAppId()).isEqualTo(APP_ID);
    assertThat(manifestFile.getApplicationManifestId()).isEqualTo(appManifest.getUuid());
  }
}
