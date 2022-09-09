package io.harness.ngsettings.services.impl.validators;

// import io.harness.connector.ConnectorCategory;
// import io.harness.connector.ConnectorFilterPropertiesDTO;
// import io.harness.connector.ConnectorResourceClient;
// import io.harness.connector.ConnectorResponseDTO;
// import io.harness.exception.InvalidRequestException;
// import io.harness.ngsettings.dto.SettingDTO;
// import io.harness.ngsettings.services.SettingValidator;

import com.google.inject.Inject;
import io.harness.exception.InvalidRequestException;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.services.SettingValidator;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class BuiltInHarnessSMValidator implements SettingValidator {
//    @Inject ConnectorResourceClient connectorResourceClient;
    @Override
    public void validate(String accountIdentifier, SettingDTO oldSettingDTO, SettingDTO newSettingDTO) {
//      try {
//        List<ConnectorResponseDTO> connectorResponseDTOList =
//            connectorResourceClient
//                .listConnectors(accountIdentifier, null, null, 0, 10,
//                    ConnectorFilterPropertiesDTO.builder()
//                        .categories(Arrays.asList(ConnectorCategory.SECRET_MANAGER))
//                        .build(),
//                    false)
//                .execute()
//                .body()
//                .getData()
//                .getContent();
//        if (connectorResponseDTOList.size() < 2) {
//          throw new InvalidRequestException(String.format(
//              "Cannot disable BuiltIn Harness Secrets Manager as there is not other Secret Manager present in the
//              account."));
//        }
//      } catch (IOException e) {
//        throw new RuntimeException(e);
//      }
        throw new InvalidRequestException("validation failed");
    }
}
