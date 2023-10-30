/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator;

import static io.harness.aggregator.ACLGeneratorServiceFactory.SECONDARY_ACL_GENERATOR_SERVICE;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.acl.ACLService;
import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.permissions.persistence.repositories.InMemoryPermissionRepository;
import io.harness.accesscontrol.principals.usergroups.UserGroupService;
import io.harness.accesscontrol.resources.resourcegroups.ResourceGroupService;
import io.harness.accesscontrol.roles.RoleService;
import io.harness.accesscontrol.scopes.core.ScopeLevel;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.aggregator.consumers.ACLGeneratorService;
import io.harness.aggregator.consumers.AccessControlChangeConsumer;
import io.harness.aggregator.consumers.ChangeEventFailureHandler;
import io.harness.aggregator.consumers.RoleChangeConsumer;
import io.harness.aggregator.consumers.UserGroupChangeConsumer;
import io.harness.aggregator.models.RoleChangeEventData;
import io.harness.aggregator.models.UserGroupUpdateEventData;
import io.harness.annotations.dev.OwnedBy;
import io.harness.morphia.MorphiaRegistrar;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(PL)
@Slf4j
public class AggregatorModule extends AbstractModule {
  private static AggregatorModule instance;
  private final AggregatorConfiguration configuration;

  public AggregatorModule(AggregatorConfiguration configuration) {
    this.configuration = configuration;
  }

  public static synchronized AggregatorModule getInstance(AggregatorConfiguration aggregatorConfiguration) {
    if (instance == null) {
      instance = new AggregatorModule(aggregatorConfiguration);
    }
    return instance;
  }

  @Provides
  @Singleton
  public AggregatorConfiguration getConfiguration() {
    return this.configuration;
  }

  @Override
  protected void configure() {
    Multibinder<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
        Multibinder.newSetBinder(binder(), new TypeLiteral<Class<? extends MorphiaRegistrar>>() {});
    morphiaRegistrars.addBinding().toInstance(AggregatorMorphiaRegistrar.class);
    bind(AggregatorMetricsService.class).to(AggregatorMetricsServiceImpl.class);
    bind(new TypeLiteral<AccessControlChangeConsumer<UserGroupUpdateEventData>>() {}).to(UserGroupChangeConsumer.class);
    bind(new TypeLiteral<AccessControlChangeConsumer<RoleChangeEventData>>() {}).to(RoleChangeConsumer.class);
    registerRequiredBindings();
    bind(ACLGeneratorService.class).toProvider(ACLGeneratorServiceFactory.class).in(Scopes.SINGLETON);
    bind(ACLGeneratorService.class)
        .annotatedWith(Names.named(SECONDARY_ACL_GENERATOR_SERVICE))
        .toProvider(Key.get(ACLGeneratorServiceFactory.class, Names.named(SECONDARY_ACL_GENERATOR_SERVICE)))
        .in(Scopes.SINGLETON);
  }

  @Provides
  @Singleton
  private ACLGeneratorServiceFactory primaryACLGenearatorFactory(RoleService roleService,
      UserGroupService userGroupService, ResourceGroupService resourceGroupService, ScopeService scopeService,
      Map<Pair<ScopeLevel, Boolean>, Set<String>> implicitPermissionsByScope,
      @Named(ACL.PRIMARY_COLLECTION) ACLRepository aclRepository,
      InMemoryPermissionRepository inMemoryPermissionRepository,
      @Named("batchSizeForACLCreation") int batchSizeForACLCreation) {
    return new ACLGeneratorServiceFactory(roleService, userGroupService, resourceGroupService, scopeService,
        implicitPermissionsByScope, aclRepository, inMemoryPermissionRepository, batchSizeForACLCreation);
  }

  @Provides
  @Named(SECONDARY_ACL_GENERATOR_SERVICE)
  @Singleton
  private ACLGeneratorServiceFactory secondaryACLGeneratorFactory(RoleService roleService,
      UserGroupService userGroupService, ResourceGroupService resourceGroupService, ScopeService scopeService,
      Map<Pair<ScopeLevel, Boolean>, Set<String>> implicitPermissionsByScope,
      @Named(ACL.SECONDARY_COLLECTION) ACLRepository aclRepository,
      InMemoryPermissionRepository inMemoryPermissionRepository,
      @Named("batchSizeForACLCreation") int batchSizeForACLCreation) {
    return new ACLGeneratorServiceFactory(roleService, userGroupService, resourceGroupService, scopeService,
        implicitPermissionsByScope, aclRepository, inMemoryPermissionRepository, batchSizeForACLCreation);
  }

  private void registerRequiredBindings() {
    requireBinding(ACLService.class);
    requireBinding(RoleService.class);
    requireBinding(UserGroupService.class);
    requireBinding(ResourceGroupService.class);
    requireBinding(ScopeService.class);
    requireBinding(ChangeEventFailureHandler.class);
    requireBinding(ScopeService.class);
  }
}
