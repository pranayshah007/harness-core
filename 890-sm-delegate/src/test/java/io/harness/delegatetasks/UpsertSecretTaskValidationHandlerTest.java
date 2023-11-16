package io.harness.delegatetasks;

import static io.harness.SecretConstants.EXPIRES_ON;
import static io.harness.rule.OwnerRule.NISHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConnectorDTO;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultValidationParams;
import io.harness.encryption.SecretRefData;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class UpsertSecretTaskValidationHandlerTest {
  @Mock private VaultEncryptorsRegistry vaultEncryptorsRegistry;
  @Mock private NGErrorHelper ngErrorHelper;
  @InjectMocks private UpsertSecretTaskValidationHandler upsertSecretTaskValidationHandler;

  @Before
  public void init() {
    MockitoAnnotations.openMocks(this);
  }
  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateAzureKeyVaultTaskParamsForExpiry() {
    try (MockedStatic<UpsertSecretTask> upsertSecretTaskMockedStatic = Mockito.mockStatic(UpsertSecretTask.class)) {
      upsertSecretTaskMockedStatic.when(() -> UpsertSecretTask.run(any(), any()))
          .thenReturn(UpsertSecretTaskResponse.builder().encryptedRecord(NGEncryptedData.builder().build()).build());
      ConnectorValidationParams connectorValidationParams =
          AzureKeyVaultValidationParams.builder()
              .connectorName("azureConnector")
              .azurekeyvaultConnectorDTO(AzureKeyVaultConnectorDTO.builder()
                                             .subscription("sub")
                                             .vaultName("vname")
                                             .clientId("cid")
                                             .tenantId("tid")
                                             .secretKey(SecretRefData.builder().build())
                                             .build())
              .build();

      upsertSecretTaskValidationHandler.validate(connectorValidationParams, "accountIdentifier");
      ArgumentCaptor<UpsertSecretTaskParameters> argumentCaptor =
          ArgumentCaptor.forClass(UpsertSecretTaskParameters.class);
      upsertSecretTaskMockedStatic.verify(() -> UpsertSecretTask.run(argumentCaptor.capture(), any()));
      UpsertSecretTaskParameters upsertTaskParameters = argumentCaptor.getValue();
      assertThat(upsertTaskParameters).isNotNull();
      assertThat(upsertTaskParameters.getAdditionalMetadata()).isNotNull();
      assertThat(upsertTaskParameters.getAdditionalMetadata().getValues()).containsKey(EXPIRES_ON);
    }
  }
}
