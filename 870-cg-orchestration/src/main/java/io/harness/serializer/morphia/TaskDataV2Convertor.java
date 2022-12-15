/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.morphia;

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
    taskDataV2.setExpressions(
        taskDataV2.getExpressions() != null ? new HashMap<>(taskDataV2.getExpressions()) : new HashMap<>());
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
