package io.harness.idp.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.dtos.AppConfigDTO;
import io.harness.idp.entities.AppConfig;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class AppConfigMapper {
    public AppConfigDTO toDTO(AppConfig appConfig) {
        return AppConfigDTO.builder().build();
    }

    public AppConfig fromDTO(AppConfigDTO appConfigDTO) {
        return AppConfig.builder().build();
    }
}
