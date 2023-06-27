/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.reflection;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER_SRE;

import static java.lang.String.format;

import io.harness.exception.InvalidArgumentsException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;

@Slf4j
public class ReflectionUtils {
  public static Field getFieldByName(Class<?> clazz, String fieldName) {
    while (clazz.getSuperclass() != null) {
      try {
        return clazz.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        clazz = clazz.getSuperclass();
        continue;
      }
    }
    return null;
  }

  public static Set<String> getFieldValuesByType(Class<?> clazz, Class<?> type) {
    return Arrays.stream(clazz.getDeclaredFields())
        .filter(f -> f.canAccess(null) && f.getType() == type)
        .map(f -> {
          try {
            return (String) f.get(null);
          } catch (Exception e) {
            return null;
          }
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  public static List<Field> getAllDeclaredAndInheritedFields(Class<?> clazz) {
    List<Field> declaredFields = new ArrayList<>();
    while (clazz.getSuperclass() != null) {
      Collections.addAll(declaredFields, clazz.getDeclaredFields());
      clazz = clazz.getSuperclass();
    }
    return declaredFields;
  }

  public static List<Field> getDeclaredAndInheritedFields(Class<?> clazz, Predicate<Field> predicate) {
    List<Field> declaredFields = new ArrayList<>();
    while (clazz.getSuperclass() != null) {
      for (Field f : clazz.getDeclaredFields()) {
        if (predicate.test(f)) {
          declaredFields.add(f);
        }
      }
      clazz = clazz.getSuperclass();
    }
    return declaredFields;
  }

  public interface Functor<T extends Annotation> {
    String update(T annotation, String o);
  }

  public static <T extends Annotation> void updateAnnotatedField(Class<T> cls, Object o, Functor<T> functor) {
    Class<?> c = o.getClass();
    while (c.getSuperclass() != null) {
      for (Field f : c.getDeclaredFields()) {
        T annotation = (T) f.getAnnotation(cls);
        if (annotation != null) {
          boolean isAccessible = f.isAccessible();
          f.setAccessible(true);
          try {
            Object object = f.get(o);
            if (object != null) {
              if (object instanceof List) {
                List objectList = (List) object;
                for (int i = 0; i < objectList.size(); i++) {
                  Object o1 = objectList.get(i);
                  if (o1 instanceof ExpressionReflectionUtils.NestedAnnotationResolver) {
                    updateAnnotatedField(cls, o1, functor);
                  } else if (o1 instanceof String) {
                    objectList.set(i, functor.update(annotation, (String) o1));
                  }
                }
              } else if (object instanceof Map) {
                Map objectMap = (Map) object;
                objectMap.replaceAll((k, v) -> {
                  if (v instanceof ExpressionReflectionUtils.NestedAnnotationResolver) {
                    updateAnnotatedField(cls, v, functor);
                    return v;
                  } else if (v instanceof String) {
                    return functor.update(annotation, (String) v);
                  }
                  return v;
                });
              } else if (object instanceof ExpressionReflectionUtils.NestedAnnotationResolver) {
                updateAnnotatedField(cls, object, functor);
              } else if (object instanceof String) {
                String value = functor.update(annotation, (String) object);
                f.set(o, value);
              }
            }
            f.setAccessible(isAccessible);
          } catch (IllegalAccessException e) {
            log.error("Field [{}] is not accessible ", f.getName());
          }
        }
      }
      c = c.getSuperclass();
    }
  }

  public static String getAccessorFieldName(String methodName) {
    if (methodName.startsWith("get") && methodName.length() > 3) {
      return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
    } else if (methodName.startsWith("is") && methodName.length() > 2) {
      return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
    }

    throw new InvalidArgumentsException("Invalid accessor method name", USER_SRE);
  }

  public static List<Method> getAccessorMethods(Class<?> clazz) {
    List<Method> methods = new ArrayList<>();
    while (clazz.getSuperclass() != null) {
      for (Method m : clazz.getDeclaredMethods()) {
        if (!Modifier.isPublic(m.getModifiers())) {
          continue;
        }
        boolean shouldBeBoolean = false;
        String fieldName;

        final String methodName = m.getName();
        if (methodName.startsWith("get") && methodName.length() > 3) {
          fieldName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
          fieldName = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
          shouldBeBoolean = true;
        } else {
          continue;
        }

        try {
          final Field declaredField = clazz.getDeclaredField(fieldName);
          if (shouldBeBoolean
              != (declaredField.getType() == boolean.class || declaredField.getType() == Boolean.class)) {
            continue;
          }
        } catch (NoSuchFieldException e) {
          continue;
        }

        methods.add(m);
      }
      clazz = clazz.getSuperclass();
    }
    return methods;
  }

  public static Map<String, Object> getFieldValues(@Nonnull Object obj, @Nonnull Set<String> fieldNames) {
    return getFieldValues(obj, fieldNames, true);
  }

  public static Map<String, Object> getFieldValues(
      @Nonnull Object obj, @Nonnull Set<String> fieldNames, boolean shouldLogWarnAndError) {
    Map<String, Object> fieldNameValueMap = new HashMap<>();
    for (String fieldName : fieldNames) {
      try {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(obj);
        fieldNameValueMap.put(fieldName, value);
      } catch (NoSuchFieldException ignored) {
        if (shouldLogWarnAndError) {
          log.warn(format("Field \"%s\" not available in object \"%s\"", fieldName, obj.toString()));
        }
      } catch (IllegalAccessException e) {
        if (shouldLogWarnAndError) {
          log.error(format("Unable to access field \"%s\"", fieldName));
        }
      }
    }
    return fieldNameValueMap;
  }

  public static Object getFieldValue(@NonNull Object obj, @NonNull String fieldName) {
    return getFieldValues(obj, Collections.singleton(fieldName)).get(fieldName);
  }

  public static Object getFieldValue(@NonNull Object obj, @NonNull Field field) {
    try {
      return FieldUtils.readField(field, obj, true);
    } catch (IllegalAccessException e) {
      log.error("Unable to access field {} in object {}", field.getName(), obj.getClass(), e);
    }
    return null;
  }

  public static <A extends Annotation> Set<A> fetchAnnotations(Class<?> clazz, Class<A> annotationClass) {
    Set<A> annotations = new HashSet<>();

    for (Class<?> type = clazz; type != null; type = type.getSuperclass()) {
      annotations.addAll(Arrays.asList(type.getAnnotationsByType(annotationClass)));
    }
    return annotations;
  }

  public static void setObjectField(Field field, Object obj, Object value) throws IllegalAccessException {
    field.setAccessible(true);
    field.set(obj, value);
  }

  public static Method getMethod(Class clazz, String methodName, Class<?>... paramClasses) {
    try {
      if (isNotEmpty(paramClasses)) {
        return clazz.getMethod(methodName, paramClasses);
      } else {
        return clazz.getMethod(methodName);
      }
    } catch (NoSuchMethodException e) {
      log.error("Cannot find method [{}] in class [{}]", methodName, clazz, e);
      throw new UnsupportedOperationException(String.format("No method found with name %s", methodName));
    }
  }
}
