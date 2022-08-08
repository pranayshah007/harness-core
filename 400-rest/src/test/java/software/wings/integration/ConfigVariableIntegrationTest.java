/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.UNKNOWN;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.mockChecker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.beans.EncryptedData;
import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.limits.LimitCheckerFactory;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariable.OverrideType;
import software.wings.beans.ServiceVariableType;
import software.wings.rules.SetupScheduler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.ServiceVariableService;

import com.google.inject.Inject;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@SetupScheduler
public class ConfigVariableIntegrationTest extends IntegrationTestBase {
  @Inject @InjectMocks private AppService appService;
  @Inject @InjectMocks private ServiceResourceService serviceResourceService;
  @Inject private ServiceTemplateService serviceTemplateService;
  @Inject private ServiceVariableService serviceVariableService;
  @Mock private LimitCheckerFactory limitCheckerFactory;

  private Application app;
  private Service service;
  private Environment env;
  private ServiceTemplate serviceTemplate;
  private ServiceVariable serviceVariable1, serviceVariable2;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    app = appService.save(anApplication().accountId(ACCOUNT_ID).name(APP_NAME + System.currentTimeMillis()).build());
    service = serviceResourceService.save(
        Service.builder().appId(app.getUuid()).name(SERVICE_NAME + System.currentTimeMillis()).build());
    env = anEnvironment().appId(app.getAppId()).uuid(generateUuid()).name(ENV_NAME).build();
    serviceTemplate = serviceTemplateService.save(aServiceTemplate()
                                                      .withEnvId(env.getUuid())
                                                      .withAppId(app.getAppId())
                                                      .withUuid(generateUuid())
                                                      .withServiceId(service.getUuid())
                                                      .withName(SERVICE_NAME)
                                                      .build());
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void shouldOverrideServiceConfigVariable() {
    // Config variable - Entity type as Service
    serviceVariable1 = getServiceVariable(ServiceVariableType.TEXT);
    serviceVariable1.setAppId(app.getAppId());

    String svId = serviceVariableService.save(serviceVariable1).getUuid();
    ServiceVariable parentServiceVariable = serviceVariableService.get(app.getAppId(), svId);

    assertThat(parentServiceVariable.getEntityType()).isEqualTo(EntityType.SERVICE);
    assertThat(parentServiceVariable.getUuid()).isEqualTo(svId);
    assertThat(parentServiceVariable.getType().name()).isEqualTo(ServiceVariableType.TEXT.name());

    // Config variable override - Entity type as Service Template
    serviceVariable2 = getServiceVariable(ServiceVariableType.TEXT);
    serviceVariable2.setAppId(app.getAppId());
    serviceVariable2.setEntityType(EntityType.SERVICE_TEMPLATE);
    serviceVariable2.setTemplateId(serviceTemplate.getUuid());
    serviceVariable2.setValue("testUpdated".toCharArray());
    serviceVariable2.setParentServiceVariableId(parentServiceVariable.getUuid());

    String newSvId = serviceVariableService.save(serviceVariable2).getUuid();
    ServiceVariable overrideServiceVariable = serviceVariableService.get(app.getAppId(), newSvId);

    assertThat(overrideServiceVariable.getEntityType()).isEqualTo(EntityType.SERVICE_TEMPLATE);
    assertThat(overrideServiceVariable.getUuid()).isEqualTo(newSvId);
    assertThat(overrideServiceVariable.getType().name()).isEqualTo(ServiceVariableType.TEXT.name());
    assertThat(overrideServiceVariable.getParentServiceVariableId()).isEqualTo(parentServiceVariable.getUuid());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void shouldOverrideEncryptedServiceConfigVariable() {
    String encryptedId = wingsPersistence.save(EncryptedData.builder()
                                                   .name(generateUuid())
                                                   .encryptedValue(generateUuid().toCharArray())
                                                   .encryptionKey(generateUuid())
                                                   .build());
    // Config variable - Entity type as Service
    serviceVariable1 = getServiceVariable(ServiceVariableType.ENCRYPTED_TEXT);
    serviceVariable1.setAppId(app.getAppId());
    serviceVariable1.setValue(encryptedId.toCharArray());
    serviceVariable1.setEncryptedValue("encryptedValue");

    String svId = serviceVariableService.save(serviceVariable1).getUuid();
    ServiceVariable parentServiceVariable = serviceVariableService.get(app.getAppId(), svId);

    assertThat(parentServiceVariable.getEntityType()).isEqualTo(EntityType.SERVICE);
    assertThat(parentServiceVariable.getUuid()).isEqualTo(svId);
    assertThat(parentServiceVariable.getType().name()).isEqualTo(ServiceVariableType.ENCRYPTED_TEXT.name());

    // Variable override - Entity type as Service Template
    serviceVariable2 = getServiceVariable(ServiceVariableType.ENCRYPTED_TEXT);
    serviceVariable2.setAppId(app.getAppId());
    serviceVariable2.setEntityType(EntityType.SERVICE_TEMPLATE);
    serviceVariable2.setTemplateId(serviceTemplate.getUuid());
    serviceVariable2.setValue(encryptedId.toCharArray());
    serviceVariable2.setEncryptedValue("updatedEncryptedValue");
    serviceVariable2.setParentServiceVariableId(parentServiceVariable.getUuid());

    String newSvId = serviceVariableService.save(serviceVariable2).getUuid();
    ServiceVariable overrideServiceVariable = serviceVariableService.get(app.getAppId(), newSvId);

    assertThat(overrideServiceVariable.getEntityType()).isEqualTo(EntityType.SERVICE_TEMPLATE);
    assertThat(overrideServiceVariable.getUuid()).isEqualTo(newSvId);
    assertThat(overrideServiceVariable.getType().name()).isEqualTo(ServiceVariableType.ENCRYPTED_TEXT.name());
    assertThat(overrideServiceVariable.getParentServiceVariableId()).isEqualTo(parentServiceVariable.getUuid());
  }

  private ServiceVariable getServiceVariable(ServiceVariableType type) {
    return ServiceVariable.builder()
        .envId(env.getUuid())
        .entityType(EntityType.SERVICE)
        .entityId(generateUuid())
        .overrideType(OverrideType.ALL)
        .accountId(ACCOUNT_ID)
        .name(UUID.randomUUID().toString())
        .value("test".toCharArray())
        .type(type)
        .build();
  }

  @After
  public void tearDown() {
    if (serviceVariable1 != null) {
      serviceVariableService.delete(app.getAppId(), serviceVariable1.getUuid());
    }
    if (serviceVariable2 != null) {
      serviceVariableService.delete(app.getAppId(), serviceVariable2.getUuid());
    }
    if (serviceTemplate != null) {
      serviceTemplateService.delete(app.getAppId(), serviceTemplate.getUuid());
    }
    if (service != null) {
      serviceResourceService.delete(app.getAppId(), service.getUuid());
    }
    if (app != null) {
      appService.delete(app.getAppId());
    }
  }
}
