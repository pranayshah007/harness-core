package io.harness.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.protobuf.ExtensionRegistryLite;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.service.core.client.DelegateCoreManagerClient;
import io.harness.exception.KeyManagerBuilderException;
import io.harness.exception.SslContextBuilderException;
import io.harness.managerclient.DelegateAuthInterceptor;
import io.harness.network.FibonacciBackOff;
import io.harness.network.Http;
import io.harness.network.NoopHostnameVerifier;
import io.harness.security.TokenGenerator;
import io.harness.security.X509KeyManagerBuilder;
import io.harness.security.X509SslContextBuilder;
import io.harness.security.X509TrustManagerBuilder;
import io.harness.serializer.kryo.DelegateKryoConverterFactory;
import io.harness.version.VersionInfoManager;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.converter.protobuf.ProtoConverterFactory;

import javax.net.ssl.X509TrustManager;
import java.util.concurrent.TimeUnit;
@Singleton
@Slf4j
public class MockDelegateCoreManagerClientFactory implements Provider<DelegateCoreManagerClient> {
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new GuavaModule())
            .registerModule(new JavaTimeModule());
    private static final ConnectionPool connectionPool = new ConnectionPool(16, 5, TimeUnit.MINUTES);

    private final VersionInfoManager versionInfoManager;
    private final String baseUrl;
    private final TokenGenerator tokenGenerator;
    private final String clientCertificateFilePath;
    private final String clientCertificateKeyFilePath;
    private final OkHttpClient httpClient;

    private final DelegateKryoConverterFactory kryoConverterFactory;

    @Inject
    public MockDelegateCoreManagerClientFactory(final DelegateConfiguration configuration,
                                            final VersionInfoManager versionInfoManager, final TokenGenerator tokenGenerator, final DelegateKryoConverterFactory kryoConverterFactory) {
        this.baseUrl = configuration.getManagerUrl();
        this.tokenGenerator = tokenGenerator;
        this.clientCertificateFilePath = configuration.getClientCertificateFilePath();
        this.clientCertificateKeyFilePath = configuration.getClientCertificateKeyFilePath();
        boolean trustAllCertificates = configuration.isTrustAllCertificates();
        this.versionInfoManager = versionInfoManager;
        this.kryoConverterFactory = kryoConverterFactory;
        this.httpClient = trustAllCertificates ? this.getUnsafeOkHttpClient() : this.getSafeOkHttpClient();
    }

    @Override
    public DelegateCoreManagerClient get() {
        var retrofit =
                new Retrofit.Builder()
                        .baseUrl(this.baseUrl)
                        .client(httpClient)
                        .addConverterFactory(this.kryoConverterFactory)
                        .addConverterFactory(ProtoConverterFactory.createWithRegistry(ExtensionRegistryLite.newInstance()))
                        .addConverterFactory(JacksonConverterFactory.create(mapper))
                        .build();
        return retrofit.create(DelegateCoreManagerClient.class);
    }

    private OkHttpClient getSafeOkHttpClient() {
        try {
            var trustManager = new X509TrustManagerBuilder().trustDefaultTrustStore().build();
            return this.getHttpClient(trustManager);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Trusts all certificates - should only be used for local development.
     */
    private OkHttpClient getUnsafeOkHttpClient() {
        try {
            var trustManager = new X509TrustManagerBuilder().trustAllCertificates().build();
            return this.getHttpClient(trustManager);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private OkHttpClient getHttpClient(final X509TrustManager trustManager)
            throws KeyManagerBuilderException, SslContextBuilderException {
        var sslContextBuilder = new X509SslContextBuilder().trustManager(trustManager);

        if (StringUtils.isNotEmpty(this.clientCertificateFilePath)
                && StringUtils.isNotEmpty(this.clientCertificateKeyFilePath)) {
            var keyManager =
                    new X509KeyManagerBuilder()
                            .withClientCertificateFromFile(this.clientCertificateFilePath, this.clientCertificateKeyFilePath)
                            .build();
            sslContextBuilder.keyManager(keyManager);
        }

        var sslContext = sslContextBuilder.build();

        return Http.getOkHttpClientWithProxyAuthSetup()
                .hostnameVerifier(new NoopHostnameVerifier())
                .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
                .connectionPool(connectionPool)
                .retryOnConnectionFailure(true)
                .addInterceptor(new DelegateAuthInterceptor(this.tokenGenerator))
                .addInterceptor(chain -> {
                    Request.Builder request = chain.request().newBuilder().addHeader(
                            "User-Agent", "delegate/" + this.versionInfoManager.getVersionInfo().getVersion());
                    return chain.proceed(request.build());
                })
                .addInterceptor(chain -> FibonacciBackOff.executeForEver(() -> chain.proceed(chain.request())))
                .readTimeout(1, TimeUnit.MINUTES)
                .build();
    }
}

