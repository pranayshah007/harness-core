/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.ngsettings.custom;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngsettings.entities.SettingsConfigurationState;
import io.harness.ngsettings.entities.SettingsConfigurationState.SettingsConfigurationStateKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Optional;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(PL)
@Singleton
public class ConfigurationStateRepository {
  private final MongoTemplate mongoTemplate;

  @Inject
  public ConfigurationStateRepository(@Named("mongoTemplate") MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  public Optional<SettingsConfigurationState> getByIdentifier(@NotEmpty String identifier) {
    Criteria criteria = Criteria.where(SettingsConfigurationStateKeys.identifier).is(identifier);
    return Optional.ofNullable(mongoTemplate.findOne(new Query(criteria), SettingsConfigurationState.class));
  }

  public void upsert(@NotNull SettingsConfigurationState configurationState) {
    mongoTemplate.save(configurationState);
  }
}
