/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.reflection;

import static java.lang.String.format;

import com.google.common.base.Preconditions;
import com.google.protobuf.ProtocolMessageEnum;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CodeUtils {
  public static String location(Class clazz) {
    final ProtectionDomain protectionDomain = clazz.getProtectionDomain();
    if (protectionDomain == null) {
      return null;
    }

    final CodeSource codeSource = protectionDomain.getCodeSource();
    if (codeSource == null) {
      return null;
    }

    final URL location = codeSource.getLocation();
    if (location == null) {
      return null;
    }
    Path path = Paths.get(location.toString());
    if (path.getFileName().toString().endsWith(".jar")) {
      path = path.getParent();
    }

    return path.toString();
  }

  public static boolean isHarnessClass(Class clazz) {
    final String pkg = clazz.getCanonicalName();
    if (pkg == null) {
      return false;
    }
    // TODO(prashant) : This is a temp hack, with bazel our check is not working correctly check with @george how to fix
    // this appropriately
    if (checkIfProtobuf(clazz)) {
      return false;
    }
    if (pkg.startsWith("io.harness.")) {
      return true;
    }
    if (pkg.startsWith("software.wings.")) {
      return true;
    }
    return false;
  }

  private static boolean checkIfProtobuf(Class clazz) {
    return clazz.getSuperclass() != null
        && (clazz.getSuperclass().getCanonicalName().startsWith("com.google.protobuf")
            || ProtocolMessageEnum.class.isAssignableFrom(clazz));
  }

  public static boolean isTestClass(Class clazz) {
    String location = location(clazz);
    return location.endsWith("/test-classes/");
  }

  public static void checkHarnessClassesBelongToModule(String location, Set<? extends Class> classes) {
    for (Class clazz : classes) {
      if (!thirdPartyOrBelongsToModule(location, clazz)) {
        throw new RuntimeException(
            format("%s class should be registered in module %s", clazz.getCanonicalName(), location));
      }
    }
  }

  public static boolean thirdPartyOrBelongsToModule(String location, Class clazz) {
    if (!CodeUtils.isHarnessClass(clazz)) {
      return true;
    }
    final String clazzLocation = Preconditions.checkNotNull(CodeUtils.location(clazz));

    // Return true if clazz is located in any folder inside the parent module.
    return clazzLocation.startsWith(location);
  }
}
