/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import static java.lang.String.format;

import io.harness.annotation.RecasterFieldName;
import io.harness.core.Recaster;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exceptions.RecasterException;
import io.harness.utils.RecastReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class CastedField {
  private final Map<Class<? extends Annotation>, Annotation> foundAnnotations = new HashMap<>();
  private final List<CastedField> typeParameters = new ArrayList<>();
  private Class<?> persistedClass;
  private Field field;
  private Class<?> realType;
  private Constructor constructor;
  private String recasterFieldName;
  private Type subType;
  private Type mapKeyType;
  private boolean isSingleValue = true;
  private boolean isMap;
  private boolean isSet;
  // for debugging
  private boolean isArray; // indicated if it is an Array
  private boolean isCollection; // indicated if the collection is a list)
  private Type genericType;

  CastedField(final Field f, final Class<?> clazz, final Recaster recaster) {
    f.setAccessible(true);
    field = f;
    recasterFieldName = getRecasterFieldNameFromField(f);
    persistedClass = clazz;
    realType = field.getType();
    genericType = field.getGenericType();
    discover(recaster);
  }

  CastedField(final Field field, final Type type, final Recaster recaster) {
    this.field = field;
    genericType = type;
    discoverType(recaster);
  }

  String getRecasterFieldNameFromField(Field f) {
    // If RecasterFieldName annotation is declared on a field and value provided is non-empty, then set the
    // recasterFieldName with the value provided in annotation else null.
    if (f.getDeclaredAnnotation(RecasterFieldName.class) != null) {
      String recasterFieldName = f.getDeclaredAnnotation(RecasterFieldName.class).name();
      return EmptyPredicate.isEmpty(recasterFieldName) ? null : recasterFieldName;
    }
    return null;
  }

  /**
   * @param clazz the annotation to search for
   * @param <T>   the type of the annotation
   * @return the annotation instance if it exists on this field
   */
  @SuppressWarnings("unchecked")
  public <T extends Annotation> T getAnnotation(final Class<T> clazz) {
    return (T) foundAnnotations.get(clazz);
  }

  /**
   * @return the annotations found while mapping
   */
  public Map<Class<? extends Annotation>, Annotation> getAnnotations() {
    return foundAnnotations;
  }
  /**
   * @return the underlying java field
   */
  public Field getField() {
    return field;
  }

  /**
   * @return the full name of the class plus java field name
   */
  public String getFullName() {
    return field.getDeclaringClass().getName() + "." + field.getName();
  }

  public Class<?> getType() {
    return realType;
  }

  /**
   * Indicates whether the annotation is present in the mapping (does not check the java field annotations, just the
   * ones discovered)
   *
   * @param ann the annotation to search for
   * @return true if the annotation was found
   */
  public boolean hasAnnotation(final Class<?> ann) {
    return foundAnnotations.containsKey(ann);
  }

  /**
   * @return true if the MappedField is an array
   */
  public boolean isArray() {
    return isArray;
  }

  /**
   * @return true if the MappedField is a Map
   */
  public boolean isMap() {
    return isMap;
  }

  /**
   * @return true if the MappedField is a Set
   */
  public boolean isSet() {
    return isSet;
  }

  /**
   * Discovers interesting (that we care about) things about the field.
   */
  protected void discover(final Recaster recaster) {
    // TODO : Evaluate Later
    //    for (final Class<? extends Annotation> clazz : INTERESTING) {
    //      addAnnotation(clazz);
    //    }

    // type must be discovered before the constructor.
    discoverType(recaster);
    constructor = discoverConstructor();
    discoverMultivalued();
  }

  @SuppressWarnings("unchecked")
  protected void discoverType(final Recaster recaster) {
    if (genericType instanceof TypeVariable) {
      realType = extractTypeVariable((TypeVariable<?>) genericType);
    } else if (genericType instanceof ParameterizedType) {
      ParameterizedType pt = (ParameterizedType) genericType;
      final Type[] types = pt.getActualTypeArguments();
      realType = toClass(pt);

      for (Type type : types) {
        if (type instanceof ParameterizedType) {
          typeParameters.add(new EphemeralCastedField((ParameterizedType) type, this, recaster));
        } else {
          if (type instanceof WildcardType) {
            type = ((WildcardType) type).getUpperBounds()[0];
          }
          typeParameters.add(new EphemeralCastedField(type, this, recaster));
        }
      }
    } else if (genericType instanceof WildcardType) {
      final WildcardType wildcardType = (WildcardType) genericType;
      final Type[] types = wildcardType.getUpperBounds();
      realType = toClass(types[0]);
    } else if (genericType instanceof Class) {
      realType = (Class<?>) genericType;
    } else if (genericType instanceof GenericArrayType) {
      final Type genericComponentType = ((GenericArrayType) genericType).getGenericComponentType();
      if (genericComponentType instanceof ParameterizedType) {
        ParameterizedType pt = (ParameterizedType) genericComponentType;
        realType = toClass(genericType);

        final Type[] types = pt.getActualTypeArguments();
        for (Type type : types) {
          if (type instanceof ParameterizedType) {
            typeParameters.add(new EphemeralCastedField((ParameterizedType) type, this, recaster));
          } else {
            if (type instanceof WildcardType) {
              type = ((WildcardType) type).getUpperBounds()[0];
            }
            typeParameters.add(new EphemeralCastedField(type, this, recaster));
          }
        }
      } else {
        if (genericComponentType instanceof TypeVariable) {
          realType = toClass(genericType);
        } else {
          realType = (Class<?>) genericComponentType;
        }
      }
    }

    if (realType == null) {
      throw new RecasterException(format("A type could not be found for the field %s.%s", getType(), getField()));
    }
  }

  private Class<?> extractTypeVariable(final TypeVariable<?> type) {
    final Class<?> typeArgument = RecastReflectionUtils.getTypeArgument(persistedClass, type);
    return typeArgument != null ? typeArgument : Object.class;
  }

  protected Class<?> toClass(final Type t) {
    if (t == null) {
      return null;
    } else if (t instanceof Class) {
      return (Class<?>) t;
    } else if (t instanceof GenericArrayType) {
      final Type type = ((GenericArrayType) t).getGenericComponentType();
      Class<?> aClass;
      if (type instanceof ParameterizedType) {
        aClass = (Class<?>) ((ParameterizedType) type).getRawType();
      } else if (type instanceof TypeVariable) {
        aClass = RecastReflectionUtils.getTypeArgument(persistedClass, (TypeVariable<?>) type);
        if (aClass == null) {
          aClass = Object.class;
        }
      } else {
        aClass = (Class<?>) type;
      }
      return Array.newInstance(aClass, 0).getClass();
    } else if (t instanceof ParameterizedType) {
      return (Class<?>) ((ParameterizedType) t).getRawType();
    } else if (t instanceof WildcardType) {
      return (Class<?>) ((WildcardType) t).getUpperBounds()[0];
    }
    throw new RuntimeException("Generic TypeVariable not supported!");
  }

  private Constructor<?> discoverConstructor() {
    Class<?> type = null;
    // get the first annotation with a concreteClass that isn't Object.class
    for (final Annotation an : foundAnnotations.values()) {
      try {
        final Method m = an.getClass().getMethod("concreteClass");
        m.setAccessible(true);
        final Object o = m.invoke(an);
        // noinspection EqualsBetweenInconvertibleTypes
        if (o != null && !(o.equals(Object.class))) {
          type = (Class<?>) o;
          break;
        }
      } catch (NoSuchMethodException e) {
        // do nothing
      } catch (IllegalArgumentException e) {
        log.warn("There should not be an argument", e);
      } catch (Exception e) {
        log.warn("", e);
      }
    }

    if (type != null) {
      try {
        constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
      } catch (NoSuchMethodException e) {
        log.warn("No usable constructor for " + type.getName(), e);
      }
    } else {
      // see if we can create instances of the type used for declaration
      type = getType();

      // short circuit to avoid wasting time throwing an exception trying to get a constructor we know doesnt exist
      if (type == List.class || type == Map.class) {
        return null;
      }

      if (type != null) {
        try {
          constructor = type.getDeclaredConstructor();
          constructor.setAccessible(true);
        } catch (NoSuchMethodException | SecurityException e) {
          // never mind.
        }
      }
    }
    return constructor;
  }

  private void discoverMultivalued() {
    if (realType.isArray() || Collection.class.isAssignableFrom(realType) || Map.class.isAssignableFrom(realType)
        || GenericArrayType.class.isAssignableFrom(genericType.getClass())) {
      isSingleValue = false;

      isMap = Map.class.isAssignableFrom(realType);
      isSet = Set.class.isAssignableFrom(realType);
      // for debugging
      isCollection = Collection.class.isAssignableFrom(realType);
      isArray = realType.isArray();

      // for debugging with issue
      if (!isMap && !isSet && !isCollection && !isArray) {
        throw new RecasterException(format(
            "%s.%s is not a map/set/collection/array : %s", field.getName(), field.getDeclaringClass(), realType));
      }

      // get the subtype T, T[]/List<T>/Map<?,T>; subtype of Long[], List<Long> is Long
      subType = (realType.isArray()) ? realType.getComponentType()
                                     : RecastReflectionUtils.getParameterizedType(field, isMap ? 1 : 0);

      if (isMap) {
        mapKeyType = RecastReflectionUtils.getParameterizedType(field, 0);
      }
    }
  }

  void setIsMap(final boolean isMap) {
    this.isMap = isMap;
  }

  void setIsSet(final boolean isSet) {
    this.isSet = isSet;
  }

  void setMapKeyType(final Class<?> mapKeyType) {
    this.mapKeyType = mapKeyType;
  }

  public Class<?> getMapKeyClass() {
    return toClass(mapKeyType);
  }

  public Type getSubType() {
    return subType;
  }

  void setSubType(final Type subType) {
    this.subType = subType;
  }

  public Class<?> getSubClass() {
    return toClass(subType);
  }

  public boolean isMultipleValues() {
    return !isSingleValue;
  }

  public Object getFieldValue(final Object instance) {
    try {
      return field.get(instance);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public void setFieldValue(final Object instance, final Object value) {
    try {
      field.set(instance, value);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  public Object getRecastedMapValue(final RecasterMap recasterMap) {
    return recasterMap.get(getNameToStore());
  }

  public String getNameToStore() {
    // if recasterFieldName is non-empty then use it while recasting else use the fieldName.
    return EmptyPredicate.isEmpty(recasterFieldName) ? field.getName() : recasterFieldName;
  }
}
