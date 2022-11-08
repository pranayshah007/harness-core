/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filter.serializer.FiltersRegistrars;
import io.harness.ldap.serializer.NGLdapCommonsRegistrars;
import io.harness.morphia.MorphiaRegistrar;

import com.google.common.collect.ImmutableSet;
import io.harness.serializer.common.CommonsRegistrars;
import io.serializer.registrars.NGCommonsRegistrars;
import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class NGLdapServiceRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(CommonsRegistrars.kryoRegistrars)
          .addAll(NGCommonsRegistrars.kryoRegistrars)
          .addAll(PersistenceRegistrars.kryoRegistrars)
          .addAll(FiltersRegistrars.kryoRegistrars)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .addAll(CommonsRegistrars.morphiaRegistrars)
          .addAll(NGCommonsRegistrars.morphiaRegistrars)
          .addAll(PersistenceRegistrars.morphiaRegistrars)
          .addAll(FiltersRegistrars.morphiaRegistrars)
          .addAll(NGLdapCommonsRegistrars.morphiaRegistrars)
          .build();
}
