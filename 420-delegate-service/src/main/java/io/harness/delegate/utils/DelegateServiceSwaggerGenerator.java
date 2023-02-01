package io.harness.delegate.utils;

import io.harness.delegate.resources.DelegateSetupResource;
import io.harness.swagger.SwaggerBundleConfigurationFactory;

import com.google.common.collect.ImmutableList;
import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import java.util.Collection;
import java.util.List;

public class DelegateServiceSwaggerGenerator {
  // Add resource classes explicitly here.
  // Adding DelegateSetupResource as a dummy resource for swagger to work, will change later.
  private static final List<Class<?>> RESOURCE_CLASSES =
      ImmutableList.<Class<?>>builder().add(DelegateSetupResource.class).build();

  /**
   * Gets swagger bundle configuration.
   *
   * @return the swagger bundle configuration
   */
  public static SwaggerBundleConfiguration getSwaggerBundleConfiguration() {
    Collection<Class<?>> resourceClasses = getResourceClasses();
    SwaggerBundleConfiguration defaultSwaggerBundleConfiguration =
        SwaggerBundleConfigurationFactory.buildSwaggerBundleConfiguration(resourceClasses);
    defaultSwaggerBundleConfiguration.setResourcePackage("io.harness.delegate.resources");
    defaultSwaggerBundleConfiguration.setSchemes(new String[] {"https", "http"});
    defaultSwaggerBundleConfiguration.setHost("{{host}}");
    defaultSwaggerBundleConfiguration.setTitle("Delegate Service API Reference");
    return defaultSwaggerBundleConfiguration;
  }

  public static Collection<Class<?>> getResourceClasses() {
    return RESOURCE_CLASSES;
  }
}
