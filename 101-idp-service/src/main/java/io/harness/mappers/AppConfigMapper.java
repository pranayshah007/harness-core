package io.harness.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.AppConfigDTO;
import io.harness.entities.AppConfig;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class AppConfigMapper {
    public AppConfigDTO toDTO(AppConfig appConfig) {
        return AppConfigDTO.builder()
                .accountIdentifier(appConfig.getAccountIdentifier())
                .createdAt(appConfig.getCreatedAt())
                .lastModifiedAt(appConfig.getLastModifiedAt())
                .isDeleted(appConfig.isDeleted())
                .deletedAt(appConfig.getDeletedAt())
                .build();
    }

    public AppConfig fromDTO(AppConfigDTO appConfigDTO) {
        return AppConfig.builder()
                .accountIdentifier(appConfigDTO.getAccountIdentifier())
                .createdAt(appConfigDTO.getCreatedAt())
                .lastModifiedAt(appConfigDTO.getLastModifiedAt())
                .isDeleted(appConfigDTO.isDeleted())
                .deletedAt(appConfigDTO.getDeletedAt())
                .build();
    }
}
