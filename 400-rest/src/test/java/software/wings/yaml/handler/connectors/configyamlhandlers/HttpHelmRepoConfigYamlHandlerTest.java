/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.handler.connectors.configyamlhandlers;

import static io.harness.rule.OwnerRule.ANSHUL;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.service.impl.yaml.handler.setting.artifactserver.HttpHelmRepoConfigYamlHandler;
import software.wings.service.impl.yaml.handler.templatelibrary.SettingValueConfigYamlHandlerTestBase;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class HttpHelmRepoConfigYamlHandlerTest extends SettingValueConfigYamlHandlerTestBase {
  @InjectMocks @Inject private HttpHelmRepoConfigYamlHandler yamlHandler;

  private Class yamlClass = HttpHelmRepoConfig.Yaml.class;
  private static final String chartRepoUrl = "http://storage.googleapis.com/kubernetes-charts/";
  private static final String HTTP_HELM_CHART_SETTING_NAME = "Http-Helm-Repo";

  private String invalidYamlContent = "repoUrl: " + chartRepoUrl + "\n"
      + "harnessApiVersion: '1.0'\n"
      + "type: DOCKER";

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    String httpHelmRepoSettingName = HTTP_HELM_CHART_SETTING_NAME + System.currentTimeMillis();

    SettingAttribute settingAttributeSaved = createHttpHelmRepoConnector(httpHelmRepoSettingName);
    assertThat(settingAttributeSaved.getName()).isEqualTo(httpHelmRepoSettingName);

    testCRUD(generateSettingValueYamlConfig(httpHelmRepoSettingName, settingAttributeSaved));
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    String httpHelmRepoSettingName = HTTP_HELM_CHART_SETTING_NAME + System.currentTimeMillis();

    SettingAttribute settingAttributeSaved = createHttpHelmRepoConnector(httpHelmRepoSettingName);
    testFailureScenario(generateSettingValueYamlConfig(httpHelmRepoSettingName, settingAttributeSaved));
  }

  private SettingAttribute createHttpHelmRepoConnector(String settingName) {
    when(settingValidationService.validate(any(SettingAttribute.class))).thenReturn(true);

    return settingsService.save(
        aSettingAttribute()
            .withCategory(SettingCategory.HELM_REPO)
            .withName(settingName)
            .withAccountId(ACCOUNT_ID)
            .withValue(HttpHelmRepoConfig.builder().accountId(ACCOUNT_ID).chartRepoUrl(chartRepoUrl).build())
            .build());
  }

  private SettingValueYamlConfig generateSettingValueYamlConfig(String name, SettingAttribute settingAttributeSaved) {
    return SettingValueYamlConfig.builder()
        .yamlHandler(yamlHandler)
        .yamlClass(yamlClass)
        .settingAttributeSaved(settingAttributeSaved)
        .yamlDirPath(artifactServerYamlDir)
        .invalidYamlContent(invalidYamlContent)
        .name(name)
        .configclazz(HttpHelmRepoConfig.class)
        .updateMethodName("setChartRepoUrl")
        .currentFieldValue(chartRepoUrl)
        .build();
  }
}
