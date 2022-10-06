/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.telemetry.segment.remote;

import io.harness.network.Http;
import io.harness.telemetry.TelemetryConfiguration;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.validator.routines.UrlValidator;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Singleton
@Slf4j
public class RemoteSegmentClientFactory implements Provider<RemoteSegmentClient> {
  private TelemetryConfiguration telemetryConfiguration;
  private static final String DEFAULT_URL = "https://stats.drone.ci/api/v1/";

  @Inject
  public RemoteSegmentClientFactory(TelemetryConfiguration telemetryConfiguration) {
    this.telemetryConfiguration = telemetryConfiguration;
  }

  @Override
  public RemoteSegmentClient get() {
    // validate url before initiating retrofit, ensure not block the initialize flow
    UrlValidator urlValidator = new UrlValidator();
    String url = telemetryConfiguration.getUrl();
    if (!urlValidator.isValid(url)) {
      url = DEFAULT_URL;
    }

      OkHttpClient result;
      synchronized (Http.class) {
          result = Http.getUnsafeOkHttpClientBuilder(telemetryConfiguration.getUrl(), 15, 20).build();
      }
      final Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .client(telemetryConfiguration.isCertValidationRequired()
                    ? Http.getSafeOkHttpClientBuilder(telemetryConfiguration.getUrl(), 15, 20).build()
                    : result)
            .build();
    return retrofit.create(RemoteSegmentClient.class);
  }
}
