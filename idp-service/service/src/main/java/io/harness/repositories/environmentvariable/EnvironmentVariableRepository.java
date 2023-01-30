package io.harness.repositories.environmentvariable;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.entity.EnvironmentVariable;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface EnvironmentVariableRepository extends CrudRepository<EnvironmentVariable, String>,
        EnvironmentVariableRepositoryCustom {
    Optional<EnvironmentVariable> findByEnvNameAndAccountIdentifier(String envName, String accountIdentifier);
}
