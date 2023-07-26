/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.CVAnalyzeDeploymentStepNode;
import io.harness.cvng.core.beans.CVVerifyStepNode;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.serializer.kryo.PmsContractsKryoRegistrar;
import io.harness.serializer.kryo.CVNGKryoRegistrar;
import io.harness.serializer.kryo.NotificationBeansKryoRegistrar;
import io.harness.serializer.morphia.CVNextGenMorphiaRegister;
import io.harness.serializer.morphia.NotificationBeansMorphiaRegistrar;
import io.harness.yaml.schema.beans.SchemaNamespaceConstants;
import io.harness.yaml.schema.beans.YamlGroup;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.morphia.converters.TypeConverter;
import java.util.Collections;
import lombok.experimental.UtilityClass;
import org.springframework.core.convert.converter.Converter;

@UtilityClass
@OwnedBy(HarnessTeam.CV)
public class CvNextGenRegistrars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(CvNextGenBeansRegistrars.kryoRegistrars)
          .addAll(CvNextGenCommonsRegistrars.kryoRegistrars)
          .addAll(ConnectorNextGenRegistrars.kryoRegistrars)
          .add(CVNGKryoRegistrar.class)
          .add(PmsContractsKryoRegistrar.class)
          .add(NotificationBeansKryoRegistrar.class)
          .addAll(AccessControlClientRegistrars.kryoRegistrars)
          .addAll(DelegateTaskRegistrars.kryoRegistrars)
          .addAll(NGCommonModuleRegistrars.kryoRegistrars)
          .addAll(ProjectAndOrgRegistrars.kryoRegistrars)
          .build();

  public static final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .addAll(CvNextGenCommonsRegistrars.morphiaRegistrars)
          .add(CVNextGenMorphiaRegister.class)
          .add(NotificationBeansMorphiaRegistrar.class)
          .addAll(ConnectorBeansRegistrars.morphiaRegistrars)
          .addAll(PrimaryVersionManagerRegistrars.morphiaRegistrars)
          .addAll(DelegateTaskRegistrars.morphiaRegistrars)
          .addAll(NGCommonModuleRegistrars.morphiaRegistrars)
          .addAll(ProjectAndOrgRegistrars.morphiaRegistrars)
          .build();

  public static final ImmutableSet<Class<? extends TypeConverter>> morphiaConverters =
      ImmutableSet.<Class<? extends TypeConverter>>builder().addAll(PersistenceRegistrars.morphiaConverters).build();
  public static final ImmutableList<YamlSchemaRootClass> yamlSchemaRegistrars =
      ImmutableList.<YamlSchemaRootClass>builder()
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.VERIFY_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(CVVerifyStepNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.CVNG)
                                           .modulesSupported(Collections.singletonList(ModuleType.CD))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .build())
          .add(YamlSchemaRootClass.builder()
                   .entityType(EntityType.ANALYZE_DEPLOYMENT_IMPACT_STEP)
                   .availableAtProjectLevel(true)
                   .availableAtOrgLevel(false)
                   .availableAtAccountLevel(false)
                   .clazz(CVAnalyzeDeploymentStepNode.class)
                   .yamlSchemaMetadata(YamlSchemaMetadata.builder()
                                           .namespace(SchemaNamespaceConstants.CVNG)
                                           .modulesSupported(Collections.singletonList(ModuleType.SRM))
                                           .yamlGroup(YamlGroup.builder().group(StepCategory.STEP.name()).build())
                                           .build())
                   .build())
          .build();
  public static final ImmutableList<Class<? extends Converter<?, ?>>> springConverters =
      ImmutableList.<Class<? extends Converter<?, ?>>>builder().build();
}
