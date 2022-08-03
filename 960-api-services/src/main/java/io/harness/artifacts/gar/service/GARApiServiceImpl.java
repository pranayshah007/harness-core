package io.harness.artifacts.gar.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.artifacts.docker.service.DockerRegistryServiceImpl.isSuccessful;
import static io.harness.exception.WingsException.USER;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorAscending;
import io.harness.artifacts.gar.GarRestClient;
import io.harness.artifacts.gar.beans.GarInternalConfig;
import io.harness.artifacts.gar.beans.GarPackageVersionResponse;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.expression.RegexFunctor;
import io.harness.network.Http;

import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class GARApiServiceImpl implements GarApiService {
  private static final int Page_Size = 50;
  private GarRestClient getGarRestClient(GarInternalConfig garinternalConfig) {
    String url = getUrl();
    OkHttpClient okHttpClient = Http.getOkHttpClient(url, garinternalConfig.isCertValidationRequired());
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(url)
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(GarRestClient.class);
  }
  public String getUrl() {
    return "https://"
        + "artifactregistry.googleapis.com";
  }

  @Override
  public List<BuildDetailsInternal> getBuilds(
      GarInternalConfig garinternalConfig, String versionRegex, int maxNumberOfBuilds) {
    String project = garinternalConfig.getProject();
    String region = garinternalConfig.getRegion();
    String repositories = garinternalConfig.getRepositoryName();
    String pkg = garinternalConfig.getPkg();
    try {
      GarRestClient garRestClient = getGarRestClient(garinternalConfig);
      //      Response<GarPackageVersionResponse> response =
      //              garRestClient
      //              .listImageTags(garinternalConfig.getBearerToken(), project, region, repositories,
      //              pkg,Page_Size,"") .execute();
      return paginate(garinternalConfig, garRestClient, versionRegex, maxNumberOfBuilds);
    } catch (IOException ie) {
      return emptyList(); // todo @vivek
    }
  }

  private List<BuildDetailsInternal> paginate(GarInternalConfig garinternalConfig, GarRestClient garRestClient,
      String versionRegex, int maxNumberOfBuilds) throws IOException {
    // process first page
    List<BuildDetailsInternal> details = Collections.<BuildDetailsInternal>emptyList();

    //    if (details.size() >= maxNumberOfBuilds || tagsPage == null || tagsPage.getNextPageToken() == null) {
    //      return details.stream().limit(maxNumberOfBuilds).collect(Collectors.toList());
    //    }

    String nextPage = "";
    String project = garinternalConfig.getProject();
    String region = garinternalConfig.getRegion();
    String repositories = garinternalConfig.getRepositoryName();
    String pkg = garinternalConfig.getPkg();
    // process rest of pages
    do {
      Response<GarPackageVersionResponse> response = garRestClient
                                                         .listImageTags(garinternalConfig.getBearerToken(), project,
                                                             region, repositories, pkg, Page_Size, nextPage)
                                                         .execute();

      if (!isSuccessful(response)) {
        throw NestedExceptionUtils.hintWithExplanationException("Unable to fetch the versions for the package",
            "Check if the package exists and if the permissions are scoped for the authenticated user",
            new InvalidArtifactServerException(response.message(), USER));
      }

      GarPackageVersionResponse page = response.body();
      List<BuildDetailsInternal> pageDetails = processPage(page, versionRegex);
      if (details.isEmpty()) {
        details = pageDetails;
      } else {
        details.addAll(pageDetails);
      }

      if (details.size() >= maxNumberOfBuilds || page == null || StringUtils.isBlank(page.getNextPageToken())) {
        break;
      }

      nextPage = StringUtils.isBlank(page.getNextPageToken()) ? null : page.getNextPageToken();
    } while (EmptyPredicate.isNotEmpty(nextPage));

    return details.stream().limit(maxNumberOfBuilds).collect(Collectors.toList());
  }
  private List<BuildDetailsInternal> processPage(GarPackageVersionResponse tagsPage, String versionRegex) {
    if (tagsPage != null && EmptyPredicate.isNotEmpty(tagsPage.getTags())) {
      int index = tagsPage.getTags().get(0).getName().lastIndexOf("/");
      List<BuildDetailsInternal> buildDetails = StringUtils.isNotBlank(versionRegex)
          ? tagsPage.getTags()
                .stream()
                .map(tag -> {
                  String tagFinal = tag.getName().substring(index + 1);
                  Map<String, String> metadata = new HashMap();
                  metadata.put(ArtifactMetadataKeys.PACKAGE, tagFinal);
                  metadata.put(ArtifactMetadataKeys.TAG, tagFinal);
                  return BuildDetailsInternal.builder()
                      .uiDisplayName("Tag# " + tagFinal)
                      .number(tagFinal)
                      .metadata(metadata)
                      .build();
                })
                .filter(build -> new RegexFunctor().match(versionRegex, build.getNumber()))
                .collect(toList())
          : tagsPage.getTags()
                .stream()
                .map(tag -> {
                  String tagFinal = tag.getName().substring(index + 1);
                  Map<String, String> metadata = new HashMap();
                  metadata.put(ArtifactMetadataKeys.PACKAGE, tagFinal);
                  metadata.put(ArtifactMetadataKeys.TAG, tagFinal);
                  return BuildDetailsInternal.builder()
                      .uiDisplayName("Tag# " + tagFinal)
                      .number(tagFinal)
                      .metadata(metadata)
                      .build();
                })
                .collect(toList());
      return buildDetails.stream().sorted(new BuildDetailsInternalComparatorAscending()).collect(toList());

    } else {
      if (tagsPage == null) {
        log.warn("Google Artifact Registry Package version response was null.");
      } else {
        log.warn("Google Artifact Registry Package version response had an empty or missing tag list.");
      }
      return Collections.emptyList();
    }
  }
}
