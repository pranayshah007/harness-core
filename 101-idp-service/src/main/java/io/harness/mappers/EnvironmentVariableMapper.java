package io.harness.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.EnvironmentVariableDTO;
import io.harness.entities.EnvironmentVariable;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class EnvironmentVariableMapper {
    public EnvironmentVariableDTO toDTO(EnvironmentVariable environmentVariable) {
        return EnvironmentVariableDTO.builder()
                .envName(environmentVariable.getEnvName())
                .secretIdentifier(environmentVariable.getSecretIdentifier())
                .accountIdentifier(environmentVariable.getAccountIdentifier())
                .createdAt(environmentVariable.getCreatedAt())
                .lastModifiedAt(environmentVariable.getLastModifiedAt())
                .isDeleted(environmentVariable.isDeleted())
                .deletedAt(environmentVariable.getDeletedAt())
                .build();
    }

    public EnvironmentVariable fromDTO(EnvironmentVariableDTO environmentVariableDTO) {
        return EnvironmentVariable.builder()
                .envName(environmentVariableDTO.getEnvName())
                .secretIdentifier(environmentVariableDTO.getSecretIdentifier())
                .accountIdentifier(environmentVariableDTO.getAccountIdentifier())
                .createdAt(environmentVariableDTO.getCreatedAt())
                .lastModifiedAt(environmentVariableDTO.getLastModifiedAt())
                .isDeleted(environmentVariableDTO.isDeleted())
                .deletedAt(environmentVariableDTO.getDeletedAt())
                .build();
    }
}
