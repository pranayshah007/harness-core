package io.harness.batch.processing.config;

import io.harness.notification.NotificationChannelPersistenceConfig;
import io.harness.springdata.SpringPersistenceModule;

public class BatchProcessingPersistenceModule extends SpringPersistenceModule {
  @Override
  protected Class<?>[] getConfigClasses() {
    return new Class[] {NotificationChannelPersistenceConfig.class};
  }
}
