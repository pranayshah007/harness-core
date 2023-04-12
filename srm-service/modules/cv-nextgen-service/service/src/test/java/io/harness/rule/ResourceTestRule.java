/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rule;

import io.harness.accesscontrol.NGAccessDeniedExceptionMapper;
import io.harness.cvng.exception.BadRequestExceptionMapper;
import io.harness.cvng.exception.ConstraintViolationExceptionMapper;
import io.harness.cvng.exception.GenericExceptionMapper;
import io.harness.cvng.exception.NotFoundExceptionMapper;
import io.harness.serializer.JsonSubtypeResolver;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.common.collect.Sets;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.DropwizardResourceConfig;
import io.dropwizard.jersey.jackson.JacksonMessageBodyProvider;
import io.dropwizard.jersey.validation.Validators;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.validation.Validator;
import javax.ws.rs.client.Client;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.servlet.ServletProperties;
import org.glassfish.jersey.test.DeploymentContext;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.ServletDeploymentContext;
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * @deprecated
 * Avoid using {@link ResourceTestRule} if it needs to be used in a static way (which is AFAIK always). When used like
 * this all mocks that rule "instantiates" need to be static which means that verification and/or stubbing is carried
 * over between tests (we need to use atLeastOnce() to overcome this for example which is bad). This makes tests flaky
 * and less safe, but also all mocking is lenient which makes them even less reliable. <P> <P> Instead, it's better to
 * use {@link JerseyTest} directly. You can find example of good usage of JerseyTest with non-static mocked service in
 * the see also section.
 *
 * @see software.wings.resources.DelegateSetupResourceTest Example of bad usage
 * @see io.harness.delegate.resources.DelegateUpgraderResourceTest Example of good usage
 */
public class ResourceTestRule implements TestRule {
  private final Set<Object> singletons;
  private final Set<Class<?>> providers;
  private final Map<String, Object> properties;
  private final ObjectMapper mapper;
  private final TestContainerFactory testContainerFactory;
  private JerseyTest test;

  private ResourceTestRule(Set<Object> singletons, Set<Class<?>> providers, Map<String, Object> properties,
      ObjectMapper mapper, Validator validator, TestContainerFactory testContainerFactory) {
    this.singletons = singletons;
    this.providers = providers;
    this.properties = properties;
    this.mapper = mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true);
    this.testContainerFactory = testContainerFactory;
  }

  /**
   * Builder builder.
   *
   * @return the builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Client client.
   *
   * @return the client
   */
  public Client client() {
    return test.client();
  }

  @Override
  public Statement apply(Statement base, Description description) {
    ResourceTestRule rule = this;
    String ruleId = String.valueOf(rule.hashCode());
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        try {
          test = new JerseyTest() {
            @Override
            protected TestContainerFactory getTestContainerFactory() {
              return testContainerFactory;
            }

            @Override
            protected DeploymentContext configureDeployment() {
              ResourceTestResourceConfig resourceConfig = new ResourceTestResourceConfig(ruleId, rule);
              return ServletDeploymentContext.builder(resourceConfig)
                  .initParam(ServletProperties.JAXRS_APPLICATION_CLASS, ResourceTestResourceConfig.class.getName())
                  .initParam(ResourceTestResourceConfig.RULE_ID, ruleId)
                  .build();
            }

            @Override
            protected void configureClient(ClientConfig config) {
              JacksonJsonProvider jsonProvider = new JacksonJsonProvider();
              jsonProvider.setMapper(mapper);
              mapper.setSubtypeResolver(new JsonSubtypeResolver(mapper.getSubtypeResolver()));
              config.register(jsonProvider);
            }
          };
          test.setUp();
          base.evaluate();
        } finally {
          ResourceTestResourceConfig.RULE_ID_TO_RULE.remove(ruleId);
          if (test != null) {
            test.tearDown();
          }
        }
      }
    };
  }

  /**
   * The type Builder.
   */
  public static class Builder {
    private final Set<Object> singletons = Sets.newHashSet();
    private final Set<Class<?>> providers = Sets.newHashSet();
    private final Map<String, Object> properties = new HashMap<>();
    private ObjectMapper mapper = Jackson.newObjectMapper();
    private Validator validator = Validators.newValidator();
    private TestContainerFactory testContainerFactory = new InMemoryTestContainerFactory();

    /**
     * Add resource builder.
     *
     * @param resource the resource
     * @return the builder
     */
    public Builder addResource(Object resource) {
      singletons.add(resource);
      return this;
    }

    /**
     * Build resource test rule.
     *
     * @return the resource test rule
     */
    public ResourceTestRule build() {
      return new ResourceTestRule(singletons, providers, properties, mapper, validator, testContainerFactory);
    }
  }

  /**
   * The type Resource test resource config.
   */
  public static class ResourceTestResourceConfig extends DropwizardResourceConfig {
    private static final String RULE_ID = "io.dropwizard.testing.junit.resourceTestRuleId";
    private static final Map<String, ResourceTestRule> RULE_ID_TO_RULE = new HashMap<>();

    /**
     * Instantiates a new Resource test resource config.
     *
     * @param ruleId           the rule id
     * @param resourceTestRule the resource test rule
     */
    public ResourceTestResourceConfig(String ruleId, ResourceTestRule resourceTestRule) {
      super(new MetricRegistry());
      RULE_ID_TO_RULE.put(ruleId, resourceTestRule);
      configure(resourceTestRule);
    }

    private void configure(ResourceTestRule resourceTestRule) {
      register(ConstraintViolationExceptionMapper.class);
      register(BadRequestExceptionMapper.class);
      register(NotFoundExceptionMapper.class);
      register(GenericExceptionMapper.class);
      register(NGAccessDeniedExceptionMapper.class);
      for (Class<?> provider : resourceTestRule.providers) {
        register(provider);
      }
      property(ServerProperties.RESPONSE_SET_STATUS_OVER_SEND_ERROR, "true");
      for (Map.Entry<String, Object> property : resourceTestRule.properties.entrySet()) {
        property(property.getKey(), property.getValue());
      }
      register(new JacksonMessageBodyProvider(resourceTestRule.mapper));
      for (Object singleton : resourceTestRule.singletons) {
        register(singleton);
      }
    }
  }
}
