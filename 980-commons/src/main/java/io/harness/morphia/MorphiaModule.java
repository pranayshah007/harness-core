/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.morphia;

import static io.harness.govern.IgnoreThrowable.ignoredOnPurpose;
import static io.harness.morphia.MorphiaRegistrar.putClass;

import io.harness.agent.sdk.HarnessTrace;
import io.harness.exception.GeneralException;
import io.harness.exception.UnexpectedException;
import io.harness.govern.Switch;
import io.harness.reflection.CodeUtils;
import io.harness.testing.TestExecution;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.ObjectFactory;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.converters.TypeConverter;
import org.mongodb.morphia.mapping.MappedClass;
import org.mongodb.morphia.mapping.MappingException;
import org.mongodb.morphia.utils.ReflectionUtils;

@Slf4j
public class MorphiaModule extends AbstractModule {
  private static volatile MorphiaModule instance;

  public static MorphiaModule getInstance() {
    if (instance == null) {
      instance = new MorphiaModule();
    }
    return instance;
  }

  private MorphiaModule() {}

  @HarnessTrace
  @Provides
  @Named("morphiaClasses")
  @Singleton
  Set<Class> classes(Set<Class<? extends MorphiaRegistrar>> registrars) {
    Set<Class> classes = new HashSet<>();
    try {
      for (Class clazz : registrars) {
        Constructor<?> constructor = clazz.getConstructor();
        final MorphiaRegistrar morphiaRegistrar = (MorphiaRegistrar) constructor.newInstance();
        morphiaRegistrar.registerClasses(classes);
      }
    } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
      throw new GeneralException("Failed initializing morphia", e);
    }

    return classes;
  }

  @HarnessTrace
  @Provides
  @Named("morphiaInterfaceImplementersClasses")
  @Singleton
  Map<String, Class> collectMorphiaInterfaceImplementers(Set<Class<? extends MorphiaRegistrar>> registrars) {
    Map<String, Class> map = new ConcurrentHashMap<>();
    MorphiaRegistrarHelperPut h = (name, clazz) -> putClass(map, "io.harness." + name, clazz);
    MorphiaRegistrarHelperPut w = (name, clazz) -> putClass(map, "software.wings." + name, clazz);

    try {
      for (Class clazz : registrars) {
        Constructor<?> constructor = clazz.getConstructor();
        final MorphiaRegistrar morphiaRegistrar = (MorphiaRegistrar) constructor.newInstance();

        morphiaRegistrar.registerImplementationClasses(h, w);
      }
    } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new GeneralException("Failed to initialize MorphiaInterfaceImplementers", e);
    }

    return map;
  }

  @Provides
  @Singleton
  public Morphia morphia(@Named("morphiaClasses") Set<Class> classes,
      @Named("morphiaClasses") Map<Class, String> customCollectionName, ObjectFactory objectFactory, Injector injector,
      Set<Class<? extends TypeConverter>> morphiaConverters) {
    Morphia morphia = new Morphia();
    morphia.getMapper().getOptions().setObjectFactory(objectFactory);
    morphia.getMapper().getOptions().setMapSubPackages(true);

    Set<Class> classesCopy = new HashSet<>(classes);

    try {
      Method method =
          morphia.getMapper().getClass().getDeclaredMethod("addMappedClass", MappedClass.class, boolean.class);
      method.setAccessible(true);

      for (Map.Entry<Class, String> entry : customCollectionName.entrySet()) {
        classesCopy.remove(entry.getKey());

        HMappedClass mappedClass = new HMappedClass(entry.getValue(), entry.getKey(), morphia.getMapper());

        method.invoke(morphia.getMapper(), mappedClass, Boolean.TRUE);
      }
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new UnexpectedException("We cannot add morphia MappedClass", e);
    }
    morphia.map(classesCopy);
    morphiaConverters.forEach(
        converter -> morphia.getMapper().getConverters().addConverter(injector.getInstance(converter)));
    return morphia;
  }

  // This is a copy of the morphia method that has a bug and does not unescape '%' in the path of the jar file
  private static Set<Class<?>> getClasses(String packageName, boolean mapSubPackages)
      throws IOException, ClassNotFoundException {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    Set<Class<?>> classes = new HashSet();
    String path = packageName.replace('.', '/');
    Enumeration<URL> resources = loader.getResources(path);
    if (resources != null) {
      while (true) {
        while (true) {
          String filePath;
          do {
            if (!resources.hasMoreElements()) {
              return classes;
            }

            filePath = ((URL) resources.nextElement()).getFile();
            if (filePath.indexOf("%20") > 0) {
              filePath = filePath.replaceAll("%20", " ");
            }

            if (filePath.indexOf("%23") > 0) {
              filePath = filePath.replaceAll("%23", "#");
            }

            // {{ This is the code that was injected
            if (filePath.indexOf("%25") > 0) {
              filePath = filePath.replaceAll("%25", "%");
            }
            // }}
          } while (filePath == null);

          if (filePath.indexOf('!') > 0 && filePath.indexOf(".jar") > 0) {
            String jarPath = filePath.substring(0, filePath.indexOf('!')).substring(filePath.indexOf(':') + 1);
            if (jarPath.contains(":")) {
              jarPath = jarPath.substring(1);
            }

            classes.addAll(ReflectionUtils.getFromJARFile(loader, jarPath, path, mapSubPackages));
          } else {
            classes.addAll(ReflectionUtils.getFromDirectory(loader, new File(filePath), packageName, mapSubPackages));
          }
        }
      }
    } else {
      return classes;
    }
  }

  // This is a copy of the morphia method that calls method with a bug
  private void mapPackage(Morphia morphia, String packageName) {
    try {
      Iterator classes = getClasses(packageName, morphia.getMapper().getOptions().isMapSubPackages()).iterator();

      while (classes.hasNext()) {
        Class clazz = (Class) classes.next();

        try {
          Entity entityAnn = ReflectionUtils.getClassEntityAnnotation(clazz);
          boolean isAbstract = Modifier.isAbstract(clazz.getModifiers());
          if (entityAnn != null && !isAbstract) {
            morphia.map(clazz);
          }
        } catch (MappingException exception) {
          Switch.unhandled(exception);
        }
      }
    } catch (IOException exception) {
      throw new MappingException("Could not get map classes from package " + packageName, exception);
    } catch (ClassNotFoundException exception) {
      throw new MappingException("Could not get map classes from package " + packageName, exception);
    }
  }

  public void testAutomaticSearch(Provider<Set<Class>> classesProvider) {
    Morphia morphia;
    try {
      morphia = new Morphia();
      morphia.getMapper().getOptions().setMapSubPackages(true);
      mapPackage(morphia, "software.wings");
      mapPackage(morphia, "io.harness");
    } catch (NoClassDefFoundError error) {
      ignoredOnPurpose(error);
      return;
    }

    log.info("Checking {} classes", morphia.getMapper().getMappedClasses().size());

    boolean success = true;
    for (MappedClass cls : morphia.getMapper().getMappedClasses()) {
      if (!classesProvider.get().contains(cls.getClazz())) {
        log.error("Class {} is missing in the registrars", cls.getClazz().getName());
        success = false;
      }
    }

    if (!success) {
      throw new GeneralException("there are classes that are not registered");
    }
  }

  public void testAllRegistrars(Provider<Set<Class<? extends MorphiaRegistrar>>> registrarsProvider) {
    try {
      for (Class clazz : registrarsProvider.get()) {
        Constructor<?> constructor = null;
        constructor = clazz.getConstructor();
        final MorphiaRegistrar morphiaRegistrar = (MorphiaRegistrar) constructor.newInstance();

        if (CodeUtils.isTestClass(clazz)) {
          continue;
        }

        log.info("Checking registrar {}", clazz.getName());
        morphiaRegistrar.testClassesModule();
        morphiaRegistrar.testImplementationClassesModule();
      }
    } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new UnexpectedException("Unexpected exception while constructing registrar", e);
    }
  }

  @Override
  protected void configure() {
    MapBinder<String, TestExecution> testExecutionMapBinder =
        MapBinder.newMapBinder(binder(), String.class, TestExecution.class);
    if (!binder().currentStage().name().equals("TOOL")) {
      Provider<Set<Class>> providerClasses =
          getProvider(Key.get(new TypeLiteral<Set<Class>>() {}, Names.named("morphiaClasses")));
      testExecutionMapBinder.addBinding("Morphia test registration")
          .toInstance(() -> testAutomaticSearch(providerClasses));
      Provider<Set<Class<? extends MorphiaRegistrar>>> providerRegistrars =
          getProvider(Key.get(new TypeLiteral<Set<Class<? extends MorphiaRegistrar>>>() {}));
      testExecutionMapBinder.addBinding("Morphia test registrars")
          .toInstance(() -> testAllRegistrars(providerRegistrars));
    }
  }
}
