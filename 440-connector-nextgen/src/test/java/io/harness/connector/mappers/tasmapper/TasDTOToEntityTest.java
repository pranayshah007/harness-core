/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.connector.mappers.tasmapper;

import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.tasconnector.TasConfig;
import io.harness.connector.entities.embedded.tasconnector.TasManualCredential;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialDTO;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialType;
import io.harness.delegate.beans.connector.tasconnector.TasManualDetailsDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class TasDTOToEntityTest extends CategoryTest {
  @InjectMocks TasDTOToEntity tasDTOToEntity;
  private static final String URL = "endpoint_url";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testToConnectorEntity() {
    TasConfig tasConfig = tasDTOToEntity.toConnectorEntity(getConnectorConfigDTO(true));
    validate(tasConfig);

    assertThatThrownBy(() -> tasDTOToEntity.toConnectorEntity(getConnectorConfigDTO(false)))
        .hasMessage("Invalid Credential type.");
  }

  private void validate(TasConfig tasConfig) {
    assertThat(tasConfig).isNotNull();
    assertThat(tasConfig.getCredentialType()).isEqualTo(TasCredentialType.MANUAL_CREDENTIALS);
    assertThat(tasConfig.getCredential()).isInstanceOf(TasManualCredential.class);
    TasManualCredential credential = (TasManualCredential) tasConfig.getCredential();
    assertThat(credential.getEndpointUrl()).isEqualTo(URL);
  }

  private TasConnectorDTO getConnectorConfigDTO(boolean manualCred) {
    if (manualCred) {
      return TasConnectorDTO.builder()
          .credential(TasCredentialDTO.builder()
                          .type(TasCredentialType.MANUAL_CREDENTIALS)
                          .spec(TasManualDetailsDTO.builder().endpointUrl(URL).build())
                          .build())
          .build();
    } else {
      return TasConnectorDTO.builder().credential(TasCredentialDTO.builder().type(null).build()).build();
    }
  }
}
