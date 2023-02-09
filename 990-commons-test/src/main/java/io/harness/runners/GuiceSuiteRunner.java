/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.runners;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

public class GuiceSuiteRunner extends Suite {
  private static Injector injector;

  public GuiceSuiteRunner(Class<?> klass, RunnerBuilder builder) throws Exception {
    this(builder, klass, getAnnotatedClasses(klass));
  }

  protected GuiceSuiteRunner(RunnerBuilder builder, Class<?> klass, Class<?>[] suiteClasses) throws Exception {
    super(klass,
        Arrays.stream(suiteClasses)
            .map(sc -> {
              try {
                return getRunner(klass, sc);
              } catch (Exception e) {
                return null;
              }
            })
            .collect(Collectors.toList()));
  }

  @SneakyThrows
  private static Injector getInjector(Class<?> sc) {
    if (injector == null) {
      injector = Guice.createInjector(getModulesFor(sc));
    }
    return injector;
  }

  private static Class<?>[] getAnnotatedClasses(Class<?> klass) throws InitializationError {
    SuiteClasses annotation = klass.getAnnotation(SuiteClasses.class);
    if (annotation == null) {
      throw new InitializationError(String.format("class '%s' must have a SuiteClasses annotation", klass.getName()));
    } else {
      return annotation.value();
    }
  }

  private static List<Module> getModulesFor(Class<?> klass)
      throws InitializationError, IllegalAccessException, InstantiationException {
    final ModuleProvider annotation = klass.getAnnotation(ModuleProvider.class);
    if (annotation == null) {
      final String message =
          String.format("Missing ModuleProvider annotation for HarnessTestSuite Class '%s'", klass.getName());
      throw new InitializationError(message);
    }
    ModuleListProvider moduleListProvider = annotation.value().newInstance();
    return moduleListProvider.modules();
  }

  private static GuiceRunner getRunner(Class<?> klass, Class<?> sc) throws Exception {
    final ModuleRunner annotation = klass.getAnnotation(ModuleRunner.class);
    if (annotation == null) {
      final String message =
          String.format("Missing ModuleProvider annotation for HarnessTestSuite Class '%s'", klass.getName());
      throw new InitializationError(message);
    }
    Class<?> moduleRunner = annotation.value();
    return (GuiceRunner) moduleRunner.getDeclaredConstructor(Class.class, Injector.class)
        .newInstance(sc, getInjector(sc));
  }
}
