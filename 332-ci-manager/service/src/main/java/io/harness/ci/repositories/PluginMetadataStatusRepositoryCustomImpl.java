package io.harness.repositories;

import io.harness.app.beans.entities.PluginMetadataStatus;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class PluginMetadataStatusRepositoryCustomImpl implements PluginMetadataStatusRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public PluginMetadataStatus find() {
    Query query = new Query();
    return mongoTemplate.findOne(query, PluginMetadataStatus.class);
  }
}
