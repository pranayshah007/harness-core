package io.harness.mongo.helper;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.mongo.helper.MongoConstants.SECONDARY;

import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@OwnedBy(PL)
@Singleton
public class SecondaryMongoTemplateHolder {
  @Inject @Named(SECONDARY) public MongoTemplate secondaryMongoTemplate;

  public MongoTemplate getSecondaryMongoTemplate() {
    return secondaryMongoTemplate;
  }
}
