/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.service;

import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.appmanifest.AppManifestKind.HELM_CHART_OVERRIDE;
import static software.wings.beans.appmanifest.AppManifestKind.K8S_MANIFEST;
import static software.wings.beans.appmanifest.AppManifestKind.PCF_OVERRIDE;
import static software.wings.beans.appmanifest.AppManifestKind.VALUES;
import static software.wings.beans.appmanifest.StoreType.Local;
import static software.wings.beans.appmanifest.StoreType.Remote;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.service.intfc.ApplicationManifestService;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ApplicationManifestYamlHandlerTest extends WingsBaseTest {
  @Mock private ApplicationManifestService applicationManifestService;
  @InjectMocks private ApplicationManifestYamlHandler yamlHandler = spy(ApplicationManifestYamlHandler.class);

  private ChangeContext<ApplicationManifest.Yaml> changeContext;

  private String localValidYamlContent = "harnessApiVersion: '1.0'\n"
      + "type: APPLICATION_MANIFEST\n"
      + "storeType: Local";
  private String validYamlFilePath = "Setup/Applications/APP_NAME/Services/SERVICE_NAME/Manifests/Index.yaml";

  @Before
  public void setup() {
    changeContext = createChangeContext(localValidYamlContent, validYamlFilePath);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldNotDeleteAppManifestForService() throws Exception {
    shouldNotDeleteDeleteK8sManifest();
    shouldDeleteValuesYaml();
    shouldDeleteHelmChartOverride();
    shouldDeletePcfOverrride();
    shouldDeleteDeleteK8sManifestWhenPollForChangesEnabled();
  }

  private void shouldDeleteDeleteK8sManifestWhenPollForChangesEnabled() {
    doReturn(ApplicationManifest.builder()
                 .storeType(Local)
                 .kind(K8S_MANIFEST)
                 .serviceId("service")
                 .pollForChanges(true)
                 .build())
        .when(yamlHandler)
        .get(anyString(), anyString());

    yamlHandler.delete(changeContext);

    verify(applicationManifestService, times(1)).deleteAppManifest(any(ApplicationManifest.class));
    reset(applicationManifestService);
  }

  private void shouldDeleteHelmChartOverride() throws Exception {
    doReturn(ApplicationManifest.builder().storeType(Remote).kind(HELM_CHART_OVERRIDE).serviceId("service").build())
        .when(yamlHandler)
        .get(anyString(), anyString());

    yamlHandler.delete(changeContext);

    verify(applicationManifestService, times(1)).deleteAppManifest(any(ApplicationManifest.class));
    reset(applicationManifestService);
  }

  private void shouldDeletePcfOverrride() throws Exception {
    doReturn(ApplicationManifest.builder().storeType(Remote).kind(PCF_OVERRIDE).serviceId("service").build())
        .when(yamlHandler)
        .get(anyString(), anyString());

    yamlHandler.delete(changeContext);

    verify(applicationManifestService, times(1)).deleteAppManifest(any(ApplicationManifest.class));
    reset(applicationManifestService);
  }

  private void shouldDeleteValuesYaml() throws Exception {
    doReturn(ApplicationManifest.builder().storeType(Local).kind(VALUES).serviceId("service").build())
        .when(yamlHandler)
        .get(anyString(), anyString());

    yamlHandler.delete(changeContext);

    verify(applicationManifestService, times(1)).deleteAppManifest(any(ApplicationManifest.class));
    reset(applicationManifestService);
  }

  private void shouldNotDeleteDeleteK8sManifest() {
    doReturn(ApplicationManifest.builder().storeType(Local).kind(K8S_MANIFEST).serviceId("service").build())
        .when(yamlHandler)
        .get(anyString(), anyString());

    yamlHandler.delete(changeContext);

    verify(applicationManifestService, never()).deleteAppManifest(any(ApplicationManifest.class));
    reset(applicationManifestService);
  }

  private ChangeContext<ApplicationManifest.Yaml> createChangeContext(String fileContent, String filePath) {
    GitFileChange gitFileChange = new GitFileChange();
    gitFileChange.setFileContent(fileContent);
    gitFileChange.setFilePath(filePath);
    gitFileChange.setAccountId(ACCOUNT_ID);

    ChangeContext<ApplicationManifest.Yaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.APPLICATION_MANIFEST);
    changeContext.setYamlSyncHandler(yamlHandler);

    return changeContext;
  }
}
