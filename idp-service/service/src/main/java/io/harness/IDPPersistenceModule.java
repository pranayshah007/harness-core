package io.harness;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.MongoConfig;
import io.harness.springdata.HTransactionTemplate;
import io.harness.springdata.SpringPersistenceConfig;
import io.harness.springdata.SpringPersistenceModule;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@OwnedBy(HarnessTeam.IDP)
public class IDPPersistenceModule extends SpringPersistenceModule {
    @Override
    protected Class<?>[] getConfigClasses() {
        List<Class<?>> resultClasses = Lists.newArrayList(ImmutableList.of(SpringPersistenceConfig.class));
        Class<?>[] resultClassesArray = new Class<?>[ resultClasses.size() ];
        return resultClasses.toArray(resultClassesArray);
    }

    @Provides
    @Singleton
    protected TransactionTemplate getTransactionTemplate(
            MongoTransactionManager mongoTransactionManager, MongoConfig mongoConfig) {
        return new HTransactionTemplate(mongoTransactionManager, mongoConfig.isTransactionsEnabled());
    }
}
