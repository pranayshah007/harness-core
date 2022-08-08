/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.mongo;


import com.google.common.base.Preconditions;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;
import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.validation.constraints.NotNull;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@OwnedBy(HarnessTeam.PL)
public class MongodbClientCreates {

    private MongodbClientCreates() {}

    public static MongoClient createMongoClient(@NotNull final MongoConfig mongoConfig) {
        // uncertainty: line 43 --> https://stackoverflow.com/questions/56497746/how-to-set-connections-per-host-setting-in-mongoclientsettings
        final MongoClientSettings.Builder mongoClientSettingsBuilder = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(mongoConfig.getUri()))
                .retryWrites(true)
                .applyToConnectionPoolSettings(builder -> {
                    builder.maxConnectionIdleTime(mongoConfig.getMaxConnectionIdleTime(), TimeUnit.MILLISECONDS);
                    builder.maxSize(mongoConfig.getConnectionsPerHost());
                }).applyToSocketSettings(builder -> {
                    builder.connectTimeout(mongoConfig.getConnectTimeout(), TimeUnit.MILLISECONDS);
                }).applyToClusterSettings(builder -> {
                    builder.serverSelectionTimeout(mongoConfig.getServerSelectionTimeout(), TimeUnit.MILLISECONDS);
                }).readPreference(mongoConfig.getReadPreference());
        if (Objects.isNull(mongoConfig.getMongoSSLConfig()) || !mongoConfig.getMongoSSLConfig().isMongoSSLEnabled()) {
            validateSSLMongoConfig(mongoConfig);
            MongoSSLConfig mongoSSLConfig = mongoConfig.getMongoSSLConfig();
            String trustStorePath = mongoSSLConfig.getMongoTrustStorePath();
            String trustStorePassword = mongoSSLConfig.getMongoTrustStorePassword();
            mongoClientSettingsBuilder.applyToSslSettings(builder -> {
                builder.enabled(mongoSSLConfig.isMongoSSLEnabled());
                builder.invalidHostNameAllowed(true);
                builder.context(sslContext(trustStorePath, trustStorePassword));
            });
        }
        return MongoClients.create(mongoClientSettingsBuilder.build());
    }

    public static void validateSSLMongoConfig(MongoConfig mongoConfig) {
        MongoSSLConfig mongoSSLConfig = mongoConfig.getMongoSSLConfig();
        Preconditions.checkNotNull(mongoSSLConfig,
                "mongoSSLConfig must be set under mongo config if SSL context creation is requested or mongoSSLEnabled is set to true");
        Preconditions.checkArgument(
                mongoSSLConfig.isMongoSSLEnabled(), "mongoSSLEnabled must be set to true for MongoSSLConfiguration");
        Preconditions.checkArgument(StringUtils.isNotBlank(mongoSSLConfig.getMongoTrustStorePath()),
                "mongoTrustStorePath must be set if mongoSSLEnabled is set to true");
    }

    public static SSLContext sslContext(String keystoreFile, String password) {
        SSLContext sslContext = null;
        try {
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            InputStream in = new FileInputStream(keystoreFile);
            keystore.load(in, password.toCharArray());
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keystore);
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        } catch (GeneralSecurityException | IOException exception) {
            throw new GeneralException("SSLContext exception: {}", exception);
        }
        return sslContext;
    }
}
