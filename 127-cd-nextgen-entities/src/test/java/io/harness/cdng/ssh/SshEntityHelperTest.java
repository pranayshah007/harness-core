/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;
import static io.harness.utils.PageUtils.getPageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.services.NGHostService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.pdcconnector.HostDTO;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterDTO;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterType;
import io.harness.delegate.beans.connector.pdcconnector.PhysicalDataCenterConnectorDTO;
import io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.PdcWinRmInfraDelegateConfig;
import io.harness.delegate.task.ssh.SshInfraDelegateConfig;
import io.harness.delegate.task.ssh.WinRmInfraDelegateConfig;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.services.SshKeySpecDTOHelper;
import io.harness.secretmanagerclient.services.WinRmCredentialsSpecDTOHelper;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(CDP)
public class SshEntityHelperTest extends CategoryTest {
  @Mock private SecretNGManagerClient secretManagerClient;
  @Mock private ConnectorService connectorService;
  @Mock private SshKeySpecDTOHelper sshKeySpecDTOHelper;
  @Mock private WinRmCredentialsSpecDTOHelper winRmCredentialsSpecDTOHelper;
  @Mock private NGHostService ngHostService;

  @InjectMocks private SshEntityHelper helper;

  private final String accountId = "test";
  private final String projectId = "testProject";
  private final String orgId = "testOrg";

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId)
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, projectId)
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, orgId)
                                        .build();

  private final Optional<ConnectorResponseDTO> connectorDTO = Optional.of(
      ConnectorResponseDTO.builder()
          .connector(ConnectorInfoDTO.builder()
                         .connectorType(ConnectorType.PDC)
                         .connectorConfig(PhysicalDataCenterConnectorDTO.builder()
                                              .hosts(Arrays.asList(new HostDTO("host1", ImmutableMap.of("type", "db"))))
                                              .build())
                         .build())
          .build());

  private final SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();
  private final WinRmCredentialsSpecDTO winRmCredentials = WinRmCredentialsSpecDTO.builder().build();

  @Before
  public void prepare() throws IOException {
    MockitoAnnotations.initMocks(this);
    doReturn(connectorDTO).when(connectorService).get(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetSshInfraDelegateConfigFromPdcConnector() throws IOException {
    PdcInfrastructureOutcome pdcInfrastructure =
        PdcInfrastructureOutcome.builder().connectorRef("pdcConnector").credentialsRef("sshKeyRef").build();

    Call<ResponseDTO<SecretResponseWrapper>> getSecretCall = mock(Call.class);
    ResponseDTO<SecretResponseWrapper> responseDTO =
        ResponseDTO.newResponse(SecretResponseWrapper.builder()
                                    .secret(SecretDTOV2.builder().type(SecretType.SSHKey).spec(sshKeySpecDTO).build())
                                    .build());
    doReturn(Response.success(responseDTO)).when(getSecretCall).execute();
    doReturn(getSecretCall).when(secretManagerClient).getSecret(anyString(), anyString(), anyString(), anyString());
    doReturn(Arrays.asList(EncryptedDataDetail.builder().build()))
        .when(sshKeySpecDTOHelper)
        .getSSHKeyEncryptionDetails(eq(sshKeySpecDTO), any());

    SshInfraDelegateConfig infraDelegateConfig = helper.getSshInfraDelegateConfig(pdcInfrastructure, ambiance);
    assertThat(infraDelegateConfig).isInstanceOf(PdcSshInfraDelegateConfig.class);
    PdcSshInfraDelegateConfig pdcSshInfraDelegateConfig = (PdcSshInfraDelegateConfig) infraDelegateConfig;
    assertThat(pdcSshInfraDelegateConfig.getSshKeySpecDto()).isEqualTo(sshKeySpecDTO);
    assertThat(pdcSshInfraDelegateConfig.getHosts()).isNotEmpty();
    assertThat(pdcSshInfraDelegateConfig.getHosts().get(0)).isEqualTo("host1");
    assertThat(pdcSshInfraDelegateConfig.getEncryptionDataDetails()).isNotEmpty();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetFilteredHostByHostName() throws IOException {
    PdcInfrastructureOutcome pdcInfrastructure = PdcInfrastructureOutcome.builder()
                                                     .connectorRef("pdcConnector")
                                                     .credentialsRef("sshKeyRef")
                                                     .hostFilters(Arrays.asList("host1"))
                                                     .build();

    List<HostDTO> hosts = Arrays.asList(new HostDTO("host1", new HashMap<>()));
    PageRequest pageRequest = PageRequest.builder().pageSize(hosts.size()).build();
    Page<HostDTO> pageResponse = new PageImpl<>(hosts, getPageRequest(pageRequest), hosts.size());
    doReturn(pageResponse)
        .when(ngHostService)
        .filterHostsByConnector(eq(accountId), eq(orgId), eq(projectId), eq("pdcConnector"), any(), any());
    Call<ResponseDTO<SecretResponseWrapper>> getSecretCall = mock(Call.class);
    ResponseDTO<SecretResponseWrapper> responseDTO =
        ResponseDTO.newResponse(SecretResponseWrapper.builder()
                                    .secret(SecretDTOV2.builder().type(SecretType.SSHKey).spec(sshKeySpecDTO).build())
                                    .build());
    doReturn(Response.success(responseDTO)).when(getSecretCall).execute();
    doReturn(getSecretCall).when(secretManagerClient).getSecret(anyString(), anyString(), anyString(), anyString());
    doReturn(Arrays.asList(EncryptedDataDetail.builder().build()))
        .when(sshKeySpecDTOHelper)
        .getSSHKeyEncryptionDetails(eq(sshKeySpecDTO), any());

    SshInfraDelegateConfig infraDelegateConfig = helper.getSshInfraDelegateConfig(pdcInfrastructure, ambiance);
    assertThat(infraDelegateConfig).isInstanceOf(PdcSshInfraDelegateConfig.class);
    PdcSshInfraDelegateConfig pdcSshInfraDelegateConfig = (PdcSshInfraDelegateConfig) infraDelegateConfig;
    assertThat(pdcSshInfraDelegateConfig.getSshKeySpecDto()).isEqualTo(sshKeySpecDTO);
    assertThat(pdcSshInfraDelegateConfig.getHosts()).isNotEmpty();
    assertThat(pdcSshInfraDelegateConfig.getHosts().get(0)).isEqualTo("host1");
    assertThat(pdcSshInfraDelegateConfig.getEncryptionDataDetails()).isNotEmpty();

    ArgumentCaptor<HostFilterDTO> filterCaptor = ArgumentCaptor.forClass(HostFilterDTO.class);
    verify(ngHostService, times(1))
        .filterHostsByConnector(
            eq(accountId), eq(orgId), eq(projectId), eq("pdcConnector"), filterCaptor.capture(), any());
    HostFilterDTO filter = filterCaptor.getValue();
    assertThat(filter.getType()).isEqualTo(HostFilterType.HOST_NAMES);
    assertThat(filter.getFilter()).isEqualTo("host1");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetFilteredHostByHostNameNoMatch() throws IOException {
    PdcInfrastructureOutcome pdcInfrastructure = PdcInfrastructureOutcome.builder()
                                                     .connectorRef("pdcConnector")
                                                     .credentialsRef("sshKeyRef")
                                                     .hostFilters(Arrays.asList("undefined"))
                                                     .build();

    List<HostDTO> hosts = Collections.emptyList();
    PageRequest pageRequest = PageRequest.builder().pageSize(1).build();
    Page<HostDTO> pageResponse = new PageImpl<>(hosts, getPageRequest(pageRequest), hosts.size());
    doReturn(pageResponse)
        .when(ngHostService)
        .filterHostsByConnector(eq(accountId), eq(orgId), eq(projectId), eq("pdcConnector"), any(), any());
    Call<ResponseDTO<SecretResponseWrapper>> getSecretCall = mock(Call.class);
    ResponseDTO<SecretResponseWrapper> responseDTO =
        ResponseDTO.newResponse(SecretResponseWrapper.builder()
                                    .secret(SecretDTOV2.builder().type(SecretType.SSHKey).spec(sshKeySpecDTO).build())
                                    .build());
    doReturn(Response.success(responseDTO)).when(getSecretCall).execute();
    doReturn(getSecretCall).when(secretManagerClient).getSecret(anyString(), anyString(), anyString(), anyString());
    doReturn(Arrays.asList(EncryptedDataDetail.builder().build()))
        .when(sshKeySpecDTOHelper)
        .getSSHKeyEncryptionDetails(eq(sshKeySpecDTO), any());

    SshInfraDelegateConfig infraDelegateConfig = helper.getSshInfraDelegateConfig(pdcInfrastructure, ambiance);
    assertThat(infraDelegateConfig).isInstanceOf(PdcSshInfraDelegateConfig.class);
    PdcSshInfraDelegateConfig pdcSshInfraDelegateConfig = (PdcSshInfraDelegateConfig) infraDelegateConfig;
    assertThat(pdcSshInfraDelegateConfig.getSshKeySpecDto()).isEqualTo(sshKeySpecDTO);
    assertThat(pdcSshInfraDelegateConfig.getHosts()).isEmpty();
    assertThat(pdcSshInfraDelegateConfig.getEncryptionDataDetails()).isNotEmpty();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetFilteredHostByHostAttributes() throws IOException {
    PdcInfrastructureOutcome pdcInfrastructure = PdcInfrastructureOutcome.builder()
                                                     .connectorRef("pdcConnector")
                                                     .credentialsRef("sshKeyRef")
                                                     .attributeFilters(ImmutableMap.of("type", "db"))
                                                     .build();

    List<HostDTO> hosts = Arrays.asList(new HostDTO("host1", ImmutableMap.of("type", "db")));
    PageRequest pageRequest = PageRequest.builder().pageSize(hosts.size()).build();
    Page<HostDTO> pageResponse = new PageImpl<>(hosts, getPageRequest(pageRequest), hosts.size());
    doReturn(pageResponse)
        .when(ngHostService)
        .filterHostsByConnector(eq(accountId), eq(orgId), eq(projectId), eq("pdcConnector"), any(), any());
    Call<ResponseDTO<SecretResponseWrapper>> getSecretCall = mock(Call.class);
    ResponseDTO<SecretResponseWrapper> responseDTO =
        ResponseDTO.newResponse(SecretResponseWrapper.builder()
                                    .secret(SecretDTOV2.builder().type(SecretType.SSHKey).spec(sshKeySpecDTO).build())
                                    .build());
    doReturn(Response.success(responseDTO)).when(getSecretCall).execute();
    doReturn(getSecretCall).when(secretManagerClient).getSecret(anyString(), anyString(), anyString(), anyString());
    doReturn(Arrays.asList(EncryptedDataDetail.builder().build()))
        .when(sshKeySpecDTOHelper)
        .getSSHKeyEncryptionDetails(eq(sshKeySpecDTO), any());

    SshInfraDelegateConfig infraDelegateConfig = helper.getSshInfraDelegateConfig(pdcInfrastructure, ambiance);
    assertThat(infraDelegateConfig).isInstanceOf(PdcSshInfraDelegateConfig.class);
    PdcSshInfraDelegateConfig pdcSshInfraDelegateConfig = (PdcSshInfraDelegateConfig) infraDelegateConfig;
    assertThat(pdcSshInfraDelegateConfig.getSshKeySpecDto()).isEqualTo(sshKeySpecDTO);
    assertThat(pdcSshInfraDelegateConfig.getHosts()).isNotEmpty();
    assertThat(pdcSshInfraDelegateConfig.getHosts().get(0)).isEqualTo("host1");
    assertThat(pdcSshInfraDelegateConfig.getEncryptionDataDetails()).isNotEmpty();

    ArgumentCaptor<HostFilterDTO> filterCaptor = ArgumentCaptor.forClass(HostFilterDTO.class);
    verify(ngHostService, times(1))
        .filterHostsByConnector(
            eq(accountId), eq(orgId), eq(projectId), eq("pdcConnector"), filterCaptor.capture(), any());
    HostFilterDTO filter = filterCaptor.getValue();
    assertThat(filter.getType()).isEqualTo(HostFilterType.HOST_ATTRIBUTES);
    assertThat(filter.getFilter()).isEqualTo("type:db");
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetFilteredHostByHostAttributesNoMatch() throws IOException {
    PdcInfrastructureOutcome pdcInfrastructure = PdcInfrastructureOutcome.builder()
                                                     .connectorRef("pdcConnector")
                                                     .credentialsRef("sshKeyRef")
                                                     .attributeFilters(ImmutableMap.of("type", "node"))
                                                     .build();

    List<HostDTO> hosts = Collections.emptyList();
    PageRequest pageRequest = PageRequest.builder().pageSize(1).build();
    Page<HostDTO> pageResponse = new PageImpl<>(hosts, getPageRequest(pageRequest), hosts.size());
    doReturn(pageResponse)
        .when(ngHostService)
        .filterHostsByConnector(eq(accountId), eq(orgId), eq(projectId), eq("pdcConnector"), any(), any());
    Call<ResponseDTO<SecretResponseWrapper>> getSecretCall = mock(Call.class);
    ResponseDTO<SecretResponseWrapper> responseDTO =
        ResponseDTO.newResponse(SecretResponseWrapper.builder()
                                    .secret(SecretDTOV2.builder().type(SecretType.SSHKey).spec(sshKeySpecDTO).build())
                                    .build());
    doReturn(Response.success(responseDTO)).when(getSecretCall).execute();
    doReturn(getSecretCall).when(secretManagerClient).getSecret(anyString(), anyString(), anyString(), anyString());
    doReturn(Arrays.asList(EncryptedDataDetail.builder().build()))
        .when(sshKeySpecDTOHelper)
        .getSSHKeyEncryptionDetails(eq(sshKeySpecDTO), any());

    SshInfraDelegateConfig infraDelegateConfig = helper.getSshInfraDelegateConfig(pdcInfrastructure, ambiance);
    assertThat(infraDelegateConfig).isInstanceOf(PdcSshInfraDelegateConfig.class);
    PdcSshInfraDelegateConfig pdcSshInfraDelegateConfig = (PdcSshInfraDelegateConfig) infraDelegateConfig;
    assertThat(pdcSshInfraDelegateConfig.getSshKeySpecDto()).isEqualTo(sshKeySpecDTO);
    assertThat(pdcSshInfraDelegateConfig.getHosts()).isEmpty();
    assertThat(pdcSshInfraDelegateConfig.getEncryptionDataDetails()).isNotEmpty();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetSshInfraDelegateConfigFromPdcInfrastructure() throws IOException {
    PdcInfrastructureOutcome pdcInfrastructure =
        PdcInfrastructureOutcome.builder().credentialsRef("sshKeyRef").hosts(Arrays.asList("host2")).build();

    Call<ResponseDTO<SecretResponseWrapper>> getSecretCall = mock(Call.class);
    ResponseDTO<SecretResponseWrapper> responseDTO =
        ResponseDTO.newResponse(SecretResponseWrapper.builder()
                                    .secret(SecretDTOV2.builder().type(SecretType.SSHKey).spec(sshKeySpecDTO).build())
                                    .build());
    doReturn(Response.success(responseDTO)).when(getSecretCall).execute();
    doReturn(getSecretCall).when(secretManagerClient).getSecret(anyString(), anyString(), anyString(), anyString());
    doReturn(Arrays.asList(EncryptedDataDetail.builder().build()))
        .when(sshKeySpecDTOHelper)
        .getSSHKeyEncryptionDetails(eq(sshKeySpecDTO), any());

    SshInfraDelegateConfig infraDelegateConfig = helper.getSshInfraDelegateConfig(pdcInfrastructure, ambiance);
    assertThat(infraDelegateConfig).isInstanceOf(PdcSshInfraDelegateConfig.class);
    PdcSshInfraDelegateConfig pdcSshInfraDelegateConfig = (PdcSshInfraDelegateConfig) infraDelegateConfig;
    assertThat(pdcSshInfraDelegateConfig.getSshKeySpecDto()).isEqualTo(sshKeySpecDTO);
    assertThat(pdcSshInfraDelegateConfig.getHosts()).isNotEmpty();
    assertThat(pdcSshInfraDelegateConfig.getHosts().get(0)).isEqualTo("host2");
    assertThat(pdcSshInfraDelegateConfig.getEncryptionDataDetails()).isNotEmpty();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testThrowUnsupportedExceptionForNonePdcInfra() {
    assertThatThrownBy(
        () -> helper.getSshInfraDelegateConfig(K8sDirectInfrastructureOutcome.builder().build(), ambiance))
        .isInstanceOf(UnsupportedOperationException.class);

    assertThatThrownBy(() -> helper.getSshInfraDelegateConfig(K8sGcpInfrastructureOutcome.builder().build(), ambiance))
        .isInstanceOf(UnsupportedOperationException.class);

    assertThatThrownBy(
        () -> helper.getWinRmInfraDelegateConfig(K8sDirectInfrastructureOutcome.builder().build(), ambiance))
        .isInstanceOf(UnsupportedOperationException.class);

    assertThatThrownBy(
        () -> helper.getWinRmInfraDelegateConfig(K8sGcpInfrastructureOutcome.builder().build(), ambiance))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetWinRmInfraDelegateConfigFromPdcConnector() throws IOException {
    PdcInfrastructureOutcome pdcInfrastructure =
        PdcInfrastructureOutcome.builder().connectorRef("pdcConnector").credentialsRef("winrmCredentialsRef").build();

    Call<ResponseDTO<SecretResponseWrapper>> getSecretCall = mock(Call.class);
    ResponseDTO<SecretResponseWrapper> responseDTO = ResponseDTO.newResponse(
        SecretResponseWrapper.builder()
            .secret(SecretDTOV2.builder().type(SecretType.WinRmCredentials).spec(winRmCredentials).build())
            .build());
    doReturn(Response.success(responseDTO)).when(getSecretCall).execute();
    doReturn(getSecretCall).when(secretManagerClient).getSecret(anyString(), anyString(), anyString(), anyString());
    doReturn(Arrays.asList(EncryptedDataDetail.builder().build()))
        .when(winRmCredentialsSpecDTOHelper)
        .getWinRmEncryptionDetails(eq(winRmCredentials), any());

    WinRmInfraDelegateConfig infraDelegateConfig = helper.getWinRmInfraDelegateConfig(pdcInfrastructure, ambiance);
    assertThat(infraDelegateConfig).isInstanceOf(PdcWinRmInfraDelegateConfig.class);
    PdcWinRmInfraDelegateConfig pdcWinRmInfraDelegateConfig = (PdcWinRmInfraDelegateConfig) infraDelegateConfig;
    assertThat(pdcWinRmInfraDelegateConfig.getWinRmCredentials()).isEqualTo(winRmCredentials);
    assertThat(pdcWinRmInfraDelegateConfig.getHosts()).isNotEmpty();
    assertThat(pdcWinRmInfraDelegateConfig.getHosts().get(0)).isEqualTo("host1");
    assertThat(pdcWinRmInfraDelegateConfig.getEncryptionDataDetails()).isNotEmpty();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetWinRmInfraDelegateConfigFromPdcInfrastructure() throws IOException {
    PdcInfrastructureOutcome pdcInfrastructure =
        PdcInfrastructureOutcome.builder().credentialsRef("winrmCredentialsRef").hosts(Arrays.asList("host2")).build();

    Call<ResponseDTO<SecretResponseWrapper>> getSecretCall = mock(Call.class);
    ResponseDTO<SecretResponseWrapper> responseDTO = ResponseDTO.newResponse(
        SecretResponseWrapper.builder()
            .secret(SecretDTOV2.builder().type(SecretType.WinRmCredentials).spec(winRmCredentials).build())
            .build());
    doReturn(Response.success(responseDTO)).when(getSecretCall).execute();
    doReturn(getSecretCall).when(secretManagerClient).getSecret(anyString(), anyString(), anyString(), anyString());
    doReturn(Arrays.asList(EncryptedDataDetail.builder().build()))
        .when(winRmCredentialsSpecDTOHelper)
        .getWinRmEncryptionDetails(eq(winRmCredentials), any());

    WinRmInfraDelegateConfig infraDelegateConfig = helper.getWinRmInfraDelegateConfig(pdcInfrastructure, ambiance);
    assertThat(infraDelegateConfig).isInstanceOf(PdcWinRmInfraDelegateConfig.class);
    PdcWinRmInfraDelegateConfig pdcWinRmInfraDelegateConfig = (PdcWinRmInfraDelegateConfig) infraDelegateConfig;
    assertThat(pdcWinRmInfraDelegateConfig.getWinRmCredentials()).isEqualTo(winRmCredentials);
    assertThat(pdcWinRmInfraDelegateConfig.getHosts()).isNotEmpty();
    assertThat(pdcWinRmInfraDelegateConfig.getHosts().get(0)).isEqualTo("host2");
    assertThat(pdcWinRmInfraDelegateConfig.getEncryptionDataDetails()).isNotEmpty();
  }
}
