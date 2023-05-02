/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.utils;

import java.security.cert.CertificateException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.FieldMap;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.HeaderMap;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.QueryMap;
import retrofit2.http.Url;

@Slf4j
public class RequestExecutor {
  public Object executeRequest(Call call) {
    Response response = null;
    int retry = 0;
    while (retry < 3) {
      try {
        response = call.clone().execute();
        return response.body();
      } catch (Exception ex) {
        retry++;
        log.error(ex.getMessage());
        if (retry == 3) {
          throw new RuntimeException(ex.getMessage());
        }
      }
    }
    return null;
  }

  public static OkHttpClient getUnsafeOkHttpClient() {
    try {
      // Create a trust manager that does not validate certificate chains
      final TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager(){
          @Override public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType)
              throws CertificateException{}

          @Override public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
              throws CertificateException{}

                     @Override public java.security.cert.X509Certificate[] getAcceptedIssuers(){
                         return new java.security.cert.X509Certificate[] {};
    }
  }
};

// Install the all-trusting trust manager
final SSLContext sslContext = SSLContext.getInstance("SSL");
sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
// Create an ssl socket factory with our all-trusting manager
final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

OkHttpClient.Builder builder = new OkHttpClient.Builder();
builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
builder.hostnameVerifier(new HostnameVerifier() {
  @Override
  public boolean verify(String hostname, SSLSession session) {
    return true;
  }
});

OkHttpClient okHttpClient =
    builder.connectTimeout(Duration.of(1, ChronoUnit.MINUTES)).readTimeout(Duration.of(1, ChronoUnit.MINUTES)).build();
return okHttpClient;
}
catch (Exception e) {
  throw new RuntimeException(e);
}
}

public interface CustomRestClient {
  @GET Call<Object> get(@Url String url, @HeaderMap Map<String, String> headers, @QueryMap Map<String, Object> options);

  @DELETE
  Call<Object> delete(@Url String url, @HeaderMap Map<String, String> headers, @QueryMap Map<String, Object> options);

  @PUT
  Call<Object> put(@Url String url, @HeaderMap Map<String, String> headers, @QueryMap Map<String, Object> options,
      @Body String body);

  @FormUrlEncoded
  @PUT
  Call<Object> put(@Url String url, @HeaderMap Map<String, String> headers, @QueryMap Map<String, Object> options,
      @FieldMap Map<String, String> body);

  @POST
  Call<Object> post(@Url String url, @HeaderMap Map<String, String> headers, @QueryMap Map<String, Object> options,
      @Body Map<String, Object> body);
}
}
