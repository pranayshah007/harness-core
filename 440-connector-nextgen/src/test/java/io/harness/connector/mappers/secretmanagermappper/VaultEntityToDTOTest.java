package io.harness.connector.mappers.secretmanagermappper;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.vaultconnector.VaultConnector;
import io.harness.connector.mappers.secretmanagermapper.VaultEntityToDTO;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class VaultEntityToDTOTest extends CategoryTest {
  @InjectMocks VaultEntityToDTO vaultEntityToDTO;
  String vaultURL = "https://vault.com";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testConnectorWithoutK8sAuthToDTO() {
    VaultConnector vaultConnector = VaultConnector.builder().vaultUrl(vaultURL).build();
    VaultConnectorDTO vaultConnectorDTO = vaultEntityToDTO.createConnectorDTO(vaultConnector);
    assertNotNull(vaultConnectorDTO);
    assertThat(vaultConnectorDTO.getK8sAuthEndpoint()).isNull();
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testConnectorWithK8sAuthToDTO() {
    VaultConnector vaultConnector = VaultConnector.builder().vaultUrl(vaultURL).useK8sAuth(true).build();
    VaultConnectorDTO vaultConnectorDTO = vaultEntityToDTO.createConnectorDTO(vaultConnector);
    assertNotNull(vaultConnectorDTO);
    assertThat(vaultConnectorDTO.getK8sAuthEndpoint()).isEqualTo("kubernetes");
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testConnector_ExistingVaultWithoutThisUseK8sAuthField() {
    VaultConnector vaultConnector = VaultConnector.builder().vaultUrl(vaultURL).useK8sAuth(null).build();
    VaultConnectorDTO vaultConnectorDTO = vaultEntityToDTO.createConnectorDTO(vaultConnector);
    assertNotNull(vaultConnectorDTO);
    assertThat(vaultConnectorDTO.getK8sAuthEndpoint()).isNull();
  }
}
