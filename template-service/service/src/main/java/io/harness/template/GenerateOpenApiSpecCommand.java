/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

import io.harness.TemplateServiceConfiguration;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import io.swagger.v3.jaxrs2.integration.resources.BaseOpenApiResource;
import java.io.FileOutputStream;
import java.io.OutputStream;
import javax.servlet.ServletConfig;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

@Slf4j
public class GenerateOpenApiSpecCommand extends ConfiguredCommand<TemplateServiceConfiguration> {
  public static final String OUTPUT_FILE_PATH = "outputFilePath";
  private OutputStream outputStream;

  public GenerateOpenApiSpecCommand() {
    super("generate-openapi-spec", "Generates Openapi 3 specification file");
  }

  @VisibleForTesting
  public GenerateOpenApiSpecCommand(OutputStream outputStream) {
    this();
    this.outputStream = outputStream;
  }

  @Override
  public void configure(Subparser subparser) {
    subparser.addArgument(OUTPUT_FILE_PATH).help("Absolute path to output openapi spec file");
    subparser.addArgument(new String[] {"file"}).nargs("?").help("application configuration file");
  }

  @Override
  protected void run(Bootstrap<TemplateServiceConfiguration> bootstrap, Namespace namespace,
      TemplateServiceConfiguration configuration) throws Exception {
    String outputFilePath = namespace.getString(OUTPUT_FILE_PATH);

    LocalOpenAPIResource localOpenAPIResource = new LocalOpenAPIResource();

    localOpenAPIResource.setOpenApiConfiguration(configuration.getOasConfig());
    Response json = localOpenAPIResource.getOpenApi(null, null, null, null, APPLICATION_JSON_TYPE.getSubtype());

    try (OutputStream out = outputStream != null ? outputStream : new FileOutputStream(outputFilePath)) {
      String openApiSpecContent = (String) json.getEntity();
      out.write(openApiSpecContent.getBytes());
    } catch (Exception exception) {
      log.error("Failed to generate OpenAPI spec at location : " + OUTPUT_FILE_PATH + " because of : " + exception);
    }
  }

  static class LocalOpenAPIResource extends BaseOpenApiResource {
    @Override
    protected Response getOpenApi(
        HttpHeaders headers, ServletConfig config, Application app, UriInfo uriInfo, String type) throws Exception {
      return super.getOpenApi(headers, config, app, uriInfo, type);
    }
  }
}
