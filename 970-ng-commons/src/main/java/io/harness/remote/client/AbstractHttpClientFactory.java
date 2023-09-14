/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.remote.client;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.network.Http.DEFAULT_OKHTTP_CLIENT;
import static io.harness.network.Http.checkAndGetNonProxyIfApplicable;
import static io.harness.network.Http.getSslContext;
import static io.harness.network.Http.getTrustManagers;
import static io.harness.ng.core.CorrelationContext.getCorrelationIdInterceptor;
import static io.harness.request.RequestContextFilter.getRequestContextInterceptor;
import static io.harness.security.JWTAuthenticationFilter.X_SOURCE_PRINCIPAL;

import static com.fasterxml.jackson.core.JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT;
import static com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static org.apache.http.HttpHeaders.AUTHORIZATION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.context.GlobalContextData;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.manage.GlobalContextManager;
import io.harness.network.NoopHostnameVerifier;
import io.harness.security.PmsAuthInterceptor;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.ServiceTokenGenerator;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.serializer.JsonSubtypeResolver;
import io.harness.serializer.kryo.KryoConverterFactory;

import software.wings.jersey.JsonViews;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hubspot.jackson.datatype.protobuf.ProtobufModule;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retrofit.CircuitBreakerCallAdapter;
import io.serializer.HObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@OwnedBy(PL)
@Slf4j
public abstract class AbstractHttpClientFactory {
  private final ServiceHttpClientConfig serviceHttpClientConfig;
  private final String serviceSecret;
  private final ServiceTokenGenerator tokenGenerator;
  private final KryoConverterFactory kryoConverterFactory;
  protected final String clientId;
  private final ObjectMapper objectMapper;
  private final boolean enableCircuitBreaker;
  private final ClientMode clientMode;

  protected AbstractHttpClientFactory(ServiceHttpClientConfig httpClientConfig, String serviceSecret,
      ServiceTokenGenerator tokenGenerator, KryoConverterFactory kryoConverterFactory, String clientId) {
    this.serviceHttpClientConfig = httpClientConfig;
    this.serviceSecret = serviceSecret;
    this.tokenGenerator = tokenGenerator;
    this.kryoConverterFactory = kryoConverterFactory;
    this.clientId = clientId;
    this.objectMapper = getObjectMapper();
    this.enableCircuitBreaker = false;
    this.clientMode = ClientMode.NON_PRIVILEGED;
  }

  protected AbstractHttpClientFactory(ServiceHttpClientConfig httpClientConfig, String serviceSecret,
      ServiceTokenGenerator tokenGenerator, KryoConverterFactory kryoConverterFactory, String clientId,
      boolean enableCircuitBreaker, ClientMode clientMode) {
    this.serviceHttpClientConfig = httpClientConfig;
    this.serviceSecret = serviceSecret;
    this.tokenGenerator = tokenGenerator;
    this.kryoConverterFactory = kryoConverterFactory;
    this.clientId = clientId;
    this.objectMapper = getObjectMapper();
    this.enableCircuitBreaker = enableCircuitBreaker;
    this.clientMode = clientMode;
  }

  private Retrofit getRetrofit(boolean isSafeOk) {
    String baseUrl = serviceHttpClientConfig.getBaseUrl();
    log.info(
        "OkHttpClientsTracker: Creating a new Retrofit client with OkHttpClient with baseUrl: [{}], and for clientId: [{}], and isSafeOk param as: [{}]",
        baseUrl, this.clientId, isSafeOk);
    Retrofit.Builder retrofitBuilder = new Retrofit.Builder().baseUrl(baseUrl);
    if (this.kryoConverterFactory != null) {
      retrofitBuilder.addConverterFactory(kryoConverterFactory);
    }
    if (isSafeOk) {
      retrofitBuilder.client(getSafeOkHttpClient());
    } else {
      retrofitBuilder.client(getUnsafeOkHttpClient(
          baseUrl, this.clientMode, Boolean.TRUE.equals(this.serviceHttpClientConfig.getEnableHttpLogging())));
    }
    if (this.enableCircuitBreaker) {
      retrofitBuilder.addCallAdapterFactory(CircuitBreakerCallAdapter.of(getCircuitBreaker()));
    }
    retrofitBuilder.addConverterFactory(JacksonConverterFactory.create(objectMapper));

    return retrofitBuilder.build();
  }

  protected Retrofit getRetrofit() {
    return getRetrofit(false);
  }

  protected Retrofit getSafeOkRetrofit() {
    return getRetrofit(true);
  }

  protected CircuitBreaker getCircuitBreaker() {
    return CircuitBreaker.ofDefaults(this.clientId);
  }

  private ObjectMapper getObjectMapper() {
    ObjectMapper objMapper = HObjectMapper.get();
    objMapper.setSubtypeResolver(new JsonSubtypeResolver(objMapper.getSubtypeResolver()));
    objMapper.setConfig(objMapper.getSerializationConfig().withView(JsonViews.Public.class));
    objMapper.disable(FAIL_ON_UNKNOWN_PROPERTIES);
    objMapper.configure(AUTO_CLOSE_SOURCE, false);
    objMapper.configure(AUTO_CLOSE_JSON_CONTENT, false);
    objMapper.registerModule(new ProtobufModule());
    objMapper.registerModule(new Jdk8Module());
    objMapper.registerModule(new GuavaModule());
    objMapper.registerModule(new JavaTimeModule());
    return objMapper;
  }

  private OkHttpClient getSafeOkHttpClient() {
    try {
      KeyStore keyStore = getKeyStore();

      TrustManagerFactory trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(keyStore);
      TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, trustManagers, null);

      return DEFAULT_OKHTTP_CLIENT.newBuilder()
          .addInterceptor(new PmsAuthInterceptor(serviceSecret))
          .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0])
          .build();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private KeyStore getKeyStore() throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException {
    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    keyStore.load(null, null);

    // Load self-signed certificate created only for the purpose of local development
    try (InputStream certInputStream = getClass().getClassLoader().getResourceAsStream("localhost.pem")) {
      keyStore.setCertificateEntry(
          "localhost", CertificateFactory.getInstance("X509").generateCertificate(certInputStream));
    }

    // Load all trusted issuers from default java trust store
    TrustManagerFactory defaultTrustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    defaultTrustManagerFactory.init((KeyStore) null);
    for (TrustManager trustManager : defaultTrustManagerFactory.getTrustManagers()) {
      if (trustManager instanceof X509TrustManager) {
        for (X509Certificate acceptedIssuer : ((X509TrustManager) trustManager).getAcceptedIssuers()) {
          keyStore.setCertificateEntry(acceptedIssuer.getSubjectDN().getName(), acceptedIssuer);
        }
      }
    }

    return keyStore;
  }

  private OkHttpClient getUnsafeOkHttpClient(String baseUrl, ClientMode clientMode, boolean addHttpLogging) {
    try {
      OkHttpClient.Builder builder =
          DEFAULT_OKHTTP_CLIENT.newBuilder()
              .sslSocketFactory(getSslContext().getSocketFactory(), (X509TrustManager) getTrustManagers()[0])
              .hostnameVerifier(new NoopHostnameVerifier())
              .proxy(checkAndGetNonProxyIfApplicable(baseUrl))
              .connectTimeout(serviceHttpClientConfig.getConnectTimeOutSeconds(), TimeUnit.SECONDS)
              .readTimeout(serviceHttpClientConfig.getReadTimeOutSeconds(), TimeUnit.SECONDS)
              .addInterceptor(getAuthorizationInterceptor(clientMode))
              .addInterceptor(getCorrelationIdInterceptor())
              .addInterceptor(getGitContextInterceptor())
              .addInterceptor(getRequestContextInterceptor());
      if (addHttpLogging) {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        builder.addInterceptor(loggingInterceptor);
      }
      builder.addInterceptor(chain -> {
        Request original = chain.request();

        // Request customization: add connection close headers
        Request.Builder requestBuilder = original.newBuilder().header("Connection", "close");

        Request request = requestBuilder.build();
        return chain.proceed(request);
      });
      return builder.build();
    } catch (Exception e) {
      throw new GeneralException(String.format("error while creating okhttp client for %s service", clientId), e);
    }
  }

  @NotNull
  private Interceptor getGitContextInterceptor() {
    return chain -> {
      Request request = chain.request();
      GlobalContextData globalContextData = GlobalContextManager.get(GitSyncBranchContext.NG_GIT_SYNC_CONTEXT);

      if (globalContextData != null) {
        final GitEntityInfo gitBranchInfo =
            ((GitSyncBranchContext) Objects.requireNonNull(globalContextData)).getGitBranchInfo();
        if (gitBranchInfo != null && gitBranchInfo.getYamlGitConfigId() != null && gitBranchInfo.getBranch() != null) {
          HttpUrl url = request.url()
                            .newBuilder()
                            .addQueryParameter("repoIdentifier", gitBranchInfo.getYamlGitConfigId())
                            .addQueryParameter("branch", gitBranchInfo.getBranch())
                            .addQueryParameter(
                                "getDefaultFromOtherRepo", String.valueOf(gitBranchInfo.isFindDefaultFromOtherRepos()))
                            .build();
          return chain.proceed(request.newBuilder().url(url).build());
        } else {
          return chain.proceed(request);
        }
      } else {
        return chain.proceed(request);
      }
    };
  }

  @NotNull
  protected Interceptor getAuthorizationInterceptor(ClientMode clientMode) {
    final Supplier<String> secretKeySupplier = this::getServiceSecret;
    return chain -> {
      Request.Builder builder = chain.request().newBuilder();
      String authorizationToken;
      if (ClientMode.PRIVILEGED == clientMode) {
        authorizationToken = tokenGenerator.getServiceTokenWithDuration(
            secretKeySupplier.get(), Duration.ofHours(4), new ServicePrincipal(this.clientId));
      } else {
        authorizationToken = tokenGenerator.getServiceTokenWithDuration(
            secretKeySupplier.get(), Duration.ofHours(4), SecurityContextBuilder.getPrincipal());
      }
      Principal sourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal() != null
          ? SourcePrincipalContextBuilder.getSourcePrincipal()
          : SecurityContextBuilder.getPrincipal();
      String sourcePrincipalToken =
          tokenGenerator.getServiceTokenWithDuration(secretKeySupplier.get(), Duration.ofHours(4), sourcePrincipal);
      builder.header(X_SOURCE_PRINCIPAL, clientId + StringUtils.SPACE + sourcePrincipalToken);
      builder.header(AUTHORIZATION, clientId + StringUtils.SPACE + authorizationToken);
      return chain.proceed(builder.build());
    };
  }

  protected String getServiceSecret() {
    String managerServiceSecret = this.serviceSecret;
    if (StringUtils.isNotBlank(managerServiceSecret)) {
      return managerServiceSecret.trim();
    }
    throw new InvalidRequestException("No secret key for client for " + clientId);
  }
}
