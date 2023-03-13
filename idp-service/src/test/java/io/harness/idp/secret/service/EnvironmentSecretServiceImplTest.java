/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.secret.service;

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
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.secret.beans.entity.EnvironmentSecretEntity;
import io.harness.idp.secret.mappers.EnvironmentSecretMapper;
import io.harness.idp.secret.repositories.EnvironmentSecretRepository;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.spec.server.idp.v1.model.EnvironmentSecret;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;

import com.google.protobuf.StringValue;
import java.util.*;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class EnvironmentSecretServiceImplTest extends CategoryTest {
  static final String TEST_SECRET_VALUE = "e55fa2b3e55fe55f";
  static final String TEST_DECRYPTED_VALUE = "abc123";
  static final String TEST_ENV_NAME = "HARNESS_API_KEY";
  static final String TEST_IDENTIFIER = "accountHarnessKey";
  static final String TEST_SECRET_IDENTIFIER = "accountHarnessKey";
  static final String TEST_SECRET_IDENTIFIER1 = "harnessKey";
  static final String TEST_ACCOUNT_IDENTIFIER = "accountId";
  static final String TEST_NAMESPACE = "namespace";
  AutoCloseable openMocks;
  @Mock EnvironmentSecretRepository environmentSecretRepository;
  @Mock K8sClient k8sClient;
  @Mock NamespaceService namespaceService;
  @Mock SecretManagerClientService ngSecretService;
  @InjectMocks EnvironmentSecretServiceImpl environmentSecretServiceImpl;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFindByIdAndAccountIdentifier() {
    EnvironmentSecretEntity envSecretEntity = EnvironmentSecretEntity.builder().build();
    when(environmentSecretRepository.findByIdAndAccountIdentifier(TEST_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(envSecretEntity));
    Optional<EnvironmentSecret> envSecretOpt =
        environmentSecretServiceImpl.findByIdAndAccountIdentifier(TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    assertTrue(envSecretOpt.isPresent());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testSaveAndSyncK8sSecret() {
    mockAccountNamespaceMapping();
    EnvironmentSecret envSecret = new EnvironmentSecret();
    envSecret.secretIdentifier(TEST_SECRET_IDENTIFIER);
    envSecret.envName(TEST_ENV_NAME);
    EnvironmentSecretEntity envSecretEntity = EnvironmentSecretMapper.fromDTO(envSecret, TEST_ACCOUNT_IDENTIFIER);
    when(environmentSecretRepository.save(envSecretEntity)).thenReturn(envSecretEntity);
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);
    assertEquals(envSecret, environmentSecretServiceImpl.saveAndSyncK8sSecret(envSecret, TEST_ACCOUNT_IDENTIFIER));
    verify(k8sClient).updateSecretData(eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap(), eq(false));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testSaveAndSyncK8sSecrets() {
    mockAccountNamespaceMapping();
    EnvironmentSecret envSecret1 = new EnvironmentSecret();
    envSecret1.secretIdentifier(TEST_SECRET_IDENTIFIER);
    envSecret1.envName(TEST_ENV_NAME);
    EnvironmentSecret envSecret2 = new EnvironmentSecret();
    envSecret2.secretIdentifier(TEST_SECRET_IDENTIFIER);
    envSecret2.envName(TEST_ENV_NAME);
    EnvironmentSecretEntity envSecretEntity1 = EnvironmentSecretMapper.fromDTO(envSecret1, TEST_ACCOUNT_IDENTIFIER);
    EnvironmentSecretEntity envSecretEntity2 = EnvironmentSecretMapper.fromDTO(envSecret2, TEST_ACCOUNT_IDENTIFIER);
    List<EnvironmentSecretEntity> entities = Arrays.asList(envSecretEntity1, envSecretEntity2);
    when(environmentSecretRepository.saveAll(entities)).thenReturn(entities);
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);
    List<EnvironmentSecret> responseSecrets = environmentSecretServiceImpl.saveAndSyncK8sSecrets(
        Arrays.asList(envSecret1, envSecret2), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(envSecret1, responseSecrets.get(0));
    assertEquals(envSecret2, responseSecrets.get(1));
    verify(k8sClient).updateSecretData(eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap(), eq(false));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdateAndSyncK8sSecret() {
    mockAccountNamespaceMapping();
    EnvironmentSecret envSecret = new EnvironmentSecret();
    envSecret.secretIdentifier(TEST_SECRET_IDENTIFIER);
    envSecret.envName(TEST_ENV_NAME);
    EnvironmentSecretEntity envSecretEntity = EnvironmentSecretMapper.fromDTO(envSecret, TEST_ACCOUNT_IDENTIFIER);
    when(environmentSecretRepository.update(envSecretEntity)).thenReturn(envSecretEntity);
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);
    assertEquals(envSecret, environmentSecretServiceImpl.updateAndSyncK8sSecret(envSecret, TEST_ACCOUNT_IDENTIFIER));
    verify(k8sClient).updateSecretData(eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap(), eq(false));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testUpdateAndSyncK8sSecrets() {
    mockAccountNamespaceMapping();
    EnvironmentSecret envSecret1 = new EnvironmentSecret();
    envSecret1.secretIdentifier(TEST_SECRET_IDENTIFIER);
    envSecret1.envName(TEST_ENV_NAME);
    EnvironmentSecret envSecret2 = new EnvironmentSecret();
    envSecret2.secretIdentifier(TEST_SECRET_IDENTIFIER);
    envSecret2.envName(TEST_ENV_NAME);
    EnvironmentSecretEntity envSecretEntity1 = EnvironmentSecretMapper.fromDTO(envSecret1, TEST_ACCOUNT_IDENTIFIER);
    EnvironmentSecretEntity envSecretEntity2 = EnvironmentSecretMapper.fromDTO(envSecret2, TEST_ACCOUNT_IDENTIFIER);
    when(environmentSecretRepository.update(envSecretEntity1)).thenReturn(envSecretEntity1);
    when(environmentSecretRepository.update(envSecretEntity2)).thenReturn(envSecretEntity2);
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);
    List<EnvironmentSecret> responseSecrets = environmentSecretServiceImpl.updateAndSyncK8sSecrets(
        Arrays.asList(envSecret1, envSecret2), TEST_ACCOUNT_IDENTIFIER);
    assertEquals(envSecret1, responseSecrets.get(0));
    assertEquals(envSecret2, responseSecrets.get(1));
    verify(k8sClient).updateSecretData(eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap(), eq(false));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifier() {
    EnvironmentSecretEntity envSecretEntity = EnvironmentSecretEntity.builder().build();
    envSecretEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    when(environmentSecretRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Collections.singletonList(envSecretEntity));
    List<EnvironmentSecret> secrets = environmentSecretServiceImpl.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(1, secrets.size());
    assertEquals(envSecretEntity, EnvironmentSecretMapper.fromDTO(secrets.get(0), TEST_ACCOUNT_IDENTIFIER));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDelete() {
    mockAccountNamespaceMapping();
    EnvironmentSecretEntity environmentSecretEntity = EnvironmentSecretEntity.builder().build();
    when(environmentSecretRepository.findByAccountIdentifierAndSecretIdentifier(
             TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(environmentSecretEntity));
    environmentSecretServiceImpl.delete(TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    verify(k8sClient).removeSecretData(eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyList());
    verify(environmentSecretRepository).delete(environmentSecretEntity);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteNotFound() {
    environmentSecretServiceImpl.delete(TEST_SECRET_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteMulti() {
    mockAccountNamespaceMapping();
    EnvironmentSecretEntity environmentSecretEntity1 = EnvironmentSecretEntity.builder().build();
    environmentSecretEntity1.setSecretIdentifier(TEST_SECRET_IDENTIFIER);
    EnvironmentSecretEntity environmentSecretEntity2 = EnvironmentSecretEntity.builder().build();
    environmentSecretEntity2.setSecretIdentifier(TEST_SECRET_IDENTIFIER1);
    List<EnvironmentSecretEntity> secrets = Arrays.asList(environmentSecretEntity1, environmentSecretEntity2);
    List<String> secretsIds = Arrays.asList(TEST_SECRET_IDENTIFIER, TEST_SECRET_IDENTIFIER1);
    when(environmentSecretRepository.findAllById(secretsIds)).thenReturn(secrets);
    environmentSecretServiceImpl.deleteMulti(secretsIds, TEST_ACCOUNT_IDENTIFIER);
    verify(k8sClient).removeSecretData(eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyList());
    verify(environmentSecretRepository).deleteAllById(secretsIds);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessSecretUpdate() {
    mockAccountNamespaceMapping();
    EnvironmentSecret envSecret = new EnvironmentSecret();
    envSecret.secretIdentifier(TEST_SECRET_IDENTIFIER);
    envSecret.envName(TEST_ENV_NAME);
    EnvironmentSecretEntity envSecretEntity = EnvironmentSecretMapper.fromDTO(envSecret, TEST_ACCOUNT_IDENTIFIER);
    when(environmentSecretRepository.findByAccountIdentifierAndSecretIdentifier(
             TEST_ACCOUNT_IDENTIFIER, TEST_SECRET_IDENTIFIER))
        .thenReturn(Optional.of(envSecretEntity));
    EntityChangeDTO entityChangeDTO = EntityChangeDTO.newBuilder()
                                          .setAccountIdentifier(StringValue.of(TEST_ACCOUNT_IDENTIFIER))
                                          .setIdentifier(StringValue.of(TEST_SECRET_IDENTIFIER))
                                          .build();
    DecryptedSecretValue decryptedSecretValue = DecryptedSecretValue.builder().build();
    decryptedSecretValue.setDecryptedValue(TEST_SECRET_VALUE);
    when(ngSecretService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_SECRET_IDENTIFIER))
        .thenReturn(decryptedSecretValue);
    environmentSecretServiceImpl.processSecretUpdate(entityChangeDTO);
    verify(k8sClient).updateSecretData(eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap(), eq(false));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessSecretUpdateNonIdpSecret() {
    EnvironmentSecret envSecret = new EnvironmentSecret();
    envSecret.secretIdentifier(TEST_SECRET_IDENTIFIER);
    envSecret.envName(TEST_ENV_NAME);
    EntityChangeDTO entityChangeDTO = EntityChangeDTO.newBuilder()
                                          .setAccountIdentifier(StringValue.of(TEST_ACCOUNT_IDENTIFIER))
                                          .setIdentifier(StringValue.of(TEST_SECRET_IDENTIFIER))
                                          .build();
    environmentSecretServiceImpl.processSecretUpdate(entityChangeDTO);
    verify(k8sClient, times(0)).updateSecretData(eq(TEST_NAMESPACE), eq(BACKSTAGE_SECRET), anyMap(), eq(false));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testSyncK8sSecretForDecryptedValue() {
    mockAccountNamespaceMapping();
    EnvironmentSecret envSecret1 = new EnvironmentSecret();
    envSecret1.secretIdentifier(TEST_SECRET_IDENTIFIER);
    envSecret1.envName(TEST_ENV_NAME);
    envSecret1.setDecryptedValue(TEST_DECRYPTED_VALUE);
    environmentSecretServiceImpl.syncK8sSecret(Collections.singletonList(envSecret1), TEST_ACCOUNT_IDENTIFIER);
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
