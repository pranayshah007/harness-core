package io.harness.idp.mappers;

import io.harness.idp.dtos.AppConfigDTO;
import io.harness.idp.entities.AppConfig;

public class AppConfigMapper {
    public AppConfigDTO toDTO(AppConfig appConfig) {
        return AppConfigDTO.builder().build();
    }

    public AppConfig fromDTO(AppConfigDTO appConfigDTO) {
        return AppConfig.builder().build();
    }
}
