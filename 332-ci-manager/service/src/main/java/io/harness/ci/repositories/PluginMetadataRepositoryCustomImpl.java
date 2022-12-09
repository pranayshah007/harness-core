/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.app.beans.entities.PluginMetadataConfig;
import io.harness.app.beans.entities.PluginMetadataConfig.PluginMetadataConfigKeys;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class PluginMetadataRepositoryCustomImpl implements PluginMetadataRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public boolean deleteAllExcept(int latestVersion) {
    Query query = new Query().addCriteria(Criteria.where(PluginMetadataConfigKeys.version).ne(latestVersion));
    return mongoTemplate.remove(query, PluginMetadataConfig.class).wasAcknowledged();
  }

  @Override
  public Page<PluginMetadataConfig> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);

    List<PluginMetadataConfig> pluginMetadataConfigs = mongoTemplate.find(query, PluginMetadataConfig.class);
    return PageableExecutionUtils.getPage(pluginMetadataConfigs, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), PluginMetadataConfig.class));
  }
}
