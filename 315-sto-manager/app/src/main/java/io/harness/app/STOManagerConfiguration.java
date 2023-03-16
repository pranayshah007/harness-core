/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import static java.util.stream.Collectors.toSet;

import io.harness.mongo.MongoConfig;
import io.harness.reflection.HarnessReflections;

import io.federecio.dropwizard.swagger.SwaggerBundleConfiguration;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.integration.api.OpenAPIConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.Path;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class STOManagerConfiguration {
  public static final String MONGO_CONNECTION_ENV_NAME = "STOMANAGER_MONGO_URI";
  public static final String DEFAULT_MONGO_CONNECTION = "mongodb://localhost:27017/sto-harness";
  public static final String BASE_PACKAGE = "io.harness.app.resources";

  public static final String CI_API_PACKAGE = "io.harness.ci.api";
  public static final String NG_PIPELINE_PACKAGE = "io.harness.ngpipeline";
  public static final String ENFORCEMENT_CLIENT_PACKAGE = "io.harness.enforcement.client.resources";
  protected static final Collection<Class<?>> HARNESS_RESOURCE_CLASSES = getResourceClasses();

  public static SwaggerBundleConfiguration getSwaggerBundleConfiguration(
      SwaggerBundleConfiguration swaggerBundleConfiguration) {
    SwaggerBundleConfiguration defaultSwaggerBundleConfiguration = new SwaggerBundleConfiguration();

    String resourcePackage = String.join(",", getUniquePackages(HARNESS_RESOURCE_CLASSES));
    defaultSwaggerBundleConfiguration.setResourcePackage(resourcePackage);
    defaultSwaggerBundleConfiguration.setSchemes(new String[] {"https", "http"});
    defaultSwaggerBundleConfiguration.setHost(
        "localhost"); // TODO, we should set the appropriate host here ex: qa.harness.io etc
    defaultSwaggerBundleConfiguration.setTitle("STO API Reference");
    defaultSwaggerBundleConfiguration.setVersion("2.0");

    return Optional.ofNullable(swaggerBundleConfiguration).orElse(defaultSwaggerBundleConfiguration);
  }

  public static MongoConfig getHarnessSTOMongo(MongoConfig config) {
    String envConnection = System.getenv(MONGO_CONNECTION_ENV_NAME);
    return config.toBuilder().uri(envConnection == null ? DEFAULT_MONGO_CONNECTION : envConnection).build();
  }

  public static OpenAPIConfiguration getOasConfig(String hostname, String basePathPrefix) {
    OpenAPI oas = new OpenAPI();
    Info info =
        new Info()
            .title("STO API Reference")
            .description(
                "This is the Open Api Spec 3 for the STO Manager. This is under active development. Beware of the breaking change with respect to the generated code stub")
            .termsOfService("https://harness.io/terms-of-use/")
            .version("3.0")
            .contact(new Contact().email("contact@harness.io"));
    oas.info(info);
    URL baseurl = null;
    try {
      baseurl = new URL("https", hostname, basePathPrefix);
      Server server = new Server();
      server.setUrl(baseurl.toString());
      oas.servers(Collections.singletonList(server));
    } catch (MalformedURLException e) {
      log.error("failed to set baseurl for server, {}/{}", hostname, basePathPrefix);
    }
    Set<String> packages = getUniquePackages(getOAS3ResourceClassesOnly());
    return new SwaggerConfiguration().openAPI(oas).prettyPrint(true).resourcePackages(packages).scannerClass(
        "io.swagger.v3.jaxrs2.integration.JaxrsAnnotationScanner");
  }

  public static Collection<Class<?>> getResourceClasses() {
    return HarnessReflections.get()
        .getTypesAnnotatedWith(Path.class)
        .stream()
        .filter(klazz
            -> StringUtils.startsWithAny(klazz.getPackage().getName(), BASE_PACKAGE, CI_API_PACKAGE,
                NG_PIPELINE_PACKAGE, ENFORCEMENT_CLIENT_PACKAGE))
        .collect(Collectors.toSet());
  }

  public static Collection<Class<?>> getOAS3ResourceClassesOnly() {
    return HARNESS_RESOURCE_CLASSES.stream().filter(x -> x.isAnnotationPresent(Tag.class)).collect(Collectors.toList());
  }

  protected static Set<String> getUniquePackages(Collection<Class<?>> classes) {
    return classes.stream().map(aClass -> aClass.getPackage().getName()).collect(toSet());
  }
}
