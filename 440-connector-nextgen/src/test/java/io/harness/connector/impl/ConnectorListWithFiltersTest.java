/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.connector.ConnectorCategory.ARTIFACTORY;
import static io.harness.connector.ConnectorCategory.CLOUD_PROVIDER;
import static io.harness.connector.impl.ConnectorFilterServiceImpl.CREDENTIAL_TYPE_KEY;
import static io.harness.connector.impl.ConnectorFilterServiceImpl.INHERIT_FROM_DELEGATE_STRING;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.connector.ConnectorType.AWS;
import static io.harness.delegate.beans.connector.ConnectorType.DOCKER;
import static io.harness.delegate.beans.connector.ConnectorType.GCP;
import static io.harness.delegate.beans.connector.ConnectorType.KUBERNETES_CLUSTER;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.MANUAL_CREDENTIALS;
import static io.harness.encryption.Scope.ACCOUNT;
import static io.harness.encryption.SecretRefData.SECRET_DOT_DELIMINITER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorConnectivityDetails;
import io.harness.connector.ConnectorConnectivityMode;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorFilterPropertiesDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorsTestBase;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.connector.entities.embedded.awsconnector.AwsConfig.AwsConfigKeys;
import io.harness.connector.entities.embedded.gcpconnector.GcpConfig.GcpConfigKeys;
import io.harness.connector.entities.embedded.kubernetescluster.K8sUserNamePassword;
import io.harness.connector.entities.embedded.kubernetescluster.KubernetesClusterConfig;
import io.harness.connector.entities.embedded.kubernetescluster.KubernetesClusterConfig.KubernetesClusterConfigKeys;
import io.harness.connector.entities.embedded.kubernetescluster.KubernetesClusterDetails;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsInheritFromDelegateSpecDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerRegistryProviderType;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpDelegateDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.git.model.ChangeType;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.CGRestUtils;
import io.harness.repositories.ConnectorRepository;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import retrofit2.Call;
import retrofit2.Response;

@Slf4j
@OwnedBy(DX)
public class ConnectorListWithFiltersTest extends ConnectorsTestBase {
  @Mock OrganizationService organizationService;
  @Mock ProjectService projectService;
  @Mock ConnectorEntityReferenceHelper connectorEntityReferenceHelper;
  @Inject OutboxService outboxService;
  @Inject @InjectMocks @Spy DefaultConnectorServiceImpl connectorService;
  @Inject ConnectorRepository connectorRepository;
  @Inject FilterService filterService;
  @Mock NGSettingsClient settingsClient;
  @Mock private Call<ResponseDTO<SettingValueResponseDTO>> request;
  String accountIdentifier = "accountIdentifier";
  String orgIdentifier = "orgIdentifier";
  String projectIdentifier = "projectIdentifier";
  String name = "name";
  String identifier = "identifier";
  String description = "description";
  Pageable pageable;
  ConnectorType connectorType = ConnectorType.DOCKER;

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
    doNothing().when(connectorService).assurePredefined(any(), any());
    when(settingsClient.getSetting(
             SettingIdentifiers.DISABLE_HARNESS_BUILT_IN_SECRET_MANAGER, accountIdentifier, null, null))
        .thenReturn(request);
    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("false").valueType(SettingValueType.BOOLEAN).build();
    when(request.execute()).thenReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO)));
    mockStatic(CGRestUtils.class);
    when(CGRestUtils.getResponse(any())).thenReturn(true);
    pageable = PageUtils.getPageRequest(0, 100, List.of(ConnectorKeys.lastModifiedAt, Sort.Direction.DESC.toString()));
  }
  private ConnectorInfoDTO getConnector(String name, String identifier, String description) {
    return ConnectorInfoDTO.builder()
        .name(name)
        .identifier(identifier)
        .projectIdentifier(projectIdentifier)
        .orgIdentifier(orgIdentifier)
        .description(description)
        .connectorType(connectorType)
        .connectorConfig(DockerConnectorDTO.builder()
                             .providerType(DockerRegistryProviderType.DOCKER_HUB)
                             .dockerRegistryUrl("abc")
                             .auth(DockerAuthenticationDTO.builder().authType(DockerAuthType.ANONYMOUS).build())
                             .build())
        .build();
  }

  private void createConnectorsWithGivenOrgs(List<String> orgs) {
    ConnectorInfoDTO connectorInfoDTO = getConnector(name, identifier, description);
    for (String orgId : orgs) {
      connectorInfoDTO.setOrgIdentifier(orgId);
      connectorInfoDTO.setProjectIdentifier(null);
      connectorInfoDTO.setIdentifier(generateUuid());
      connectorInfoDTO.setName(name + System.currentTimeMillis());
      connectorService.create(ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build(), accountIdentifier);
    }
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testListWithOrgFilters() {
    createConnectorsWithGivenOrgs(Arrays.asList("org1", "org2", "org3", "org1", "org1", "org5"));
    Page<ConnectorResponseDTO> connectorDTOS =
        connectorService.list(accountIdentifier, null, "org1", null, "", "", false, false, pageable);
    assertThat(connectorDTOS).isNotNull();
    assertThat(connectorDTOS.getTotalElements()).isEqualTo(3);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testListWithProjectFilters() {
    createConnectorsWithGivenProjects(Arrays.asList("proj1", "proj1", "proj1", "proj2", "proj3", "proj4"));
    Page<ConnectorResponseDTO> connectorDTOS =
        connectorService.list(accountIdentifier, null, orgIdentifier, "proj1", "", "", false, false, pageable);
    assertThat(connectorDTOS).isNotNull();
    assertThat(connectorDTOS.getTotalElements()).isEqualTo(3);
  }

  private void createConnectorsWithGivenProjects(List<String> projIds) {
    ConnectorInfoDTO connectorInfoDTO = getConnector(name, identifier, description);
    for (String projId : projIds) {
      connectorInfoDTO.setProjectIdentifier(projId);
      connectorInfoDTO.setIdentifier(generateUuid());
      connectorInfoDTO.setName(name + System.currentTimeMillis());
      connectorService.create(ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build(), accountIdentifier);
    }
  }

  private void createAccountLevelConnectors() {
    ConnectorInfoDTO connectorInfoDTO = getConnector(name + System.currentTimeMillis(), generateUuid(), description);
    connectorInfoDTO.setOrgIdentifier(null);
    connectorInfoDTO.setProjectIdentifier(null);
    connectorService.create(ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build(), accountIdentifier);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testListWithAccountScope() {
    createAccountLevelConnectors();
    createConnectorsWithGivenOrgs(Arrays.asList("org1", "org2"));
    createConnectorsWithGivenProjects(Arrays.asList("proj1", "proj2"));
    Page<ConnectorResponseDTO> connectorDTOS =
        connectorService.list(accountIdentifier, null, null, null, "", "", false, false, pageable);
    assertThat(connectorDTOS).isNotNull();
    assertThat(connectorDTOS.getTotalElements()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testListWithNamesFilter() {
    createConnectorsWithNames(Arrays.asList("docker", "docker connector", "qa connector", "a docker connector"));
    ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO =
        ConnectorFilterPropertiesDTO.builder().connectorNames(Arrays.asList("docker", "docker connector")).build();
    Page<ConnectorResponseDTO> connectorDTOS = connectorService.list(accountIdentifier, connectorFilterPropertiesDTO,
        orgIdentifier, projectIdentifier, "", "", false, false, pageable);
    assertThat(connectorDTOS).isNotNull();
    assertThat(connectorDTOS.getTotalElements()).isEqualTo(3);
    List<String> connectorNames = connectorDTOS.stream()
                                      .map(connectorResponseDTO -> connectorResponseDTO.getConnector().getName())
                                      .collect(Collectors.toList());
    assertThat(connectorNames.containsAll(Arrays.asList("docker", "docker connector", "a docker connector"))).isTrue();
  }

  private void createConnectorsWithNames(List<String> namesList) {
    ConnectorInfoDTO connectorInfoDTO = getConnector(name, identifier, description);
    //    doReturn(a).when(outboxService).save(any());
    for (String name : namesList) {
      connectorInfoDTO.setName(name);
      connectorInfoDTO.setIdentifier(generateUuid());
      connectorService.create(ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build(), accountIdentifier);
    }
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testListWithIdentifierFilter() {
    createConnectorsWithIdentifiers(Arrays.asList("dockerId", "identifier", "qa connector", "dockerId1"));
    ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO =
        ConnectorFilterPropertiesDTO.builder()
            .connectorIdentifiers(Arrays.asList("identifier", "qa connector"))
            .build();
    Page<ConnectorResponseDTO> connectorDTOS = connectorService.list(accountIdentifier, connectorFilterPropertiesDTO,
        orgIdentifier, projectIdentifier, "", "", false, false, pageable);
    assertThat(connectorDTOS).isNotNull();
    assertThat(connectorDTOS.getTotalElements()).isEqualTo(2);
    List<String> identifiersName = connectorDTOS.stream()
                                       .map(connectorResponseDTO -> connectorResponseDTO.getConnector().getIdentifier())
                                       .collect(Collectors.toList());
    assertThat(identifiersName.containsAll(Arrays.asList("identifier", "qa connector"))).isTrue();
  }

  private void createConnectorsWithIdentifiers(List<String> identifiers) {
    ConnectorInfoDTO connectorInfoDTO = getConnector(name, identifier, description);
    for (String identifier : identifiers) {
      connectorInfoDTO.setIdentifier(identifier);
      connectorInfoDTO.setName(name + System.currentTimeMillis());
      connectorService.create(ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build(), accountIdentifier);
    }
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testListWithDescriptionFilter() {
    createConnectorsWithDescriptions(
        Arrays.asList("docker connector", "docker", "qa connector", "qa", "harness test", "description"));
    ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO =
        ConnectorFilterPropertiesDTO.builder().description("docker").build();
    Page<ConnectorResponseDTO> connectorDTOS = connectorService.list(accountIdentifier, connectorFilterPropertiesDTO,
        orgIdentifier, projectIdentifier, "", "", false, false, pageable);
    assertThat(connectorDTOS).isNotNull();
    assertThat(connectorDTOS.getTotalElements()).isEqualTo(2);
    List<String> descriptions = connectorDTOS.stream()
                                    .map(connectorResponseDTO -> connectorResponseDTO.getConnector().getDescription())
                                    .collect(Collectors.toList());
    assertThat(descriptions.containsAll(Arrays.asList("docker connector", "docker"))).isTrue();
  }

  private void createConnectorsWithDescriptions(List<String> descriptions) {
    ConnectorInfoDTO connectorInfoDTO = getConnector(name, identifier, description);
    for (String description : descriptions) {
      connectorInfoDTO.setDescription(description);
      connectorInfoDTO.setIdentifier(generateUuid());
      connectorInfoDTO.setName(name + System.currentTimeMillis());
      connectorService.create(ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build(), accountIdentifier);
    }
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testListWithSearchTerm() {
    createConnectorsWithNames(Arrays.asList("docker connector", "docker dev", "qa connector test", "qb"));
    createConnectorsWithIdentifiers(Arrays.asList("docker", "identifier", "dockerId"));
    createConnectorsWithDescriptions(
        Arrays.asList("docker connector description", "docker", "qa connector test", "harness test", "description"));
    Page<ConnectorResponseDTO> connectorDTOS = connectorService.list(
        accountIdentifier, null, orgIdentifier, projectIdentifier, "", "docker", false, false, pageable);
    assertThat(connectorDTOS).isNotNull();
    assertThat(connectorDTOS.getTotalElements()).isEqualTo(6);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testListWithTypes() {
    createConnectorsWithNames(Arrays.asList("docker connector test", "docker dev connector"));
    createK8sConnector();
    ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO =
        ConnectorFilterPropertiesDTO.builder().types(Arrays.asList(KUBERNETES_CLUSTER, DOCKER)).build();
    Page<ConnectorResponseDTO> connectorDTOS = connectorService.list(accountIdentifier, connectorFilterPropertiesDTO,
        orgIdentifier, projectIdentifier, "", "", false, false, pageable);
    assertThat(connectorDTOS).isNotNull();
    assertThat(connectorDTOS.getTotalElements()).isEqualTo(3);
    List<ConnectorType> connectorTypes =
        connectorDTOS.stream()
            .map(connectorResponseDTO -> connectorResponseDTO.getConnector().getConnectorType())
            .collect(Collectors.toList());
    assertThat(connectorTypes.containsAll(Arrays.asList(KUBERNETES_CLUSTER, DOCKER))).isTrue();
  }

  private void createK8sConnector() {
    ConnectorDTO connectorDTO =
        ConnectorDTO.builder()
            .connectorInfo(ConnectorInfoDTO.builder()
                               .name(name)
                               .orgIdentifier(orgIdentifier)
                               .projectIdentifier(projectIdentifier)
                               .identifier(identifier)
                               .connectorType(KUBERNETES_CLUSTER)
                               .connectorConfig(KubernetesClusterConfigDTO.builder()
                                                    .credential(KubernetesCredentialDTO.builder()
                                                                    .kubernetesCredentialType(
                                                                        KubernetesCredentialType.INHERIT_FROM_DELEGATE)
                                                                    .config(null)
                                                                    .build())
                                                    .build())
                               .build())
            .build();
    connectorService.create(connectorDTO, accountIdentifier);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testListWithCategories() {
    createConnectorsWithNames(Arrays.asList("docker connector", "docker dev"));
    createK8sConnector();
    ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO =
        ConnectorFilterPropertiesDTO.builder().categories(Arrays.asList(ARTIFACTORY, CLOUD_PROVIDER)).build();
    Page<ConnectorResponseDTO> connectorDTOS = connectorService.list(accountIdentifier, connectorFilterPropertiesDTO,
        orgIdentifier, projectIdentifier, "", "", false, false, pageable);
    assertThat(connectorDTOS).isNotNull();
    assertThat(connectorDTOS.getTotalElements()).isEqualTo(3);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testListConnectivityStatus() {
    createConnectorsWithStatus(4, ConnectivityStatus.SUCCESS);
    createConnectorsWithStatus(6, ConnectivityStatus.FAILURE);
    ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO =
        ConnectorFilterPropertiesDTO.builder().connectivityStatuses(Arrays.asList(ConnectivityStatus.SUCCESS)).build();
    Page<ConnectorResponseDTO> connectorWithSuccessStatus = connectorService.list(accountIdentifier,
        connectorFilterPropertiesDTO, orgIdentifier, projectIdentifier, "", "", false, false,
        PageUtils.getPageRequest(0, 100, List.of(ConnectorKeys.lastModifiedAt, Sort.Direction.DESC.toString())));
    assertThat(connectorWithSuccessStatus.getTotalElements()).isEqualTo(4);
    connectorFilterPropertiesDTO =
        ConnectorFilterPropertiesDTO.builder().connectivityStatuses(Arrays.asList(ConnectivityStatus.FAILURE)).build();
    Page<ConnectorResponseDTO> connectorWithFailedStatus = connectorService.list(accountIdentifier,
        connectorFilterPropertiesDTO, orgIdentifier, projectIdentifier, "", "", false, false, pageable);
    assertThat(connectorWithFailedStatus.getTotalElements()).isEqualTo(6);
    connectorFilterPropertiesDTO =
        ConnectorFilterPropertiesDTO.builder()
            .connectivityStatuses(Arrays.asList(ConnectivityStatus.SUCCESS, ConnectivityStatus.FAILURE))
            .build();
    Page<ConnectorResponseDTO> allConnectors = connectorService.list(accountIdentifier, connectorFilterPropertiesDTO,
        orgIdentifier, projectIdentifier, "", "", false, false, pageable);
    assertThat(allConnectors.getTotalElements()).isEqualTo(10);
  }

  private KubernetesClusterConfig getConnectorEntity() {
    String masterURL = "masterURL";
    String userName = "userName";
    String passwordIdentifier = "passwordIdentifier";
    String passwordRef = ACCOUNT + SECRET_DOT_DELIMINITER + passwordIdentifier;
    K8sUserNamePassword k8sUserNamePassword =
        K8sUserNamePassword.builder().userName(userName).passwordRef(passwordRef).build();
    KubernetesClusterDetails kubernetesClusterDetails = KubernetesClusterDetails.builder()
                                                            .masterUrl(masterURL)
                                                            .authType(KubernetesAuthType.USER_PASSWORD)
                                                            .auth(k8sUserNamePassword)
                                                            .build();
    KubernetesClusterConfig connector = KubernetesClusterConfig.builder()
                                            .credentialType(MANUAL_CREDENTIALS)
                                            .credential(kubernetesClusterDetails)
                                            .build();
    connector.setAccountIdentifier(accountIdentifier);
    connector.setOrgIdentifier(orgIdentifier);
    connector.setProjectIdentifier(projectIdentifier);
    connector.setType(KUBERNETES_CLUSTER);
    return connector;
  }

  private void createConnectorsWithStatus(int numberOfConnectors, ConnectivityStatus status) {
    for (int i = 0; i < numberOfConnectors; i++) {
      KubernetesClusterConfig connector = getConnectorEntity();
      connector.setName(name + System.currentTimeMillis());
      connector.setIdentifier(generateUuid());
      connector.setConnectivityDetails(ConnectorConnectivityDetails.builder().status(status).build());
      connectorRepository.save(connector, ChangeType.ADD);
    }
  }

  private void createConnectorsWithExecuteOnDelegate(int numberOfConnectors, Boolean mode) {
    for (int i = 0; i < numberOfConnectors; i++) {
      KubernetesClusterConfig connector = getConnectorEntity();
      connector.setName(name + System.currentTimeMillis());
      connector.setIdentifier(generateUuid());
      connector.setExecuteOnDelegate(mode);
      connectorRepository.save(connector, ChangeType.ADD);
    }
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testListForInheritFromDelegate() {
    createConnectorsWithNames(Arrays.asList("docker hub", "docker test"));
    createK8sConnectorWithInheritFromDelegate();
    createAWSConnectorWithInheritFromDelegate();
    createGCPConnectorWithInheritFromDelegate();
    ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO =
        ConnectorFilterPropertiesDTO.builder().inheritingCredentialsFromDelegate(true).build();
    Page<ConnectorResponseDTO> connectorsInheritingCredentialsFromDelegate = connectorService.list(accountIdentifier,
        connectorFilterPropertiesDTO, orgIdentifier, projectIdentifier, "", "", false, false, pageable);
    assertThat(connectorsInheritingCredentialsFromDelegate.getTotalElements()).isEqualTo(3);
    connectorFilterPropertiesDTO =
        ConnectorFilterPropertiesDTO.builder().inheritingCredentialsFromDelegate(false).build();
    Page<ConnectorResponseDTO> connectorsNotInheritingCredentialsFromDelegate = connectorService.list(accountIdentifier,
        connectorFilterPropertiesDTO, orgIdentifier, projectIdentifier, "", "", false, false, pageable);
    assertThat(connectorsNotInheritingCredentialsFromDelegate.getTotalElements()).isEqualTo(2);
    Page<ConnectorResponseDTO> connectorsWhenFilterIsNotGiven = connectorService.list(
        accountIdentifier, null, orgIdentifier, projectIdentifier, null, "", false, false, pageable);
    assertThat(connectorsWhenFilterIsNotGiven.getTotalElements()).isEqualTo(5);
  }

  private void createGCPConnectorWithInheritFromDelegate() {
    String delegateSelector = "delegateSelector";
    final GcpConnectorDTO gcpConnectorDTO =
        GcpConnectorDTO.builder()
            .credential(GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE)
                            .config(GcpDelegateDetailsDTO.builder()
                                        .delegateSelectors(Collections.singleton(delegateSelector))
                                        .build())
                            .build())
            .build();
    ConnectorDTO connectorDTO = createConnectorDTO(generateUuid(), GCP, gcpConnectorDTO);
    connectorService.create(connectorDTO, accountIdentifier);
  }

  private ConnectorDTO createConnectorDTO(String identifier, ConnectorType type, ConnectorConfigDTO connectorConfig) {
    return ConnectorDTO.builder()
        .connectorInfo(ConnectorInfoDTO.builder()
                           .identifier(identifier)
                           .orgIdentifier(orgIdentifier)
                           .projectIdentifier(projectIdentifier)
                           .connectorType(type)
                           .connectorConfig(connectorConfig)
                           .name(name + System.currentTimeMillis())
                           .build())
        .build();
  }

  private void createAWSConnectorWithInheritFromDelegate() {
    String delegateSelector = "delegateSelector";
    final AwsConnectorDTO awsCredentialDTO =
        AwsConnectorDTO.builder()
            .credential(AwsCredentialDTO.builder()
                            .awsCredentialType(AwsCredentialType.INHERIT_FROM_DELEGATE)
                            .crossAccountAccess(null)
                            .config(AwsInheritFromDelegateSpecDTO.builder()
                                        .delegateSelectors(Collections.singleton(delegateSelector))
                                        .build())
                            .build())
            .build();
    ConnectorDTO connectorDTO = createConnectorDTO(generateUuid(), AWS, awsCredentialDTO);
    connectorService.create(connectorDTO, accountIdentifier);
  }

  private void createK8sConnectorWithInheritFromDelegate() {
    String delegateName = "delegateName";
    KubernetesClusterConfigDTO connectorDTOWithDelegateCreds =
        KubernetesClusterConfigDTO.builder()
            .credential(
                KubernetesCredentialDTO.builder().kubernetesCredentialType(INHERIT_FROM_DELEGATE).config(null).build())
            .build();
    ConnectorDTO connectorDTO = createConnectorDTO(generateUuid(), KUBERNETES_CLUSTER, connectorDTOWithDelegateCreds);
    connectorService.create(connectorDTO, accountIdentifier);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void checkThatTheFieldWhichStoresCredentialTypeIsNotChanged() {
    assertThat(CREDENTIAL_TYPE_KEY).isEqualTo(KubernetesClusterConfigKeys.credentialType);
    assertThat(CREDENTIAL_TYPE_KEY).isEqualTo(AwsConfigKeys.credentialType);
    assertThat(CREDENTIAL_TYPE_KEY).isEqualTo(GcpConfigKeys.credentialType);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void checkThatTheInheritFromDelegateStringIsNotChanged() {
    assertThat(INHERIT_FROM_DELEGATE_STRING).isEqualTo(INHERIT_FROM_DELEGATE.name());
    assertThat(INHERIT_FROM_DELEGATE_STRING).isEqualTo(AwsCredentialType.INHERIT_FROM_DELEGATE.name());
    assertThat(INHERIT_FROM_DELEGATE_STRING).isEqualTo(GcpCredentialType.INHERIT_FROM_DELEGATE.name());
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testListForWhenAFilterIsGiven() {
    String filterIdentifier = "filterIdentifier";
    List<String> names =
        Arrays.asList("docker test", "docker qa", "docker prod", "k8s dev", "test k8s", "k8s qb", "k8s");
    List<String> identifiers =
        Arrays.asList("dockerTest", "dockerQa", "dockerProd", "k8sDev", " k8s Docker", "k8s qb", "k8sHub");
    List<String> descriptions =
        Arrays.asList("Docker Test", "Docker qa", "Docker prod", "k8s dev", "Test k8s", "k8s qb", "k8sHub");
    createConnectorsWithGivenValues(names, identifiers, descriptions);

    ConnectorFilterPropertiesDTO connectorFilter =
        ConnectorFilterPropertiesDTO.builder()
            .connectorNames(Arrays.asList("docker"))
            .connectorIdentifiers(Arrays.asList("dockerQa", "dockerProd", "dockerDev"))
            .description("Docker")
            .build();
    connectorFilter.setFilterType(FilterType.CONNECTOR);
    FilterDTO filterDTO = FilterDTO.builder()
                              .name("connectorFilter")
                              .identifier(filterIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .filterProperties(connectorFilter)
                              .build();
    filterService.create(accountIdentifier, filterDTO);

    int numberOfConnectors = connectorService
                                 .list(accountIdentifier, null, orgIdentifier, projectIdentifier, filterIdentifier,
                                     null, false, false, pageable)
                                 .getNumberOfElements();
    assertThat(numberOfConnectors).isEqualTo(2);
  }

  @Test
  @Owner(developers = OwnerRule.TEJAS)
  @Category(UnitTests.class)
  public void testListConnectivityModes() {
    createConnectorsWithExecuteOnDelegate(4, true);
    createConnectorsWithExecuteOnDelegate(6, false);
    ConnectorFilterPropertiesDTO connectorFilterPropertiesDTO =
        ConnectorFilterPropertiesDTO.builder()
            .connectorConnectivityModes(Arrays.asList(ConnectorConnectivityMode.DELEGATE))
            .build();
    Page<ConnectorResponseDTO> connectorWithDelegateMode = connectorService.list(accountIdentifier,
        connectorFilterPropertiesDTO, orgIdentifier, projectIdentifier, "", "", false, false, pageable);
    assertThat(connectorWithDelegateMode.getTotalElements()).isEqualTo(4);
    connectorFilterPropertiesDTO = ConnectorFilterPropertiesDTO.builder()
                                       .connectorConnectivityModes(Arrays.asList(ConnectorConnectivityMode.MANAGER))
                                       .build();
    Page<ConnectorResponseDTO> connectorWithManagerMode = connectorService.list(accountIdentifier,
        connectorFilterPropertiesDTO, orgIdentifier, projectIdentifier, "", "", false, false, pageable);
    assertThat(connectorWithManagerMode.getTotalElements()).isEqualTo(6);
    connectorFilterPropertiesDTO = ConnectorFilterPropertiesDTO.builder()
                                       .connectorConnectivityModes(Arrays.asList(
                                           ConnectorConnectivityMode.DELEGATE, ConnectorConnectivityMode.MANAGER))
                                       .build();
    Page<ConnectorResponseDTO> allConnectors = connectorService.list(accountIdentifier, connectorFilterPropertiesDTO,
        orgIdentifier, projectIdentifier, "", "", false, false, pageable);
    assertThat(allConnectors.getTotalElements()).isEqualTo(10);
  }

  private void createConnectorsWithGivenValues(
      List<String> nameList, List<String> identifierList, List<String> descriptionsList) {
    int size = nameList.size();
    for (int i = 0; i < size; i++) {
      ConnectorInfoDTO connectorInfoDTO = getConnector(nameList.get(i), identifierList.get(i), descriptionsList.get(i));
      connectorService.create(ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build(), accountIdentifier);
    }
  }
}
