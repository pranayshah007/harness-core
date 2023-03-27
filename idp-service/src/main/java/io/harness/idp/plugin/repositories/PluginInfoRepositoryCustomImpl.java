/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.plugin.repositories;

import io.harness.idp.plugin.beans.PluginInfoEntity;
import io.harness.idp.plugin.beans.PluginInfoEntity.PluginInfoEntityKeys;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class PluginInfoRepositoryCustomImpl implements PluginInfoRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public PluginInfoEntity saveOrUpdate(PluginInfoEntity pluginInfoEntity) {
    Criteria criteria = Criteria.where(PluginInfoEntityKeys.identifier).is(pluginInfoEntity.getIdentifier());
    PluginInfoEntity entity = findByIdentifier(criteria);
    if (entity == null) {
      return mongoTemplate.save(pluginInfoEntity);
    }
    Query query = new Query(criteria);
    Update update = buildUpdateQuery(pluginInfoEntity);
    FindAndModifyOptions options = new FindAndModifyOptions().returnNew(true);
    return mongoTemplate.findAndModify(query, update, options, PluginInfoEntity.class);
  }

  private PluginInfoEntity findByIdentifier(Criteria criteria) {
    return mongoTemplate.findOne(Query.query(criteria), PluginInfoEntity.class);
  }

  private Update buildUpdateQuery(PluginInfoEntity pluginInfoEntity) {
    Update update = new Update();
    update.set(PluginInfoEntityKeys.name, pluginInfoEntity.getName());
    update.set(PluginInfoEntityKeys.description, pluginInfoEntity.getDescription());
    update.set(PluginInfoEntityKeys.createdBy, pluginInfoEntity.getCreatedBy());
    update.set(PluginInfoEntityKeys.category, pluginInfoEntity.getCategory());
    update.set(PluginInfoEntityKeys.source, pluginInfoEntity.getSource());
    update.set(PluginInfoEntityKeys.iconUrl, pluginInfoEntity.getIconUrl());
    update.set(PluginInfoEntityKeys.layout, pluginInfoEntity.getLayout());
    update.set(PluginInfoEntityKeys.config, pluginInfoEntity.getConfig());
    return update;
  }
}
