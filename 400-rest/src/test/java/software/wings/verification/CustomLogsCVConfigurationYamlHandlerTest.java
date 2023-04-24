/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.verification;

import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.apache.cxf.ws.addressing.ContextUtils.generateUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.LogCollectionInfo;
import software.wings.beans.LogResponseMapping;
import software.wings.beans.apm.Method;
import software.wings.beans.apm.ResponseType;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.ChangeContext;
import software.wings.service.impl.analysis.FeedbackPriority;
import software.wings.sm.StateType;
import software.wings.verification.log.CustomLogCVServiceConfiguration;
import software.wings.verification.log.CustomLogCVServiceConfiguration.CustomLogsCVConfigurationYaml;
import software.wings.verification.log.LogsCVConfiguration.LogsCVConfigurationYaml;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CustomLogsCVConfigurationYamlHandlerTest extends CVConfigurationYamlHandlerTestBase {
  @Inject CustomLogCVConfigurationYamlHandler yamlHandler;

  @Before
  public void setup() throws Exception {
    setupTests(yamlHandler);
  }

  private LogCollectionInfo buildLogCollectionInfo() {
    return LogCollectionInfo.builder()
        .collectionUrl("testUrl")
        .method(Method.GET)
        .responseType(ResponseType.JSON)
        .responseMapping(LogResponseMapping.builder()
                             .hostJsonPath("hostname")
                             .logMessageJsonPath("message")
                             .timestampJsonPath("@timestamp")
                             .build())
        .build();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testToYaml() {
    final String appId = "appId";
    CustomLogCVServiceConfiguration cvServiceConfiguration =
        CustomLogCVServiceConfiguration.builder().logCollectionInfo(buildLogCollectionInfo()).build();
    setBasicInfo(cvServiceConfiguration);
    cvServiceConfiguration.setStateType(StateType.LOG_VERIFICATION);
    cvServiceConfiguration.setQuery(generateUUID());
    cvServiceConfiguration.setBaselineStartMinute(16);
    cvServiceConfiguration.setBaselineEndMinute(30);
    cvServiceConfiguration.setAlertPriority(FeedbackPriority.P5);
    CustomLogsCVConfigurationYaml yaml = yamlHandler.toYaml(cvServiceConfiguration, appId);

    assertThat(yaml.getServiceName()).isEqualTo(serviceName);
    assertThat(yaml.getQuery()).isEqualTo(cvServiceConfiguration.getQuery());
    assertThat(yaml.getBaselineStartMinute()).isEqualTo(cvServiceConfiguration.getBaselineStartMinute());
    assertThat(yaml.getBaselineEndMinute()).isEqualTo(cvServiceConfiguration.getBaselineEndMinute());
    assertThat(yaml.getLogCollectionInfo()).isNotNull();
    assertThat(yaml.getLogCollectionInfo().getCollectionUrl()).isEqualTo(buildLogCollectionInfo().getCollectionUrl());
    assertThat(yaml.getLogCollectionInfo().getResponseMapping())
        .isEqualTo(buildLogCollectionInfo().getResponseMapping());
    assertThat(yaml.getAlertPriority()).isEqualTo(FeedbackPriority.P5.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpsert() {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);
    when(yamlHelper.getNameFromYamlFilePath("TestAppDConfig.yaml")).thenReturn("TestAppDConfig");

    ChangeContext<LogsCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestAppDConfig.yaml").build();
    changeContext.setChange(c);
    CustomLogsCVConfigurationYaml yaml = CustomLogsCVConfigurationYaml.builder().build();
    buildYaml(yaml);
    yaml.setLogCollectionInfo(LogCollectionInfo.builder()
                                  .collectionUrl("testURL ${start_time} ${end_time}")
                                  .method(Method.GET)
                                  .responseMapping(LogResponseMapping.builder()
                                                       .hostJsonPath("host")
                                                       .logMessageJsonPath("logMessage")
                                                       .timestampJsonPath("time")
                                                       .build())
                                  .build());
    yaml.setBaselineStartMinute(16L);
    yaml.setBaselineEndMinute(30L);
    yaml.setAlertPriority(FeedbackPriority.P5.name());
    yaml.setType(StateType.LOG_VERIFICATION.name());
    changeContext.setYaml(yaml);
    CustomLogCVServiceConfiguration bean = yamlHandler.upsertFromYaml(changeContext, null);

    assertThat(bean.getName()).isEqualTo("TestAppDConfig");
    assertThat(bean.getAppId()).isEqualTo(appId);
    assertThat(bean.getEnvId()).isEqualTo(envId);
    assertThat(bean.getServiceId()).isEqualTo(serviceId);
    assertThat(bean.getQuery()).isEqualTo("testURL ${start_time} ${end_time}");
    assertThat(bean.getBaselineStartMinute()).isEqualTo(16);
    assertThat(bean.getBaselineEndMinute()).isEqualTo(30);
    assertThat(bean.getAlertPriority()).isEqualTo(FeedbackPriority.P5);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpsertAlreadyExisting() {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);
    when(yamlHelper.getNameFromYamlFilePath("TestAppDConfig.yaml")).thenReturn("TestAppDConfig");

    CustomLogCVServiceConfiguration cvConfig = CustomLogCVServiceConfiguration.builder().build();
    cvConfig.setUuid("testUUID");
    when(cvConfigurationService.getConfiguration("TestAppDConfig", appId, envId)).thenReturn(cvConfig);
    ChangeContext<LogsCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestAppDConfig.yaml").build();
    changeContext.setChange(c);
    CustomLogsCVConfigurationYaml yaml = CustomLogsCVConfigurationYaml.builder().build();
    buildYaml(yaml);
    yaml.setLogCollectionInfo(LogCollectionInfo.builder()
                                  .collectionUrl("testURL ${start_time} ${end_time}")
                                  .method(Method.GET)
                                  .responseMapping(LogResponseMapping.builder()
                                                       .hostJsonPath("host")
                                                       .logMessageJsonPath("logMessage")
                                                       .timestampJsonPath("time")
                                                       .build())
                                  .build());
    yaml.setBaselineStartMinute(16L);
    yaml.setBaselineEndMinute(30L);
    yaml.setAlertPriority(FeedbackPriority.P5.name());
    yaml.setType(StateType.LOG_VERIFICATION.name());
    changeContext.setYaml(yaml);
    CustomLogCVServiceConfiguration bean = yamlHandler.upsertFromYaml(changeContext, null);

    assertThat(bean.getName()).isEqualTo("TestAppDConfig");
    assertThat(bean.getUuid()).isEqualTo(cvConfig.getUuid());
  }
}
