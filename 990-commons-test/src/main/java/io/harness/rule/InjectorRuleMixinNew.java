/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rule;

import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.asList;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import java.lang.annotation.Annotation;
import java.util.List;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface InjectorRuleMixinNew {
  List<Module> modules(List<Annotation> annotations) throws Exception;

  default void initialize(Injector injector, List<Module> modules) {}

  default Statement applyInjector(
      Logger log, Statement statement, FrameworkMethod frameworkMethod, Object target, Injector[] injector) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        final List<Annotation> annotations = asList(frameworkMethod.getAnnotations());
        final List<Module> modules = modules(annotations);
        long created = currentTimeMillis();
        if (injector[0] == null) {
          long start = currentTimeMillis();
          injector[0] = Guice.createInjector(modules);

          log.info("Creating guice injector took: {}ms", created - start);

          initialize(injector[0], modules);
        }
        injector[0].injectMembers(target);

        long initialized = currentTimeMillis();
        log.info("Initializing test injections took: {}ms", initialized - created);

        try {
          statement.evaluate();
        } catch (RuntimeException exception) {
          LoggerFactory.getLogger(InjectorRuleMixin.class).error("Test exception", exception);
          throw exception;
        }
      }
    };
  }
}
