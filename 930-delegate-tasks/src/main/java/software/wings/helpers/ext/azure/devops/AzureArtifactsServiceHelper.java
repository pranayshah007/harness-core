/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.azure.devops;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.exception.WingsException.USER;
import static io.harness.network.Http.getOkHttpClientBuilder;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.network.Http;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.AzureArtifactsArtifactStreamProtocolType;
import software.wings.beans.settings.azureartifacts.AzureArtifactsConfig;
import software.wings.beans.settings.azureartifacts.AzureArtifactsPATConfig;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@OwnedBy(CDC)
@TargetModule(HarnessModule._960_API_SERVICES)
@BreakDependencyOn("software.wings.beans.artifact.ArtifactStreamAttributes")
@BreakDependencyOn("software.wings.beans.settings.azureartifacts.AzureArtifactsConfig")
@BreakDependencyOn("software.wings.beans.settings.azureartifacts.AzureArtifactsPATConfig")
@UtilityClass
public class AzureArtifactsServiceHelper {
  private static final int CONNECT_TIMEOUT = 5;
  private static final int READ_TIMEOUT = 10;

  static AzureDevopsRestClient getAzureDevopsRestClient(String azureDevopsUrl) {
    String url = ensureTrailingSlash(azureDevopsUrl);
    OkHttpClient okHttpClient = getOkHttpClientBuilder()
                                    .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                                    .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                                    .proxy(Http.checkAndGetNonProxyIfApplicable(url))
                                    .retryOnConnectionFailure(true)
                                    .build();
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(url)
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(AzureDevopsRestClient.class);
  }

  static AzureArtifactsRestClient getAzureArtifactsRestClient(String azureDevopsUrl, String project) {
    String url = ensureTrailingSlash(getSubdomainUrl(azureDevopsUrl, "feeds"));
    if (isNotBlank(project)) {
      url += project + "/";
    }
    OkHttpClient okHttpClient = getOkHttpClientBuilder()
                                    .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                                    .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                                    .proxy(Http.checkAndGetNonProxyIfApplicable(url))
                                    .retryOnConnectionFailure(true)
                                    .build();
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(url)
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(AzureArtifactsRestClient.class);
  }

  static OkHttpClient getAzureArtifactsDownloadClient(String artifactDownloadUrl) {
    return getOkHttpClientBuilder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .proxy(Http.checkAndGetNonProxyIfApplicable(artifactDownloadUrl))
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build();
  }

  public static String getAuthHeader(AzureArtifactsConfig azureArtifactsConfig) {
    if (!(azureArtifactsConfig instanceof AzureArtifactsPATConfig)) {
      return "";
    }
    AzureArtifactsPATConfig azureArtifactsPATConfig = (AzureArtifactsPATConfig) azureArtifactsConfig;
    return "Basic " + encodeBase64(format(":%s", new String(azureArtifactsPATConfig.getPat())));
  }

  private static String ensureTrailingSlash(String azureDevopsUrl) {
    return azureDevopsUrl + (azureDevopsUrl.endsWith("/") ? "" : "/");
  }

  public static String getSubdomainUrl(String azureDevopsUrl, String subdomain) {
    // Assuming azureDevopsUrl starts with AZURE_DEVOPS_SERVICES_URL.
    try {
      validateAzureDevopsUrl(azureDevopsUrl);
      if (azureDevopsUrl.startsWith("https://")) {
        return format("https://%s.%s", subdomain, azureDevopsUrl.substring(8));
      } else if (azureDevopsUrl.startsWith("http://")) {
        return format("http://%s.%s", subdomain, azureDevopsUrl.substring(7));
      } else {
        return azureDevopsUrl;
      }
    } catch (InvalidArtifactServerException e) {
      return azureDevopsUrl;
    }
  }

  public static void validateAzureDevopsUrl(String azureDevopsUrl) {
    try {
      URI uri = new URI(azureDevopsUrl);
    } catch (URISyntaxException e) {
      throw new InvalidArtifactServerException("Azure DevOps URL is invalid");
    }
  }

  public static void validateResponse(Response<?> response) {
    validateRawResponse(response == null ? null : response.raw());
  }

  public static void validateRawResponse(okhttp3.Response response) {
    if (response == null) {
      throw new InvalidArtifactServerException("Null response found", USER);
    }
    if (response.code() == 401 || response.code() == 203) {
      throw new InvalidArtifactServerException(
          "Invalid Azure Artifacts credentials. The Personal Access Token might have expired", USER);
    }
    if (!response.isSuccessful()) {
      throw new InvalidArtifactServerException(response.message(), USER);
    }
  }

  static <T> T execute(Call<T> call) {
    try {
      Response<T> response = call.execute();
      validateResponse(response);
      return response.body();
    } catch (InvalidArtifactServerException ex) {
      throw ex;
    } catch (Exception e) {
      throw new InvalidArtifactServerException("Authentication failed for Azure Artifacts server", e);
    }
  }

  public static String getDownloadUrl(String azureDevopsUrl, ArtifactStreamAttributes artifactStreamAttributes,
      String version, String artifactFileName) {
    String protocolType = artifactStreamAttributes.getProtocolType();
    if (AzureArtifactsArtifactStreamProtocolType.maven.name().equals(protocolType)) {
      return getMavenDownloadUrl(azureDevopsUrl, artifactStreamAttributes, version, artifactFileName);
    } else if (AzureArtifactsArtifactStreamProtocolType.nuget.name().equals(protocolType)) {
      return getNuGetDownloadUrl(azureDevopsUrl, artifactStreamAttributes, version);
    } else {
      return null;
    }
  }

  static String getMavenDownloadUrl(String azureDevopsUrl, ArtifactStreamAttributes artifactStreamAttributes,
      String version, String artifactFileName) {
    String url = ensureTrailingSlash(getSubdomainUrl(azureDevopsUrl, "pkgs"));
    String project = artifactStreamAttributes.getProject();
    if (isNotBlank(project)) {
      url += project + "/";
    }

    String feed = artifactStreamAttributes.getFeed();
    String packageName = artifactStreamAttributes.getPackageName();
    String groupId = "";
    String artifactId = "";
    if (isNotBlank(packageName)) {
      String[] parts = packageName.split(":", 2);
      if (parts.length == 2) {
        groupId = parts[0];
        artifactId = parts[1];
      }
    }
    return url
        + format("_apis/packaging/feeds/%s/maven/%s/%s/%s/%s/content?api-version=5.1-preview.1", feed, groupId,
            artifactId, version, artifactFileName);
  }

  static String getNuGetDownloadUrl(
      String azureDevopsUrl, ArtifactStreamAttributes artifactStreamAttributes, String version) {
    String url = ensureTrailingSlash(getSubdomainUrl(azureDevopsUrl, "pkgs"));
    String project = artifactStreamAttributes.getProject();
    if (isNotBlank(project)) {
      url += project + "/";
    }

    String feed = artifactStreamAttributes.getFeed();
    String packageName = artifactStreamAttributes.getPackageName();
    return url
        + format("_apis/packaging/feeds/%s/nuget/packages/%s/versions/%s/content?api-version=5.1-preview.1", feed,
            packageName, version);
  }

  static boolean shouldDownloadFile(String artifactFileName) {
    if (isBlank(artifactFileName)) {
      return false;
    }
    return Lists.newArrayList("pom", "md5", "sha1", "sha256", "sha512").stream().noneMatch(artifactFileName::endsWith);
  }

  static long getInputStreamSize(InputStream inputStream) throws IOException {
    long size = 0;
    int chunk;
    byte[] buffer = new byte[1024];
    while ((chunk = inputStream.read(buffer)) != -1) {
      size += chunk;
      if (size > Integer.MAX_VALUE) {
        return -1;
      }
    }
    return size;
  }
}
