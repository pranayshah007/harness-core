package io.harness.connector.heartbeat;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.connector.tasconnector.TasValidationParams;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.List;

@OwnedBy(HarnessTeam.CDP)
public class TasConnectorValidationParamsProvider implements ConnectorValidationParamsProvider {
  @Inject EncryptionHelper encryptionHelper;

  @Override
  public ConnectorValidationParams getConnectorValidationParams(ConnectorInfoDTO connectorInfoDTO, String connectorName,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    final TasConnectorDTO connectorConfig = (TasConnectorDTO) connectorInfoDTO.getConnectorConfig();
    final List<DecryptableEntity> decryptableEntityList = connectorConfig.getDecryptableEntities();
    DecryptableEntity decryptableEntity = null;
    if (isNotEmpty(decryptableEntityList)) {
      decryptableEntity = decryptableEntityList.get(0);
    }
    final List<EncryptedDataDetail> encryptionDetail =
        encryptionHelper.getEncryptionDetail(decryptableEntity, accountIdentifier, orgIdentifier, projectIdentifier);
    return TasValidationParams.builder()
        .tasConnectorDTO(connectorConfig)
        .encryptionDetails(encryptionDetail)
        .connectorName(connectorName)
        .build();
  }
}
