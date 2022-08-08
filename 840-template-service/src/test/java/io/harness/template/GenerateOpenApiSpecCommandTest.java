/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template;

import static io.harness.rule.OwnerRule.ASHISHSANODIA;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.TemplateServiceApplication;
import io.harness.TemplateServiceConfiguration;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import io.dropwizard.cli.Cli;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.util.JarLocation;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.util.Optional;
import org.assertj.core.api.SoftAssertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GenerateOpenApiSpecCommandTest {
  public static final String TEST_OPENAPI_JSON = "openapi.json";
  public static final String GENERATE_OPENAPI_SPEC = "generate-openapi-spec";
  public static final String EXIT_SUCCESS_DESCRIPTION = "Exit success";
  public static final String OPEN_API_CONTENT_DESCRIPTION = "OpenApi Content";
  public static final String STDERR_DESCRIPTION = "stderr";

  private final PrintStream originalOut = System.out;
  private final PrintStream originalErr = System.err;
  private final InputStream originalIn = System.in;
  private final ByteArrayOutputStream openApiOutputStream = new ByteArrayOutputStream();

  private final ByteArrayOutputStream stdOut = new ByteArrayOutputStream();
  private final ByteArrayOutputStream stdErr = new ByteArrayOutputStream();
  private Cli cli;

  @Before
  public void setUp() throws MalformedURLException {
    // Setup necessary mock
    final JarLocation location = mock(JarLocation.class);
    when(location.getVersion()).thenReturn(Optional.of("1.0.0"));

    // Add commands you want to test
    final Bootstrap<TemplateServiceConfiguration> bootstrap = new Bootstrap<>(new TemplateServiceApplication());
    bootstrap.addCommand(new GenerateOpenApiSpecCommand(openApiOutputStream));

    // Redirect stdout and stderr to our byte streams
    System.setOut(new PrintStream(stdOut));
    System.setErr(new PrintStream(stdErr));

    // Build what'll run the command and interpret arguments
    cli = new Cli(location, bootstrap, stdOut, stdErr);
  }

  @After
  public void teardown() {
    System.setOut(originalOut);
    System.setErr(originalErr);
    System.setIn(originalIn);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void generatesOpenApiSpec() throws Exception {
    final Optional<Throwable> success = cli.run(GENERATE_OPENAPI_SPEC, TEST_OPENAPI_JSON);
    String openApiContent = openApiOutputStream.toString(UTF_8.name());

    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(success).as(EXIT_SUCCESS_DESCRIPTION).isEmpty();
    softly.assertThat(openApiContent).as(OPEN_API_CONTENT_DESCRIPTION).isNotEmpty();
    softly.assertThat(stdErr.toString()).as(STDERR_DESCRIPTION).isEmpty();
    softly.assertAll();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void doNotGeneratesOpenApiSpecIfOutputFileParameterNotProvided() throws Exception {
    final Optional<Throwable> success = cli.run(GENERATE_OPENAPI_SPEC);
    String openApiContent = openApiOutputStream.toString(UTF_8.name());

    SoftAssertions softly = new SoftAssertions();
    softly.assertThat(success).as(EXIT_SUCCESS_DESCRIPTION).isNotEmpty();
    softly.assertThat(openApiContent).as(OPEN_API_CONTENT_DESCRIPTION).isEmpty();
    softly.assertThat(stdErr.toString()).as(STDERR_DESCRIPTION).isNotEmpty();
    softly.assertAll();
  }
}
