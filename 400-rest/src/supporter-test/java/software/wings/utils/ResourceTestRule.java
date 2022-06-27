/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.ConstraintViolationExceptionMapper;

import software.wings.jersey.KryoMessageBodyProvider;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.common.base.Preconditions;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.DropwizardResourceConfig;
import io.dropwizard.jersey.jackson.JacksonMessageBodyProvider;
import io.dropwizard.jersey.validation.Validators;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletConfig;
import javax.validation.Validator;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Context;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.Singular;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
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
@OwnedBy(PL)
@Getter
@Builder
@TargetModule(HarnessModule._980_COMMONS)
@Deprecated
public class ResourceTestRule implements TestRule {
  @Singular private final Set<Object> instances;
  @Singular private final Set<Class<?>> types;
  @Singular private final Map<String, Object> properties;
  @Default private ObjectMapper mapper = Jackson.newObjectMapper();
  @Default private Validator validator = Validators.newValidator();
  @Default private TestContainerFactory testContainerFactory = new InMemoryTestContainerFactory();

  private JerseyTest test;

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
              config.register(KryoMessageBodyProvider.class, 0);
              config.register(MultiPartFeature.class);

              JacksonJsonProvider jsonProvider = new JacksonJsonProvider();
              jsonProvider.setMapper(mapper);
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

    /**
     * Instantiates a new Resource test resource config.
     *
     * @param servletConfig the servlet config
     */
    public ResourceTestResourceConfig(@Context ServletConfig servletConfig) {
      super(new MetricRegistry());
      String ruleId = servletConfig.getInitParameter(RULE_ID);
      Preconditions.checkNotNull(ruleId);

      ResourceTestRule resourceTestRule = RULE_ID_TO_RULE.get(ruleId);
      Preconditions.checkNotNull(resourceTestRule);
      configure(resourceTestRule);
    }

    private void configure(ResourceTestRule resourceTestRule) {
      register(new ConstraintViolationExceptionMapper());
      register(new JacksonMessageBodyProvider(resourceTestRule.mapper));
      // TODO: refactor to allow initialization of KryoFeature
      // register(KryoFeature.class);

      property(ServerProperties.RESPONSE_SET_STATUS_OVER_SEND_ERROR, "true");

      if (isNotEmpty(resourceTestRule.types)) {
        for (Class<?> provider : resourceTestRule.types) {
          register(provider);
        }
      }

      if (isNotEmpty(resourceTestRule.properties)) {
        for (Map.Entry<String, Object> property : resourceTestRule.properties.entrySet()) {
          property(property.getKey(), property.getValue());
        }
      }

      if (isNotEmpty(resourceTestRule.instances)) {
        for (Object instance : resourceTestRule.instances) {
          register(instance);
        }
      }
    }
  }
}
