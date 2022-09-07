/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.eraro.ErrorCode.RESOURCE_NOT_FOUND;
import static io.harness.exception.WingsException.USER;
import static io.harness.govern.Switch.unhandled;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;

import io.harness.configuration.ConfigurationType;
import io.harness.exception.WingsException;
import io.harness.health.HealthException;
import io.harness.health.HealthService;
import io.harness.mongo.MongoConfig;
import io.harness.rest.RestResponse;
import io.harness.security.AsymmetricEncryptor;
import io.harness.security.annotations.PublicApi;

import software.wings.app.MainConfiguration;
import software.wings.search.framework.ElasticsearchConfig;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheck.Result;
import com.codahale.metrics.health.jvm.ThreadDeadlockHealthCheck;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by brett on 12/3/17
 */
@Api("health")
@Path("/health")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@PublicApi
@Slf4j
public class HealthResource {
  private MainConfiguration mainConfiguration;
  private AsymmetricEncryptor asymmetricEncryptor;
  private HealthService healthService;
  private final ThreadDeadlockHealthCheck threadDeadlockHealthCheck;

  @Inject
  public HealthResource(
      MainConfiguration mainConfiguration, AsymmetricEncryptor asymmetricEncryptor, HealthService healthService) {
    this.mainConfiguration = mainConfiguration;
    this.asymmetricEncryptor = asymmetricEncryptor;
    this.healthService = healthService;
    this.threadDeadlockHealthCheck = new ThreadDeadlockHealthCheck();
  }

  @GET
  @Timed
  @ExceptionMetered
  public RestResponse<String> doReadinessCheck() throws Exception {
    if (getMaintenanceFlag()) {
      log.info("In maintenance mode. Throwing exception to prevent traffic.");
      throw new WingsException(RESOURCE_NOT_FOUND, USER);
    }

    final Result check = healthService.check();
    if (check.isHealthy()) {
      return new RestResponse<>("healthy");
    }

    throw new HealthException(check.getMessage(), check.getError());
  }

  @GET
  @Path("liveness")
  @Timed
  @ExceptionMetered
  public RestResponse<String> doLivenessCheck() {
    HealthCheck.Result check = threadDeadlockHealthCheck.execute();
    if (check.isHealthy()) {
      return new RestResponse<>("live");
    }
    log.info(check.getMessage());
    throw new HealthException(check.getMessage(), check.getError());
  }

  @GET
  @Path("configuration")
  @Timed
  @ExceptionMetered
  public RestResponse<Object> getConfiguration(@QueryParam("configurationType") ConfigurationType configurationType)
      throws IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, NoSuchAlgorithmException,
             InvalidKeyException {
    // The following code can be refactored to move to service layer
    switch (configurationType) {
      case MONGO:
        MongoConfig mongoConfig = mainConfiguration.getMongoConnectionFactory();
        return new RestResponse<>(MongoConfig.builder()
                                      .encryptedUri(asymmetricEncryptor.encryptText(mongoConfig.getUri()))
                                      .encryptedLocksUri(asymmetricEncryptor.encryptText(
                                          mongoConfig.getLocksUri() != null ? mongoConfig.getLocksUri() : ""))
                                      .build());
      case ELASTICSEARCH:
        ElasticsearchConfig elasticsearchConfig = mainConfiguration.getElasticsearchConfig();
        return new RestResponse<>(ElasticsearchConfig.builder()
                                      .encryptedUri(asymmetricEncryptor.encryptText(elasticsearchConfig.getUri()))
                                      .indexSuffix(elasticsearchConfig.getIndexSuffix())
                                      .build());
      case SEARCH_ENABLED:
        boolean isSearchEnabled = mainConfiguration.isSearchEnabled();
        return new RestResponse<>(isSearchEnabled);
      default:
        unhandled(configurationType);
    }

    return new RestResponse<>();
  }
}
