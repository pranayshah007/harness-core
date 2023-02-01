/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.terraformcloudmapper;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialType.API_TOKEN;
import static io.harness.rule.OwnerRule.BUHA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.terraformcloudconncetor.TerraformCloudConfig;
import io.harness.connector.entities.embedded.terraformcloudconncetor.TerraformCloudTokenCredential;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudTokenCredentialsDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class TerraformCloudDTOToEntityTest extends CategoryTest {
  @InjectMocks TerraformCloudDTOToEntity terraformCloudDTOToEntity;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testTerraformCloudWithApiTokenDtoToConnectorEntity() {
    String url = "https://some.io";
    SecretRefData keyTokenRef = SecretRefData.builder().identifier("tokenRef").scope(Scope.ACCOUNT).build();
    TerraformCloudConnectorDTO terraformCloudConnectorDTO =
        TerraformCloudConnectorDTO.builder()
            .terraformCloudUrl(url)
            .credential(TerraformCloudCredentialDTO.builder()
                            .type(API_TOKEN)
                            .spec(TerraformCloudTokenCredentialsDTO.builder().apiToken(keyTokenRef).build())
                            .build())
            .build();

    final TerraformCloudConfig terraformCloudConfig =
        terraformCloudDTOToEntity.toConnectorEntity(terraformCloudConnectorDTO);

    assertThat(terraformCloudConfig).isNotNull();
    assertThat(terraformCloudConfig.getCredentialType()).isEqualTo(API_TOKEN);
    assertThat(terraformCloudConfig.getUrl()).isEqualTo(url);
    assertThat(terraformCloudConfig.getCredential()).isNotNull();

    TerraformCloudTokenCredential terraformCloudTokenCredential =
        (TerraformCloudTokenCredential) terraformCloudConfig.getCredential();
    assertThat(terraformCloudTokenCredential.getTokenRef()).isEqualTo(keyTokenRef.toSecretRefStringValue());
  }
}