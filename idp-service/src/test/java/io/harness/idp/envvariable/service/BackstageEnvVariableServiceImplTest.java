/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.envvariable.service;

import static io.harness.idp.k8s.constants.K8sConstants.BACKSTAGE_SECRET;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.*;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptedSecretValue;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.app.IdpServiceRule;
import io.harness.idp.envvariable.beans.entity.BackstageEnvSecretVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableEntity.BackstageEnvVariableMapper;
import io.harness.idp.envvariable.beans.entity.BackstageEnvVariableType;
import io.harness.idp.envvariable.repositories.BackstageEnvVariableRepository;
import io.harness.idp.events.producers.SetupUsageProducer;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.rule.LifecycleRule;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;

import com.google.inject.Inject;
import com.google.protobuf.StringValue;
import java.util.*;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class BackstageEnvVariableServiceImplTest extends CategoryTest {
  static final String TEST_SECRET_VALUE = "e55fa2b3e55fe55f";
  static final String TEST_DECRYPTED_VALUE = "abc123";
  static final String TEST_ENV_NAME = "HARNESS_API_KEY";
  static final String TEST_IDENTIFIER = "accountHarnessKey";
  static final String TEST_SECRET_IDENTIFIER = "accountHarnessKey";
  static final String TEST_SECRET_IDENTIFIER1 = "harnessKey";
  static final String TEST_ACCOUNT_IDENTIFIER = "accountId";
  static final String TEST_NAMESPACE = "namespace";
  AutoCloseable openMocks;
  @Mock private BackstageEnvVariableRepository backstageEnvVariableRepository;
  @Mock K8sClient k8sClient;
  @Mock NamespaceService namespaceService;
  @Mock SecretManagerClientService ngSecretService;
  @Mock SetupUsageProducer setupUsageProducer;
  @Rule public LifecycleRule lifecycleRule = new LifecycleRule();
  @Rule public IdpServiceRule apiServiceRule = new IdpServiceRule(lifecycleRule.getClosingFactory());
  @Inject private Map<BackstageEnvVariableType, BackstageEnvVariableMapper> mapBinder;
  private BackstageEnvVariableServiceImpl backstageEnvVariableService;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    backstageEnvVariableService = new BackstageEnvVariableServiceImpl(
        backstageEnvVariableRepository, k8sClient, ngSecretService, namespaceService, mapBinder, setupUsageProducer);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFindByIdAndAccountIdentifier() {
    BackstageEnvSecretVariableEntity envVariableEntity = BackstageEnvSecretVariableEntity.builder().build();
    when(backstageEnvVariableRepository.findByIdAndAccountIdentifier(TEST_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(envVariableEntity));
    Optional<BackstageEnvVariable> envVariableOpt =
        backstageEnvVariableService.findByIdAndAccountIdentifier(TEST_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    assertTrue(envVariableOpt.isPresent());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreate() {
    mockAccountNamespaceMapping();
    BackstageEnvSecretVariable envVariable = new BackstageEnvSecretVariable();
    envVariable.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariable.envName(TEST_ENV_NAME);
    envVariable.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvVariableEntity envVariableEntity =
        mapBinder.get(BackstageEnvVariableType.SECRET).fromDto(envVariable, TEST_ACCOUNT_IDENTIFIER);
    when(backstageEnvVariableRepository.save(envVariableEntity)).thenReturn(envVariableEntity);
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);
    assertEquals(envVariable, backstageEnvVariableService.create(envVariable, TEST_ACCOUNT_IDENTIFIER));
    verify(k8sClient).updateSecretData(eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap(), eq(false));
    verify(setupUsageProducer)
        .publishEnvVariableSetupUsage(Collections.singletonList(envVariable), TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCreateMulti() {
    mockAccountNamespaceMapping();
    BackstageEnvSecretVariable envVariable1 = new BackstageEnvSecretVariable();
    envVariable1.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariable1.envName(TEST_ENV_NAME);
    envVariable1.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvSecretVariable envVariable2 = new BackstageEnvSecretVariable();
    envVariable2.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariable2.envName(TEST_ENV_NAME);
    envVariable2.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvVariableEntity envVariableEntity1 =
        mapBinder.get(BackstageEnvVariableType.SECRET).fromDto(envVariable1, TEST_ACCOUNT_IDENTIFIER);
    BackstageEnvVariableEntity envVariableEntity2 =
        mapBinder.get(BackstageEnvVariableType.SECRET).fromDto(envVariable2, TEST_ACCOUNT_IDENTIFIER);
    List<BackstageEnvVariableEntity> entities = Arrays.asList(envVariableEntity1, envVariableEntity2);
    when(backstageEnvVariableRepository.saveAll(entities)).thenReturn(entities);
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);
    List<BackstageEnvVariable> responseVariables =
        backstageEnvVariableService.createMulti(Arrays.asList(envVariable1, envVariable2), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(envVariable1, responseVariables.get(0));
    assertEquals(envVariable2, responseVariables.get(1));
    verify(k8sClient).updateSecretData(eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap(), eq(false));
    verify(setupUsageProducer)
        .publishEnvVariableSetupUsage(Arrays.asList(envVariable1, envVariable2), TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdate() {
    mockAccountNamespaceMapping();
    BackstageEnvSecretVariable envVariable = new BackstageEnvSecretVariable();
    envVariable.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariable.envName(TEST_ENV_NAME);
    envVariable.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvVariableEntity envVariableEntity =
        mapBinder.get(BackstageEnvVariableType.SECRET).fromDto(envVariable, TEST_ACCOUNT_IDENTIFIER);
    when(backstageEnvVariableRepository.update(envVariableEntity)).thenReturn(envVariableEntity);
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);
    assertEquals(envVariable, backstageEnvVariableService.update(envVariable, TEST_ACCOUNT_IDENTIFIER));
    verify(k8sClient).updateSecretData(eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap(), eq(false));
    verify(setupUsageProducer)
        .deleteEnvVariableSetupUsage(Collections.singletonList(envVariable), TEST_ACCOUNT_IDENTIFIER);
    verify(setupUsageProducer)
        .publishEnvVariableSetupUsage(Collections.singletonList(envVariable), TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdateMulti() {
    mockAccountNamespaceMapping();
    BackstageEnvSecretVariable envVariable1 = new BackstageEnvSecretVariable();
    envVariable1.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariable1.envName(TEST_ENV_NAME);
    envVariable1.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvSecretVariable envVariable2 = new BackstageEnvSecretVariable();
    envVariable2.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariable2.envName(TEST_ENV_NAME);
    envVariable2.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvVariableEntity envVariableEntity1 =
        mapBinder.get(BackstageEnvVariableType.SECRET).fromDto(envVariable1, TEST_ACCOUNT_IDENTIFIER);
    BackstageEnvVariableEntity envVariableEntity2 =
        mapBinder.get(BackstageEnvVariableType.SECRET).fromDto(envVariable2, TEST_ACCOUNT_IDENTIFIER);
    when(backstageEnvVariableRepository.update(envVariableEntity1)).thenReturn(envVariableEntity1);
    when(backstageEnvVariableRepository.update(envVariableEntity2)).thenReturn(envVariableEntity2);
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);
    List<BackstageEnvVariable> responseVariables =
        backstageEnvVariableService.updateMulti(Arrays.asList(envVariable1, envVariable2), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(envVariable1, responseVariables.get(0));
    assertEquals(envVariable2, responseVariables.get(1));
    verify(k8sClient).updateSecretData(eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap(), eq(false));
    verify(setupUsageProducer)
        .deleteEnvVariableSetupUsage(Arrays.asList(envVariable1, envVariable2), TEST_ACCOUNT_IDENTIFIER);
    verify(setupUsageProducer)
        .publishEnvVariableSetupUsage(Arrays.asList(envVariable1, envVariable2), TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifier() {
    BackstageEnvSecretVariableEntity envVariableEntity = BackstageEnvSecretVariableEntity.builder().build();
    envVariableEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    when(backstageEnvVariableRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Collections.singletonList(envVariableEntity));
    List<BackstageEnvVariable> variables = backstageEnvVariableService.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(1, variables.size());
    assertEquals(envVariableEntity,
        mapBinder.get(BackstageEnvVariableType.SECRET)
            .fromDto((BackstageEnvSecretVariable) variables.get(0), TEST_ACCOUNT_IDENTIFIER));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDelete() {
    mockAccountNamespaceMapping();
    BackstageEnvSecretVariableEntity envVariableEntity = BackstageEnvSecretVariableEntity.builder().build();
    when(backstageEnvVariableRepository.findByIdAndAccountIdentifier(TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(envVariableEntity));
    backstageEnvVariableService.delete(TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    verify(k8sClient).removeSecretData(eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyList());
    verify(backstageEnvVariableRepository).delete(envVariableEntity);
    verify(setupUsageProducer).deleteEnvVariableSetupUsage(anyList(), eq(TEST_ACCOUNT_IDENTIFIER));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteNotFound() {
    backstageEnvVariableService.delete(TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteMulti() {
    mockAccountNamespaceMapping();
    BackstageEnvSecretVariableEntity backstageEnvVariableEntity1 = BackstageEnvSecretVariableEntity.builder().build();
    backstageEnvVariableEntity1.setHarnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    BackstageEnvSecretVariableEntity backstageEnvVariableEntity2 = BackstageEnvSecretVariableEntity.builder().build();
    backstageEnvVariableEntity2.setHarnessSecretIdentifier(TEST_SECRET_IDENTIFIER1);
    List<BackstageEnvVariableEntity> secrets = Arrays.asList(backstageEnvVariableEntity1, backstageEnvVariableEntity2);
    List<String> variableIds = Arrays.asList(TEST_SECRET_IDENTIFIER, TEST_SECRET_IDENTIFIER1);
    when(backstageEnvVariableRepository.findAllById(variableIds)).thenReturn(secrets);
    backstageEnvVariableService.deleteMulti(variableIds, TEST_ACCOUNT_IDENTIFIER);
    verify(k8sClient).removeSecretData(eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyList());
    verify(backstageEnvVariableRepository).deleteAllById(variableIds);
    verify(setupUsageProducer).deleteEnvVariableSetupUsage(anyList(), eq(TEST_ACCOUNT_IDENTIFIER));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessSecretUpdate() {
    mockAccountNamespaceMapping();
    BackstageEnvSecretVariable envVariable = new BackstageEnvSecretVariable();
    envVariable.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariable.envName(TEST_ENV_NAME);
    envVariable.type(BackstageEnvVariable.TypeEnum.SECRET);
    BackstageEnvVariableEntity envVariableEntity =
        mapBinder.get(BackstageEnvVariableType.SECRET).fromDto(envVariable, TEST_ACCOUNT_IDENTIFIER);
    when(backstageEnvVariableRepository.findByAccountIdentifierAndHarnessSecretIdentifier(
             TEST_ACCOUNT_IDENTIFIER, TEST_SECRET_IDENTIFIER))
        .thenReturn(Optional.of(envVariableEntity));
    EntityChangeDTO entityChangeDTO = EntityChangeDTO.newBuilder()
                                          .setAccountIdentifier(StringValue.of(TEST_ACCOUNT_IDENTIFIER))
                                          .setIdentifier(StringValue.of(TEST_SECRET_IDENTIFIER))
                                          .build();
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);
    backstageEnvVariableService.processSecretUpdate(entityChangeDTO);
    verify(k8sClient).updateSecretData(eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap(), eq(false));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessSecretUpdateNonIdpSecret() {
    BackstageEnvSecretVariable envVariable = new BackstageEnvSecretVariable();
    envVariable.harnessSecretIdentifier(TEST_SECRET_IDENTIFIER);
    envVariable.envName(TEST_ENV_NAME);
    envVariable.type(BackstageEnvVariable.TypeEnum.SECRET);
    EntityChangeDTO entityChangeDTO = EntityChangeDTO.newBuilder()
                                          .setAccountIdentifier(StringValue.of(TEST_ACCOUNT_IDENTIFIER))
                                          .setIdentifier(StringValue.of(TEST_SECRET_IDENTIFIER))
                                          .build();
    backstageEnvVariableService.processSecretUpdate(entityChangeDTO);
    verify(k8sClient, times(0)).updateSecretData(eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap(), eq(false));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testSyncConfigValue() {
    mockAccountNamespaceMapping();
    BackstageEnvConfigVariable envVariable1 = new BackstageEnvConfigVariable();
    envVariable1.envName(TEST_ENV_NAME);
    envVariable1.setValue(TEST_DECRYPTED_VALUE);
    envVariable1.type(BackstageEnvVariable.TypeEnum.CONFIG);
    backstageEnvVariableService.sync(Collections.singletonList(envVariable1), TEST_ACCOUNT_IDENTIFIER);
    verify(k8sClient).updateSecretData(eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap(), eq(false));
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private void mockAccountNamespaceMapping() {
    NamespaceInfo namespaceInfo = new NamespaceInfo();
    namespaceInfo.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    namespaceInfo.setNamespace(TEST_NAMESPACE);
    when(namespaceService.getNamespaceForAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(namespaceInfo);
  }
}
