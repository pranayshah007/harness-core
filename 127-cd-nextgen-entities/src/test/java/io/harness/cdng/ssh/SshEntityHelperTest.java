/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.pdcconnector.HostDTO;
import io.harness.delegate.beans.connector.pdcconnector.PhysicalDataCenterConnectorDTO;
import io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.SshInfraDelegateConfig;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.services.SshKeySpecDTOHelper;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(CDP)
public class SshEntityHelperTest extends CategoryTest {
  @Mock private SecretNGManagerClient secretManagerClient;
  @Mock private ConnectorService connectorService;
  @Mock private SshKeySpecDTOHelper sshKeySpecDTOHelper;

  @InjectMocks private SshEntityHelper helper;

  private final String accountId = "test";
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId)
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "testProject")
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "testOrg")
                                        .build();

  private final Optional<ConnectorResponseDTO> connectorDTO = Optional.of(
      ConnectorResponseDTO.builder()
          .connector(
              ConnectorInfoDTO.builder()
                  .connectorType(ConnectorType.PDC)
                  .connectorConfig(
                      PhysicalDataCenterConnectorDTO.builder().hosts(Arrays.asList(new HostDTO("host1", null))).build())
                  .build())
          .build());

  private final SSHKeySpecDTO sshKeySpecDTO = SSHKeySpecDTO.builder().build();

  @Before
  public void prepare() throws IOException {
    MockitoAnnotations.initMocks(this);

    doReturn(connectorDTO).when(connectorService).get(anyString(), anyString(), anyString(), anyString());

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
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGetSshInfraDelegateConfigFromPdcConnector() {
    PdcInfrastructureOutcome pdcInfrastructure =
        PdcInfrastructureOutcome.builder().connectorRef("pdcConnector").credentialsRef("sshKeyRef").build();

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
  public void testGetSshInfraDelegateConfigFromPdcInfrastructure() {
    PdcInfrastructureOutcome pdcInfrastructure =
        PdcInfrastructureOutcome.builder().credentialsRef("sshKeyRef").hosts(Arrays.asList("host2")).build();

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
  }
}
