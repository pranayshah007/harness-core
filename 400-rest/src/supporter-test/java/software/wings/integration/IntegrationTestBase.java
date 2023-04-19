/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.network.Localhost.getLocalHostName;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.User.Builder.anUser;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static java.lang.String.format;
import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.commons.codec.binary.Base64.encodeBase64String;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.beans.SearchFilter.Operator;
import io.harness.rest.RestResponse;
import io.harness.scm.ScmSecret;
import io.harness.serializer.JsonSubtypeResolver;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.LoginRequest;
import software.wings.beans.Role;
import software.wings.beans.RoleType;
import software.wings.beans.Service;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.compliance.GovernanceConfigService;
import software.wings.service.intfc.security.KmsService;
import software.wings.service.intfc.security.LocalSecretManagerService;
import software.wings.service.intfc.security.SecretManagementDelegateService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.utils.WingsIntegrationTestConstants;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.inject.Inject;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.GenericType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.internal.MultiPartWriter;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockito.Mock;

/**
 * Created by rsingh on 4/24/17.
 */
@Slf4j
public abstract class IntegrationTestBase extends WingsBaseTest implements WingsIntegrationTestConstants {
  protected static Client client;

  @Inject protected WingsPersistence wingsPersistence;
  @Inject protected AccountService accountService;
  @Inject protected UserService userService;
  @Inject protected SettingsService settingsService;
  @Inject protected AppService appService;
  @Inject protected PipelineService pipelineService;
  @Inject protected WorkflowService workflowService;
  @Inject protected TriggerService triggerService;
  @Inject protected EnvironmentService environmentService;
  @Inject protected ConfigService configService;
  @Inject protected ServiceResourceService serviceResourceService;
  @Inject protected ServiceTemplateService serviceTemplateService;
  @Inject protected KmsService kmsService;
  @Inject protected SecretManager secretManager;
  @Inject protected SecretManagementDelegateService delegateService;
  @Inject protected ScmSecret scmSecret;
  @Inject protected FileService fileService;
  @Inject protected GovernanceConfigService governanceConfigService;
  @Inject protected LocalSecretManagerService localSecretManagerService;

  @Mock private DelegateProxyFactory delegateProxyFactory;

  protected String accountId = "INVALID_ID";
  protected String userToken = "INVALID_TOKEN";
  protected final int TIMES_TO_REPEAT = 3;
  protected final int SUCCESS_COUNT = 1;

  //  @Rule public ThreadDumpRule threadDumpRule = new ThreadDumpRule();

  @BeforeClass
  public static void setup() throws KeyManagementException, NoSuchAlgorithmException {
    ClientConfig config = new ClientConfig(new JacksonJsonProvider().configure(FAIL_ON_UNKNOWN_PROPERTIES, false));
    config.register(MultiPartWriter.class);
    SSLContext sslcontext = SSLContext.getInstance("TLS");
    X509TrustManager x509TrustManager = new X509TrustManager() {
      @Override
      public void checkClientTrusted(X509Certificate[] arg0, String arg1) {}

      @Override
      public void checkServerTrusted(X509Certificate[] arg0, String arg1) {}

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
      }
    };
    sslcontext.init(null, new TrustManager[] {x509TrustManager}, new java.security.SecureRandom());

    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    objectMapper.setSubtypeResolver(new JsonSubtypeResolver(objectMapper.getSubtypeResolver()));
    JacksonJaxbJsonProvider jacksonProvider = new JacksonJaxbJsonProvider();
    jacksonProvider.setMapper(objectMapper);

    // To log HTTP Request/Response for debugging purpose.
    java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(IntegrationTestBase.class.getName());
    Feature loggingFeature = new LoggingFeature(julLogger, Level.ALL, null, null);

    client = ClientBuilder.newBuilder()
                 .sslContext(sslcontext)
                 .hostnameVerifier((s1, s2) -> true)
                 .register(MultiPartFeature.class)
                 .register(jacksonProvider)
                 .build();
  }

  @Before
  public void setUp() throws Exception {
    when(delegateProxyFactory.getV2(eq(SecretManagementDelegateService.class), any(SyncTaskContext.class)))
        .thenReturn(delegateService);
    FieldUtils.writeField(kmsService, "delegateProxyFactory", delegateProxyFactory, true);
    FieldUtils.writeField(secretManager, "kmsService", kmsService, true);
    FieldUtils.writeField(wingsPersistence, "secretManager", secretManager, true);
    //    FieldUtils.writeField(wingsPersistence, "featureFlagService", featureFlagService, true);
  }

  protected String loginUser(final String userName, final String password) {
    String basicAuthValue = "Basic " + encodeBase64String(format("%s:%s", userName, password).getBytes());
    RestResponse<User> response;
    response = client.target(API_BASE + "/users/login")
                   .request()
                   .header("Authorization", basicAuthValue)
                   .post(entity(LoginRequest.builder().authorization(basicAuthValue).build(), APPLICATION_JSON),
                       new GenericType<RestResponse<User>>() {});
    if (response.getResource() != null) {
      User loggedInUser = response.getResource();
      userToken = loggedInUser.getToken();
      accountId = loggedInUser.getAccounts().get(0).getUuid();
    }
    return userToken;
  }

  protected void loginAdminUser() {
    loginUser(adminUserEmail, new String(adminPassword));
  }

  protected void loginDefaultUser() {
    loginUser(defaultEmail, new String(defaultPassword));
  }

  protected Builder getRequestBuilderWithAuthHeader(WebTarget target) {
    return target.request().header("Authorization", "Bearer " + userToken);
  }

  protected Builder getDelegateRequestBuilderWithAuthHeader(WebTarget target) throws UnknownHostException {
    return target.request().header("Authorization", "Delegate " + getDelegateToken());
  }

  protected Builder getRequestBuilder(WebTarget target) {
    return target.request();
  }

  protected void deleteAllDocuments(List<Class> classes) {
    classes.forEach(cls -> wingsPersistence.getDatastore(cls).delete(wingsPersistence.createQuery(cls)));
  }

  protected Application createApp(String appName) {
    WebTarget target = client.target(API_BASE + "/apps?accountId=" + accountId);
    RestResponse<Application> response = getRequestBuilderWithAuthHeader(target).post(
        entity(anApplication().name(appName).description(appName).accountId(accountId).build(), APPLICATION_JSON),
        new GenericType<RestResponse<Application>>() {});
    assertThat(response.getResource()).isInstanceOf(Application.class);
    assertThat(response.getResource().getName()).isEqualTo(appName);
    return response.getResource();
  }

  protected Service createService(String appId, Map<String, Object> serviceMap) {
    WebTarget target = client.target(API_BASE + "/services/?appId=" + appId);

    RestResponse<Service> response = getRequestBuilderWithAuthHeader(target).post(
        entity(serviceMap, APPLICATION_JSON), new GenericType<RestResponse<Service>>() {});
    assertThat(response.getResource()).isInstanceOf(Service.class);
    String serviceId = response.getResource().getUuid();
    Service service = wingsPersistence.get(Service.class, serviceId);
    assertThat(service).isNotNull();
    assertThat(service.getUuid()).isNotNull();
    return service;
  }

  protected void deleteApp(String appId) {
    WebTarget target = client.target(API_BASE + "/artifactstreams/?appId=" + appId);
    RestResponse response = getRequestBuilderWithAuthHeader(target).delete(new GenericType<RestResponse>() {

    });
    assertThat(response).isNotNull();
  }

  protected void deleteService(String serviceId) {
    WebTarget target = client.target(API_BASE + "/services/?appId=" + serviceId);
    RestResponse response = getRequestBuilderWithAuthHeader(target).delete(new GenericType<RestResponse>() {

    });
    assertThat(response).isNotNull();
  }

  public String getDelegateToken() {
    JWTClaimsSet jwtClaims = new JWTClaimsSet.Builder()
                                 .issuer(getLocalHostName())
                                 .subject(accountId)
                                 .audience("https://localhost:9090")
                                 .expirationTime(new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)))
                                 .notBeforeTime(new Date())
                                 .issueTime(new Date())
                                 .jwtID(UUID.randomUUID().toString())
                                 .build();

    JWEHeader header = new JWEHeader(JWEAlgorithm.DIR, EncryptionMethod.A128GCM);
    EncryptedJWT jwt = new EncryptedJWT(header, jwtClaims);
    DirectEncrypter directEncrypter = null;
    byte[] encodedKey = new byte[0];
    try {
      encodedKey = Hex.decodeHex(delegateAccountSecret.toCharArray());
    } catch (DecoderException e) {
      log.error("", e);
    }
    try {
      directEncrypter = new DirectEncrypter(new SecretKeySpec(encodedKey, 0, encodedKey.length, "AES"));
    } catch (KeyLengthException e) {
      log.error("", e);
    }

    try {
      jwt.encrypt(directEncrypter);
    } catch (JOSEException e) {
      log.error("", e);
    }

    return jwt.serialize();
  }

  protected void signupAndLogin(String email, String pwd) throws InterruptedException {
    final String name = generateUuid();
    final String accountName = generateUuid();
    final String companyName = generateUuid();
    log.info("signing up with email {} pwd {} name {} accounName {} companyName {}", email, pwd, name, accountName,
        companyName);
    WebTarget target = client.target(API_BASE + "/users");
    RestResponse<User> response = target.request().post(
        entity(anUser()
                   .name(name)
                   .email(email)
                   .password(pwd.toCharArray())
                   .roles(wingsPersistence
                              .query(Role.class,
                                  aPageRequest().addFilter("roleType", Operator.EQ, RoleType.ACCOUNT_ADMIN).build())
                              .getResponse())
                   .accountName(accountName)
                   .companyName(companyName)
                   .build(),
            APPLICATION_JSON),
        new GenericType<RestResponse<User>>() {});
    assertThat(response.getResponseMessages()).isEmpty();
    wingsPersistence.update(wingsPersistence.createQuery(User.class).filter(UserKeys.email, email),
        wingsPersistence.createUpdateOperations(User.class).set("emailVerified", true));
    Thread.sleep(2000);
    loginUser(email, pwd);
  }
}
