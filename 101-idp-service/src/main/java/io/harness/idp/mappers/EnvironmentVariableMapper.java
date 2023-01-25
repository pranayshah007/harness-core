package io.harness.idp.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.dtos.AppConfigDTO;
import io.harness.idp.dtos.EnvironmentVariableDTO;
import io.harness.idp.entities.AppConfig;
import io.harness.idp.entities.EnvironmentVariable;
import lombok.Builder;
import lombok.Getter;

@OwnedBy(HarnessTeam.IDP)
@Getter
@Builder
public class EnvironmentVariableMapper {
    public EnvironmentVariableDTO toDTO(EnvironmentVariable environmentVariable) {
        return EnvironmentVariableDTO.builder().build();
    }

    public EnvironmentVariable fromDTO(EnvironmentVariableDTO environmentVariableDTO) {
        return EnvironmentVariable.builder().build();
    }
}
