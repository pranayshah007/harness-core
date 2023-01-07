/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.morphia.NgUserGroupMorphiaRegistrar;
import io.harness.serializer.morphia.NgUserMorphiaRegistrar;
import io.harness.serializer.morphia.NgUserProfileMorphiaRegistrars;

import com.google.common.collect.ImmutableSet;
import dev.morphia.converters.TypeConverter;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class NgUserCoreRegistrars {
  public final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .add(NgUserGroupMorphiaRegistrar.class)
          .add(NgUserMorphiaRegistrar.class)
          .add(NgUserProfileMorphiaRegistrars.class)
          .build();

  public static final ImmutableSet<Class<? extends TypeConverter>> morphiaConverters =
      ImmutableSet.<Class<? extends TypeConverter>>builder().build();
}
