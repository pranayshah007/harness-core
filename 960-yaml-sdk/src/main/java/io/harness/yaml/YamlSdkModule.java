/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;
import io.harness.testing.TestExecution;
import io.harness.yaml.schema.AbstractSchemaChecker;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;
import io.harness.yaml.snippets.AbstractSnippetChecker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Names;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(DX)
public class YamlSdkModule extends AbstractModule {
  private static volatile YamlSdkModule defaultInstance;

  public static YamlSdkModule getInstance() {
    if (defaultInstance == null) {
      defaultInstance = new YamlSdkModule();
    }
    return defaultInstance;
  }

  private YamlSdkModule() {}

  private void testSchemas() {
    final AbstractSchemaChecker abstractSchemaChecker = new AbstractSchemaChecker();
    try {
      abstractSchemaChecker.schemaTests();
    } catch (Exception e) {
      throw new GeneralException(e.getLocalizedMessage());
    }
  }

  private void testSnippets(
      Provider<List<YamlSchemaRootClass>> yamlSchemaRootClasses, Provider<ObjectMapper> providerMapper) {
    final AbstractSnippetChecker abstractSnippetChecker =
        new AbstractSnippetChecker(yamlSchemaRootClasses.get(), providerMapper.get());
    try {
      abstractSnippetChecker.snippetTests();
    } catch (Exception e) {
      throw new GeneralException(e.getLocalizedMessage());
    }
  }

  @Override
  protected void configure() {
    MapBinder<String, TestExecution> testExecutionMapBinder =
        MapBinder.newMapBinder(binder(), String.class, TestExecution.class);
    if (!binder().currentStage().name().equals("TOOL")) {
      Provider<List<YamlSchemaRootClass>> providerClasses =
          getProvider(Key.get(new TypeLiteral<List<YamlSchemaRootClass>>() {}));
      Provider<ObjectMapper> providerMapper =
          getProvider(Key.get(new TypeLiteral<ObjectMapper>() {}, Names.named("yaml-schema-mapper")));
      // todo(abhinav): add auto discovery of schema classes if it becomes chaotic.
      //      testExecutionMapBinder.addBinding("YamlSchema test registration")
      //              .toInstance(() -> testAutomaticSearch(providerClasses));

      testExecutionMapBinder.addBinding("Yaml Schema test registrars").toInstance(() -> testSchemas());
      testExecutionMapBinder.addBinding("Yaml Snippet test registrars")
          .toInstance(() -> testSnippets(providerClasses, providerMapper));
    }
  }
}
