/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.morphia;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class MorphiaUtils {
  private MorphiaUtils() {}

  public static Set<Class<?>> getFromDirectory(
      ClassLoader loader, File directory, String packageName, boolean mapSubPackages) throws ClassNotFoundException {
    Set<Class<?>> classes = new HashSet();
    if (directory.exists()) {
      Iterator iterator = getFileNames(directory, packageName, mapSubPackages).iterator();

      while (iterator.hasNext()) {
        String file = (String) iterator.next();
        if (file.endsWith(".class")) {
          String name = stripFilenameExtension(file);
          Class<?> clazz = Class.forName(name, true, loader);
          classes.add(clazz);
        }
      }
    }

    return classes;
  }

  public static Set<Class<?>> getFromJARFile(ClassLoader loader, String jar, String packageName, boolean mapSubPackages)
      throws IOException, ClassNotFoundException {
    Set<Class<?>> classes = new HashSet();
    JarInputStream jarFile = new JarInputStream(new FileInputStream(jar));

    JarEntry jarEntry;
    try {
      do {
        jarEntry = jarFile.getNextJarEntry();
        if (jarEntry != null) {
          String className = jarEntry.getName();
          if (className.endsWith(".class")) {
            String classPackageName = getPackageName(className);
            if (classPackageName.equals(packageName) || mapSubPackages && isSubPackage(classPackageName, packageName)) {
              className = stripFilenameExtension(className);
              classes.add(Class.forName(className.replace('/', '.'), true, loader));
            }
          }
        }
      } while (jarEntry != null);
    } finally {
      jarFile.close();
    }

    return classes;
  }

  private static Set<String> getFileNames(File directory, String packageName, boolean mapSubPackages) {
    Set<String> fileNames = new HashSet();
    File[] files = directory.listFiles();

    for (File file : files) {
      if (file.isFile()) {
        fileNames.add(packageName + '.' + file.getName());
      } else if (mapSubPackages) {
        fileNames.addAll(getFileNames(file, packageName + '.' + file.getName(), true));
      }
    }

    return fileNames;
  }

  private static String getPackageName(String filename) {
    return filename.contains("/") ? filename.substring(0, filename.lastIndexOf(47)) : filename;
  }

  private static String stripFilenameExtension(String filename) {
    return filename.indexOf(46) != -1 ? filename.substring(0, filename.lastIndexOf(46)) : filename;
  }

  private static boolean isSubPackage(String fullPackageName, String parentPackageName) {
    return fullPackageName.startsWith(parentPackageName);
  }
}
