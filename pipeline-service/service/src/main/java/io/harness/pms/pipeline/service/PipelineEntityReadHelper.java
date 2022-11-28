package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.pipeline.PipelineEntity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class PipelineEntityReadHelper {
  @Inject @Named("secondary-mongo") public MongoTemplate secondaryMongoTemplate;

  public long findCount(Query query) {
    return secondaryMongoTemplate.count(Query.of(query).limit(-1).skip(-1), PipelineEntity.class);
  }
}
