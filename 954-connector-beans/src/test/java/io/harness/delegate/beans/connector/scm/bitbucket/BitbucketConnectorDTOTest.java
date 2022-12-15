/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.bitbucket;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CI)
public class BitbucketConnectorDTOTest extends CategoryTest {
  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testValidate() {
    BitbucketConnectorDTO bitbucketConnectorDTO = getBitbucketConnectorDTO();
    bitbucketConnectorDTO.validate();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testValidateWithoutAPIAccess() {
    BitbucketConnectorDTO bitbucketConnectorDTO = getBitbucketConnectorDTO();
    bitbucketConnectorDTO.setApiAccess(null);
    bitbucketConnectorDTO.validate();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testValidateWithBothUsernameAsSecretText() {
    BitbucketConnectorDTO bitbucketConnectorDTO = getBitbucketConnectorDTO();
    BitbucketHttpCredentialsDTO bitbucketHttpCredentialsDTO =
        (BitbucketHttpCredentialsDTO) bitbucketConnectorDTO.getAuthentication().getCredentials();
    BitbucketUsernamePasswordDTO bitbucketUsernamePasswordDTO =
        (BitbucketUsernamePasswordDTO) bitbucketHttpCredentialsDTO.getHttpCredentialsSpec();
    bitbucketUsernamePasswordDTO.setUsername(null);
    bitbucketUsernamePasswordDTO.setUsernameRef(SecretRefData.builder().identifier("identifier").build());

    BitbucketApiAccessDTO bitbucketApiAccessDTO = bitbucketConnectorDTO.getApiAccess();
    BitbucketUsernameTokenApiAccessDTO bitbucketUsernameTokenApiAccessDTO =
        (BitbucketUsernameTokenApiAccessDTO) bitbucketApiAccessDTO.getSpec();
    bitbucketUsernameTokenApiAccessDTO.setUsername(null);
    bitbucketUsernameTokenApiAccessDTO.setUsernameRef(SecretRefData.builder().identifier("identifier").build());

    bitbucketConnectorDTO.validate();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testValidateWithMixedUsernameType() {
    BitbucketConnectorDTO bitbucketConnectorDTO = getBitbucketConnectorDTO();
    BitbucketHttpCredentialsDTO bitbucketHttpCredentialsDTO =
        (BitbucketHttpCredentialsDTO) bitbucketConnectorDTO.getAuthentication().getCredentials();
    BitbucketUsernamePasswordDTO bitbucketUsernamePasswordDTO =
        (BitbucketUsernamePasswordDTO) bitbucketHttpCredentialsDTO.getHttpCredentialsSpec();

    BitbucketApiAccessDTO bitbucketApiAccessDTO = bitbucketConnectorDTO.getApiAccess();
    BitbucketUsernameTokenApiAccessDTO bitbucketUsernameTokenApiAccessDTO =
        (BitbucketUsernameTokenApiAccessDTO) bitbucketApiAccessDTO.getSpec();
    bitbucketUsernameTokenApiAccessDTO.setUsername(null);
    bitbucketUsernameTokenApiAccessDTO.setUsernameRef(SecretRefData.builder().identifier("identifier").build());
    assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> bitbucketConnectorDTO.validate());

    bitbucketUsernamePasswordDTO.setUsername(null);
    bitbucketUsernamePasswordDTO.setUsernameRef(SecretRefData.builder().identifier("identifier").build());

    bitbucketUsernameTokenApiAccessDTO.setUsername("username");
    bitbucketUsernameTokenApiAccessDTO.setUsernameRef(null);
    assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> bitbucketConnectorDTO.validate());
  }

  private BitbucketConnectorDTO getBitbucketConnectorDTO() {
    return BitbucketConnectorDTO.builder()
        .connectionType(GitConnectionType.REPO)
        .url("https://bitbucket.org/user/repo.git")
        .authentication(
            BitbucketAuthenticationDTO.builder()
                .authType(GitAuthType.HTTP)
                .credentials(
                    BitbucketHttpCredentialsDTO.builder()
                        .type(BitbucketHttpAuthenticationType.USERNAME_AND_PASSWORD)
                        .httpCredentialsSpec(BitbucketUsernamePasswordDTO.builder()
                                                 .username("username")
                                                 .passwordRef(SecretRefData.builder().identifier("identifier").build())
                                                 .build())
                        .build())
                .build())
        .apiAccess(BitbucketApiAccessDTO.builder()
                       .type(BitbucketApiAccessType.USERNAME_AND_TOKEN)
                       .spec(BitbucketUsernameTokenApiAccessDTO.builder()
                                 .username("username")
                                 .tokenRef(SecretRefData.builder().identifier("identifier").build())
                                 .build())
                       .build())
        .build();
  }
}
