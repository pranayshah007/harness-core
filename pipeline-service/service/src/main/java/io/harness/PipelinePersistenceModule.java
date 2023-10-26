/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.persistance.GitSyncablePersistenceConfig;
import io.harness.mongo.MongoConfig;
import io.harness.notification.NotificationChannelPersistenceConfig;
import io.harness.pms.outbox.PipelineOutboxPersistenceConfig;
import io.harness.pms.template.TemplatePersistenceConfig;
import io.harness.springdata.HMongoTemplate;
import io.harness.springdata.HTransactionTemplate;
import io.harness.springdata.SpringPersistenceConfig;
import io.harness.springdata.SpringPersistenceModule;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.ReadPreference;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PIPELINE)
public class PipelinePersistenceModule extends SpringPersistenceModule {
  @Override
  protected Class<?>[] getConfigClasses() {
    return new Class[] {SpringPersistenceConfig.class, NotificationChannelPersistenceConfig.class,
        GitSyncablePersistenceConfig.class, PipelineOutboxPersistenceConfig.class};
  }

  @Provides
  @Singleton
  protected TransactionTemplate getTransactionTemplate(
      MongoTransactionManager mongoTransactionManager, MongoConfig mongoConfig) {
    return new HTransactionTemplate(mongoTransactionManager, mongoConfig.isTransactionsEnabled());
  }

  @Provides
  @Singleton
  @Named("secondary-mongo")
  protected MongoTemplate getSecondaryMongoTemplate(MongoTemplate mongoTemplate, MongoConfig primaryMongoConfig) {
    HMongoTemplate template =
        new HMongoTemplate(mongoTemplate.getMongoDbFactory(), mongoTemplate.getConverter(), primaryMongoConfig);
    template.setReadPreference(ReadPreference.secondary());
    return template;
  }
}
