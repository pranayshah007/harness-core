/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.persistence;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import com.google.cloud.datastore.Blob;
import com.google.cloud.datastore.BlobValue;
import com.google.cloud.datastore.BooleanValue;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DoubleValue;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Entity.Builder;
import com.google.cloud.datastore.ListValue;
import com.google.cloud.datastore.LongValue;
import com.google.cloud.datastore.StringValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public interface GoogleDataStoreAware extends PersistentEntity {
  String KEY_SEPARATOR = ":";

  Entity convertToCloudStorageEntity(Datastore datastore);

  GoogleDataStoreAware readFromCloudStorageEntity(Entity entity);

  static String readString(Entity entity, String fieldName) {
    return entity.contains(fieldName) ? entity.getString(fieldName) : null;
  }

  static Boolean readBoolean(Entity entity, String fieldName) {
    return entity.contains(fieldName) && entity.getBoolean(fieldName);
  }

  static <T> List readList(Entity entity, String fieldName, Class<T> clazz) {
    List list = entity.contains(fieldName) ? entity.getList(fieldName) : null;
    if (list == null) {
      return null;
    }

    List output = new ArrayList<>();
    for (Object entry : list) {
      if (StringValue.class.isInstance(entry) && clazz.equals(String.class)) {
        output.add(((StringValue) entry).get());
      } else if (DoubleValue.class.isInstance(entry) && clazz.equals(Double.class)) {
        output.add(((DoubleValue) entry).get());
      } else if (LongValue.class.isInstance(entry) && clazz.equals(Long.class)) {
        output.add(((LongValue) entry).get());
      } else if (BooleanValue.class.isInstance(entry) && clazz.equals(Boolean.class)) {
        output.add(((BooleanValue) entry).get());
      }
    }
    return output;
  }

  static long readLong(Entity entity, String fieldName) {
    return entity.contains(fieldName) ? entity.getLong(fieldName) : 0;
  }

  static double readDouble(Entity entity, String fieldName) {
    return entity.contains(fieldName) ? entity.getDouble(fieldName) : 0.0;
  }

  static byte[] readBlob(Entity entity, String fieldName) {
    return entity.contains(fieldName) ? entity.getBlob(fieldName).toByteArray() : null;
  }

  static void addFieldIfNotEmpty(Builder builder, String key, String value, boolean excludeFromIndex) {
    if (isEmpty(value)) {
      return;
    }

    builder.set(key, StringValue.newBuilder(value).setExcludeFromIndexes(excludeFromIndex).build());
  }

  static void addFieldIfNotEmpty(Builder builder, String key, long value, boolean excludeFromIndex) {
    builder.set(key, LongValue.newBuilder(value).setExcludeFromIndexes(excludeFromIndex).build());
  }

  static void addFieldIfNotEmpty(Builder builder, String key, Double value, boolean excludeFromIndex) {
    builder.set(key, DoubleValue.newBuilder(value).setExcludeFromIndexes(excludeFromIndex).build());
  }

  static void addFieldIfNotEmpty(Builder builder, String key, Boolean value, boolean excludeFromIndex) {
    if (value == null) {
      return;
    }

    builder.set(key, BooleanValue.newBuilder(value).setExcludeFromIndexes(excludeFromIndex).build());
  }

  static void addFieldIfNotEmpty(Builder builder, String key, Blob value, boolean excludeFromIndex) {
    if (value == null) {
      return;
    }

    builder.set(key, BlobValue.newBuilder(value).setExcludeFromIndexes(excludeFromIndex).build());
  }

  static <T> void addFieldIfNotEmpty(Builder builder, String key, Collection value, Class<T> clazz) {
    if (isEmpty(value)) {
      return;
    }

    com.google.cloud.datastore.ListValue.Builder listBuilder = ListValue.newBuilder();

    for (Object entry : value) {
      if (clazz.equals(Integer.class)) {
        listBuilder.addValue(((Integer) entry).longValue());
      } else if (clazz.equals(Long.class)) {
        listBuilder.addValue((Long) entry);
      } else if (clazz.equals(Double.class)) {
        listBuilder.addValue((Double) entry);
      } else if (clazz.equals(String.class)) {
        listBuilder.addValue((String) entry);
      } else {
        throw new IllegalArgumentException("GDS addField in collection: Class not supported " + entry.getClass());
      }
    }

    ListValue listValue = listBuilder.build();
    builder.set(key, listValue);
  }
}
