/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.expression.SecretString.SECRET_MASK;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VUK;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.beans.ServiceVariableType.ARTIFACT;
import static software.wings.beans.ServiceVariableType.ENCRYPTED_TEXT;
import static software.wings.beans.ServiceVariableType.TEXT;
import static software.wings.service.intfc.ServiceVariableService.EncryptedFieldMode.OBTAIN_VALUE;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_VARIABLE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_VARIABLE_NAME;
import static software.wings.utils.WingsTestConstants.TARGET_APP_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.category.element.UnitTests;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.EntityVersion.ChangeType;
import software.wings.beans.Event;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceVariable;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EntityVersionService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.service.intfc.yaml.YamlDirectoryService;
import software.wings.service.intfc.yaml.YamlPushService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.ConstraintViolationException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Created by anubhaw on 8/9/16.
 */
@OwnedBy(CDC)
public class ServiceVariableServiceTest extends WingsBaseTest {
  private static final ServiceVariable SERVICE_VARIABLE = ServiceVariable.builder()
                                                              .envId(ENV_ID)
                                                              .entityType(EntityType.SERVICE_TEMPLATE)
                                                              .entityId(TEMPLATE_ID)
                                                              .templateId(TEMPLATE_ID)
                                                              .name(SERVICE_VARIABLE_NAME)
                                                              .type(TEXT)
                                                              .value("8080".toCharArray())
                                                              .build();

  private static final ServiceVariable ENCRYPTED_SERVICE_VARIABLE = ServiceVariable.builder()
                                                                        .envId(ENV_ID)
                                                                        .entityType(EntityType.SERVICE_TEMPLATE)
                                                                        .entityId(TEMPLATE_ID)
                                                                        .templateId(TEMPLATE_ID)
                                                                        .name(SERVICE_VARIABLE_NAME + "2")
                                                                        .type(ENCRYPTED_TEXT)
                                                                        .value("9090".toCharArray())
                                                                        .build();

  static {
    SERVICE_VARIABLE.setUuid(SERVICE_VARIABLE_ID);
    SERVICE_VARIABLE.setAppId(APP_ID);

    ENCRYPTED_SERVICE_VARIABLE.setUuid(SERVICE_VARIABLE_ID + "2");
    ENCRYPTED_SERVICE_VARIABLE.setAppId(APP_ID);
  }

  /**
   * The Query.
   */
  @Mock Query<ServiceVariable> query;
  @Mock UpdateOperations<ServiceVariable> updateOperations;
  /**
   * The End.
   */
  @Mock FieldEnd end;
  @Mock private WingsPersistence wingsPersistence;
  @Mock private ServiceTemplateService serviceTemplateService;
  @Mock private EnvironmentService environmentService;
  @Mock private AppService appService;
  @Mock private YamlDirectoryService yamlDirectoryService;
  @Mock private AuditServiceHelper auditServiceHelper;
  @Mock private EntityVersionService mockEntityVersionService;
  @Mock private YamlPushService yamlPushService;
  @Mock private AuthHandler authHandler;
  @Inject @InjectMocks private ServiceVariableService serviceVariableService;

  /**
   * Sets up.
   *
   * @throws IOException the io exception
   */
  @Before
  public void setUp() throws IOException {
    SERVICE_VARIABLE.setAppId(APP_ID);
    when(wingsPersistence.createQuery(ServiceVariable.class)).thenReturn(query);
    when(query.filter(any(), any())).thenReturn(query);
    when(appService.get(TARGET_APP_ID)).thenReturn(Application.Builder.anApplication().accountId(ACCOUNT_ID).build());
    when(appService.get(APP_ID)).thenReturn(Application.Builder.anApplication().accountId(ACCOUNT_ID).build());
    when(serviceTemplateService.get(APP_ID, SERVICE_TEMPLATE_ID))
        .thenReturn(ServiceTemplate.Builder.aServiceTemplate().withEnvId(ENV_ID).build());

    doNothing().when(auditServiceHelper).reportForAuditingUsingAccountId(anyString(), any(), any(), any());
    doNothing().when(auditServiceHelper).reportDeleteForAuditingUsingAccountId(anyString(), any());
    doNothing().when(auditServiceHelper).reportDeleteForAuditing(anyString(), any());
    doNothing().when(auditServiceHelper).reportForAuditingUsingAppId(anyString(), any(), any(), any());
  }

  /**
   * Should list.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldList() {
    PageResponse<ServiceVariable> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(SERVICE_VARIABLE));
    pageResponse.setTotal(1l);

    PageRequest pageRequest = aPageRequest()
                                  .withLimit("50")
                                  .withOffset("0")
                                  .addFilter("appId", EQ, APP_ID)
                                  .addFilter("envId", EQ, ENV_ID)
                                  .addFilter("templateId", EQ, TEMPLATE_ID)
                                  .addFilter("entityId", EQ, "ENTITY_ID")
                                  .build();

    when(wingsPersistence.query(ServiceVariable.class, pageRequest)).thenReturn(pageResponse);
    PageResponse<ServiceVariable> configserviceSettingsiles = serviceVariableService.list(pageRequest);
    assertThat(configserviceSettingsiles).isNotNull();
    assertThat(configserviceSettingsiles.getResponse().get(0)).isInstanceOf(ServiceVariable.class);
  }

  /**
   * Should save.
   */
  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldSave() {
    serviceVariableService.save(SERVICE_VARIABLE);
    verify(wingsPersistence).save(SERVICE_VARIABLE);
    verify(mockEntityVersionService)
        .newEntityVersion(APP_ID, EntityType.CONFIG, SERVICE_VARIABLE.getUuid(), SERVICE_VARIABLE.getEntityId(),
            SERVICE_VARIABLE.getName(), ChangeType.CREATED, null);
  }

  /**
   * Should throw exception when saving with same name
   */
  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForSameName() {
    ServiceVariable serviceVariable = ServiceVariable.builder()
                                          .name("test")
                                          .envId(ENV_ID)
                                          .value("8080".toCharArray())
                                          .type(TEXT)
                                          .entityType(EntityType.SERVICE_TEMPLATE)
                                          .entityId(TEMPLATE_ID)
                                          .templateId(TEMPLATE_ID)
                                          .build();
    serviceVariable.setAppId(APP_ID);
    serviceVariableService.save(serviceVariable);

    EncryptedData encryptedData =
        EncryptedData.builder().name("test-encrypted-data").encryptedValue("8080".toCharArray()).build();

    ServiceVariable serviceVariableSameName = ServiceVariable.builder()
                                                  .name("test")
                                                  .envId(ENV_ID)
                                                  .value("8080".toCharArray())
                                                  .type(ENCRYPTED_TEXT)
                                                  .entityType(EntityType.SERVICE_TEMPLATE)
                                                  .entityId(TEMPLATE_ID)
                                                  .templateId(TEMPLATE_ID)
                                                  .build();

    serviceVariableSameName.setAppId(APP_ID);

    when(wingsPersistence.get(EncryptedData.class, "8080")).thenReturn(encryptedData);
    when(query.get()).thenReturn(serviceVariable);

    assertThatExceptionOfType(GeneralException.class)
        .isThrownBy(() -> serviceVariableService.save(serviceVariableSameName));
  }

  /**
   * Should throw exception for unsupported entity types.
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForUnsupportedEntityTypes() {
    ServiceVariable serviceVariable = ServiceVariable.builder()
                                          .envId(ENV_ID)
                                          .name(SERVICE_VARIABLE_NAME)
                                          .entityType(EntityType.APPLICATION)
                                          .entityId(TEMPLATE_ID)
                                          .templateId(TEMPLATE_ID)
                                          .type(TEXT)
                                          .value("8080".toCharArray())
                                          .build();
    serviceVariable.setAppId(APP_ID);
    serviceVariable.setUuid(SERVICE_VARIABLE_ID);

    assertThatExceptionOfType(WingsException.class).isThrownBy(() -> serviceVariableService.save(serviceVariable));
  }

  /**
   * Should get.
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldGet() {
    ServiceVariable variable = ServiceVariable.builder().build();
    variable.setAppId(APP_ID);
    variable.setUuid(SERVICE_VARIABLE_ID);
    when(wingsPersistence.getWithAppId(ServiceVariable.class, APP_ID, SERVICE_VARIABLE_ID)).thenReturn(variable);
    ServiceVariable serviceVariable = serviceVariableService.get(APP_ID, SERVICE_VARIABLE_ID);
    verify(wingsPersistence).getWithAppId(ServiceVariable.class, APP_ID, SERVICE_VARIABLE_ID);
    assertThat(serviceVariable.getUuid()).isEqualTo(SERVICE_VARIABLE_ID);
  }

  /**
   * Should get by template.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldGetByTemplate() {
    ServiceTemplate serviceTemplate =
        aServiceTemplate().withAppId(APP_ID).withEnvId(ENV_ID).withUuid(TEMPLATE_ID).build();

    PageRequest<ServiceVariable> request = aPageRequest()
                                               .addFilter("appId", Operator.EQ, APP_ID)
                                               .addFilter("envId", Operator.EQ, ENV_ID)
                                               .addFilter("templateId", Operator.EQ, TEMPLATE_ID)
                                               .build();
    PageResponse<ServiceVariable> resp = new PageResponse<>();
    resp.setResponse(asList(SERVICE_VARIABLE));
    when(wingsPersistence.query(ServiceVariable.class, request)).thenReturn(resp);
    when(wingsPersistence.getWithAppId(ServiceVariable.class, APP_ID, SERVICE_VARIABLE_ID))
        .thenReturn(SERVICE_VARIABLE);
    List<ServiceVariable> serviceVariables =
        serviceVariableService.getServiceVariablesByTemplate(APP_ID, ENV_ID, serviceTemplate, OBTAIN_VALUE);

    verify(wingsPersistence).query(ServiceVariable.class, request);
    assertThat(serviceVariables.get(0)).isEqualTo(SERVICE_VARIABLE);
  }

  /**
   * Should update.
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldUpdateNone() {
    ServiceVariable variable = ServiceVariable.builder()
                                   .name(SERVICE_VARIABLE_NAME)
                                   .entityType(EntityType.SERVICE_TEMPLATE)
                                   .type(TEXT)
                                   .value(generateUuid().toCharArray())
                                   .build();
    variable.setAppId(APP_ID);
    variable.setUuid(SERVICE_VARIABLE_ID);

    when(wingsPersistence.getWithAppId(ServiceVariable.class, APP_ID, SERVICE_VARIABLE_ID)).thenReturn(variable);
    serviceVariableService.update(variable);
    verify(wingsPersistence, times(0))
        .updateFields(ServiceVariable.class, SERVICE_VARIABLE_ID, ImmutableMap.of("type", TEXT));
  }

  /**
   * Should override service variable.
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldUpdateServiceVariable() {
    ServiceVariable variable = ServiceVariable.builder()
                                   .name(SERVICE_VARIABLE_NAME)
                                   .entityType(EntityType.SERVICE_TEMPLATE)
                                   .type(TEXT)
                                   .value("test".toCharArray())
                                   .build();

    variable.setAppId(APP_ID);
    variable.setUuid(SERVICE_VARIABLE_ID);
    when(wingsPersistence.getWithAppId(ServiceVariable.class, APP_ID, SERVICE_VARIABLE_ID)).thenReturn(variable);
    serviceVariableService.update(variable);

    verify(wingsPersistence, times(0))
        .updateFields(
            ServiceVariable.class, SERVICE_VARIABLE_ID, ImmutableMap.of("value", variable.getValue().toString()));
    verify(mockEntityVersionService)
        .newEntityVersion(APP_ID, EntityType.CONFIG, variable.getUuid(), variable.getEntityId(), variable.getName(),
            ChangeType.UPDATED, null);
  }

  /**
   * Should override service variable (Name null).
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldAllowUpdateServiceVariableWhenNameNull() {
    ServiceVariable variable = ServiceVariable.builder()
                                   .name(null)
                                   .entityType(EntityType.SERVICE_TEMPLATE)
                                   .type(TEXT)
                                   .value("test".toCharArray())
                                   .build();

    variable.setAppId(APP_ID);
    variable.setUuid(SERVICE_VARIABLE_ID);
    when(wingsPersistence.getWithAppId(ServiceVariable.class, APP_ID, SERVICE_VARIABLE_ID)).thenReturn(variable);
    serviceVariableService.update(variable);

    verify(wingsPersistence, times(0))
        .updateFields(
            ServiceVariable.class, SERVICE_VARIABLE_ID, ImmutableMap.of("value", variable.getValue().toString()));
  }

  /**
   * Should Allow service variable override (Saved name = current name).
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldAllowUpdateServiceVariable() {
    ServiceVariable variable = ServiceVariable.builder()
                                   .name(SERVICE_VARIABLE_NAME)
                                   .entityType(EntityType.SERVICE_TEMPLATE)
                                   .type(TEXT)
                                   .value(generateUuid().toCharArray())
                                   .build();

    variable.setAppId(APP_ID);
    variable.setUuid(SERVICE_VARIABLE_ID);
    when(wingsPersistence.getWithAppId(ServiceVariable.class, APP_ID, SERVICE_VARIABLE_ID)).thenReturn(variable);

    String secondVariableId = "SERVICE_VARIABLE_ID";
    ServiceVariable variable2 =
        ServiceVariable.builder().name(SERVICE_VARIABLE_NAME).entityType(EntityType.SERVICE_TEMPLATE).build();

    variable.setAppId(APP_ID);
    variable.setUuid(secondVariableId);
    when(wingsPersistence.getWithAppId(ServiceVariable.class, APP_ID, secondVariableId)).thenReturn(variable2);
    serviceVariableService.update(variable);
    verify(mockEntityVersionService)
        .newEntityVersion(APP_ID, EntityType.CONFIG, variable.getUuid(), variable.getEntityId(), variable.getName(),
            ChangeType.UPDATED, null);
  }

  /**
   * Should Throw exception for service variable override.
   */
  @Test(expected = InvalidRequestException.class)
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldThrowExceptionUpdateServiceVariable() {
    ServiceVariable variable = ServiceVariable.builder()
                                   .name(SERVICE_VARIABLE_NAME)
                                   .entityType(EntityType.SERVICE_TEMPLATE)
                                   .type(TEXT)
                                   .value(generateUuid().toCharArray())
                                   .build();

    variable.setAppId(APP_ID);
    variable.setUuid(SERVICE_VARIABLE_ID);
    when(wingsPersistence.getWithAppId(ServiceVariable.class, APP_ID, SERVICE_VARIABLE_ID)).thenReturn(variable);

    String secondVariableName = "SERVICE_VARIABLE_NAME_2";
    String secondVariableId = "SERVICE_VARIABLE_ID";
    ServiceVariable variable2 = ServiceVariable.builder()
                                    .name(secondVariableName)
                                    .entityType(EntityType.SERVICE_TEMPLATE)
                                    .type(TEXT)
                                    .value(generateUuid().toCharArray())
                                    .build();

    variable.setAppId(APP_ID);
    variable.setUuid(secondVariableId);
    when(wingsPersistence.getWithAppId(ServiceVariable.class, APP_ID, secondVariableId)).thenReturn(variable2);
    serviceVariableService.update(variable);
  }

  /**
   * Should update.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldUpdateValueAndType() {
    ServiceVariable variable = ServiceVariable.builder()
                                   .name(SERVICE_VARIABLE_NAME)
                                   .value(SERVICE_VARIABLE.getValue())
                                   .type(TEXT)
                                   .entityType(EntityType.SERVICE_TEMPLATE)
                                   .build();
    variable.setAppId(APP_ID);
    variable.setUuid(SERVICE_VARIABLE_ID);
    variable.setAccountId(ACCOUNT_ID);

    when(wingsPersistence.getWithAppId(ServiceVariable.class, APP_ID, SERVICE_VARIABLE_ID)).thenReturn(variable);
    serviceVariableService.update(variable);
    verify(wingsPersistence)
        .updateFields(ServiceVariable.class, SERVICE_VARIABLE_ID,
            ImmutableMap.of("type", TEXT, "value", SERVICE_VARIABLE.getValue(), "allowedList", new ArrayList<>()),
            Collections.emptySet());
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldUpdateAllowedList() {
    List<String> allowedList = new ArrayList<>();
    allowedList.add(ARTIFACT_STREAM_ID);
    ServiceVariable variable = ServiceVariable.builder()
                                   .name(SERVICE_VARIABLE_NAME)
                                   .type(ARTIFACT)
                                   .entityType(EntityType.SERVICE_TEMPLATE)
                                   .allowedList(allowedList)
                                   .build();
    variable.setAppId(APP_ID);
    variable.setUuid(SERVICE_VARIABLE_ID);
    variable.setAccountId(ACCOUNT_ID);

    when(wingsPersistence.getWithAppId(ServiceVariable.class, APP_ID, SERVICE_VARIABLE_ID)).thenReturn(variable);
    serviceVariableService.update(variable);
    verify(wingsPersistence)
        .updateFields(ServiceVariable.class, SERVICE_VARIABLE_ID,
            ImmutableMap.of("type", ARTIFACT, "allowedList", allowedList), Collections.emptySet());
  }

  /**
   * Should delete.
   */
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldDelete() {
    ServiceVariable variable = ServiceVariable.builder().entityType(EntityType.SERVICE_TEMPLATE).build();
    variable.setAppId(APP_ID);
    variable.setUuid(SERVICE_VARIABLE_ID);

    when(wingsPersistence.getWithAppId(ServiceVariable.class, APP_ID, SERVICE_VARIABLE_ID)).thenReturn(variable);
    when(wingsPersistence.delete(any(Query.class))).thenReturn(false);
    when(wingsPersistence.createQuery(ServiceVariable.class)).thenReturn(query);
    when(wingsPersistence.createUpdateOperations(ServiceVariable.class)).thenReturn(updateOperations);
    when(updateOperations.unset(any())).thenReturn(updateOperations);
    serviceVariableService.delete(APP_ID, SERVICE_VARIABLE_ID);
    verify(wingsPersistence).delete(query);
  }

  /**
   * Should get for entity.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldGetForEntity() {
    PageResponse<ServiceVariable> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(SERVICE_VARIABLE));
    pageResponse.setTotal(1l);

    PageRequest<ServiceVariable> pageRequest =
        aPageRequest().addFilter("appId", Operator.EQ, APP_ID).addFilter("entityId", Operator.EQ, "ENTITY_ID").build();

    when(wingsPersistence.query(ServiceVariable.class, pageRequest)).thenReturn(pageResponse);

    serviceVariableService.getServiceVariablesForEntity(APP_ID, "ENTITY_ID", OBTAIN_VALUE);
    verify(wingsPersistence).query(ServiceVariable.class, pageRequest);
  }

  /**
   * Should delete by entity id.
   */
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void shouldDeleteByEntityId() {
    deleteByEntityIdSetup();

    serviceVariableService.pruneByService(APP_ID, "ENTITY_ID");
    verify(wingsPersistence, times(2)).delete(any(ServiceVariable.class));
  }

  private void deleteByEntityIdSetup() {
    AuditServiceHelper auditServiceHelper = mock(AuditServiceHelper.class);
    doNothing().when(auditServiceHelper).reportDeleteForAuditing(anyString(), any());

    List<ServiceVariable> serviceVariables = asList(SERVICE_VARIABLE, ENCRYPTED_SERVICE_VARIABLE);
    when(wingsPersistence.createQuery(ServiceVariable.class)).thenReturn(query);
    when(query.filter(anyString(), any())).thenReturn(query);
    when(query.asList()).thenReturn(serviceVariables);
    when(wingsPersistence.delete(any(ServiceVariable.class))).thenReturn(true);
  }

  /**
   * Should mask encrypted fields.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldMaskEncryptedFields() {
    PageResponse<ServiceVariable> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(ENCRYPTED_SERVICE_VARIABLE));
    pageResponse.setTotal(1l);

    PageRequest pageRequest = aPageRequest()
                                  .withLimit("50")
                                  .withOffset("0")
                                  .addFilter("appId", EQ, APP_ID)
                                  .addFilter("envId", EQ, ENV_ID)
                                  .addFilter("templateId", EQ, TEMPLATE_ID)
                                  .addFilter("entityId", EQ, "ENTITY_ID")
                                  .build();

    when(wingsPersistence.query(ServiceVariable.class, pageRequest)).thenReturn(pageResponse);
    PageResponse<ServiceVariable> serviceVariablePageResponse = serviceVariableService.list(pageRequest);
    assertThat(serviceVariablePageResponse).isNotNull();
    assertThat(serviceVariablePageResponse.getResponse().get(0)).isInstanceOf(ServiceVariable.class);
    assertThat(serviceVariablePageResponse.getResponse().get(0).getValue()).isEqualTo("9090".toCharArray());
  }

  @Test(expected = WingsException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldNotSaveServiceVariableWithRandomEncryptedValue() {
    ServiceVariable variable = ServiceVariable.builder()
                                   .envId(ENV_ID)
                                   .entityType(EntityType.SERVICE_TEMPLATE)
                                   .entityId(TEMPLATE_ID)
                                   .templateId(TEMPLATE_ID)
                                   .name(SERVICE_VARIABLE_NAME + "3")
                                   .type(ENCRYPTED_TEXT)
                                   .value("9090.__@".toCharArray())
                                   .build();

    variable.setAppId(APP_ID);
    variable.setUuid(SERVICE_VARIABLE_ID);
    when(wingsPersistence.getWithAppId(ServiceVariable.class, APP_ID, SERVICE_VARIABLE_ID)).thenReturn(variable);
    when(wingsPersistence.get(EncryptedData.class, "9090.__@")).thenReturn(null);
    serviceVariableService.save(variable, false);
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldNotSaveServiceVariableWithDashInName() {
    ServiceVariable variable = ServiceVariable.builder()
                                   .envId(ENV_ID)
                                   .entityType(EntityType.SERVICE_TEMPLATE)
                                   .entityId(TEMPLATE_ID)
                                   .templateId(TEMPLATE_ID)
                                   .name("Test-Name")
                                   .type(TEXT)
                                   .value("TestValue".toCharArray())
                                   .build();

    variable.setAppId(APP_ID);
    variable.setUuid(SERVICE_VARIABLE_ID);
    when(wingsPersistence.getWithAppId(ServiceVariable.class, APP_ID, SERVICE_VARIABLE_ID)).thenReturn(variable);
    assertThatThrownBy(() -> serviceVariableService.save(variable, false))
        .isInstanceOf(ConstraintViolationException.class)
        .hasMessageContaining("Service Variable name can only have a-z, A-Z, 0-9 and _");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void shouldNotSaveServiceVariableWithEmptyValue() {
    ServiceVariable variable = ServiceVariable.builder()
                                   .envId(ENV_ID)
                                   .entityType(EntityType.SERVICE_TEMPLATE)
                                   .entityId(TEMPLATE_ID)
                                   .templateId(TEMPLATE_ID)
                                   .name("TestName")
                                   .type(TEXT)
                                   .value("".toCharArray())
                                   .build();

    variable.setAppId(APP_ID);
    variable.setUuid(SERVICE_VARIABLE_ID);
    when(wingsPersistence.getWithAppId(ServiceVariable.class, APP_ID, SERVICE_VARIABLE_ID)).thenReturn(variable);
    serviceVariableService.save(variable, false);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldPruneByEnvironment() {
    deleteByEntityIdSetup();

    serviceVariableService.pruneByEnvironment(APP_ID, ENV_ID);
    verify(wingsPersistence, times(2)).delete(any(ServiceVariable.class));
    verify(auditServiceHelper, times(2)).reportDeleteForAuditing(eq(APP_ID), any(ServiceVariable.class));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testSaveWithChecks_UnEncryptedService() {
    ServiceVariable variable = ServiceVariable.builder()
                                   .entityType(EntityType.SERVICE)
                                   .entityId(SERVICE_ID)
                                   .name("name")
                                   .type(TEXT)
                                   .value("TestValue".toCharArray())
                                   .uuid(generateUuid())
                                   .build();
    when(wingsPersistence.save(variable)).thenReturn(variable.getUuid());
    when(wingsPersistence.get(ServiceVariable.class, variable.getUuid())).thenReturn(variable);

    serviceVariableService.saveWithChecks(APP_ID, variable);
    verify(wingsPersistence).save(variable);
    verify(mockEntityVersionService)
        .newEntityVersion(APP_ID, EntityType.CONFIG, variable.getUuid(), variable.getEntityId(), variable.getName(),
            ChangeType.CREATED, null);
    verify(yamlPushService).pushYamlChangeSet(ACCOUNT_ID, variable, variable, Event.Type.UPDATE, false, false);
    verify(authHandler).authorize(any(), anyList(), eq(SERVICE_ID));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testSaveWithChecks_EncryptedService() {
    ServiceVariable variable = ServiceVariable.builder()
                                   .entityType(EntityType.SERVICE)
                                   .entityId(SERVICE_ID)
                                   .name("name")
                                   .type(ENCRYPTED_TEXT)
                                   .value("TestValue".toCharArray())
                                   .uuid(generateUuid())
                                   .build();
    when(wingsPersistence.save(variable)).thenReturn(variable.getUuid());
    when(wingsPersistence.get(ServiceVariable.class, variable.getUuid())).thenReturn(variable);
    when(wingsPersistence.get(eq(EncryptedData.class), anyString()))
        .thenReturn(EncryptedData.builder().encryptedValue("enc".toCharArray()).build());

    ServiceVariable savedServiceVariable = serviceVariableService.saveWithChecks(APP_ID, variable);
    verify(wingsPersistence).save(variable);
    verify(mockEntityVersionService)
        .newEntityVersion(APP_ID, EntityType.CONFIG, variable.getUuid(), variable.getEntityId(), variable.getName(),
            ChangeType.CREATED, null);
    verify(yamlPushService).pushYamlChangeSet(ACCOUNT_ID, variable, variable, Event.Type.UPDATE, false, false);
    assertThat(savedServiceVariable.getValue()).isEqualTo(SECRET_MASK.toCharArray());
    verify(authHandler).authorize(any(), anyList(), eq(SERVICE_ID));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testSaveWithChecks_UnEncryptedEnvironment() {
    ServiceVariable variable = ServiceVariable.builder()
                                   .entityType(EntityType.ENVIRONMENT)
                                   .entityId(ENV_ID)
                                   .name("name")
                                   .type(TEXT)
                                   .value("TestValue".toCharArray())
                                   .uuid(generateUuid())
                                   .build();
    when(wingsPersistence.save(variable)).thenReturn(variable.getUuid());
    when(wingsPersistence.get(ServiceVariable.class, variable.getUuid())).thenReturn(variable);

    serviceVariableService.saveWithChecks(APP_ID, variable);
    verify(wingsPersistence).save(variable);
    verify(mockEntityVersionService)
        .newEntityVersion(APP_ID, EntityType.CONFIG, variable.getUuid(), variable.getEntityId(), variable.getName(),
            ChangeType.CREATED, null);
    verify(yamlPushService).pushYamlChangeSet(ACCOUNT_ID, variable, variable, Event.Type.UPDATE, false, false);
    verify(authHandler).authorize(any(), anyList(), eq(ENV_ID));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testSaveWithChecks_EncryptedEnvironment() {
    ServiceVariable variable = ServiceVariable.builder()
                                   .entityType(EntityType.ENVIRONMENT)
                                   .entityId(ENV_ID)
                                   .name("name")
                                   .type(ENCRYPTED_TEXT)
                                   .value("TestValue".toCharArray())
                                   .uuid(generateUuid())
                                   .build();
    when(wingsPersistence.save(variable)).thenReturn(variable.getUuid());
    when(wingsPersistence.get(ServiceVariable.class, variable.getUuid())).thenReturn(variable);
    when(wingsPersistence.get(eq(EncryptedData.class), anyString()))
        .thenReturn(EncryptedData.builder().encryptedValue("enc".toCharArray()).build());

    ServiceVariable savedServiceVariable = serviceVariableService.saveWithChecks(APP_ID, variable);
    verify(wingsPersistence).save(variable);
    verify(mockEntityVersionService)
        .newEntityVersion(APP_ID, EntityType.CONFIG, variable.getUuid(), variable.getEntityId(), variable.getName(),
            ChangeType.CREATED, null);
    verify(yamlPushService).pushYamlChangeSet(ACCOUNT_ID, variable, variable, Event.Type.UPDATE, false, false);
    assertThat(savedServiceVariable.getValue()).isEqualTo(SECRET_MASK.toCharArray());
    verify(authHandler).authorize(any(), anyList(), eq(ENV_ID));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testSaveWithChecks_UnEncryptedServiceTemplate() {
    ServiceVariable variable = ServiceVariable.builder()
                                   .entityType(EntityType.SERVICE_TEMPLATE)
                                   .entityId(SERVICE_TEMPLATE_ID)
                                   .name("name")
                                   .type(TEXT)
                                   .value("TestValue".toCharArray())
                                   .templateId(SERVICE_TEMPLATE_ID)
                                   .uuid(generateUuid())
                                   .build();
    when(wingsPersistence.save(variable)).thenReturn(variable.getUuid());
    when(wingsPersistence.get(ServiceVariable.class, variable.getUuid())).thenReturn(variable);

    serviceVariableService.saveWithChecks(APP_ID, variable);
    verify(wingsPersistence).save(variable);
    verify(mockEntityVersionService)
        .newEntityVersion(APP_ID, EntityType.CONFIG, variable.getUuid(), variable.getEntityId(), variable.getName(),
            ChangeType.CREATED, null);
    verify(yamlPushService).pushYamlChangeSet(ACCOUNT_ID, variable, variable, Event.Type.UPDATE, false, false);
    verify(authHandler).authorize(any(), anyList(), eq(ENV_ID));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testSaveWithChecks_EncryptedServiceTemplate() {
    ServiceVariable variable = ServiceVariable.builder()
                                   .entityType(EntityType.SERVICE_TEMPLATE)
                                   .entityId(SERVICE_TEMPLATE_ID)
                                   .name("name")
                                   .type(ENCRYPTED_TEXT)
                                   .templateId(SERVICE_TEMPLATE_ID)
                                   .value("TestValue".toCharArray())
                                   .uuid(generateUuid())
                                   .build();
    when(wingsPersistence.save(variable)).thenReturn(variable.getUuid());
    when(wingsPersistence.get(ServiceVariable.class, variable.getUuid())).thenReturn(variable);
    when(wingsPersistence.get(eq(EncryptedData.class), anyString()))
        .thenReturn(EncryptedData.builder().encryptedValue("enc".toCharArray()).build());

    ServiceVariable savedServiceVariable = serviceVariableService.saveWithChecks(APP_ID, variable);
    verify(wingsPersistence).save(variable);
    verify(mockEntityVersionService)
        .newEntityVersion(APP_ID, EntityType.CONFIG, variable.getUuid(), variable.getEntityId(), variable.getName(),
            ChangeType.CREATED, null);
    verify(yamlPushService).pushYamlChangeSet(ACCOUNT_ID, variable, variable, Event.Type.UPDATE, false, false);
    assertThat(savedServiceVariable.getValue()).isEqualTo(SECRET_MASK.toCharArray());
    verify(authHandler).authorize(any(), anyList(), eq(ENV_ID));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testUpdateWithChecks_ValueAndTypeChange() {
    ServiceVariable savedVariable = ServiceVariable.builder()
                                        .entityType(EntityType.SERVICE)
                                        .entityId(SERVICE_ID)
                                        .name("name")
                                        .type(TEXT)
                                        .value("TestValue".toCharArray())
                                        .build();
    savedVariable.setAppId(APP_ID);
    savedVariable.setAccountId(ACCOUNT_ID);
    savedVariable.setUuid(SERVICE_VARIABLE_ID);

    ServiceVariable variable = ServiceVariable.builder()
                                   .entityType(EntityType.SERVICE)
                                   .entityId(SERVICE_ID)
                                   .name("name")
                                   .type(ENCRYPTED_TEXT)
                                   .value("TestValue1".toCharArray())
                                   .build();
    variable.setAppId(APP_ID);
    variable.setAccountId(ACCOUNT_ID);
    variable.setUuid(SERVICE_VARIABLE_ID);
    when(wingsPersistence.get(eq(EncryptedData.class), anyString()))
        .thenReturn(EncryptedData.builder().encryptedValue("enc".toCharArray()).build());
    when(wingsPersistence.getWithAppId(ServiceVariable.class, APP_ID, SERVICE_VARIABLE_ID)).thenAnswer(new Answer() {
      private int count = 0;

      @Override
      public Object answer(InvocationOnMock invocationOnMock) {
        if (count == 0) {
          count++;
          return savedVariable;
        }
        return variable;
      }
    });

    serviceVariableService.updateWithChecks(APP_ID, SERVICE_VARIABLE_ID, variable);
    verify(mockEntityVersionService)
        .newEntityVersion(APP_ID, EntityType.CONFIG, variable.getUuid(), variable.getEntityId(), variable.getName(),
            ChangeType.UPDATED, null);
    verify(yamlPushService).pushYamlChangeSet(ACCOUNT_ID, variable, variable, Event.Type.UPDATE, false, false);
    verify(authHandler).authorize(any(), anyList(), eq(SERVICE_ID));

    ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(wingsPersistence)
        .updateFields(
            eq(ServiceVariable.class), eq(SERVICE_VARIABLE_ID), argumentCaptor.capture(), eq(Collections.emptySet()));
    Map<String, Object> updateMap = argumentCaptor.getValue();
    assertThat(updateMap.get("type")).isEqualTo(variable.getType());
    assertThat(updateMap.get("allowedList")).isEqualTo(new ArrayList<>());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testUpdateWithChecks_TypeChangeFromEncryptedToText() {
    ServiceVariable variable = ServiceVariable.builder()
                                   .entityType(EntityType.SERVICE)
                                   .entityId(SERVICE_ID)
                                   .name("name")
                                   .type(TEXT)
                                   .value("TestValue".toCharArray())
                                   .build();
    ServiceVariable savedVariable = ServiceVariable.builder()
                                        .entityType(EntityType.SERVICE)
                                        .entityId(SERVICE_ID)
                                        .name("name")
                                        .type(ENCRYPTED_TEXT)
                                        .encryptedValue("TestValue1")
                                        .build();
    savedVariable.setAppId(APP_ID);
    savedVariable.setAccountId(ACCOUNT_ID);
    savedVariable.setUuid(SERVICE_VARIABLE_ID);
    variable.setAppId(APP_ID);
    variable.setAccountId(ACCOUNT_ID);
    variable.setUuid(SERVICE_VARIABLE_ID);
    when(wingsPersistence.get(eq(EncryptedData.class), anyString()))
        .thenReturn(EncryptedData.builder().encryptedValue("enc".toCharArray()).build());
    when(wingsPersistence.getWithAppId(ServiceVariable.class, APP_ID, SERVICE_VARIABLE_ID)).thenAnswer(new Answer() {
      private int count = 0;

      @Override
      public Object answer(InvocationOnMock invocationOnMock) {
        if (count == 0) {
          count++;
          return savedVariable;
        }
        return variable;
      }
    });

    serviceVariableService.updateWithChecks(APP_ID, SERVICE_VARIABLE_ID, variable);
    verify(mockEntityVersionService)
        .newEntityVersion(APP_ID, EntityType.CONFIG, variable.getUuid(), variable.getEntityId(), variable.getName(),
            ChangeType.UPDATED, null);
    verify(yamlPushService).pushYamlChangeSet(ACCOUNT_ID, variable, variable, Event.Type.UPDATE, false, false);
    verify(authHandler).authorize(any(), anyList(), eq(SERVICE_ID));

    Set<String> fieldsToRemove = new HashSet<>();
    fieldsToRemove.add("encryptedValue");
    ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(wingsPersistence)
        .updateFields(eq(ServiceVariable.class), eq(SERVICE_VARIABLE_ID), argumentCaptor.capture(), eq(fieldsToRemove));
    Map<String, Object> updateMap = argumentCaptor.getValue();
    assertThat(updateMap.get("type")).isEqualTo(variable.getType());
    assertThat(updateMap.get("allowedList")).isEqualTo(new ArrayList<>());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testDeleteWithChecks() {
    ServiceVariable variable = ServiceVariable.builder()
                                   .entityType(EntityType.SERVICE)
                                   .entityId(SERVICE_ID)
                                   .name("name")
                                   .type(ENCRYPTED_TEXT)
                                   .value("TestValue1".toCharArray())
                                   .build();
    variable.setAppId(APP_ID);
    variable.setAccountId(ACCOUNT_ID);
    variable.setUuid(SERVICE_VARIABLE_ID);
    when(wingsPersistence.getWithAppId(ServiceVariable.class, APP_ID, SERVICE_VARIABLE_ID)).thenReturn(variable);
    when(wingsPersistence.get(eq(EncryptedData.class), any()))
        .thenReturn(EncryptedData.builder().encryptedValue("enc".toCharArray()).build());
    when(wingsPersistence.delete(any(Query.class))).thenReturn(false);
    when(wingsPersistence.createQuery(ServiceVariable.class)).thenReturn(query);
    when(wingsPersistence.createUpdateOperations(ServiceVariable.class)).thenReturn(updateOperations);
    when(updateOperations.unset(any())).thenReturn(updateOperations);

    serviceVariableService.deleteWithChecks(APP_ID, SERVICE_VARIABLE_ID);
    verify(wingsPersistence).delete(query);
    verify(authHandler).authorize(any(), anyList(), eq(SERVICE_ID));
  }
}
