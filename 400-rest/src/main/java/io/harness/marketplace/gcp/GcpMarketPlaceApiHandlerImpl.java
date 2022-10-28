/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.marketplace.gcp;

import static io.harness.annotations.dev.HarnessModule._940_MARKETPLACE_INTEGRATIONS;
import static io.harness.annotations.dev.HarnessTeam.GTM;
import static io.harness.exception.WingsException.USER;
import static io.harness.marketplace.gcp.GcpMarketPlaceConstants.TOKEN_AUDIENCE;
import static io.harness.marketplace.gcp.GcpMarketPlaceConstants.TOKEN_ISSUER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.configuration.DeployMode;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.event.handler.impl.segment.SegmentHandler.Keys;
import io.harness.event.handler.impl.segment.SegmentHelper;
import io.harness.exception.FailureType;
import io.harness.exception.GeneralException;
import io.harness.exception.SignupException;
import io.harness.marketplace.gcp.procurement.GcpProcurementService;
import io.harness.marketplace.gcp.signup.GcpMarketplaceSignUpHandler;
import io.harness.marketplace.gcp.signup.annotations.NewSignUp;

import software.wings.app.MainConfiguration;
import software.wings.beans.marketplace.gcp.GCPMarketplaceCustomer;
import software.wings.dl.WingsPersistence;
import software.wings.security.authentication.AuthenticationUtils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPublicKey;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@OwnedBy(GTM)
@TargetModule(_940_MARKETPLACE_INTEGRATIONS)
@Slf4j
@Singleton
public class GcpMarketPlaceApiHandlerImpl implements GcpMarketPlaceApiHandler {
  @Inject private MainConfiguration configuration;
  @Inject private AuthenticationUtils authenticationUtils;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private GcpProcurementService gcpProcurementService;
  @Inject private SegmentHelper segmentHelper;
  @Inject @NewSignUp private GcpMarketplaceSignUpHandler newUserSignUpHandler;
  private static String SYSTEM = "system";
  private static String GCP_BILLING_REQUEST_RECEIVED = "Gcp Billing Request received";
  private static String GCP_BILLING_SUCCESS = "Gcp Billing Success";
  private static String GCP_BILLING_FAILURE = "Gcp Billing Failure";
  /*
   This dummy account id has to be added to the GCPMarketplaceCustomer to support billing only flows.
   */
  private static String DUMMY_HARNESS_ACCOUNT_ID = "HarnessGcpDummyAccount";

  @Override
  public Response signUp(final String token) {
    if (DeployMode.isOnPrem(configuration.getDeployMode().name())) {
      log.error("GCP MarketPlace is disabled in On-Prem");
      return redirectToErrorPage();
    }
    verifyGcpMarketplaceToken(token);
    return redirectTo(newUserSignUpHandler.signUp(token));
  }

  @Override
  public Response registerBillingOnlyTransaction(String gcpAccountId) {
    if (DeployMode.isOnPrem(configuration.getDeployMode().name())) {
      log.error("GCP MarketPlace billing is disabled in On-Prem");
      return redirectToErrorPage();
    }
    String decryptedGcpAccountId = verifyGcpMarketplaceToken(gcpAccountId);
    Map<String, String> properties = new HashMap<String, String>() {
      { put("gcpAccountId", decryptedGcpAccountId); }
    };
    Map<String, Boolean> integrations = new HashMap<String, Boolean>() {
      { put(Keys.SALESFORCE, Boolean.TRUE); }
    };

    try {
      log.info("Registering Billing Transaction: {}", decryptedGcpAccountId);
      segmentHelper.reportTrackEvent(SYSTEM, GCP_BILLING_REQUEST_RECEIVED, properties, integrations);
      GCPMarketplaceCustomer gcpMarketplaceCustomer = getExistingCustomer(decryptedGcpAccountId);
      if (gcpMarketplaceCustomer == null) {
        wingsPersistence.save(GCPMarketplaceCustomer.builder()
                                  .gcpAccountId(decryptedGcpAccountId)
                                  .harnessAccountId(DUMMY_HARNESS_ACCOUNT_ID)
                                  .build());
      } else {
        UpdateOperations<GCPMarketplaceCustomer> updateOperations =
            wingsPersistence.createUpdateOperations(GCPMarketplaceCustomer.class);
        updateOperations.set("harnessAccountId", DUMMY_HARNESS_ACCOUNT_ID);
        wingsPersistence.update(gcpMarketplaceCustomer, updateOperations);
      }

      gcpProcurementService.approveAccount(decryptedGcpAccountId);
      segmentHelper.reportTrackEvent(SYSTEM, GCP_BILLING_SUCCESS, properties, integrations);
      log.info("Registered Billing Transaction: {} successfully", decryptedGcpAccountId);
      return Response.ok().build();
    } catch (Exception ex) {
      log.error("Error while registering Billing Transaction: {}, the error is: {}", decryptedGcpAccountId,
          ex.getMessage(), ex);
      segmentHelper.reportTrackEvent(SYSTEM, GCP_BILLING_FAILURE, properties, integrations);
      return Response.serverError().build();
    }
  }

  private GCPMarketplaceCustomer getExistingCustomer(String gcpAccountId) {
    return wingsPersistence.createQuery(GCPMarketplaceCustomer.class).filter("gcpAccountId", gcpAccountId).get();
  }

  private static Response redirectTo(URI uri) {
    return Response.seeOther(uri).build();
  }

  private Response redirectToErrorPage() {
    try {
      String baseUrl = authenticationUtils.getBaseUrl() + "#/fallback";
      URI redirectUrl = new URIBuilder(baseUrl).addParameter("type", "GCP_ON_PREMISE_ERROR").build();
      return Response.seeOther(redirectUrl).build();
    } catch (URISyntaxException e) {
      throw new SignupException(
          null, e, ErrorCode.UNEXPECTED, Level.ERROR, USER, EnumSet.of(FailureType.APPLICATION_ERROR));
    }
  }

  @VisibleForTesting
  public String verifyGcpMarketplaceToken(String token) {
    DecodedJWT jwt = JWT.decode(token);
    log.info("GCP Marketplace registration request. gcpAccountId: {}", jwt.getSubject());
    try {
      HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
      HttpRequestFactory requestFactory = httpTransport.createRequestFactory();
      JsonParser jsonParser = new JsonParser();

      // Fetch certificate from issuer URL
      GenericUrl issuerUrl = new GenericUrl(TOKEN_ISSUER);
      HttpRequest request = requestFactory.buildGetRequest(issuerUrl);
      String certsJson = request.execute().parseAsString();

      // Lookup certificate by kid
      JsonElement jsonRoot = jsonParser.parse(certsJson);
      String certificateStr = jsonRoot.getAsJsonObject().get(jwt.getKeyId()).getAsString();

      // Get public key from certificate
      InputStream is = new ByteArrayInputStream(certificateStr.getBytes());
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      Certificate cert = cf.generateCertificate(is);
      RSAPublicKey publicKey = (RSAPublicKey) cert.getPublicKey();

      // Build JWT Verifier
      Algorithm algorithm = Algorithm.RSA256(publicKey);
      JWTVerifier verifier = JWT.require(algorithm).withIssuer(TOKEN_ISSUER).withAudience(TOKEN_AUDIENCE).build();

      // Verify token
      verifier.verify(token);
      return jwt.getSubject();
    } catch (Exception e) {
      throw new GeneralException("Failed to verify GCP Marketplace token", e);
    }
  }
}
