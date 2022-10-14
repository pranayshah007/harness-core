/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors;

import static io.harness.rule.OwnerRule.SHREYAS;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomUtils.nextInt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.secrets.SSHAuthDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.encryptors.NGManagerCustomEncryptor;
import io.harness.ng.core.encryptors.NGManagerEncryptorHelper;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SSHAuthScheme;
import io.harness.secretmanagerclient.services.SshKeySpecDTOHelper;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.IdentifierRefHelper;

import software.wings.beans.CustomSecretNGManagerConfig;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class NGManagerCustomEncryptorTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  private CustomSecretNGManagerConfig customSecretNGManagerConfig;
  @Mock private IdentifierRef identifierRef;
  @Mock private NGManagerEncryptorHelper ngManagerEncryptorHelper;
  SecretResponseWrapper secretResponseWrapper;
  @Mock(name = "PRIVILEGED") SecretNGManagerClient secretManagerClient;
  @Mock SshKeySpecDTOHelper sshKeySpecDTOHelper;
  List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
  @InjectMocks private NGManagerCustomEncryptor ngManagerCustomEncryptor;
  MockedStatic<NGRestUtils> ngRestUtilsMockedStatic;

  @Before
  public void setup() {
    customSecretNGManagerConfig = CustomSecretNGManagerConfig.builder().build();
    ngRestUtilsMockedStatic = mockStatic(NGRestUtils.class);
    mockStatic(IdentifierRefHelper.class);
    when(IdentifierRefHelper.getIdentifierRef(any(), any(), any(), any())).thenReturn(identifierRef);
    when(sshKeySpecDTOHelper.getSSHKeyEncryptionDetails(any(), any())).thenReturn(encryptedDataDetails);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testAddSSHSupportedConfig() {
    secretResponseWrapper = SecretResponseWrapper.builder().secret(SecretDTOV2.builder().build()).build();
    ngRestUtilsMockedStatic.when(NGRestUtils.getResponse(any(), any())).thenReturn(secretResponseWrapper);
    int port = nextInt();
    SSHKeySpecDTO sshKeySpecDTO =
        SSHKeySpecDTO.builder().port(port).auth(SSHAuthDTO.builder().type(SSHAuthScheme.SSH).build()).build();
    secretResponseWrapper.getSecret().setSpec(sshKeySpecDTO);
    ngManagerCustomEncryptor.addSSHSupportedConfig(customSecretNGManagerConfig);
    assertThat(customSecretNGManagerConfig.getSshKeySpecDTO()).isNotNull();
    assertThat(customSecretNGManagerConfig.getSshKeySpecDTO()).isEqualTo(sshKeySpecDTO);
    assertThat(customSecretNGManagerConfig.getSshKeyEncryptionDetails()).isNotNull();
    assertThat(customSecretNGManagerConfig.getSshKeyEncryptionDetails()).isEqualTo(encryptedDataDetails);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_whenSecretIsNull_throwsException() {
    String connectorRef = randomAlphabetic(10);
    customSecretNGManagerConfig.setConnectorRef(connectorRef);
    ngRestUtilsMockedStatic.when(NGRestUtils.getResponse(any(), any())).thenReturn(secretResponseWrapper);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> ngManagerCustomEncryptor.addSSHSupportedConfig(customSecretNGManagerConfig));
    assertThatThrownBy(() -> ngManagerCustomEncryptor.addSSHSupportedConfig(customSecretNGManagerConfig))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No secret configured with identifier: " + connectorRef);
  }
}
