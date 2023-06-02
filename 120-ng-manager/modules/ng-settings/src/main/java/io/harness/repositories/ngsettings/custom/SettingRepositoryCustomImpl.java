/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.ngsettings.custom;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngsettings.entities.Setting;
import io.harness.ngsettings.entities.Setting.SettingKeys;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class SettingRepositoryCustomImpl implements SettingRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public Setting upsert(Setting setting) {
    Criteria criteria = Criteria.where(SettingKeys.identifier)
                            .is(setting.getIdentifier())
                            .and(SettingKeys.accountIdentifier)
                            .is(setting.getAccountIdentifier())
                            .and(SettingKeys.orgIdentifier)
                            .is(setting.getOrgIdentifier())
                            .and(SettingKeys.projectIdentifier)
                            .is(setting.getProjectIdentifier());
    Query query = new Query(criteria);
    FindAndReplaceOptions findAndReplaceOptions = FindAndReplaceOptions.options().upsert().returnNew();
    return mongoTemplate.findAndReplace(query, setting, findAndReplaceOptions);
  }

  @Override
  public List<Setting> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, Setting.class);
  }

  public DeleteResult deleteAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.remove(query, Setting.class);
  }

  @Override
  public UpdateResult updateMultiple(Query query, Update update) {
    return mongoTemplate.updateMulti(query, update, Setting.class);
  }
}
