package io.harness.persistence.converters;

import io.harness.delegate.beans.TaskDataV2;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.HashMap;
import org.mongodb.morphia.converters.SimpleValueConverter;
import org.mongodb.morphia.converters.TypeConverter;
import org.mongodb.morphia.mapping.MappedField;

public class TaskDataV2Convertor extends TypeConverter implements SimpleValueConverter {
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  public TaskDataV2Convertor() {
    super(TaskDataV2.class);
  }

  @Override
  public Object encode(Object value, MappedField optionalExtraInfo) {
    if (value == null) {
      return null;
    }
    TaskDataV2 taskDataV2 = (TaskDataV2) value;
    taskDataV2.setExpressions(new HashMap<>(taskDataV2.getExpressions()));
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
