/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidAccessRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentType;
import software.wings.beans.HelmChartConfig;
import software.wings.beans.Service;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.StoreType;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.ServiceResourceService;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class AddAppManifestNameTest extends WingsBaseTest {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AddAppManifestName addAppManifestNameMigration;
  @Mock private ServiceResourceService serviceResourceService;

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldMigrateAppManifestSuccessfully() {
    Service service = Service.builder().uuid(SERVICE_ID).deploymentType(DeploymentType.KUBERNETES).build();
    wingsPersistence.save(service);
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .serviceId(SERVICE_ID)
                                                  .storeType(StoreType.HelmChartRepo)
                                                  .pollForChanges(true)
                                                  .helmChartConfig(HelmChartConfig.builder().chartName("CHART").build())
                                                  .build();
    wingsPersistence.save(applicationManifest);
    when(serviceResourceService.getName(any(), any())).thenReturn(SERVICE_NAME);
    addAppManifestNameMigration.migrateApplicationManifest(applicationManifest);
    ApplicationManifest applicationManifest1 =
        wingsPersistence.get(ApplicationManifest.class, applicationManifest.getUuid());
    assertThat(applicationManifest1.getName()).isEqualTo(SERVICE_NAME + "_CHART");

    ApplicationManifest applicationManifest2 =
        ApplicationManifest.builder().serviceId(SERVICE_ID).storeType(StoreType.Local).pollForChanges(false).build();
    wingsPersistence.save(applicationManifest2);
    addAppManifestNameMigration.migrateApplicationManifest(applicationManifest);
    applicationManifest1 = wingsPersistence.get(ApplicationManifest.class, applicationManifest.getUuid());
    assertThat(applicationManifest1.getName()).isNull();
    Service service1 = wingsPersistence.get(Service.class, service.getUuid());
    assertThat(service1.getArtifactFromManifest()).isTrue();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotMigrateAppManifestWhenError() {
    Service service = Service.builder().uuid(SERVICE_ID).deploymentType(DeploymentType.KUBERNETES).build();
    wingsPersistence.save(service);
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .serviceId(SERVICE_ID)
                                                  .storeType(StoreType.HelmChartRepo)
                                                  .pollForChanges(true)
                                                  .helmChartConfig(HelmChartConfig.builder().chartName("CHART").build())
                                                  .build();
    wingsPersistence.save(applicationManifest);
    when(serviceResourceService.getName(any(), any())).thenThrow(new InvalidAccessRequestException("Access denied"));
    addAppManifestNameMigration.migrateApplicationManifest(applicationManifest);
    ApplicationManifest applicationManifest1 =
        wingsPersistence.get(ApplicationManifest.class, applicationManifest.getUuid());
    assertThat(applicationManifest1.getName()).isNull();
    Service service1 = wingsPersistence.get(Service.class, service.getUuid());
    assertThat(service1.getArtifactFromManifest()).isFalse();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotMigrateAppManifestWhenServiceNotFound() {
    Service service = Service.builder().uuid(SERVICE_ID).deploymentType(DeploymentType.KUBERNETES).build();
    wingsPersistence.save(service);
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .serviceId(SERVICE_ID)
                                                  .storeType(StoreType.HelmChartRepo)
                                                  .pollForChanges(true)
                                                  .helmChartConfig(HelmChartConfig.builder().chartName("CHART").build())
                                                  .build();
    wingsPersistence.save(applicationManifest);
    when(serviceResourceService.getName(any(), any())).thenReturn(null);
    addAppManifestNameMigration.migrateApplicationManifest(applicationManifest);
    ApplicationManifest applicationManifest1 =
        wingsPersistence.get(ApplicationManifest.class, applicationManifest.getUuid());
    assertThat(applicationManifest1.getName()).isNull();
    Service service1 = wingsPersistence.get(Service.class, service.getUuid());
    assertThat(service1.getArtifactFromManifest()).isFalse();
  }
}
