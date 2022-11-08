package io.harness.connector.mappers.pcfmapper;

import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.pcfconnector.PcfConfig;
import io.harness.connector.entities.embedded.pcfconnector.PcfManualCredential;
import io.harness.delegate.beans.connector.pcfconnector.PcfConnectorDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfCredentialType;
import io.harness.delegate.beans.connector.pcfconnector.PcfManualDetailsDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class PcfEntityToDTOTest extends CategoryTest {
  @InjectMocks PcfEntityToDTO pcfEntityToDTO;
  private static final String URL = "endpoint_url";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testToConnectorEntity() {
    PcfConnectorDTO pcfConnectorDTO = pcfEntityToDTO.createConnectorDTO(getConnectorConfigDTO(true));
    validate(pcfConnectorDTO);

    assertThatThrownBy(() -> pcfEntityToDTO.createConnectorDTO(getConnectorConfigDTO(false)))
        .hasMessage("Invalid Credential type.");
  }

  private void validate(PcfConnectorDTO pcfConnectorDTO) {
    assertThat(pcfConnectorDTO.getCredential().getType()).isEqualTo(PcfCredentialType.MANUAL_CREDENTIALS);
    assertThat(pcfConnectorDTO.getCredential().getSpec()).isInstanceOf(PcfManualDetailsDTO.class);
    PcfManualDetailsDTO credential = (PcfManualDetailsDTO) pcfConnectorDTO.getCredential().getSpec();
    assertThat(credential.getEndpointUrl()).isEqualTo(URL);
  }

  private PcfConfig getConnectorConfigDTO(boolean manualCred) {
    if (manualCred) {
      return PcfConfig.builder()
          .credentialType(PcfCredentialType.MANUAL_CREDENTIALS)
          .credential(PcfManualCredential.builder().endpointUrl(URL).build())
          .build();
    } else {
      return PcfConfig.builder().credentialType(null).build();
    }
  }
}
