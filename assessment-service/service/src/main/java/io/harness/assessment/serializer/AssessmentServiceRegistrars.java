/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.assessment.serializer;

import io.harness.assessment.serializer.kryo.AssessmentServiceKryoRegistrar;
import io.harness.assessment.serializer.morphia.AssessmentServiceMorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.KryoRegistrar;

import com.google.common.collect.ImmutableSet;
import lombok.experimental.UtilityClass;

@UtilityClass
//@OwnedBy()
public class AssessmentServiceRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder().add(AssessmentServiceKryoRegistrar.class).build();
  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder().add(AssessmentServiceMorphiaRegistrar.class).build();
}
