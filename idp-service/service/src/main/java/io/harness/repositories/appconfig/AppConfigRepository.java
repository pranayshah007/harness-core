package io.harness.repositories.appconfig;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.entity.AppConfig;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface AppConfigRepository extends CrudRepository<AppConfig, String>, AppConfigRepositoryCustom {
}
