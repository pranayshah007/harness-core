/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.verification;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.apache.cxf.ws.addressing.ContextUtils.generateUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.ChangeContext;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.service.impl.dynatrace.DynaTraceApplication;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.dynatrace.DynaTraceService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.sm.StateType;
import software.wings.verification.dynatrace.DynaTraceCVServiceConfiguration;
import software.wings.verification.dynatrace.DynaTraceCVServiceConfiguration.DynaTraceCVConfigurationYaml;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DynatraceCVConfigurationYamlHandlerTest extends WingsBaseTest {
  @Mock YamlHelper yamlHelper;
  @Mock CVConfigurationService cvConfigService;
  @Mock EnvironmentService envService;
  @Mock ServiceResourceService serviceService;
  @Mock AppService appService;
  @Mock SettingsService settingsService;
  @Mock DynaTraceService dynaTraceService;

  @Inject DynatraceCVConfigurationYamlHandler yamlHandler;

  private String envId;
  private String serviceId;
  private String appId;
  private String connectorId;
  private String accountId;

  private String serviceName = "serviceName";
  private String connectorName = "dynaTraceConnector";

  @Before
  public void setup() throws Exception {
    accountId = generateUuid();
    envId = generateUuid();
    serviceId = generateUuid();
    appId = generateUuid();
    connectorId = generateUuid();

    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(yamlHandler, "yamlHelper", yamlHelper, true);
    FieldUtils.writeField(yamlHandler, "cvConfigurationService", cvConfigService, true);
    FieldUtils.writeField(yamlHandler, "appService", appService, true);
    FieldUtils.writeField(yamlHandler, "environmentService", envService, true);
    FieldUtils.writeField(yamlHandler, "serviceResourceService", serviceService, true);
    FieldUtils.writeField(yamlHandler, "settingsService", settingsService, true);
    FieldUtils.writeField(yamlHandler, "dynaTraceService", dynaTraceService, true);

    Service service = Service.builder().uuid(serviceId).name(serviceName).build();
    when(serviceService.getWithDetails(appId, serviceId)).thenReturn(service);
    when(serviceService.getServiceByName(appId, serviceName)).thenReturn(service);

    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withName(connectorName).withUuid(connectorId).build();
    when(settingsService.getSettingAttributeByName(accountId, connectorName)).thenReturn(settingAttribute);
    when(settingsService.get(connectorId)).thenReturn(settingAttribute);

    when(dynaTraceService.getServices(connectorId, true)).thenReturn(getDynatraceServiceList());

    Application app = Application.Builder.anApplication().name(generateUUID()).uuid(appId).build();
    when(appService.get(appId)).thenReturn(app);
  }

  private List<DynaTraceApplication> getDynatraceServiceList() {
    List<DynaTraceApplication> services = new ArrayList<>();
    services.add(DynaTraceApplication.builder().displayName("serviceName1").entityId("entityID1").build());
    services.add(DynaTraceApplication.builder().displayName("serviceName2").entityId("entityID2").build());
    services.add(DynaTraceApplication.builder().displayName("serviceName3").entityId("entityID3").build());
    return services;
  }

  private void setBasicInfo(DynaTraceCVServiceConfiguration cvServiceConfiguration) {
    cvServiceConfiguration.setStateType(StateType.DYNA_TRACE);
    cvServiceConfiguration.setAccountId(accountId);
    cvServiceConfiguration.setServiceId(serviceId);
    cvServiceConfiguration.setConnectorId(connectorId);
    cvServiceConfiguration.setEnvId(envId);
    cvServiceConfiguration.setAppId(appId);
    cvServiceConfiguration.setEnabled24x7(true);
    cvServiceConfiguration.setName("TestDynaTraceConfig");
    cvServiceConfiguration.setServiceEntityId("entityID2");
  }

  private DynaTraceCVConfigurationYaml buildYaml() {
    DynaTraceCVConfigurationYaml yaml = DynaTraceCVConfigurationYaml.builder().build();
    yaml.setServiceName(serviceName);
    yaml.setConnectorName(connectorName);
    yaml.setDynatraceServiceName("serviceName3");
    yaml.setDynatraceServiceEntityId("entityID3");
    return yaml;
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testToYaml() {
    final String appId = "appId";
    DynaTraceCVServiceConfiguration cvServiceConfiguration = DynaTraceCVServiceConfiguration.builder().build();
    setBasicInfo(cvServiceConfiguration);

    DynaTraceCVConfigurationYaml yaml = yamlHandler.toYaml(cvServiceConfiguration, appId);
    assertThat(yaml.getDynatraceServiceName()).isEqualTo("serviceName2");
    assertThat(yaml.getServiceName()).isEqualTo(serviceName);
    assertThat(yaml.getDynatraceServiceEntityId()).isEqualTo("entityID2");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testToYaml_badDynatraceServiceId() {
    DynaTraceCVServiceConfiguration cvServiceConfiguration = DynaTraceCVServiceConfiguration.builder().build();
    setBasicInfo(cvServiceConfiguration);
    cvServiceConfiguration.setServiceEntityId("entityID4");
    DynaTraceCVConfigurationYaml yaml = yamlHandler.toYaml(cvServiceConfiguration, appId);
    assertThat(yaml.getDynatraceServiceName()).isEmpty();
    assertThat(yaml.getDynatraceServiceEntityId()).isEmpty();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpsert() {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);
    when(yamlHelper.getNameFromYamlFilePath("TestDynaTraceConfig.yaml")).thenReturn("TestDynaTraceConfig");

    ChangeContext<DynaTraceCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestDynaTraceConfig.yaml").build();
    changeContext.setChange(c);
    changeContext.setYaml(buildYaml());
    DynaTraceCVServiceConfiguration bean = yamlHandler.upsertFromYaml(changeContext, null);

    assertThat(bean.getName()).isEqualTo("TestDynaTraceConfig");
    assertThat(bean.getAppId()).isEqualTo(appId);
    assertThat(bean.getEnvId()).isEqualTo(envId);
    assertThat(bean.getServiceId()).isEqualTo(serviceId);
    assertThat(bean.getServiceEntityId()).isEqualTo("entityID3");
    assertThat(bean.getUuid()).isNotNull();
  }

  @Test(expected = DataCollectionException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpsert_badDynatraceServiceName() {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);
    when(yamlHelper.getNameFromYamlFilePath("TestDynaTraceConfig.yaml")).thenReturn("TestDynaTraceConfig");

    ChangeContext<DynaTraceCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestDynaTraceConfig.yaml").build();
    changeContext.setChange(c);
    changeContext.setYaml(buildYaml());
    // setting a bad service name here and expecting an exception
    changeContext.getYaml().setDynatraceServiceName("serviceName5");
    DynaTraceCVServiceConfiguration bean = yamlHandler.upsertFromYaml(changeContext, null);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpsert_sameDynatraceServiceNameDiffID() {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);
    when(yamlHelper.getNameFromYamlFilePath("TestDynaTraceConfig.yaml")).thenReturn("TestDynaTraceConfig");

    List<DynaTraceApplication> services = getDynatraceServiceList();
    services.add(DynaTraceApplication.builder().displayName("serviceName2").entityId("entityID5").build());
    when(dynaTraceService.getServices(connectorId, true)).thenReturn(services);
    ChangeContext<DynaTraceCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestDynaTraceConfig.yaml").build();
    changeContext.setChange(c);
    changeContext.setYaml(buildYaml());
    // setting a bad service name here and expecting an exception
    changeContext.getYaml().setDynatraceServiceName("serviceName2");
    changeContext.getYaml().setDynatraceServiceEntityId("entityID5");
    DynaTraceCVServiceConfiguration bean = yamlHandler.upsertFromYaml(changeContext, null);

    assertThat(bean.getServiceEntityId()).isEqualTo("entityID5");
  }

  @Test(expected = DataCollectionException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpsert_noServicesForConnector() {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);
    when(yamlHelper.getNameFromYamlFilePath("TestDynaTraceConfig.yaml")).thenReturn("TestDynaTraceConfig");
    when(dynaTraceService.getServices(anyString(), anyBoolean())).thenReturn(null);

    ChangeContext<DynaTraceCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestDynaTraceConfig.yaml").build();
    changeContext.setChange(c);
    changeContext.setYaml(buildYaml());
    // setting a bad service name here and expecting an exception
    changeContext.getYaml().setDynatraceServiceName("serviceName1");
    DynaTraceCVServiceConfiguration bean = yamlHandler.upsertFromYaml(changeContext, null);
  }
}
