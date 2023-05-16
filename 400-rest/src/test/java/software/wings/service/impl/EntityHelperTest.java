/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.SATYAM;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.appmanifest.StoreType.Local;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.audit.EntityAuditRecord;
import software.wings.audit.EntityAuditRecord.EntityAuditRecordBuilder;
import software.wings.beans.Environment;
import software.wings.beans.Event.Type;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.container.KubernetesContainerTask;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.yaml.service.YamlHelper;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class EntityHelperTest extends WingsBaseTest {
  @Mock private WingsPersistence mockWingsPersistence;
  @Mock private YamlHelper mockYamlHelper;
  @Inject @InjectMocks private EntityHelper entityHelper;

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testLoad() {
    Environment environment = anEnvironment().name(ENV_NAME).appId(APP_ID).build();
    EntityAuditRecordBuilder builder = EntityAuditRecord.builder();
    Query mockQuery = mock(Query.class);
    doReturn(mockQuery).when(mockWingsPersistence).createQuery(any());
    doReturn(mockQuery).when(mockQuery).filter(anyString(), anyString());
    doReturn(mockQuery).when(mockQuery).project(anyString(), anyBoolean());
    doReturn(singletonList(anApplication().name(APP_NAME).build())).when(mockQuery).asList();
    entityHelper.loadMetaDataForEntity(environment, builder, Type.CREATE);
    EntityAuditRecord record = builder.build();
    assertThat(record).isNotNull();
    assertThat(record.getEntityName()).isEqualTo(ENV_NAME);
    assertThat(record.getAppId()).isEqualTo(APP_ID);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetYamlPathForDeploymentSpecification() {
    EntityAuditRecord record = EntityAuditRecord.builder().appName(APP_NAME).affectedResourceName(SERVICE_NAME).build();
    KubernetesContainerTask task = new KubernetesContainerTask();
    String yamlPath = entityHelper.getYamlPathForDeploymentSpecification(task, record);
    assertThat(yamlPath).isEqualTo(
        "Setup/Application/APP_NAME/Services/SERVICE_NAME/Deloyment Specifications/Kubernetes.yaml");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetFullYamlPathForEntity() {
    doReturn("Setup/Application/APP_NAME/Services/SERVICE_NAME/Manifests")
        .when(mockYamlHelper)
        .getYamlPathForEntity(any());
    String yamlPath = entityHelper.getFullYamlPathForEntity(
        ApplicationManifest.builder().storeType(Local).build(), EntityAuditRecord.builder().build());
    assertThat(yamlPath).isEqualTo("Setup/Application/APP_NAME/Services/SERVICE_NAME/Manifests/Index.yaml");
  }
}
