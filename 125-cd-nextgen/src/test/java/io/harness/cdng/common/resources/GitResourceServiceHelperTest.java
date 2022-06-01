/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.common.resources;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.powermock.api.mockito.PowerMockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class GitResourceServiceHelperTest extends CategoryTest {
  @Mock private ConnectorService connectorService;
  @Mock private GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  private GitResourceServiceHelper gitResourceServiceHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    gitResourceServiceHelper = spy(new GitResourceServiceHelper(connectorService, gitConfigAuthenticationInfoHelper));
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetConnectorInfoDTO() {
    ConnectorResponseDTO connectorResponseDTO =
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder().connectorType(ConnectorType.GIT).build())
            .build();
    Optional<ConnectorResponseDTO> connectorResponseDTOOptional = Optional.of(connectorResponseDTO);
    doReturn(connectorResponseDTOOptional).when(connectorService).get(any(), any(), any(), any());
    ConnectorInfoDTO response = gitResourceServiceHelper.getConnectorInfoDTO(
        "foo", BaseNGAccess.builder().accountIdentifier("bar").orgIdentifier("baz").projectIdentifier("zar").build());
    assertThat(response.getConnectorType()).isEqualTo(ConnectorType.GIT);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetConnectorInfoDTOException() {
    Optional<ConnectorResponseDTO> connectorResponseDTOOptional = Optional.empty();
    doReturn(connectorResponseDTOOptional).when(connectorService).get(any(), any(), any(), any());
    ConnectorInfoDTO response = gitResourceServiceHelper.getConnectorInfoDTO(
        "foo", BaseNGAccess.builder().accountIdentifier("bar").orgIdentifier("baz").projectIdentifier("zar").build());
    assertThat(response.getConnectorType()).isEqualTo(ConnectorType.GIT);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetGitStoreDelegateConfig() {
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorType(ConnectorType.GIT).build();
    BaseNGAccess baseNGAccess =
        BaseNGAccess.builder().accountIdentifier("bar").orgIdentifier("baz").projectIdentifier("zar").build();
    doReturn(SSHKeySpecDTO.builder().build()).when(gitResourceServiceHelper).getSshKeySpecDTO(any(), any());
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    doReturn(encryptedDataDetails).when(gitConfigAuthenticationInfoHelper).getEncryptedDataDetails(any(), any(), any());
    GitStoreDelegateConfig response = gitResourceServiceHelper.getGitStoreDelegateConfig(
        connectorInfoDTO, baseNGAccess, FetchType.BRANCH, "fo", "bar", "farz");
    assertThat(response.getFetchType()).isEqualTo(FetchType.BRANCH);
  }
}
