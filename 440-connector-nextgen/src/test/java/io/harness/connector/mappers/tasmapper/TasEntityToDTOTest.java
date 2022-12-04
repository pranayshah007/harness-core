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
import io.harness.delegate.beans.connector.tasconnector.TasCredentialType;
import io.harness.delegate.beans.connector.tasconnector.TasManualDetailsDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class TasEntityToDTOTest extends CategoryTest {
  @InjectMocks TasEntityToDTO tasEntityToDTO;
  private static final String URL = "endpoint_url";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testToConnectorEntity() {
    TasConnectorDTO tasConnectorDTO = tasEntityToDTO.createConnectorDTO(getConnectorConfigDTO(true));
    validate(tasConnectorDTO);

    assertThatThrownBy(() -> tasEntityToDTO.createConnectorDTO(getConnectorConfigDTO(false)))
        .hasMessage("Invalid Credential type.");
  }

  private void validate(TasConnectorDTO tasConnectorDTO) {
    assertThat(tasConnectorDTO.getCredential().getType()).isEqualTo(TasCredentialType.MANUAL_CREDENTIALS);
    assertThat(tasConnectorDTO.getCredential().getSpec()).isInstanceOf(TasManualDetailsDTO.class);
    TasManualDetailsDTO credential = (TasManualDetailsDTO) tasConnectorDTO.getCredential().getSpec();
    assertThat(credential.getEndpointUrl()).isEqualTo(URL);
  }

  private TasConfig getConnectorConfigDTO(boolean manualCred) {
    if (manualCred) {
      return TasConfig.builder()
          .credentialType(TasCredentialType.MANUAL_CREDENTIALS)
          .credential(TasManualCredential.builder().endpointUrl(URL).build())
          .build();
    } else {
      return TasConfig.builder().credentialType(null).build();
    }
  }
}
