package io.harness.serializer;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filter.serializer.FiltersRegistrars;
import io.harness.gitsync.serializer.GitSyncSdkRegistrar;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.serializer.kryo.NGTemplateKryoRegistrar;
import io.harness.serializer.morphia.NGTemplateMorphiaRegistrar;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.serializer.registrars.NGCommonsRegistrars;
import lombok.experimental.UtilityClass;
import org.mongodb.morphia.converters.TypeConverter;
import org.springframework.core.convert.converter.Converter;

@UtilityClass
@OwnedBy(CDC)
public class TemplateServiceModuleRegistrars {
  public final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet.<Class<? extends KryoRegistrar>>builder()
          .addAll(NGCommonsRegistrars.kryoRegistrars)
          .addAll(NGCoreBeansRegistrars.kryoRegistrars)
          .addAll(ConnectorBeansRegistrars.kryoRegistrars)
          .addAll(PrimaryVersionManagerRegistrars.kryoRegistrars)
          .addAll(FiltersRegistrars.kryoRegistrars)
          .addAll(NGAuditCommonsRegistrars.kryoRegistrars)
          .addAll(FiltersRegistrars.kryoRegistrars)
          .addAll(GitSyncSdkRegistrar.kryoRegistrars)
          .addAll(PersistenceRegistrars.kryoRegistrars)
          .addAll(OutboxEventRegistrars.kryoRegistrars)
          .addAll(SMCoreRegistrars.kryoRegistrars)
          .addAll(YamlBeansModuleRegistrars.kryoRegistrars)
          .add(NGTemplateKryoRegistrar.class)
          .addAll(NGCoreRegistrars.kryoRegistrars)
          .addAll(LicenseBeanRegistrar.kryoRegistrars)
          .build();

  public final ImmutableSet<Class<? extends MorphiaRegistrar>> morphiaRegistrars =
      ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
          .addAll(NGCommonsRegistrars.morphiaRegistrars)
          .addAll(NGCoreBeansRegistrars.morphiaRegistrars)
          .addAll(NGCoreClientRegistrars.morphiaRegistrars)
          .addAll(PrimaryVersionManagerRegistrars.morphiaRegistrars)
          .addAll(ConnectorBeansRegistrars.morphiaRegistrars)
          .addAll(FiltersRegistrars.morphiaRegistrars)
          .addAll(NGAuditCommonsRegistrars.morphiaRegistrars)
          .addAll(FiltersRegistrars.morphiaRegistrars)
          .addAll(GitSyncSdkRegistrar.morphiaRegistrars)
          .addAll(PersistenceRegistrars.morphiaRegistrars)
          .addAll(OutboxEventRegistrars.morphiaRegistrars)
          .addAll(SMCoreRegistrars.morphiaRegistrars)
          .addAll(YamlBeansModuleRegistrars.morphiaRegistrars)
          .addAll(NGCoreRegistrars.morphiaRegistrars)
          .add(NGTemplateMorphiaRegistrar.class)
          .build();

  public final ImmutableSet<Class<? extends TypeConverter>> morphiaConverters =
      ImmutableSet.<Class<? extends TypeConverter>>builder().build();

  public final ImmutableList<Class<? extends Converter<?, ?>>> springConverters =
      ImmutableList.<Class<? extends Converter<?, ?>>>builder()
          .addAll(FiltersRegistrars.springConverters)
          .addAll(GitSyncSdkRegistrar.springConverters)
          .build();
}
