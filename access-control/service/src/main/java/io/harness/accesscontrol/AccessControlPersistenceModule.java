/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.acl.ACLPersistenceConfig;
import io.harness.accesscontrol.commons.outbox.OutboxPersistenceConfig;
import io.harness.accesscontrol.permissions.persistence.PermissionPersistenceConfig;
import io.harness.accesscontrol.preference.persistence.AccessControlPreferencePersistenceConfig;
import io.harness.accesscontrol.principals.serviceaccounts.persistence.ServiceAccountPersistenceConfig;
import io.harness.accesscontrol.principals.usergroups.persistence.UserGroupPersistenceConfig;
import io.harness.accesscontrol.principals.users.persistence.UserPersistenceConfig;
import io.harness.accesscontrol.resources.resourcegroups.persistence.ResourceGroupPersistenceConfig;
import io.harness.accesscontrol.resources.resourcetypes.persistence.ResourceTypePersistenceConfig;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentPersistenceConfig;
import io.harness.accesscontrol.roleassignments.privileged.persistence.PrivilegedRoleAssignmentMorphiaRegistrar;
import io.harness.accesscontrol.roleassignments.privileged.persistence.PrivilegedRoleAssignmentsPersistenceConfig;
import io.harness.accesscontrol.roles.persistence.RolePersistenceConfig;
import io.harness.accesscontrol.scopes.core.persistence.ScopePersistenceConfig;
import io.harness.accesscontrol.support.persistence.SupportMorphiaRegistrar;
import io.harness.accesscontrol.support.persistence.SupportPersistenceConfig;
import io.harness.aggregator.AggregatorPersistenceConfig;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.morphia.OutboxEventMorphiaRegistrar;
import io.harness.springdata.HTransactionTemplate;
import io.harness.springdata.PersistenceModule;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import org.mongodb.morphia.converters.TypeConverter;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class AccessControlPersistenceModule extends PersistenceModule {
  private static AccessControlPersistenceModule instance;
  private final MongoConfig mongoConfig;

  private AccessControlPersistenceModule(MongoConfig mongoConfig) {
    this.mongoConfig = mongoConfig;
  }

  public static synchronized AccessControlPersistenceModule getInstance(MongoConfig mongoConfig) {
    if (instance == null) {
      instance = new AccessControlPersistenceModule(mongoConfig);
    }
    return instance;
  }

  @Provides
  @Singleton
  MongoConfig mongoConfig() {
    return mongoConfig;
  }

  @Provides
  @Singleton
  protected TransactionTemplate getTransactionTemplate(
      MongoTransactionManager mongoTransactionManager, MongoConfig mongoConfig) {
    return new HTransactionTemplate(mongoTransactionManager, mongoConfig.isTransactionsEnabled());
  }

  @Override
  public void configure() {
    super.configure();
    install(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new NoopUserProvider();
      }
    });
    Multibinder<Class<? extends KryoRegistrar>> kryoRegistrar =
        Multibinder.newSetBinder(binder(), new TypeLiteral<Class<? extends KryoRegistrar>>() {});

    Multibinder<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
        Multibinder.newSetBinder(binder(), new TypeLiteral<Class<? extends MorphiaRegistrar>>() {});
    morphiaRegistrars.addBinding().toInstance(OutboxEventMorphiaRegistrar.class);
    morphiaRegistrars.addBinding().toInstance(SupportMorphiaRegistrar.class);
    morphiaRegistrars.addBinding().toInstance(PrivilegedRoleAssignmentMorphiaRegistrar.class);

    Multibinder<Class<? extends TypeConverter>> morphiaConverters =
        Multibinder.newSetBinder(binder(), new TypeLiteral<Class<? extends TypeConverter>>() {});
    MapBinder<Class, String> morphiaClasses = MapBinder.newMapBinder(
        binder(), new TypeLiteral<Class>() {}, new TypeLiteral<String>() {}, Names.named("morphiaClasses"));
    bind(HPersistence.class).to(MongoPersistence.class);
  }

  @Override
  protected Class<?>[] getConfigClasses() {
    return new Class[] {OutboxPersistenceConfig.class, ResourceTypePersistenceConfig.class,
        ResourceGroupPersistenceConfig.class, ScopePersistenceConfig.class, UserPersistenceConfig.class,
        ServiceAccountPersistenceConfig.class, UserGroupPersistenceConfig.class, PermissionPersistenceConfig.class,
        RolePersistenceConfig.class, RoleAssignmentPersistenceConfig.class, ACLPersistenceConfig.class,
        AggregatorPersistenceConfig.class, AccessControlPreferencePersistenceConfig.class,
        PrivilegedRoleAssignmentsPersistenceConfig.class, SupportPersistenceConfig.class};
  }
}
