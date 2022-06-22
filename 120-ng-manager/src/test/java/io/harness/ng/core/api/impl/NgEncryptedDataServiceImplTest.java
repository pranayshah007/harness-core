package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dao.NGEncryptedDataDao;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secretmanagerclient.remote.SecretManagerClient;
import io.harness.secrets.SecretsFileService;

import software.wings.service.impl.security.GlobalEncryptDecryptClient;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(PL)
public class NgEncryptedDataServiceImplTest extends CategoryTest {
  @Mock private NGEncryptedDataDao encryptedDataDao;
  @Mock private SecretCrudService ngSecretService;
  @Mock private KmsEncryptorsRegistry kmsEncryptorsRegistry;
  @Mock private VaultEncryptorsRegistry vaultEncryptorsRegistry;
  @Mock private SecretsFileService secretsFileService;
  @Mock private GlobalEncryptDecryptClient globalEncryptDecryptClient;
  @Mock private NGConnectorSecretManagerService ngConnectorSecretManagerService;
  @Mock private SecretPermissionValidator secretPermissionValidator;
  @Mock private NGEncryptedDataServiceImpl ngEncryptedDataService;
  @Mock private NGEncryptedDataServiceImpl ngEncryptedDataServiceSpy;
  @Mock private SecretManagerClient secretManagerClient;

  @Before
  public void setup() {
    initMocks(this);
    ngEncryptedDataServiceSpy = new NGEncryptedDataServiceImpl(encryptedDataDao, kmsEncryptorsRegistry,
        vaultEncryptorsRegistry, secretsFileService, secretManagerClient, globalEncryptDecryptClient,
        ngConnectorSecretManagerService, ngSecretService, secretPermissionValidator);
    ngEncryptedDataService = spy(ngEncryptedDataServiceSpy);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testGetEncryptionDetails() {
    VaultConnectorDTO vaultConnectorDTO = getVaultConnectorDTO();
    vaultConnectorDTO.setUseK8sAuth(false);
    vaultConnectorDTO.setUseVaultAgent(false);
    vaultConnectorDTO.setUseAwsIam(false);
    NGAccess ngAccess = BaseNGAccess.builder()
                            .accountIdentifier("accountId")
                            .orgIdentifier("orgId")
                            .projectIdentifier("projectId")
                            .build();
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretText)
                                  .spec(SecretTextSpecDTO.builder().valueType(ValueType.Inline).value("value").build())
                                  .build();
    when(ngSecretService.get(any(), any(), any(), any()))
        .thenReturn(Optional.ofNullable(SecretResponseWrapper.builder().secret(secretDTOV2).build()));
    doNothing().when(secretPermissionValidator).checkForAccessOrThrow(any(), any(), any(), any());
    ngEncryptedDataService.getEncryptionDetails(ngAccess, vaultConnectorDTO);
    verify(secretPermissionValidator, times(1)).checkForAccessOrThrow(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testGetEncryptionDetails_NoAccess() {
    VaultConnectorDTO vaultConnectorDTO = getVaultConnectorDTO();
    vaultConnectorDTO.setUseK8sAuth(false);
    vaultConnectorDTO.setUseVaultAgent(false);
    vaultConnectorDTO.setUseAwsIam(false);
    NGAccess ngAccess = BaseNGAccess.builder()
                            .accountIdentifier("accountId")
                            .orgIdentifier("orgId")
                            .projectIdentifier("projectId")
                            .build();
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretText)
                                  .spec(SecretTextSpecDTO.builder().valueType(ValueType.Inline).value("value").build())
                                  .build();
    when(ngSecretService.get(any(), any(), any(), any()))
        .thenReturn(Optional.ofNullable(SecretResponseWrapper.builder().secret(secretDTOV2).build()));
    doThrow(new NGAccessDeniedException("Not enough permission", USER, Collections.emptyList()))
        .when(secretPermissionValidator)
        .checkForAccessOrThrow(any(), any(), any(), any());
    assertThatThrownBy(() -> ngEncryptedDataService.getEncryptionDetails(ngAccess, vaultConnectorDTO))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessage("Not enough permission");
  }

  private VaultConnectorDTO getVaultConnectorDTO() {
    HashSet<String> delegateSelectors = new HashSet<>();
    delegateSelectors.add(randomAlphabetic(10));
    return VaultConnectorDTO.builder()
        .authToken(SecretRefHelper.createSecretRef(randomAlphabetic(10)))
        .vaultUrl("https://localhost:9090")
        .basePath("harness")
        .isReadOnly(false)
        .isDefault(false)
        .secretEngineManuallyConfigured(false)
        .secretEngineName(randomAlphabetic(10))
        .secretEngineVersion(2)
        .renewalIntervalMinutes(10)
        .delegateSelectors(delegateSelectors)
        .build();
  }
}
