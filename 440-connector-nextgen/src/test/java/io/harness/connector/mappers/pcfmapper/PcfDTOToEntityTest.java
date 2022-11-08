package io.harness.connector.mappers.pcfmapper;

import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.pcfconnector.PcfConfig;
import io.harness.connector.entities.embedded.pcfconnector.PcfManualCredential;
import io.harness.delegate.beans.connector.pcfconnector.PcfConnectorDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfCredentialDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfCredentialType;
import io.harness.delegate.beans.connector.pcfconnector.PcfManualDetailsDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class PcfDTOToEntityTest extends CategoryTest {
  @InjectMocks PcfDTOToEntity pcfDTOToEntity;
  private static final String URL = "endpoint_url";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testToConnectorEntity() {
    PcfConfig pcfConfig = pcfDTOToEntity.toConnectorEntity(getConnectorConfigDTO(true));
    validate(pcfConfig);

    assertThatThrownBy(() -> pcfDTOToEntity.toConnectorEntity(getConnectorConfigDTO(false)))
        .hasMessage("Invalid Credential type.");
  }

  private void validate(PcfConfig pcfConfig) {
    assertThat(pcfConfig).isNotNull();
    assertThat(pcfConfig.getCredentialType()).isEqualTo(PcfCredentialType.MANUAL_CREDENTIALS);
    assertThat(pcfConfig.getCredential()).isInstanceOf(PcfManualCredential.class);
    PcfManualCredential credential = (PcfManualCredential) pcfConfig.getCredential();
    assertThat(credential.getEndpointUrl()).isEqualTo(URL);
  }

  private PcfConnectorDTO getConnectorConfigDTO(boolean manualCred) {
    if (manualCred) {
      return PcfConnectorDTO.builder()
          .credential(PcfCredentialDTO.builder()
                          .type(PcfCredentialType.MANUAL_CREDENTIALS)
                          .spec(PcfManualDetailsDTO.builder().endpointUrl(URL).build())
                          .build())
          .build();
    } else {
      return PcfConnectorDTO.builder().credential(PcfCredentialDTO.builder().type(null).build()).build();
    }
  }
}