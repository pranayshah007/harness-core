/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.environment;

import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_VARIABLE_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.EnvironmentType;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.PageResponse.PageResponseBuilder;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Environment.VariableOverrideYaml;
import software.wings.beans.Environment.Yaml;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.rules.SetupScheduler;
import software.wings.service.impl.yaml.handler.tag.HarnessTagYamlHelper;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.utils.WingsTestConstants;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.inject.Inject;
import dev.morphia.Key;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * @author rktummala on 1/9/18
 */
@SetupScheduler
public class EnvironmentYamlHandlerTest extends YamlHandlerTestBase {
  @Mock AppService appService;
  @Mock HarnessTagYamlHelper harnessTagYamlHelper;
  @Mock ServiceTemplateService mockServiceTemplateService;
  @Mock YamlHelper yamlHelper;
  @Mock ServiceVariableService mockServiceVariableService;
  @Mock EnvironmentService mockEnvironmentService;
  @Mock ServiceResourceService serviceResourceService;
  @Mock SecretManager secretManager;
  @InjectMocks @Inject private EnvironmentYamlHandler yamlHandler;

  private ArgumentCaptor<Environment> captor = ArgumentCaptor.forClass(Environment.class);

  private final String APP_NAME = "app1";
  private final String ENV_NAME = "env1";
  private Environment environment;
  private ServiceTemplate serviceTemplate;

  private String validYamlContent = "harnessApiVersion: '1.0'\n"
      + "type: ENVIRONMENT\n"
      + "configMapYaml: all:1\n"
      + "configMapYamlByServiceTemplateName:\n"
      + "  SERVICE_TEMPLATE_NAME: u:1\n"
      + "description: valid env yaml\n"
      + "environmentType: NON_PROD";
  private String validYamlFilePath = "Setup/Applications/" + APP_NAME + "/Environments/" + ENV_NAME + "/Index.yaml";
  private String invalidYamlFilePath =
      "Setup/Applications/" + APP_NAME + "/EnvironmentsInvalid/" + ENV_NAME + "/Index.yaml";

  @Before
  public void setUp() throws IOException {
    serviceTemplate = aServiceTemplate()
                          .withUuid(SERVICE_TEMPLATE_ID)
                          .withName("SERVICE_TEMPLATE_NAME")
                          .withAppId(WingsTestConstants.APP_ID)
                          .withEnvId(ENV_ID)
                          .withServiceId(WingsTestConstants.SERVICE_ID)
                          .withConfigMapYamlOverride("u:1")
                          .build();
    environment = anEnvironment()
                      .name(ENV_NAME)
                      .uuid(ENV_ID)
                      .appId(WingsTestConstants.APP_ID)
                      .environmentType(EnvironmentType.NON_PROD)
                      .configMapYaml("all:1")
                      .configMapYamlByServiceTemplateId(new HashMap<String, String>() {
                        { put(WingsTestConstants.SERVICE_TEMPLATE_ID, serviceTemplate.getConfigMapYamlOverride()); }
                      })
                      .description("valid env yaml")
                      .build();
    PageResponse<ServiceTemplate> pageResponse =
        PageResponseBuilder.<ServiceTemplate>aPageResponse().withResponse(Arrays.asList(serviceTemplate)).build();
    when(appService.getAppByName(anyString(), anyString()))
        .thenReturn(anApplication().name(APP_NAME).uuid(WingsTestConstants.APP_ID).build());
    when(appService.getAccountIdByAppId(anyString())).thenReturn(WingsTestConstants.ACCOUNT_ID);
    when(appService.get(anyString()))
        .thenReturn(anApplication().name(APP_NAME).uuid(WingsTestConstants.APP_ID).build());
    when(appService.get(anyString(), anyBoolean()))
        .thenReturn(anApplication().name(APP_NAME).accountId(ACCOUNT_ID).uuid(WingsTestConstants.APP_ID).build());
    when(mockServiceTemplateService.listWithoutServiceAndInfraMappingSummary(any(), anyBoolean(), any()))
        .thenReturn(pageResponse);
    when(mockServiceTemplateService.list(any(), anyBoolean(), any())).thenReturn(pageResponse);
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(APP_ID);
    when(yamlHelper.getEnvironmentName(validYamlFilePath)).thenReturn(ENV_NAME);
    when(yamlHelper.getEnvironment(APP_ID, invalidYamlFilePath))
        .thenThrow(new InvalidRequestException("env does not exist"));
    when(yamlHelper.getApplicationIfPresent(ACCOUNT_ID, validYamlFilePath))
        .thenReturn(Optional.of(anApplication().uuid(APP_ID).build()));
    when(yamlHelper.getEnvIfPresent(APP_ID, validYamlFilePath))
        .thenReturn(Optional.of(anEnvironment().uuid(ENV_ID).build()));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws IOException {
    GitFileChange gitFileChange = new GitFileChange();
    gitFileChange.setFileContent(validYamlContent);
    gitFileChange.setFilePath(validYamlFilePath);
    gitFileChange.setAccountId(WingsTestConstants.ACCOUNT_ID);

    ChangeContext<Yaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.ENVIRONMENT);
    changeContext.setYamlSyncHandler(yamlHandler);

    Yaml yamlObject = (Yaml) getYaml(validYamlContent, Yaml.class);
    changeContext.setYaml(yamlObject);

    when(mockEnvironmentService.save(environment)).thenReturn(environment);
    yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    verify(mockEnvironmentService, times(1)).save(captor.capture());
    Environment savedEnv = captor.getValue();
    compareEnv(environment, savedEnv);

    Yaml yaml = yamlHandler.toYaml(this.environment, WingsTestConstants.APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isNotNull();

    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(validYamlContent);

    when(yamlHelper.getEnvironment(APP_ID, validYamlFilePath)).thenReturn(environment);
    Environment envFromGet = yamlHandler.get(WingsTestConstants.ACCOUNT_ID, validYamlFilePath);
    compareEnv(environment, envFromGet);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testFailures() throws IOException {
    // Invalid yaml path
    GitFileChange gitFileChange = new GitFileChange();
    gitFileChange.setFileContent(validYamlContent);
    gitFileChange.setFilePath(invalidYamlFilePath);
    gitFileChange.setAccountId(WingsTestConstants.ACCOUNT_ID);

    ChangeContext<Yaml> changeContext = new ChangeContext();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.ENVIRONMENT);
    changeContext.setYamlSyncHandler(yamlHandler);

    Yaml yamlObject = (Yaml) getYaml(validYamlContent, Yaml.class);
    changeContext.setYaml(yamlObject);

    thrown.expect(WingsException.class);
    yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
  }

  private void compareEnv(Environment lhs, Environment rhs) {
    assertThat(rhs.getName()).isEqualTo(lhs.getName());
    assertThat(rhs.getAppId()).isEqualTo(lhs.getAppId());
    assertThat(rhs.getEnvironmentType()).isEqualTo(lhs.getEnvironmentType());
    assertThat(rhs.getDescription()).isEqualTo(lhs.getDescription());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetRequiredServiceTemplates() {
    int pageSize = PageRequest.DEFAULT_UNLIMITED;
    int offset = 0;
    List<ServiceTemplate> serviceTemplates = new ArrayList<>();
    for (int i = 0; i < pageSize * 2 + 386; i++) {
      serviceTemplates.add(aServiceTemplate().withName("name" + i).withUuid("id" + i).build());
    }
    PageRequest<ServiceTemplate> pageRequest =
        PageRequestBuilder.<ServiceTemplate>aPageRequest().withOffset(String.valueOf(offset)).build();
    when(mockServiceTemplateService.listWithoutServiceAndInfraMappingSummary(any(), anyBoolean(), any()))
        .thenReturn(aPageResponse().withResponse(serviceTemplates.subList(0, pageSize)).build(),
            aPageResponse().withResponse(serviceTemplates.subList(pageSize, pageSize * 2)).build(),
            aPageResponse().withResponse(serviceTemplates.subList(pageSize * 2, serviceTemplates.size())).build());
    List<ServiceTemplate> response = yamlHandler.getRequiredServiceTemplates(pageSize, pageRequest);
    assertThat(response).hasSize(serviceTemplates.size());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testCreate_VariableOverride() {
    final String encryped_yaml_ref = "safeharness:some-secret";
    final String expected_value_for_encrypted_var = "safeharness:some-secret";
    ArgumentCaptor<ServiceVariable> captor = ArgumentCaptor.forClass(ServiceVariable.class);
    Environment env = getDefaultEnvironment();
    Yaml yaml = yamlHandler.toYaml(env, env.getAppId());
    VariableOverrideYaml newVariableOverride_1 =
        VariableOverrideYaml.builder().name("var-1").value("value-1").valueType("TEXT").build();
    VariableOverrideYaml newVariableOverride_2 =
        VariableOverrideYaml.builder().name("var-2").value("value-2").valueType("TEXT").build();
    VariableOverrideYaml newVariableOverride_3 =
        VariableOverrideYaml.builder().name("var-3").value(encryped_yaml_ref).valueType("ENCRYPTED_TEXT").build();
    yaml.setVariableOverrides(Arrays.asList(newVariableOverride_1, newVariableOverride_2, newVariableOverride_3));

    ChangeContext<Yaml> changeContext = getChangeContext(yaml);

    when(mockEnvironmentService.save(env)).thenReturn(env);
    when(yamlHelper.extractEncryptedRecordId(eq(newVariableOverride_3.getValue()), anyString()))
        .thenReturn(expected_value_for_encrypted_var);
    yamlHandler.upsertFromYaml(changeContext, null);
    verify(mockServiceVariableService, times(3)).save(captor.capture(), anyBoolean());
    List<String> varNames =
        captor.getAllValues().stream().map(ServiceVariable::getValue).map(String::valueOf).collect(Collectors.toList());
    assertThat(varNames).containsExactlyInAnyOrder(
        newVariableOverride_1.getValue(), newVariableOverride_2.getValue(), expected_value_for_encrypted_var);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testUpdateDelete_variableOverride() throws Exception {
    final String encryped_yaml_ref = "safeharness:some-secret";
    final String expected_value_for_encrypted_var = "safeharness:some-secret";
    ArgumentCaptor<ServiceVariable> captor = ArgumentCaptor.forClass(ServiceVariable.class);
    Environment environment = getDefaultEnvironment();
    Service parentService = Service.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build();
    ServiceVariable parentServiceVariable =
        ServiceVariable.builder().name("sv").serviceId(SERVICE_ID).entityId(SERVICE_ID).build();
    parentService.setUuid(SERVICE_VARIABLE_ID);
    ServiceVariable existing_1 = ServiceVariable.builder()
                                     .name("sv-1")
                                     .type(ServiceVariableType.ENCRYPTED_TEXT)
                                     .envId(ENV_ID)
                                     .serviceId(SERVICE_ID)
                                     .entityType(EntityType.SERVICE_TEMPLATE)
                                     .entityId(SERVICE_TEMPLATE_ID)
                                     .parentServiceVariableId(SERVICE_VARIABLE_ID)
                                     .build();
    ServiceVariable existing_2 = ServiceVariable.builder()
                                     .name("sv-2")
                                     .type(ServiceVariableType.TEXT)
                                     .envId(ENV_ID)
                                     .serviceId(SERVICE_ID)
                                     .entityType(EntityType.SERVICE_TEMPLATE)
                                     .entityId(SERVICE_TEMPLATE_ID)
                                     .parentServiceVariableId(SERVICE_VARIABLE_ID)
                                     .build();
    existing_1.setAppId(APP_ID);
    existing_2.setAppId(APP_ID);

    VariableOverrideYaml existing_1_override = VariableOverrideYaml.builder()
                                                   .name(existing_1.getName())
                                                   .value(encryped_yaml_ref)
                                                   .valueType("ENCRYPTED_TEXT")
                                                   .serviceName(parentService.getName())
                                                   .build();
    ServiceTemplate serviceTemplate_1 = aServiceTemplate().withServiceId(SERVICE_ID).withEnvId(ENV_ID).build();
    environment.setServiceTemplates(Arrays.asList(serviceTemplate_1));

    when(yamlHelper.extractEncryptedRecordId(eq(existing_1_override.getValue()), any()))
        .thenReturn(expected_value_for_encrypted_var);
    when(mockServiceVariableService.getServiceVariablesByTemplate(any(), any(), any(ServiceTemplate.class), any()))
        .thenReturn(Arrays.asList(existing_1, existing_2));
    when(yamlHelper.getEnvironment(APP_ID, validYamlFilePath)).thenReturn(environment);
    when(serviceResourceService.getServiceByName(APP_ID, existing_1_override.getServiceName()))
        .thenReturn(Service.builder().uuid(SERVICE_ID).name(existing_1.getName()).build());
    when(mockServiceTemplateService.getTemplateRefKeysByService(APP_ID, SERVICE_ID, ENV_ID))
        .thenReturn(Arrays.asList(new Key(ServiceTemplate.class, "ServiceTemplate", SERVICE_TEMPLATE_ID)));
    when(mockServiceVariableService.get(APP_ID, SERVICE_VARIABLE_ID)).thenReturn(parentServiceVariable);
    when(serviceResourceService.getWithDetails(APP_ID, SERVICE_ID)).thenReturn(parentService);
    when(secretManager.getEncryptedYamlRef(any(), any())).thenReturn(existing_1_override.getValue());
    when(mockServiceTemplateService.get(APP_ID, SERVICE_TEMPLATE_ID)).thenReturn(serviceTemplate_1);

    Yaml yaml = yamlHandler.toYaml(environment, environment.getAppId());
    yaml.setVariableOverrides(Arrays.asList(existing_1_override));
    ChangeContext<Yaml> changeContext = getChangeContext(yaml);
    yamlHandler.upsertFromYaml(changeContext, null);
    verify(mockServiceVariableService, times(1)).delete(anyString(), any(), eq(true));
    verify(mockServiceVariableService, times(1)).update(captor.capture(), eq(true));
    String encrypted_serviceVariableValue = String.valueOf(captor.getValue().getValue());
    assertThat(encrypted_serviceVariableValue).isEqualTo(expected_value_for_encrypted_var);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testDelete() {
    Environment env = getDefaultEnvironment();
    Yaml yaml = yamlHandler.toYaml(environment, environment.getAppId());
    ChangeContext<Yaml> changeContext = getChangeContext(yaml);
    yamlHandler.delete(changeContext);
    verify(mockEnvironmentService, times(1)).delete(APP_ID, ENV_ID, false);
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

  private Environment getDefaultEnvironment() {
    return anEnvironment()
        .name(ENV_NAME)
        .uuid(ENV_ID)
        .appId(APP_ID)
        .environmentType(EnvironmentType.NON_PROD)
        .description("valid env yaml")
        .configMapYamlByServiceTemplateId(Collections.emptyMap())
        .build();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testSaveGet_variableOverride_OnUpdateEnvironment() {
    final String encryped_yaml_ref = "safeharness:some-secret";
    final String expected_value_for_encrypted_var = "safeharness:some-secret";
    ArgumentCaptor<ServiceVariable> captor = ArgumentCaptor.forClass(ServiceVariable.class);
    Environment environment = getDefaultEnvironment();
    ServiceVariable existing = ServiceVariable.builder()
                                   .name("sv-1")
                                   .type(ServiceVariableType.TEXT)
                                   .envId(GLOBAL_ENV_ID)
                                   .entityType(EntityType.ENVIRONMENT)
                                   .build();
    existing.setAppId(APP_ID);

    VariableOverrideYaml new_override =
        VariableOverrideYaml.builder().name("sv-2").value(encryped_yaml_ref).valueType("ENCRYPTED_TEXT").build();

    when(yamlHelper.extractEncryptedRecordId(eq(new_override.getValue()), anyString()))
        .thenReturn(expected_value_for_encrypted_var);
    when(mockServiceVariableService.getServiceVariablesByTemplate(
             anyString(), anyString(), any(ServiceTemplate.class), any()))
        .thenReturn(Collections.emptyList());
    when(mockServiceVariableService.getServiceVariablesForEntity(
             anyString(), anyString(), eq(ServiceVariableService.EncryptedFieldMode.OBTAIN_VALUE)))
        .thenReturn(asList(existing));
    when(yamlHelper.getEnvironment(APP_ID, validYamlFilePath)).thenReturn(environment);
    when(secretManager.getEncryptedYamlRef(any(), any())).thenReturn(new_override.getValue());

    Yaml yaml = yamlHandler.toYaml(environment, environment.getAppId());
    yaml.setVariableOverrides(Arrays.asList(new_override));
    ChangeContext<Yaml> changeContext = getChangeContext(yaml);

    yamlHandler.upsertFromYaml(changeContext, null);
    verify(mockServiceVariableService, times(1)).save(captor.capture(), eq(true));
    List<String> varNames =
        captor.getAllValues().stream().map(ServiceVariable::getValue).map(String::valueOf).collect(Collectors.toList());
    assertThat(varNames).containsExactlyInAnyOrder(new_override.getValue());

    ServiceVariable newly_added = ServiceVariable.builder()
                                      .name("sv-2")
                                      .type(ServiceVariableType.ENCRYPTED_TEXT)
                                      .envId(GLOBAL_ENV_ID)
                                      .entityType(EntityType.ENVIRONMENT)
                                      .value(encryped_yaml_ref.toCharArray())
                                      .build();
    newly_added.setAppId(APP_ID);
    when(mockServiceVariableService.getServiceVariablesForEntity(
             anyString(), anyString(), eq(ServiceVariableService.EncryptedFieldMode.OBTAIN_VALUE)))
        .thenReturn(asList(existing, newly_added));

    yaml = yamlHandler.toYaml(environment, environment.getAppId());
    List<VariableOverrideYaml> variableOverrides = yaml.getVariableOverrides();
    assertThat(variableOverrides).isNotNull().hasSize(2);
    assertThat(variableOverrides).contains(new_override);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGitSyncFlagOnUpsertFromYaml() throws IOException {
    GitFileChange gitFileChange = new GitFileChange();
    gitFileChange.setFileContent(validYamlContent);
    gitFileChange.setFilePath(validYamlFilePath);
    gitFileChange.setAccountId(WingsTestConstants.ACCOUNT_ID);
    gitFileChange.setSyncFromGit(true);

    ChangeContext<Yaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.ENVIRONMENT);
    changeContext.setYamlSyncHandler(yamlHandler);

    Yaml yamlObject = (Yaml) getYaml(validYamlContent, Yaml.class);
    changeContext.setYaml(yamlObject);

    when(mockEnvironmentService.save(environment)).thenReturn(environment);
    yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    verify(mockEnvironmentService, times(1)).save(captor.capture());
    Environment savedEnv = captor.getValue();
    assertThat(savedEnv).isNotNull();
    assertThat(savedEnv.isSyncFromGit()).isTrue();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGitSyncFlagOnDelete() {
    Environment env = getDefaultEnvironment();
    Yaml yaml = yamlHandler.toYaml(environment, environment.getAppId());
    ChangeContext<Yaml> changeContext = getChangeContext(yaml);
    changeContext.getChange().setSyncFromGit(true);

    yamlHandler.delete(changeContext);
    verify(mockEnvironmentService, times(1)).delete(APP_ID, ENV_ID, true);
  }
}
