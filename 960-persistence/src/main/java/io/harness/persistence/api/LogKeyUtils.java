/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.persistence;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class LogKeyUtils {
  private static Map<Class, String> entityIdName = new ConcurrentHashMap<>();

  public static String logKeyForId(Class entityClass) {
    return entityIdName.computeIfAbsent(entityClass, cls -> {
      final String key = calculateLogKeyForId(cls);
      log.warn("Provide logKeyForId for the class {} instead of using the default one", cls.getName());
      return key;
    });
  }

  @SuppressWarnings("squid:S1872")
  public static String calculateLogKeyForId(Class cls) {
    if (!(UuidAccess.class.isAssignableFrom(cls))) {
      return "";
    }

    while (UuidAccess.class.isAssignableFrom(cls.getSuperclass())) {
      // TODO: remove when base is removed
      if ("Base".equals(cls.getSuperclass().getSimpleName())) {
        break;
      }
      cls = cls.getSuperclass();
    }

    String name = cls.getSimpleName() + "Id";

    char[] c = name.toCharArray();
    c[0] = Character.toLowerCase(c[0]);
    return new String(c);
  }
}
