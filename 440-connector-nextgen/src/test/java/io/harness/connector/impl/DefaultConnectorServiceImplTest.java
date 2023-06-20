/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.connector.ConnectivityStatus.SUCCESS;
import static io.harness.delegate.beans.connector.ConnectorType.KUBERNETES_CLUSTER;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.MANUAL_CREDENTIALS;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;
import static io.harness.rule.OwnerRule.PHOENIKX;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.EntityType;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.ConnectorsTestBase;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.connector.entities.embedded.kubernetescluster.KubernetesClusterConfig;
import io.harness.connector.entities.embedded.localconnector.LocalConnector;
import io.harness.connector.validator.ConnectionValidator;
import io.harness.connector.validator.KubernetesConnectionValidator;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesUserNamePasswordDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.gitsync.clients.YamlGitConfigClient;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.remote.client.CGRestUtils;
import io.harness.repositories.ConnectorRepository;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.FullyQualifiedIdentifierHelper;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(DX)
@Slf4j
public class DefaultConnectorServiceImplTest extends ConnectorsTestBase {
  @Mock KubernetesConnectionValidator kubernetesConnectionValidator;
  @Inject ConnectorRepository connectorRepository;
  @Mock private Map<String, ConnectionValidator> connectionValidatorMap;
  @Mock EntitySetupUsageService entitySetupUsageService;
  @Mock SecretRefInputValidationHelper secretRefInputValidationHelper;
  @Mock ConnectorEntityReferenceHelper connectorEntityReferenceHelper;
  @Mock GitSyncSdkService gitSyncSdkService;
  @Mock YamlGitConfigClient yamlGitConfigClient;
  @Mock NGSettingsClient settingsClient;
  @Mock Call<ResponseDTO<SettingValueResponseDTO>> request;
  @Mock AccountClient accountClient;

  @Mock Call<RestResponse<Boolean>> featureFlagCall1;
  @Mock Call<RestResponse<Boolean>> featureFlagCall2;
  @Spy @Inject @InjectMocks private DefaultConnectorServiceImpl connectorService;
  @Inject MongoTemplate mongoTemplate;

  String userName = "userName";
  String cacertIdentifier = "cacertIdentifier";
  String masterUrl = "https://abc.com";
  String identifier = "identifier";
  String name = "name";
  KubernetesClusterConfig connector;
  String accountIdentifier = "accountIdentifier";
  String orgIdentifier = "orgIdentifier";
  String passwordIdentifier = "passwordIdentifier";
  @Rule public ExpectedException expectedEx = ExpectedException.none();
  SecretRefData passwordSecretRef;
  SecretRefData secretRefDataCACert;
  String updatedName = "updatedName";
  String updatedUserName = "updatedUserName";
  String updatedMasterUrl = "updatedMasterUrl";
  String updatedPasswordIdentifier = "updatedPasswordIdentifier";
  String dummyExceptionMessage = "DUMMY_MESSAGE";
  Pageable pageable;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    secretRefDataCACert = SecretRefData.builder().identifier(cacertIdentifier).scope(Scope.ACCOUNT).build();
    passwordSecretRef = SecretRefData.builder().identifier(passwordIdentifier).scope(Scope.ACCOUNT).build();
    doNothing().when(secretRefInputValidationHelper).validateTheSecretInput(any(), any());
    doReturn(false).when(gitSyncSdkService).isGitSyncEnabled(anyString(), anyString(), anyString());
    pageable = PageUtils.getPageRequest(0, 100, List.of(ConnectorKeys.lastModifiedAt, Sort.Direction.DESC.toString()));
  }

  private ConnectorDTO createKubernetesConnectorRequestDTO(
      String connectorIdentifier, String name, SecretRefData passwordSecretRef) {
    KubernetesAuthDTO kubernetesAuthDTO =
        KubernetesAuthDTO.builder()
            .authType(KubernetesAuthType.USER_PASSWORD)
            .credentials(
                KubernetesUserNamePasswordDTO.builder().username(userName).passwordRef(passwordSecretRef).build())
            .build();
    KubernetesCredentialDTO connectorDTOWithDelegateCreds =
        KubernetesCredentialDTO.builder()
            .kubernetesCredentialType(MANUAL_CREDENTIALS)
            .config(KubernetesClusterDetailsDTO.builder().masterUrl(masterUrl).auth(kubernetesAuthDTO).build())
            .build();
    KubernetesClusterConfigDTO k8sClusterConfig =
        KubernetesClusterConfigDTO.builder().credential(connectorDTOWithDelegateCreds).build();
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .name(name)
                                         .identifier(connectorIdentifier)
                                         .connectorType(KUBERNETES_CLUSTER)
                                         .connectorConfig(k8sClusterConfig)
                                         .build();
    return ConnectorDTO.builder().connectorInfo(connectorInfo).build();
  }

  private ConnectorResponseDTO createConnector(String connectorIdentifier, String name) {
    ConnectorDTO connectorRequestDTO =
        createKubernetesConnectorRequestDTO(connectorIdentifier, name, passwordSecretRef);
    return connectorService.create(connectorRequestDTO, accountIdentifier);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testCreate() {
    ConnectorResponseDTO connectorDTOOutput = createConnector(identifier, name);
    ensureKubernetesConnectorFieldsAreCorrect(connectorDTOOutput);
  }

  @Test
  @Owner(developers = OwnerRule.DEV_MITTAL)
  @Category(UnitTests.class)
  public void testCreateConnectorsWithSameName() {
    ConnectorResponseDTO connectorDTOOutput1 = createConnector(identifier, name);
    ConnectorResponseDTO connectorDTOOutput2 = createConnector("identifier2", name);
    assertThat(connectorDTOOutput2.getConnector().getName()).isEqualTo(name);
    assertThat(connectorDTOOutput2.getConnector().getIdentifier()).isEqualTo("identifier2");
    assertThat(connectorDTOOutput1.getConnector().getName()).isEqualTo(name);
    assertThat(connectorDTOOutput1.getConnector().getIdentifier()).isEqualTo(identifier);
  }

  @Test
  @Owner(developers = OwnerRule.NAMANG)
  @Category(UnitTests.class)
  public void testCreateConnectorsWithInvalidSecretRef() {
    SecretRefData invalidRef = SecretRefData.builder().identifier("").scope(Scope.ACCOUNT).build();
    ConnectorDTO connectorRequestDTO = createKubernetesConnectorRequestDTO("identifier", name, passwordSecretRef);
    Map<String, SecretRefData> secretRefDataMap = new HashMap<>();
    secretRefDataMap.put("fieldName", invalidRef);
    when(secretRefInputValidationHelper.getDecryptableFieldsData(any())).thenReturn(secretRefDataMap);
    InvalidRequestException invalidRequestException = new InvalidRequestException(dummyExceptionMessage);
    doThrow(invalidRequestException).when(secretRefInputValidationHelper).validateTheSecretInput(any(), any());
    assertThatThrownBy(() -> connectorService.create(connectorRequestDTO, accountIdentifier))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Error while validating %s field : %s", "fieldName", ExceptionUtils.getMessage(invalidRequestException)));
  }

  @Test
  @Owner(developers = OwnerRule.DEV_MITTAL)
  @Category(UnitTests.class)
  public void testUpdateConnectorsWithSameName() {
    ConnectorResponseDTO connectorDTOOutput1 = createConnector(identifier, updatedName);
    ConnectorResponseDTO connectorDTOOutput2 = createConnector("identifier2", "name2");
    ConnectorResponseDTO updated = connectorService.update(getUpdatedConnector("identifier2"), accountIdentifier);
    assertThat(updated.getConnector().getName()).isEqualTo(updatedName);
    assertThat(updated.getConnector().getIdentifier()).isEqualTo("identifier2");
    assertThat(connectorDTOOutput1.getConnector().getName()).isEqualTo(updatedName);
    assertThat(connectorDTOOutput1.getConnector().getIdentifier()).isEqualTo(identifier);
  }

  @Test
  @Owner(developers = OwnerRule.NAMANG)
  @Category(UnitTests.class)
  public void testUpdateConnectorsWithInvalidSecretRef() {
    SecretRefData invalidRef = SecretRefData.builder().identifier("").scope(Scope.ACCOUNT).build();
    ConnectorDTO connectorRequestDTO = createKubernetesConnectorRequestDTO("identifier", name, passwordSecretRef);
    Map<String, SecretRefData> secretRefDataMap = new HashMap<>();
    secretRefDataMap.put("fieldName", invalidRef);
    when(secretRefInputValidationHelper.getDecryptableFieldsData(any())).thenReturn(secretRefDataMap);
    InvalidRequestException invalidRequestException = new InvalidRequestException(dummyExceptionMessage);
    doThrow(invalidRequestException).when(secretRefInputValidationHelper).validateTheSecretInput(any(), any());
    assertThatThrownBy(() -> connectorService.update(connectorRequestDTO, accountIdentifier))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Error while validating %s field : %s", "fieldName", ExceptionUtils.getMessage(invalidRequestException)));
  }

  @Test
  @Owner(developers = OwnerRule.DEV_MITTAL)
  @Category(UnitTests.class)
  public void testCreateConnectorsWithSameId() {
    ConnectorResponseDTO connectorDTOOutput1 = createConnector(identifier, name);
    assertThatThrownBy(() -> createConnector(identifier, name)).isExactlyInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> createConnector(identifier, "differentName"))
        .isExactlyInstanceOf(InvalidRequestException.class);
  }

  private ConnectorDTO getUpdatedConnector(String identifier) {
    SecretRefData passwordRefData =
        SecretRefData.builder().identifier(updatedPasswordIdentifier).scope(Scope.ACCOUNT).build();
    KubernetesAuthDTO kubernetesAuthDTO =
        KubernetesAuthDTO.builder()
            .authType(KubernetesAuthType.USER_PASSWORD)
            .credentials(
                KubernetesUserNamePasswordDTO.builder().username(updatedUserName).passwordRef(passwordRefData).build())
            .build();
    KubernetesCredentialDTO connectorDTOWithUserNamePwdCreds =
        KubernetesCredentialDTO.builder()
            .kubernetesCredentialType(MANUAL_CREDENTIALS)
            .config(KubernetesClusterDetailsDTO.builder().masterUrl(updatedMasterUrl).auth(kubernetesAuthDTO).build())
            .build();
    KubernetesClusterConfigDTO k8sClusterConfig =
        KubernetesClusterConfigDTO.builder().credential(connectorDTOWithUserNamePwdCreds).build();
    return ConnectorDTO.builder()
        .connectorInfo(ConnectorInfoDTO.builder()
                           .name(updatedName)
                           .identifier(identifier)
                           .connectorType(KUBERNETES_CLUSTER)
                           .connectorConfig(k8sClusterConfig)
                           .build())
        .build();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testUpdate() {
    SecretRefData passwordRefData =
        SecretRefData.builder().identifier(updatedPasswordIdentifier).scope(Scope.ACCOUNT).build();
    createConnector(identifier, name);
    ConnectorResponseDTO connectorResponse =
        connectorService.update(getUpdatedConnector(identifier), accountIdentifier);
    ConnectorInfoDTO connectorInfo = connectorResponse.getConnector();
    assertThat(connectorInfo).isNotNull();
    assertThat(connectorInfo.getName()).isEqualTo(updatedName);
    assertThat(connectorInfo.getIdentifier()).isEqualTo(identifier);
    assertThat(connectorInfo.getConnectorType()).isEqualTo(KUBERNETES_CLUSTER);
    KubernetesClusterConfigDTO kubernetesCluster = (KubernetesClusterConfigDTO) connectorInfo.getConnectorConfig();
    assertThat(kubernetesCluster).isNotNull();
    assertThat(kubernetesCluster.getCredential().getConfig()).isNotNull();
    assertThat(kubernetesCluster.getCredential().getKubernetesCredentialType()).isEqualTo(MANUAL_CREDENTIALS);
    KubernetesClusterDetailsDTO credentialDTO =
        (KubernetesClusterDetailsDTO) kubernetesCluster.getCredential().getConfig();
    assertThat(credentialDTO).isNotNull();
    assertThat(credentialDTO.getMasterUrl()).isNotNull();
    assertThat(credentialDTO.getMasterUrl()).isEqualTo(updatedMasterUrl);
    assertThat(credentialDTO.getAuth()).isNotNull();
    KubernetesUserNamePasswordDTO userNamePasswordDTO =
        (KubernetesUserNamePasswordDTO) credentialDTO.getAuth().getCredentials();
    assertThat(userNamePasswordDTO.getUsername()).isEqualTo(updatedUserName);
    assertThat(userNamePasswordDTO.getPasswordRef()).isEqualTo(passwordRefData);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category({UnitTests.class})
  public void testList() throws IOException {
    String connectorIdentifier1 = "connectorIdentifier1";
    String connectorIdentifier2 = "connectorIdentifier2";
    String connectorIdentifier3 = "connectorIdentifier3";
    createConnector(connectorIdentifier1, name + "1");
    createConnector(connectorIdentifier2, name + "2");
    createConnector(connectorIdentifier3, name + "3");
    ArgumentCaptor<Page> connectorsListArgumentCaptor = ArgumentCaptor.forClass(Page.class);
    mockStatic(CGRestUtils.class);
    when(CGRestUtils.getResponse(any())).thenReturn(true);
    when(settingsClient.getSetting(
             SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER, accountIdentifier, null, null))
        .thenReturn(request);
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("false").valueType(SettingValueType.BOOLEAN).build();
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));
    Page<ConnectorResponseDTO> connectorSummaryDTOSList =
        connectorService.list(accountIdentifier, null, null, null, "", "", false, false, pageable);
    assertThat(connectorSummaryDTOSList.getTotalElements()).isEqualTo(3);
    List<String> connectorIdentifierList =
        connectorSummaryDTOSList.stream()
            .map(connectorSummaryDTO -> connectorSummaryDTO.getConnector().getIdentifier())
            .collect(toList());
    assertThat(connectorIdentifierList).contains(connectorIdentifier1);
    assertThat(connectorIdentifierList).contains(connectorIdentifier2);
    assertThat(connectorIdentifierList).contains(connectorIdentifier3);
  }

  @Test
  @Owner(developers = OwnerRule.NISHANT)
  @Category({UnitTests.class})
  public void testListByFqn() {
    String connectorIdentifier1 = "connectorIdentifier1";
    String connectorIdentifier2 = "connectorIdentifier2";
    String connectorIdentifier3 = "connectorIdentifier3";
    ConnectorResponseDTO connector1 = createConnector(connectorIdentifier1, name + "1");
    ConnectorResponseDTO connector2 = createConnector(connectorIdentifier2, name + "2");
    ConnectorResponseDTO connector3 = createConnector(connectorIdentifier3, name + "3");
    List<String> fqns = new ArrayList<>();
    fqns.add(FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(accountIdentifier,
        connector1.getConnector().getOrgIdentifier(), connector1.getConnector().getProjectIdentifier(),
        connectorIdentifier1));
    fqns.add(FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(accountIdentifier,
        connector2.getConnector().getOrgIdentifier(), connector2.getConnector().getProjectIdentifier(),
        connectorIdentifier2));
    fqns.add(FullyQualifiedIdentifierHelper.getFullyQualifiedIdentifier(accountIdentifier,
        connector3.getConnector().getOrgIdentifier(), connector3.getConnector().getProjectIdentifier(),
        connectorIdentifier3));
    List<ConnectorResponseDTO> connectorSummaryDTOSList = connectorService.listbyFQN(accountIdentifier, fqns);
    assertThat(connectorSummaryDTOSList.size()).isEqualTo(3);
    List<String> connectorIdentifierList =
        connectorSummaryDTOSList.stream()
            .map(connectorSummaryDTO -> connectorSummaryDTO.getConnector().getIdentifier())
            .collect(toList());
    assertThat(connectorIdentifierList).contains(connectorIdentifier1);
    assertThat(connectorIdentifierList).contains(connectorIdentifier2);
    assertThat(connectorIdentifierList).contains(connectorIdentifier3);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testGet() {
    createConnector(identifier, name);
    ConnectorResponseDTO connectorDTO = connectorService.get(accountIdentifier, null, null, identifier).get();
    ensureKubernetesConnectorFieldsAreCorrect(connectorDTO);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testMarkConnectorInvalid() {
    createConnector(identifier, name);
    IdentifierRef identifierRef = IdentifierRef.builder().identifier(identifier).build();
    connectorService.markEntityInvalid(accountIdentifier, identifierRef, "xyz");
    Optional<ConnectorResponseDTO> connectorResponseDTO =
        connectorService.get(accountIdentifier, null, null, identifier);
    assertThat(connectorResponseDTO).isPresent();
    assertThat(connectorResponseDTO.get().getEntityValidityDetails().isValid()).isFalse();
    assertThat(connectorResponseDTO.get().getEntityValidityDetails().getInvalidYaml()).isEqualTo("xyz");
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testGetWhenConnectorDoesntExists() {
    createConnector(identifier, name);
    Optional<ConnectorResponseDTO> connectorDTO =
        connectorService.get(accountIdentifier, "orgIdentifier", "projectIdentifier", identifier);
    assertThat(connectorDTO.isPresent()).isFalse();
  }

  private void ensureKubernetesConnectorFieldsAreCorrect(ConnectorResponseDTO connectorResponse) {
    ConnectorInfoDTO connectorInfo = connectorResponse.getConnector();
    assertThat(connectorInfo).isNotNull();
    assertThat(connectorInfo.getName()).isEqualTo(name);
    assertThat(connectorInfo.getIdentifier()).isEqualTo(identifier);
    assertThat(connectorInfo.getConnectorType()).isEqualTo(KUBERNETES_CLUSTER);
    KubernetesClusterConfigDTO kubernetesCluster = (KubernetesClusterConfigDTO) connectorInfo.getConnectorConfig();
    assertThat(kubernetesCluster).isNotNull();
    assertThat(kubernetesCluster.getCredential().getConfig()).isNotNull();
    assertThat(kubernetesCluster.getCredential().getKubernetesCredentialType()).isEqualTo(MANUAL_CREDENTIALS);
    KubernetesClusterDetailsDTO credentialDTO =
        (KubernetesClusterDetailsDTO) kubernetesCluster.getCredential().getConfig();
    assertThat(credentialDTO).isNotNull();
    assertThat(credentialDTO.getMasterUrl()).isNotNull();
    KubernetesUserNamePasswordDTO kubernetesUserNamePasswordDTO =
        (KubernetesUserNamePasswordDTO) credentialDTO.getAuth().getCredentials();
    assertThat(kubernetesUserNamePasswordDTO.getUsername()).isEqualTo(userName);
    assertThat(kubernetesUserNamePasswordDTO.getPasswordRef()).isEqualTo(passwordSecretRef);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testDelete() {
    createConnector(identifier, name);

    when(entitySetupUsageService.isEntityReferenced(any(), any(), any())).thenReturn(false);
    boolean deleted = connectorService.delete(accountIdentifier, null, null, identifier, false);
    verify(entitySetupUsageService, times(1)).isEntityReferenced(anyString(), anyString(), any(EntityType.class));
    assertThat(deleted).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testDelete_forceDeleteTrue_forceDeleteFFON_settingsFFOFF() {
    doReturn(true).when(connectorService).isForceDeleteFFEnabled(accountIdentifier);
    doReturn(true).when(connectorService).isForceDeleteFFEnabledViaSettings(accountIdentifier);

    createConnector(identifier, name);
    try {
      connectorService.delete(accountIdentifier, null, null, identifier, true);
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage())
          .isEqualTo(
              "Parameter forcedDelete cannot be true. Force Delete is not enabled for account [accountIdentifier]");
    }
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testDelete_forceDeleteTrue_forceDeleteFFON_settingsFFON_settingsDisabled() {
    doReturn(true).when(connectorService).isForceDeleteFFEnabled(accountIdentifier);
    doReturn(false).when(connectorService).isForceDeleteFFEnabledViaSettings(accountIdentifier);
    createConnector(identifier, name);
    try {
      connectorService.delete(accountIdentifier, null, null, identifier, true);
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage())
          .isEqualTo(
              "Parameter forcedDelete cannot be true. Force Delete is not enabled for account [accountIdentifier]");
    }
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testDelete_forceDeleteTrue_forceDeleteFFOFF_settingsFFON_settingsDisabled() {
    doReturn(false).when(connectorService).isForceDeleteFFEnabled(accountIdentifier);
    doReturn(false).when(connectorService).isForceDeleteFFEnabledViaSettings(accountIdentifier);
    createConnector(identifier, name);
    try {
      connectorService.delete(accountIdentifier, null, null, identifier, true);
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage())
          .isEqualTo(
              "Parameter forcedDelete cannot be true. Force Delete is not enabled for account [accountIdentifier]");
    }
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testDelete_forceDeleteTrue_forceDeleteFFOFF_settingsFFON_settingsEnabled() {
    doReturn(false).when(connectorService).isForceDeleteFFEnabled(accountIdentifier);
    doReturn(true).when(connectorService).isForceDeleteFFEnabledViaSettings(accountIdentifier);
    createConnector(identifier, name);
    try {
      connectorService.delete(accountIdentifier, null, null, identifier, true);
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage())
          .isEqualTo(
              "Parameter forcedDelete cannot be true. Force Delete is not enabled for account [accountIdentifier]");
    }
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testDelete_withForceDeleteAsTrue() {
    doReturn(true).when(connectorService).isForceDeleteFFEnabled(accountIdentifier);
    doReturn(true).when(connectorService).isForceDeleteFFEnabledViaSettings(accountIdentifier);
    createConnector(identifier, name);
    doNothing()
        .when(connectorEntityReferenceHelper)
        .deleteExistingSetupUsages(accountIdentifier, null, null, identifier);
    boolean deleted = connectorService.delete(accountIdentifier, null, null, identifier, true);
    verify(entitySetupUsageService, times(0)).isEntityReferenced(anyString(), anyString(), any(EntityType.class));
    assertThat(deleted).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category(UnitTests.class)
  public void testDelete_withForceDeleteAsTrue_throwsException() {
    doReturn(true).when(connectorService).isForceDeleteFFEnabled(accountIdentifier);
    doReturn(true).when(connectorService).isForceDeleteFFEnabledViaSettings(accountIdentifier);
    createConnector(identifier, name);
    doThrow(RuntimeException.class)
        .when(connectorEntityReferenceHelper)
        .deleteExistingSetupUsages(accountIdentifier, null, null, identifier);
    boolean deleted = connectorService.delete(accountIdentifier, null, null, identifier, true);
    assertThat(deleted).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.VIKAS_M)
  @Category(UnitTests.class)
  public void testDeleteWithEntitiesReferenced_throwsException() {
    createConnector(identifier, name);
    when(entitySetupUsageService.isEntityReferenced(any(), any(), any())).thenReturn(false);
    try {
      connectorService.delete(accountIdentifier, null, null, identifier, false);
    } catch (ReferencedEntityException e) {
      assertThat(e.getMessage())
          .isEqualTo("Could not delete the connector identifier as it is referenced by other entities");
    }
    verify(entitySetupUsageService, times(1)).isEntityReferenced(anyString(), anyString(), any(EntityType.class));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testDeleteWhenConnectorDoesNotExists() {
    boolean deleted = connectorService.delete(accountIdentifier, null, null, identifier, false);
    assertThat(deleted).isFalse();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testValidate() {
    String userName = "userName";
    String cacert = "caCertRef";
    String masterUrl = "https://abc.com";
    String identifier = "identifier";
    String name = "name";
    SecretRefData secretRefDataCACert = SecretRefData.builder().identifier(cacert).scope(Scope.ACCOUNT).build();
    createConnector(identifier, name);
    KubernetesAuthDTO kubernetesAuthDTO =
        KubernetesAuthDTO.builder()
            .authType(KubernetesAuthType.USER_PASSWORD)
            .credentials(
                KubernetesUserNamePasswordDTO.builder()
                    .username(userName)
                    .passwordRef(SecretRefData.builder().identifier(passwordIdentifier).scope(Scope.ACCOUNT).build())
                    .build())
            .build();
    KubernetesCredentialDTO connectorDTOWithDelegateCreds =
        KubernetesCredentialDTO.builder()
            .kubernetesCredentialType(MANUAL_CREDENTIALS)
            .config(KubernetesClusterDetailsDTO.builder().masterUrl(masterUrl).auth(kubernetesAuthDTO).build())
            .build();
    KubernetesClusterConfigDTO k8sClusterConfig =
        KubernetesClusterConfigDTO.builder().credential(connectorDTOWithDelegateCreds).build();
    ConnectorDTO connectorRequestDTO = ConnectorDTO.builder()
                                           .connectorInfo(ConnectorInfoDTO.builder()
                                                              .name(name)
                                                              .identifier(identifier)
                                                              .connectorType(KUBERNETES_CLUSTER)
                                                              .connectorConfig(k8sClusterConfig)
                                                              .build())
                                           .build();

    when(connectionValidatorMap.get(any())).thenReturn(kubernetesConnectionValidator);
    when(kubernetesConnectionValidator.validate(
             (ConnectorConfigDTO) any(), anyString(), any(), anyString(), anyString()))
        .thenReturn(null);
    connectorService.validate(connectorRequestDTO, accountIdentifier);
    verify(kubernetesConnectionValidator, times(1))
        .validate((ConnectorConfigDTO) any(), anyString(), any(), any(), anyString());
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testConnection() {
    createConnector(identifier, name);
    when(connectionValidatorMap.get(any())).thenReturn(kubernetesConnectionValidator);
    when(kubernetesConnectionValidator.validate(
             (ConnectorConfigDTO) any(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(ConnectorValidationResult.builder().status(SUCCESS).build());
    connectorService.testConnection(accountIdentifier, null, null, identifier);
    verify(kubernetesConnectionValidator, times(1))
        .validate((ConnectorConfigDTO) any(), anyString(), any(), any(), anyString());
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testValidateTheIdentifierIsUnique() {
    createConnector(identifier, name);
    boolean isIdentifierUnique =
        connectorService.validateTheIdentifierIsUnique(accountIdentifier, null, null, identifier);
    assertThat(isIdentifierUnique).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.ABHINAV)
  @Category({UnitTests.class})
  public void testListWithBranchesFlag() throws IOException {
    String connectorIdentifier1 = "connectorIdentifier1";
    String connectorIdentifier2 = "connectorIdentifier2";
    String connectorIdentifier3 = "connectorIdentifier3";
    createConnector(connectorIdentifier1, name + "1");
    createConnector(connectorIdentifier2, name + "2");
    createConnector(connectorIdentifier3, name + "3");
    doReturn(true).when(gitSyncSdkService).isGitSyncEnabled(anyString(), anyString(), anyString());
    mockStatic(CGRestUtils.class);
    when(CGRestUtils.getResponse(any())).thenReturn(true);
    when(settingsClient.getSetting(
             SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER, accountIdentifier, null, null))
        .thenReturn(request);
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("false").valueType(SettingValueType.BOOLEAN).build();
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));
    Page<ConnectorResponseDTO> connectorSummaryDTOSList =
        connectorService.list(accountIdentifier, null, null, null, "", "", false, true, pageable);
    assertThat(connectorSummaryDTOSList.getTotalElements()).isEqualTo(3);
    List<String> connectorIdentifierList =
        connectorSummaryDTOSList.stream()
            .map(connectorSummaryDTO -> connectorSummaryDTO.getConnector().getIdentifier())
            .collect(toList());
    assertThat(connectorIdentifierList).contains(connectorIdentifier1);
    assertThat(connectorIdentifierList).contains(connectorIdentifier2);
    assertThat(connectorIdentifierList).contains(connectorIdentifier3);
    Pageable pageable2 =
        PageUtils.getPageRequest(1, 2, List.of(ConnectorKeys.lastModifiedAt, Sort.Direction.DESC.toString()));
    Pageable pageable3 =
        PageUtils.getPageRequest(0, 2, List.of(ConnectorKeys.lastModifiedAt, Sort.Direction.DESC.toString()));
    Page<ConnectorResponseDTO> connectorSummaryDTOSList_1 =
        connectorService.list(accountIdentifier, null, null, null, "", "", false, true, pageable3);
    assertThat(connectorSummaryDTOSList_1.get().count()).isEqualTo(2L);
    Page<ConnectorResponseDTO> connectorSummaryDTOSList_2 =
        connectorService.list(accountIdentifier, null, null, null, "", "", false, true, pageable2);
    assertThat(connectorSummaryDTOSList_2.get().count()).isEqualTo(1L);
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category({UnitTests.class})
  public void testListWhenDefaultSMIsDisabled() throws IOException {
    String connectorIdentifier1 = "harnessSecretManger";
    String connectorIdentifier2 = "connectorIdentifier2";
    String connectorIdentifier3 = "connectorIdentifier3";

    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));
    when(settingsClient.getSetting(
             SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER, accountIdentifier, null, null))
        .thenReturn(request);

    final Connector connector = LocalConnector.builder().harnessManaged(true).isDefault(false).build();
    connector.setAccountIdentifier(accountIdentifier);
    connector.setIdentifier(connectorIdentifier1);
    connector.setName("Harness Vault");
    connector.setScope(Scope.ACCOUNT);
    connector.setFullyQualifiedIdentifier(accountIdentifier + "/" + connectorIdentifier1);
    connector.setType(ConnectorType.LOCAL);

    mongoTemplate.save(connector);
    createConnector(connectorIdentifier2, name + "2");
    createConnector(connectorIdentifier3, name + "3");
    mockStatic(CGRestUtils.class);
    when(CGRestUtils.getResponse(any())).thenReturn(true);
    Page<ConnectorResponseDTO> connectorSummaryDTOSList =
        connectorService.list(accountIdentifier, null, null, null, "", "", false, false, pageable);
    assertThat(connectorSummaryDTOSList.getTotalElements()).isEqualTo(2);
    List<String> connectorIdentifierList =
        connectorSummaryDTOSList.stream()
            .map(connectorSummaryDTO -> connectorSummaryDTO.getConnector().getIdentifier())
            .collect(toList());
    assertThat(connectorIdentifierList).doesNotContain(connectorIdentifier1);
    assertThat(connectorIdentifierList).contains(connectorIdentifier2);
    assertThat(connectorIdentifierList).contains(connectorIdentifier3);
  }

  @Test
  @Owner(developers = OwnerRule.MEENAKSHI)
  @Category({UnitTests.class})
  public void testListWhenDefaultSMIsEnabled() throws IOException {
    String connectorIdentifier1 = "harnessSecretManger";
    String connectorIdentifier2 = "connectorIdentifier2";
    String connectorIdentifier3 = "connectorIdentifier3";

    final Connector connector = LocalConnector.builder().harnessManaged(true).isDefault(false).build();
    connector.setAccountIdentifier(accountIdentifier);
    connector.setIdentifier(connectorIdentifier1);
    connector.setName("Harness Vault");
    connector.setScope(Scope.ACCOUNT);
    connector.setFullyQualifiedIdentifier(accountIdentifier + "/" + connectorIdentifier1);
    connector.setType(ConnectorType.LOCAL);
    mongoTemplate.save(connector);
    createConnector(connectorIdentifier2, name + "2");
    createConnector(connectorIdentifier3, name + "3");
    mockStatic(CGRestUtils.class);
    when(CGRestUtils.getResponse(any())).thenReturn(true);
    when(settingsClient.getSetting(
             SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER, accountIdentifier, null, null))
        .thenReturn(request);
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("false").valueType(SettingValueType.BOOLEAN).build();
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));
    Page<ConnectorResponseDTO> connectorSummaryDTOSList =
        connectorService.list(accountIdentifier, null, null, null, "", "", false, false, pageable);
    assertThat(connectorSummaryDTOSList.getTotalElements()).isEqualTo(3);
    List<String> connectorIdentifierList =
        connectorSummaryDTOSList.stream()
            .map(connectorSummaryDTO -> connectorSummaryDTO.getConnector().getIdentifier())
            .collect(toList());
    assertThat(connectorIdentifierList).contains(connectorIdentifier1);
    assertThat(connectorIdentifierList).contains(connectorIdentifier2);
    assertThat(connectorIdentifierList).contains(connectorIdentifier3);
  }

  @Test
  @Owner(developers = OwnerRule.MOHIT_GARG)
  @Category({UnitTests.class})
  public void testGetConnectorByRef() {
    createConnector(identifier, name);
    Optional<ConnectorResponseDTO> connectorResponseDTOOptional =
        connectorService.getByRef(accountIdentifier, null, null, "account." + identifier);
    assertThat(connectorResponseDTOOptional.isPresent()).isTrue();
    assertThat(connectorResponseDTOOptional.get().getConnector().getName()).isEqualTo(name);
    assertThat(connectorResponseDTOOptional.get().getConnector().getIdentifier()).isEqualTo(identifier);
    assertThat(connectorResponseDTOOptional.get().getConnector().getOrgIdentifier()).isEqualTo(null);
    assertThat(connectorResponseDTOOptional.get().getConnector().getProjectIdentifier()).isEqualTo(null);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category({UnitTests.class})
  public void testGetConnectorByRefWithIncorrectScope() {
    createConnector(identifier, name);
    assertThatThrownBy(() -> connectorService.getByRef(accountIdentifier, null, null, identifier))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The project level connector cannot be used at account level. Ref: [identifier]");

    assertThatThrownBy(() -> connectorService.getByRef(accountIdentifier, orgIdentifier, null, identifier))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The project level connector cannot be used at org level. Ref: [identifier]");

    assertThatThrownBy(() -> connectorService.getByRef(accountIdentifier, null, null, "org." + identifier))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("The org level connector cannot be used at account level. Ref: [org.identifier]");

    Optional<ConnectorResponseDTO> connectorResponseDTOOptional =
        connectorService.getByRef(accountIdentifier, null, null, "account." + identifier);
    assertThat(connectorResponseDTOOptional.isPresent()).isTrue();
    assertThat(connectorResponseDTOOptional.get().getConnector().getName()).isEqualTo(name);
    assertThat(connectorResponseDTOOptional.get().getConnector().getIdentifier()).isEqualTo(identifier);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void listAll_Connectors_ReturnsAllConnectors() {
    createConnector(identifier, name);
    Page<Connector> connectors = connectorService.listAll(accountIdentifier, null, null);
    assertThat(connectors.getTotalElements()).isEqualTo(1);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void listAllByConnectorType_ReturnsAllByConnectorType() throws IOException {
    String connectorIdentifier1 = "harnessSecretManger";
    String connectorIdentifier2 = "connectorIdentifier2";
    String connectorIdentifier3 = "connectorIdentifier3";

    final Connector connector = LocalConnector.builder().harnessManaged(true).isDefault(false).build();
    connector.setAccountIdentifier(accountIdentifier);
    connector.setIdentifier(connectorIdentifier1);
    connector.setName("Harness Vault");
    connector.setScope(Scope.ACCOUNT);
    connector.setFullyQualifiedIdentifier(accountIdentifier + "/" + connectorIdentifier1);
    connector.setType(ConnectorType.LOCAL);
    mongoTemplate.save(connector);
    createConnector(connectorIdentifier2, name + "2");
    createConnector(connectorIdentifier3, name + "3");
    mockStatic(CGRestUtils.class);
    when(CGRestUtils.getResponse(any())).thenReturn(true);
    when(settingsClient.getSetting(
             SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER, accountIdentifier, null, null))
        .thenReturn(request);
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("false").valueType(SettingValueType.BOOLEAN).build();
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));
    Page<Connector> connectors =
        connectorService.listAll(0, 10, accountIdentifier, null, null, null, ConnectorType.LOCAL, null, null, null);
    assertThat(connectors.getTotalElements()).isEqualTo(1);
    List<String> connectorIdentifierList = connectors.stream().map(con -> con.getIdentifier()).collect(toList());
    assertThat(connectorIdentifierList).contains(connectorIdentifier1);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void listAllByFilter_ReturnsAllByFilter() throws IOException {
    String connectorIdentifier1 = "harnessSecretManger";
    String connectorIdentifier2 = "connectorIdentifier2";
    String connectorIdentifier3 = "connectorIdentifier3";

    final Connector connector = LocalConnector.builder().harnessManaged(true).isDefault(false).build();
    connector.setAccountIdentifier(accountIdentifier);
    connector.setIdentifier(connectorIdentifier1);
    connector.setName("Harness Vault");
    connector.setScope(Scope.ACCOUNT);
    connector.setFullyQualifiedIdentifier(accountIdentifier + "/" + connectorIdentifier1);
    connector.setType(ConnectorType.LOCAL);
    mongoTemplate.save(connector);
    createConnector(connectorIdentifier2, name + "2");
    createConnector(connectorIdentifier3, name + "3");
    mockStatic(CGRestUtils.class);
    when(CGRestUtils.getResponse(any())).thenReturn(true);
    when(settingsClient.getSetting(
             SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER, accountIdentifier, null, null))
        .thenReturn(request);
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("false").valueType(SettingValueType.BOOLEAN).build();
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));
    ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO =
        ConnectorFilterPropertiesDTO.builder().connectorNames(List.of("Harness Vault")).build();
    Pageable pageable = Pageable.ofSize(10);
    Page<Connector> connectors = connectorService.listAll(accountIdentifier, connectorFilterPropertiesDTO, null, null,
        null, null, Boolean.FALSE, Boolean.FALSE, pageable, null);
    assertThat(connectors.getTotalElements()).isEqualTo(1);
    List<String> connectorIdentifierList = connectors.stream().map(con -> con.getIdentifier()).collect(toList());
    assertThat(connectorIdentifierList).contains(connectorIdentifier1);
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void listAllConnectorsByConnectorIds_ReturnsAllConnectorsByFilterIds() throws IOException {
    String connectorIdentifier1 = "harnessSecretManger";
    String connectorIdentifier2 = "connectorIdentifier2";
    String connectorIdentifier3 = "connectorIdentifier3";

    final Connector connector = LocalConnector.builder().harnessManaged(true).isDefault(false).build();
    connector.setAccountIdentifier(accountIdentifier);
    connector.setIdentifier(connectorIdentifier1);
    connector.setName("Harness Vault");
    connector.setScope(Scope.ACCOUNT);
    connector.setFullyQualifiedIdentifier(accountIdentifier + "/" + connectorIdentifier1);
    connector.setType(ConnectorType.LOCAL);
    Connector savedConnector = mongoTemplate.save(connector);
    createConnector(connectorIdentifier2, name + "2");
    createConnector(connectorIdentifier3, name + "3");
    mockStatic(CGRestUtils.class);
    when(CGRestUtils.getResponse(any())).thenReturn(true);
    when(settingsClient.getSetting(
             SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER, accountIdentifier, null, null))
        .thenReturn(request);
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("false").valueType(SettingValueType.BOOLEAN).build();
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));
    List<String> connectorIds = List.of(savedConnector.getId());
    Page<ConnectorResponseDTO> connectorSummaryDTOSList =
        connectorService.list(0, 10, accountIdentifier, null, null, null, null, null, null, null, connectorIds);
    assertThat(connectorSummaryDTOSList.getTotalElements()).isEqualTo(1);
    List<String> connectorIdentifierList =
        connectorSummaryDTOSList.stream()
            .map(connectorSummaryDTO -> connectorSummaryDTO.getConnector().getIdentifier())
            .collect(toList());
    assertThat(connectorIdentifierList).contains(connectorIdentifier1);
  }

  @Test
  @Owner(developers = OwnerRule.JIMIT_GANDHI)
  @Category({UnitTests.class})
  public void listAllConnectorsByIdentifier_ReturnsAllConnectorsByIdentifiers() throws IOException {
    String connectorIdentifier1 = "harnessSecretManger";
    String connectorIdentifier2 = "connectorIdentifier2";
    String connectorIdentifier3 = "connectorIdentifier3";

    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));
    when(settingsClient.getSetting(
             SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER, accountIdentifier, null, null))
        .thenReturn(request);

    final Connector connector = LocalConnector.builder().harnessManaged(true).isDefault(false).build();
    connector.setAccountIdentifier(accountIdentifier);
    connector.setIdentifier(connectorIdentifier1);
    connector.setName("Harness Vault");
    connector.setScope(Scope.ACCOUNT);
    connector.setFullyQualifiedIdentifier(accountIdentifier + "/" + connectorIdentifier1);
    connector.setType(ConnectorType.LOCAL);

    mongoTemplate.save(connector);
    createConnector(connectorIdentifier2, name + "2");
    createConnector(connectorIdentifier3, name + "3");
    mockStatic(CGRestUtils.class);
    when(CGRestUtils.getResponse(any())).thenReturn(true);

    ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO =
        ConnectorFilterPropertiesDTO.builder().connectorIdentifiers(List.of(connectorIdentifier2)).build();
    Pageable pageable = Pageable.ofSize(10);

    Page<ConnectorResponseDTO> connectorSummaryDTOSList = connectorService.list(
        accountIdentifier, connectorFilterPropertiesDTO, null, null, "", "", false, false, pageable, null, false);
    assertThat(connectorSummaryDTOSList.getTotalElements()).isEqualTo(1);
    List<String> connectorIdentifierList =
        connectorSummaryDTOSList.stream()
            .map(connectorSummaryDTO -> connectorSummaryDTO.getConnector().getIdentifier())
            .collect(toList());
    assertThat(connectorIdentifierList).contains(connectorIdentifier2);
  }
}
