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
public class TerraformCloudEntityToDTOTest extends CategoryTest {
  @InjectMocks TerraformCloudEntityToDTO terraformCloudEntityToDTO;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testCreateConnectorDTO() {
    String url = "https://some.io";
    SecretRefData keyTokenRef = SecretRefData.builder().identifier("tokenRef").scope(Scope.ACCOUNT).build();

    final TerraformCloudConfig terraformCloudConfig =
        TerraformCloudConfig.builder()
            .url(url)
            .credentialType(API_TOKEN)
            .credential(TerraformCloudTokenCredential.builder().tokenRef("account.tokenRef").build())
            .build();

    final TerraformCloudConnectorDTO connectorDTO = terraformCloudEntityToDTO.createConnectorDTO(terraformCloudConfig);

    assertThat(connectorDTO).isNotNull();
    assertThat(connectorDTO.getTerraformCloudUrl()).isEqualTo(url);
    assertThat(connectorDTO.getCredential()).isNotNull();
    assertThat(connectorDTO.getCredential().getType()).isEqualTo(API_TOKEN);
    assertThat(connectorDTO.getCredential().getSpec()).isNotNull();
    TerraformCloudTokenCredentialsDTO terraformCloudTokenCredentialsDTO =
        (TerraformCloudTokenCredentialsDTO) connectorDTO.getCredential().getSpec();
    assertThat(terraformCloudTokenCredentialsDTO.getApiToken()).isEqualTo(keyTokenRef);
  }
}
