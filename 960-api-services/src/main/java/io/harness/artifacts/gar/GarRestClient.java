package io.harness.artifacts.gar;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.gar.beans.GarPackageVersionResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.CDC)

public interface GarRestClient {
  // v1beta2/projects/cd-play/locations/us/repositories/puthraya-test/packages/alphine/tags
  @GET("/v1beta2/projects/{project}/locations/{region}/repositories/{repositories}/packages/{package}/tags")
  Call<GarPackageVersionResponse> listImageTags(@Header("Authorization") String bearerAuthHeader,
      @Path(value = "project", encoded = true) String project, @Path(value = "region", encoded = true) String region,
      @Path(value = "repositories", encoded = true) String repositories,
      @Path(value = "package", encoded = true) String pkg, @Query(value = "pageSize", encoded = true) int pageSize,
      @Query(value = "pageToken", encoded = true) String pageToken);
}
