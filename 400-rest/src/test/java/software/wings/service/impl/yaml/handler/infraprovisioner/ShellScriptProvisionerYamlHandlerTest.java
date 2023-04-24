/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.infraprovisioner;

import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.NameValuePair;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.shellscript.provisioner.ShellScriptInfrastructureProvisioner;
import software.wings.beans.shellscript.provisioner.ShellScriptInfrastructureProvisioner.Yaml;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlConstants;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.handler.tag.HarnessTagYamlHelper;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ShellScriptProvisionerYamlHandlerTest extends YamlHandlerTestBase {
  private static final String NAME = "yamlTest";
  private static final String SCRIPT_BODY = "script";
  private final String resourcePath = "400-rest/src/test/resources/yaml";
  private final String yamlFilePath = "shellScriptProvisoner.yaml";

  @Mock private AppService appService;
  @Mock private HarnessTagYamlHelper harnessTagYamlHelper;
  @Mock private YamlHelper mockYamlHelper;
  @Mock private ServiceResourceService mockServiceResourceService;
  @Mock private InfrastructureProvisionerService mockInfrastructureProvisionerService;

  @Inject @InjectMocks private ShellScriptProvisionerYamlHandler handler;

  private ArgumentCaptor<InfrastructureProvisioner> captor = ArgumentCaptor.forClass(InfrastructureProvisioner.class);
  private File yamlFile;
  private ChangeContext<ShellScriptInfrastructureProvisioner.Yaml> changeContext;
  private String yamlString;
  private ShellScriptInfrastructureProvisioner.Yaml yaml;

  @Before
  public void runBeforeTest() throws IOException {
    doReturn(Application.Builder.anApplication().uuid(APP_ID).build()).when(appService).get(any());
    doReturn(ACCOUNT_ID).when(appService).getAccountIdByAppId(any());
    doReturn(APP_ID).when(mockYamlHelper).getAppId(any(), any());
    Service service = Service.builder().name("Test").uuid(SERVICE_ID).build();
    doReturn(service).when(mockServiceResourceService).getServiceByName(any(), any());
    doReturn(service).when(mockServiceResourceService).get(any(), any());
    doReturn(null).when(mockInfrastructureProvisionerService).save(any());
    readYamlFile();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testToYaml() {
    ShellScriptInfrastructureProvisioner provisioner = prepareProvisioner();
    Yaml yaml = handler.toYaml(provisioner, "my-app");
    assertThat(yaml.getScriptBody()).isEqualTo(SCRIPT_BODY);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testCreationFromYaml() {
    handler.upsertFromYaml(changeContext, Arrays.asList(changeContext));

    verify(mockInfrastructureProvisionerService).save(captor.capture());
    ShellScriptInfrastructureProvisioner savedProvisioner = (ShellScriptInfrastructureProvisioner) captor.getValue();

    // convert to Yaml from bean
    Yaml yamlFromBean = handler.toYaml(savedProvisioner, APP_ID);
    assertThat(yamlFromBean).isNotNull();

    String yamlContent = getYamlContent(yamlFromBean);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(yamlString);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetSavedProvisioner() {
    ShellScriptInfrastructureProvisioner saved = ShellScriptInfrastructureProvisioner.builder().build();
    doReturn(saved).when(mockYamlHelper).getInfrastructureProvisioner(any(), any());
    ShellScriptInfrastructureProvisioner provisioner = handler.get(ACCOUNT_ID, yamlFilePath);
    assertThat(provisioner).isNotNull();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testUpdationFromYaml() {
    ShellScriptInfrastructureProvisioner saved =
        ShellScriptInfrastructureProvisioner.builder().name("name").uuid("uuid").build();
    doReturn(saved).when(mockInfrastructureProvisionerService).getByName(any(), any());
    handler.upsertFromYaml(changeContext, Arrays.asList(changeContext));
    verify(mockInfrastructureProvisionerService).update(captor.capture());
    ShellScriptInfrastructureProvisioner updated = (ShellScriptInfrastructureProvisioner) captor.getValue();
    assertThat(updated).isNotNull();
  }

  private ShellScriptInfrastructureProvisioner prepareProvisioner() {
    ShellScriptInfrastructureProvisioner provisioner = ShellScriptInfrastructureProvisioner.builder().build();
    provisioner.setName(NAME);
    provisioner.setScriptBody(SCRIPT_BODY);

    List<NameValuePair> variables =
        Arrays.asList(NameValuePair.builder().name("var1").valueType(ServiceVariableType.ENCRYPTED_TEXT.name()).build(),
            NameValuePair.builder().name("var2").valueType(ServiceVariableType.TEXT.name()).build());
    provisioner.setVariables(variables);
    return provisioner;
  }

  private ChangeContext<ShellScriptInfrastructureProvisioner.Yaml> getChangeContext(
      String validYamlContent, String yamlFilePath) {
    GitFileChange gitFileChange = GitFileChange.Builder.aGitFileChange()
                                      .withAccountId(ACCOUNT_ID)
                                      .withFilePath(yamlFilePath)
                                      .withFileContent(validYamlContent)
                                      .build();

    ChangeContext<ShellScriptInfrastructureProvisioner.Yaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.PROVISIONER);
    changeContext.setYamlSyncHandler(handler);
    return changeContext;
  }

  private void readYamlFile() throws IOException {
    yamlFile = new File(resourcePath + YamlConstants.PATH_DELIMITER + yamlFilePath);

    assertThat(yamlFile).isNotNull();
    yamlString = FileUtils.readFileToString(yamlFile, "UTF-8");
    changeContext = getChangeContext(yamlString, yamlFilePath);
    yaml = (Yaml) getYaml(yamlString, Yaml.class);
    changeContext.setYaml(yaml);
  }
}
