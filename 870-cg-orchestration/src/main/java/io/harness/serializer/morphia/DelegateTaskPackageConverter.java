package io.harness.serializer.morphia;

import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import dev.morphia.converters.SimpleValueConverter;
import dev.morphia.converters.TypeConverter;
import dev.morphia.mapping.MappedField;

public class DelegateTaskPackageConverter extends TypeConverter implements SimpleValueConverter {
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  public DelegateTaskPackageConverter() {
    super(DelegateTaskPackage.class);
  }

  @Override
  public Object encode(Object value, MappedField optionalExtraInfo) {
    if (value == null) {
      return null;
    }
    return referenceFalseKryoSerializer.asBytes(value);
  }

  @Override
  public Object decode(Class<?> targetClass, Object fromDBObject, MappedField optionalExtraInfo) {
    if (fromDBObject == null) {
      return null;
    }
    return referenceFalseKryoSerializer.asObject((byte[]) fromDBObject);
  }
}