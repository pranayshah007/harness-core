/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.provider;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.ff.FeatureFlagService;

import software.wings.graphql.directive.DataFetcherDirective;
import software.wings.graphql.instrumentation.QLAuditInstrumentation;
import software.wings.graphql.instrumentation.QueryDepthInstrumentation;
import software.wings.graphql.scalar.GraphQLDateTimeScalar;
import software.wings.graphql.scalar.LongScalar;
import software.wings.graphql.scalar.NumberScalar;
import software.wings.graphql.schema.TypeResolverManager;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import graphql.GraphQL;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentationOptions;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;

@Singleton
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class GraphQLProvider implements QueryLanguageProvider<GraphQL> {
  private static final String GRAPHQL_SCHEMA_PUBLIC_DIRECTORY_PATH = "graphql/public/";
  private static final String GRAPHQL_SCHEMA_PRIVATE_DIRECTORY_PATH = "graphql/private/";
  private static final Pattern GRAPHQL_FILE_PATTERN = Pattern.compile(".*\\.graphql$");

  private GraphQL privateGraphQL;
  private GraphQL publicGraphQL;

  @Inject private TypeResolverManager typeResolverManager;
  @Inject private DataFetcherDirective dataFetcherDirective;
  @Inject FeatureFlagService featureFlagService;
  @Inject QLAuditInstrumentation qlAuditInstrumentation;

  @Inject
  public void init() {
    TypeDefinitionRegistry typeDefinitionRegistry = new TypeDefinitionRegistry();
    publicGraphQL = getGraphQL(GRAPHQL_SCHEMA_PUBLIC_DIRECTORY_PATH, typeDefinitionRegistry);
    privateGraphQL = getGraphQL(GRAPHQL_SCHEMA_PRIVATE_DIRECTORY_PATH, typeDefinitionRegistry);
  }

  private GraphQL getGraphQL(String path, TypeDefinitionRegistry typeDefinitionRegistry) {
    SchemaParser schemaParser = new SchemaParser();

    loadSchemaForEnv(path, typeDefinitionRegistry, schemaParser);
    RuntimeWiring runtimeWiring = buildRuntimeWiring();

    SchemaGenerator schemaGenerator = new SchemaGenerator();

    GraphQLSchema graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);

    DataLoaderDispatcherInstrumentationOptions options =
        DataLoaderDispatcherInstrumentationOptions.newOptions().includeStatistics(false);

    DataLoaderDispatcherInstrumentation dispatcherInstrumentation = new DataLoaderDispatcherInstrumentation(options);

    return GraphQL.newGraphQL(graphQLSchema)
        .instrumentation(dispatcherInstrumentation)
        .instrumentation(
            new ChainedInstrumentation(Arrays.asList(new QueryDepthInstrumentation(), qlAuditInstrumentation)))
        .build();
  }

  private void loadSchemaForEnv(
      final String schemaPathForEnv, TypeDefinitionRegistry typeDefinitionRegistry, final SchemaParser schemaParser) {
    Reflections reflections = new Reflections(schemaPathForEnv, new ResourcesScanner());
    reflections.getResources(GRAPHQL_FILE_PATTERN)
        .forEach(resource -> typeDefinitionRegistry.merge(schemaParser.parse(loadSchemaFile(resource))));
  }

  private RuntimeWiring buildRuntimeWiring() {
    RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();

    typeResolverManager.getTypeResolverMap().forEach(
        (k, v) -> builder.type(k, typeWiring -> typeWiring.typeResolver(v)));

    builder.scalar(GraphQLDateTimeScalar.type)
        .scalar(LongScalar.type)
        .scalar(NumberScalar.type)
        .directive("dataFetcher", dataFetcherDirective);
    return builder.build();
  }

  private String loadSchemaFile(String resource) {
    try {
      URL url = GraphQLProvider.class.getClassLoader().getResource(resource);
      return Resources.toString(url, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(String.format("Failed to read %s file", resource), e);
    }
  }

  @Override
  public GraphQL getPrivateGraphQL() {
    return privateGraphQL;
  }

  @Override
  public GraphQL getPublicGraphQL() {
    return publicGraphQL;
  }
}
