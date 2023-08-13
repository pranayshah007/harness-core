/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl;

import com.google.inject.Scopes;
import io.harness.accesscontrol.acl.consumers.AccessControlChangeConsumer;
import io.harness.accesscontrol.acl.consumers.UserGroupChangeConsumer;
import io.harness.accesscontrol.acl.models.UserGroupUpdateEventData;
import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.ACLDAO;
import io.harness.accesscontrol.acl.persistence.ACLDAOImpl;
import io.harness.accesscontrol.acl.persistence.ACLMorphiaRegistrar;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.acl.persistence.repositories.PrimaryACLRepositoryImpl;
import io.harness.accesscontrol.acl.persistence.repositories.SecondaryACLRepositoryImpl;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.morphia.MorphiaRegistrar;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

@OwnedBy(HarnessTeam.PL)
public class ACLModule extends AbstractModule {
  private static ACLModule instance;

  public static ACLModule getInstance() {
    if (instance == null) {
      instance = new ACLModule();
    }
    return instance;
  }

  @Override
  protected void configure() {
    Multibinder<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
        Multibinder.newSetBinder(binder(), new TypeLiteral<Class<? extends MorphiaRegistrar>>() {});
    morphiaRegistrars.addBinding().toInstance(ACLMorphiaRegistrar.class);

    bind(ACLService.class).to(ACLServiceImpl.class);
    bind(ACLDAO.class).to(ACLDAOImpl.class);
    bind(ACLRepository.class).annotatedWith(Names.named(ACL.PRIMARY_COLLECTION)).to(PrimaryACLRepositoryImpl.class);
    bind(ACLRepository.class).annotatedWith(Names.named(ACL.SECONDARY_COLLECTION)).to(SecondaryACLRepositoryImpl.class);
    bind(ACLGeneratorService.class).to(ACLGeneratorServiceImpl.class).in(Scopes.SINGLETON);
    bind(new TypeLiteral<AccessControlChangeConsumer<UserGroupUpdateEventData>>() {}).to(UserGroupChangeConsumer.class);
  }
}
