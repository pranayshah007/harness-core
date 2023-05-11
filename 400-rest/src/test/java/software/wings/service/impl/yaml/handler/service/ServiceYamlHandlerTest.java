/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.service;

import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.model.HelmVersion;
import io.harness.pcf.model.CfCliVersion;
import io.harness.rule.Owner;

import software.wings.api.DeploymentType;
import software.wings.beans.Application;
import software.wings.beans.Application.Builder;
import software.wings.beans.NameValuePair;
import software.wings.beans.Service;
import software.wings.beans.Service.Yaml;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.persistence.AppContainer;
import software.wings.service.impl.yaml.handler.tag.HarnessTagYamlHelper;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppContainerService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.service.intfc.customdeployment.CustomDeploymentTypeService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.utils.ArtifactType;
import software.wings.utils.WingsTestConstants;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ServiceYamlHandlerTest extends YamlHandlerTestBase {
  @Mock YamlHelper yamlHelper;
  @Mock HarnessTagYamlHelper harnessTagYamlHelper;
  @Mock SecretManager secretManager;
  @Mock ServiceResourceService serviceResourceService;
  @Mock AppService appService;
  @Mock ServiceVariableService serviceVariableService;
  @Mock CustomDeploymentTypeService customDeploymentTypeService;
  @Mock AppContainerService appContainerService;
  @InjectMocks @Inject private ServiceYamlHandler serviceYamlHandler;

  private Service service;
  private String validYamlFilePath = "Setup/Applications/" + APP_NAME + "/Service/" + SERVICE_NAME + "/Index.yaml";
  private ArgumentCaptor<ServiceVariable> captor = ArgumentCaptor.forClass(ServiceVariable.class);
  private ArgumentCaptor<Service> serviceCaptor = ArgumentCaptor.forClass(Service.class);

  @Before
  public void setUp() throws Exception {
    Application application = Builder.anApplication().accountId(ACCOUNT_ID).uuid(APP_ID).build();
    ServiceVariable enc_1 = ServiceVariable.builder()
                                .name("enc_1")
                                .type(ServiceVariableType.ENCRYPTED_TEXT)
                                .encryptedValue("some-secret")
                                .accountId(ACCOUNT_ID)
                                .build();
    ServiceVariable enc_2 = ServiceVariable.builder()
                                .name("enc_2")
                                .type(ServiceVariableType.ENCRYPTED_TEXT)
                                .encryptedValue("other-secret")
                                .accountId(ACCOUNT_ID)
                                .build();
    ServiceVariable var_1 =
        ServiceVariable.builder().name("var_1").type(ServiceVariableType.TEXT).value("var".toCharArray()).build();
    service = Service.builder()
                  .appId(APP_ID)
                  .uuid(SERVICE_ID)
                  .name(SERVICE_NAME)
                  .accountId(ACCOUNT_ID)
                  .artifactType(ArtifactType.DOCKER)
                  .appContainer(AppContainer.Builder.anAppContainer().withName("Tomcat 8").build())
                  .deploymentType(DeploymentType.SSH)
                  .serviceVariables(Arrays.asList(enc_1, enc_2, var_1))
                  .deploymentTypeTemplateId(WingsTestConstants.TEMPLATE_ID)
                  .build();
    when(secretManager.getEncryptedYamlRef(ACCOUNT_ID, enc_1.getEncryptedValue()))
        .thenReturn("safeharness:some-secret");
    when(secretManager.getEncryptedYamlRef(ACCOUNT_ID, enc_2.getEncryptedValue())).thenReturn("amazonkms:other-secret");
    doNothing().when(harnessTagYamlHelper).updateYamlWithHarnessTagLinks(any(), any(), any());
    when(yamlHelper.getAppId(ACCOUNT_ID, validYamlFilePath)).thenReturn(APP_ID);
    when(yamlHelper.getServiceName(validYamlFilePath)).thenReturn(SERVICE_NAME);
    when(serviceResourceService.getServiceByName(APP_ID, SERVICE_NAME)).thenReturn(service);
    when(appContainerService.getByName(anyString(), anyString()))
        .thenReturn(AppContainer.Builder.anAppContainer().withName("Tomcat 7").build());
    when(serviceResourceService.save(any(), anyBoolean(), anyBoolean()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, Service.class));
    when(serviceResourceService.update(any(), anyBoolean()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, Service.class));
    when(serviceVariableService.save(any(), anyBoolean()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, ServiceVariable.class));
    when(appService.get(APP_ID)).thenReturn(application);
    when(yamlHelper.getApplicationIfPresent(ACCOUNT_ID, validYamlFilePath)).thenReturn(Optional.of(application));
    when(yamlHelper.extractEncryptedRecordId(eq("safeharness:some-secret"), anyString())).thenReturn("some-secret");
    when(yamlHelper.extractEncryptedRecordId(eq("amazonkms:other-secret"), anyString())).thenReturn("other-secret");
    when(serviceVariableService.update(any(), anyBoolean()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, ServiceVariable.class));
    when(customDeploymentTypeService.fetchDeploymentTemplateUri(WingsTestConstants.TEMPLATE_ID))
        .thenReturn("template-uri");
    when(customDeploymentTypeService.fetchDeploymentTemplateIdFromUri(anyString(), eq("template-uri")))
        .thenReturn(WingsTestConstants.TEMPLATE_ID);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void toYaml() {
    final Yaml yaml = serviceYamlHandler.toYaml(service, APP_ID);
    final List<String> varNames =
        yaml.getConfigVariables().stream().map(NameValuePair.Yaml::getValue).collect(Collectors.toList());
    assertThat(varNames).containsExactlyInAnyOrder("safeharness:some-secret", "amazonkms:other-secret", "var");
    assertThat(yaml.getDeploymentTypeTemplateUri()).isEqualTo("template-uri");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void upsertFromYaml() {
    final Yaml yaml = serviceYamlHandler.toYaml(service, APP_ID);
    ChangeContext<Yaml> changeContext = getChangeContext(yaml);
    Service fromYaml = serviceYamlHandler.upsertFromYaml(changeContext, null);
    assertThat(fromYaml).isNotNull();
    verify(serviceVariableService, times(3)).save(captor.capture(), eq(true));
    assertThat(
        captor.getAllValues().stream().map(ServiceVariable::getValue).map(String::valueOf).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("some-secret", "other-secret", "var");
    assertThat(fromYaml.getDeploymentTypeTemplateId()).isEqualTo(WingsTestConstants.TEMPLATE_ID);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testUpdateServiceVariables() {
    when(yamlHelper.getService(APP_ID, validYamlFilePath)).thenReturn(service);

    Yaml yaml = serviceYamlHandler.toYaml(service, APP_ID);
    ChangeContext<Yaml> changeContext = getChangeContext(yaml);
    yaml.getConfigVariables().get(0).setValue("amazonkms:other-secret");
    Service fromYaml = serviceYamlHandler.upsertFromYaml(changeContext, null);
    assertThat(fromYaml).isNotNull();
    verify(serviceVariableService, times(2)).update(captor.capture(), eq(true));
    assertThat(
        captor.getAllValues().stream().map(ServiceVariable::getValue).map(String::valueOf).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("other-secret", "other-secret");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void getYamlClass() {
    assertThat(serviceYamlHandler.getYamlClass()).isEqualTo(Yaml.class);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void get() {
    when(yamlHelper.getService(APP_ID, validYamlFilePath)).thenReturn(service);
    assertThat(serviceYamlHandler.get(ACCOUNT_ID, validYamlFilePath)).isEqualTo(service);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testUpdateHelmVersionFromYaml() {
    Service.Yaml yaml = Service.Yaml.builder().helmVersion("V2").deploymentType(DeploymentType.HELM.toString()).build();
    Service service = new Service();
    serviceYamlHandler.setHelmVersion(yaml, service);
    assertThat(service.getHelmVersion()).isEqualTo(HelmVersion.V2);

    yaml.setHelmVersion("V3");
    service.setHelmVersion(null);
    serviceYamlHandler.setHelmVersion(yaml, service);
    assertThat(service.getHelmVersion()).isEqualTo(HelmVersion.V3);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldThrowIfInvalidHelmVersion() {
    Service.Yaml yaml =
        Service.Yaml.builder().helmVersion("garbage-text").deploymentType(DeploymentType.HELM.toString()).build();
    Service service = new Service();
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> serviceYamlHandler.setHelmVersion(yaml, service))
        .withMessageContaining("helmVersion");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testSetHelmVersionInYaml() {
    Service helmService =
        Service.builder().appId(APP_ID).helmVersion(HelmVersion.V2).artifactType(ArtifactType.DOCKER).build();
    Yaml yaml = serviceYamlHandler.toYaml(helmService, APP_ID);
    assertThat(yaml.getHelmVersion()).isEqualTo(HelmVersion.V2.toString());

    Service sshService = Service.builder().deploymentType(DeploymentType.SSH).artifactType(ArtifactType.JAR).build();
    yaml = serviceYamlHandler.toYaml(sshService, APP_ID);
    assertThat(yaml.getHelmVersion()).isNull();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testUpdateCfCliVersionFromYaml() {
    Service.Yaml yaml = Service.Yaml.builder().cfCliVersion("V6").deploymentType(DeploymentType.PCF.toString()).build();
    Service service = new Service();
    serviceYamlHandler.setCfCliVersion(yaml, service);
    assertThat(service.getCfCliVersion()).isEqualTo(CfCliVersion.V6);

    yaml.setCfCliVersion("V7");
    service.setCfCliVersion(null);
    serviceYamlHandler.setCfCliVersion(yaml, service);
    assertThat(service.getCfCliVersion()).isEqualTo(CfCliVersion.V7);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testUpdateCfCliVersionFromYamlWithInvalidVersion() {
    Service.Yaml yaml =
        Service.Yaml.builder().cfCliVersion("invalid-version").deploymentType(DeploymentType.PCF.toString()).build();
    Service service = new Service();

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> serviceYamlHandler.setCfCliVersion(yaml, service))
        .withMessageContaining("cfCliVersion");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetYamlFromServiceWithCfCliVersion() {
    Service pcfService =
        Service.builder().appId(APP_ID).cfCliVersion(CfCliVersion.V6).artifactType(ArtifactType.DOCKER).build();
    Yaml yaml = serviceYamlHandler.toYaml(pcfService, APP_ID);
    assertThat(yaml.getCfCliVersion()).isEqualTo(CfCliVersion.V6.toString());

    Service sshService = Service.builder().deploymentType(DeploymentType.SSH).artifactType(ArtifactType.JAR).build();
    yaml = serviceYamlHandler.toYaml(sshService, APP_ID);
    assertThat(yaml.getCfCliVersion()).isNull();
  }

  private ChangeContext<Yaml> getChangeContext(Yaml yaml) {
    GitFileChange gitFileChange = new GitFileChange();
    gitFileChange.setFilePath(validYamlFilePath);
    gitFileChange.setAccountId(ACCOUNT_ID);
    ChangeContext<Yaml> changeContext = new ChangeContext();
    changeContext.setChange(gitFileChange);
    changeContext.setYaml(yaml);
    return changeContext;
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testAddNewServiceVariables() {
    when(yamlHelper.getService(APP_ID, validYamlFilePath)).thenReturn(service);

    Yaml yaml = serviceYamlHandler.toYaml(service, APP_ID);
    ChangeContext<Yaml> changeContext = getChangeContext(yaml);

    NameValuePair.Yaml newServiceVar = NameValuePair.Yaml.builder()
                                           .name("enc_3")
                                           .value("safeharness:new-secret")
                                           .valueType(ServiceVariableType.ENCRYPTED_TEXT.name())
                                           .build();
    yaml.getConfigVariables().add(newServiceVar);
    when(yamlHelper.extractEncryptedRecordId(eq("safeharness:new-secret"), anyString())).thenReturn("new-secret");
    Service fromYaml = serviceYamlHandler.upsertFromYaml(changeContext, null);
    assertThat(fromYaml).isNotNull();
    verify(serviceVariableService, times(1)).save(captor.capture(), anyBoolean());
    assertThat(
        captor.getAllValues().stream().map(ServiceVariable::getValue).map(String::valueOf).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("new-secret");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testAddNewServiceVariableWithDashInName() {
    when(yamlHelper.getService(APP_ID, validYamlFilePath)).thenReturn(service);

    Yaml yaml = serviceYamlHandler.toYaml(service, APP_ID);
    ChangeContext<Yaml> changeContext = getChangeContext(yaml);

    NameValuePair.Yaml newServiceVar = NameValuePair.Yaml.builder()
                                           .name("enc-4")
                                           .value("safeharness:new-secret")
                                           .valueType(ServiceVariableType.ENCRYPTED_TEXT.name())
                                           .build();
    yaml.getConfigVariables().add(newServiceVar);
    when(yamlHelper.extractEncryptedRecordId(eq("safeharness:new-secret"), anyString())).thenReturn("new-secret");
    Service fromYaml = serviceYamlHandler.upsertFromYaml(changeContext, null);
    assertThat(fromYaml).isNotNull();
    verify(serviceVariableService, times(1)).save(captor.capture(), anyBoolean());
    assertThatThrownBy(() -> serviceYamlHandler.upsertFromYaml(changeContext, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Adding variable name enc-4 with hyphens (dashes) is not allowed");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnEditAppStack() {
    Service initialService = Service.builder()
                                 .appContainer(AppContainer.Builder.anAppContainer().withName("Tomcat 7").build())
                                 .deploymentType(DeploymentType.ECS)
                                 .artifactType(ArtifactType.TAR)
                                 .build();
    when(serviceResourceService.getServiceByName(APP_ID, SERVICE_NAME)).thenReturn(initialService);
    Yaml yaml = serviceYamlHandler.toYaml(service, APP_ID);
    ChangeContext<Yaml> changeContext = getChangeContext(yaml);
    assertThatThrownBy(() -> serviceYamlHandler.upsertFromYaml(changeContext, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("The 'applicationStack' can not be updated when a Service is already created.");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnEditArtifactType() {
    Service initialService = Service.builder()
                                 .appContainer(AppContainer.Builder.anAppContainer().withName("Tomcat 8").build())
                                 .deploymentType(DeploymentType.ECS)
                                 .artifactType(ArtifactType.TAR)
                                 .build();
    when(serviceResourceService.getServiceByName(APP_ID, SERVICE_NAME)).thenReturn(initialService);
    Yaml yaml = serviceYamlHandler.toYaml(service, APP_ID);
    ChangeContext<Yaml> changeContext = getChangeContext(yaml);
    assertThatThrownBy(() -> serviceYamlHandler.upsertFromYaml(changeContext, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("The 'artifactType' can not be updated when a Service is already created.");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldThrowExceptionOnEditDeploymentType() {
    Service initialService = Service.builder()
                                 .appContainer(AppContainer.Builder.anAppContainer().withName("Tomcat 8").build())
                                 .deploymentType(DeploymentType.ECS)
                                 .artifactType(ArtifactType.DOCKER)
                                 .build();
    when(serviceResourceService.getServiceByName(APP_ID, SERVICE_NAME)).thenReturn(initialService);
    Yaml yaml = serviceYamlHandler.toYaml(service, APP_ID);
    ChangeContext<Yaml> changeContext = getChangeContext(yaml);
    assertThatThrownBy(() -> serviceYamlHandler.upsertFromYaml(changeContext, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("The 'deploymentType' can not be updated when a Service is already created.");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testServiceCreationWithWinRM() {
    Service winRmService = Service.builder()
                               .appId(APP_ID)
                               .deploymentType(DeploymentType.WINRM)
                               .artifactType(ArtifactType.IIS_VirtualDirectory)
                               .build();
    final Yaml yaml = serviceYamlHandler.toYaml(winRmService, APP_ID);

    when(serviceResourceService.getServiceByName(APP_ID, SERVICE_NAME)).thenReturn(null);
    when(yamlHelper.getService(APP_ID, validYamlFilePath)).thenReturn(null);
    ChangeContext<Yaml> changeContext = getChangeContext(yaml);
    Service updatedFromYaml = serviceYamlHandler.upsertFromYaml(changeContext, null);
    assertThat(updatedFromYaml).isNotNull();
    assertThat(updatedFromYaml.getArtifactType()).isEqualTo(ArtifactType.IIS_VirtualDirectory);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void shouldThrowErrorIfArtifactTypeIncorrect() {
    Service winRmService = Service.builder()
                               .appId(APP_ID)
                               .deploymentType(DeploymentType.WINRM)
                               .artifactType(ArtifactType.IIS_VirtualDirectory)
                               .build();
    final Yaml yaml = serviceYamlHandler.toYaml(winRmService, APP_ID);
    yaml.setArtifactType("garbageValue");
    when(serviceResourceService.getServiceByName(APP_ID, SERVICE_NAME)).thenReturn(null);
    when(yamlHelper.getService(APP_ID, validYamlFilePath)).thenReturn(null);
    ChangeContext<Yaml> changeContext = getChangeContext(yaml);

    assertThatThrownBy(() -> serviceYamlHandler.upsertFromYaml(changeContext, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot find the value: garbageValue");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGitSyncFlagOnUpsertFromYaml() {
    final Yaml yaml = serviceYamlHandler.toYaml(service, APP_ID);
    ChangeContext<Yaml> changeContext = getChangeContext(yaml);
    changeContext.getChange().setSyncFromGit(true);

    Service fromYaml = serviceYamlHandler.upsertFromYaml(changeContext, null);
    assertThat(fromYaml).isNotNull();

    verify(serviceResourceService, times(1)).save(serviceCaptor.capture(), anyBoolean(), anyBoolean());
    Service capturedService = serviceCaptor.getValue();
    assertThat(capturedService).isNotNull();
    assertThat(capturedService.isSyncFromGit()).isTrue();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGitSyncFlagOnDelete() {
    final Yaml yaml = serviceYamlHandler.toYaml(service, APP_ID);
    ChangeContext<Yaml> changeContext = getChangeContext(yaml);
    changeContext.getChange().setSyncFromGit(true);
    when(yamlHelper.getServiceIfPresent(anyString(), anyString())).thenReturn(Optional.of(service));

    serviceYamlHandler.delete(changeContext);
    verify(serviceResourceService, times(1)).deleteByYamlGit(APP_ID, SERVICE_ID, true);
  }
}
