/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.configmanager.ConfigType;
import io.harness.idp.configmanager.beans.entity.AppConfigEntity;
import io.harness.idp.configmanager.beans.entity.AppConfigEntity.AppConfigEntityKeys;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class AppConfigRepositoryCustomImpl implements AppConfigRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public AppConfigEntity updateConfig(AppConfigEntity appConfigEntity, ConfigType configType) {
    Criteria criteria =
        getCriteriaForConfig(appConfigEntity.getAccountIdentifier(), appConfigEntity.getConfigId(), configType);
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(AppConfigEntityKeys.configs, appConfigEntity.getConfigs());
    update.set(AppConfigEntityKeys.lastModifiedAt, System.currentTimeMillis());
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
    return mongoTemplate.findAndModify(query, update, options, AppConfigEntity.class);
  }

  @Override
  public AppConfigEntity updateConfigEnablement(
      String accountIdentifier, String configId, Boolean enabled, ConfigType configType) {
    Criteria criteria = getCriteriaForConfig(accountIdentifier, configId, configType);
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(AppConfigEntityKeys.enabled, enabled);
    update.set(AppConfigEntityKeys.enabledDisabledAt, System.currentTimeMillis());
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
    return mongoTemplate.findAndModify(query, update, options, AppConfigEntity.class);
  }

  @Override
  public List<AppConfigEntity> deleteDisabledPluginsConfigBasedOnTimestampsForEnabledDisabledTime(long baseTimeStamp) {
    Criteria criteria = Criteria.where(AppConfigEntityKeys.enabledDisabledAt)
                            .lte(baseTimeStamp)
                            .and(AppConfigEntityKeys.configType)
                            .is(ConfigType.PLUGIN)
                            .and(AppConfigEntityKeys.enabled)
                            .is(false);
    Query query = new Query(criteria);
    return mongoTemplate.findAllAndRemove(query, AppConfigEntity.class);
  }

  protected Criteria getCriteriaForConfig(String accountIdentifier, String configId, ConfigType configType) {
    return Criteria.where(AppConfigEntityKeys.accountIdentifier)
        .is(accountIdentifier)
        .and(AppConfigEntityKeys.configId)
        .is(configId)
        .and(AppConfigEntityKeys.configType)
        .is(configType);
  }
}
