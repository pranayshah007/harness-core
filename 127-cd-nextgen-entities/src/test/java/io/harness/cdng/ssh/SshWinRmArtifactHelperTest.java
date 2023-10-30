/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.outcome.GoogleCloudStorageArtifactOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.ssh.artifact.GoogleCloudStorageArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactType;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.ff.FeatureFlagService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class SshWinRmArtifactHelperTest extends CategoryTest {
  @Mock private ConnectorService connectorService;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private FeatureFlagService featureFlagService;

  @InjectMocks private SshWinRmArtifactHelper helper;

  private final String accountId = "test";
  private final String projectId = "testProject";
  private final String orgId = "testOrg";

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId)
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, projectId)
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, orgId)
                                        .build();

  @Before
  public void prepare() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testGcsArtifactOutcome() {
    GoogleCloudStorageArtifactOutcome outcome = GoogleCloudStorageArtifactOutcome.builder()
                                                    .artifactPath("path/to/artifact")
                                                    .primaryArtifact(true)
                                                    .bucket("bucket")
                                                    .project("project")
                                                    .connectorRef("gcsConnectorRef")
                                                    .identifier("gcsoutcome")
                                                    .build();

    doReturn(Optional.of(getGcpConnectorDto())).when(connectorService).get(any(), any(), any(), any());
    doReturn(Collections.emptyList()).when(secretManagerClientService).getEncryptionDetails(any(), any());

    SshWinRmArtifactDelegateConfig delegateConfig = helper.getArtifactDelegateConfigConfig(outcome, ambiance);

    assertThat(delegateConfig).isInstanceOf(GoogleCloudStorageArtifactDelegateConfig.class);
    GoogleCloudStorageArtifactDelegateConfig gcsArtifactDelegateConfig =
        (GoogleCloudStorageArtifactDelegateConfig) delegateConfig;
    assertThat(gcsArtifactDelegateConfig.getArtifactPath()).isEqualTo("path/to/artifact");
    assertThat(gcsArtifactDelegateConfig.getBucket()).isEqualTo("bucket");
    assertThat(gcsArtifactDelegateConfig.getProject()).isEqualTo("project");
    assertThat(gcsArtifactDelegateConfig.getArtifactType()).isEqualTo(SshWinRmArtifactType.GCS);
    assertThat(gcsArtifactDelegateConfig.getConnectorDTO()).isNotNull();
    assertThat(gcsArtifactDelegateConfig.getConnectorDTO().getConnectorConfig()).isInstanceOf(GcpConnectorDTO.class);
    assertThat(gcsArtifactDelegateConfig.getEncryptedDataDetails()).isEmpty();

    ArgumentCaptor<String> connectorRefCaptor = ArgumentCaptor.forClass(String.class);
    verify(connectorService, times(1)).get(eq(accountId), eq(orgId), eq(projectId), connectorRefCaptor.capture());
    assertThat(connectorRefCaptor.getValue()).isEqualTo("gcsConnectorRef");
  }

  private ConnectorResponseDTO getGcpConnectorDto() {
    return ConnectorResponseDTO.builder()
        .connector(ConnectorInfoDTO.builder()
                       .identifier("gcsConnectorRef")
                       .connectorConfig(
                           GcpConnectorDTO.builder()
                               .executeOnDelegate(false)
                               .credential(GcpConnectorCredentialDTO.builder()
                                               .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                                               .config(GcpManualDetailsDTO.builder()
                                                           .secretKeyRef(SecretRefData.builder()
                                                                             .scope(Scope.ACCOUNT)
                                                                             .decryptedValue("top-secret".toCharArray())
                                                                             .identifier("gcpcredid")
                                                                             .build())
                                                           .build())
                                               .build())
                               .build())
                       .connectorType(ConnectorType.GCP)
                       .build())
        .build();
  }
}
