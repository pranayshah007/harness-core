package io.harness.ng.core.artifacts.resources.gar;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARResponseDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.service.GARResourceService;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Api("artifacts")
@Path("/artifacts/gar")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})

@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class GARArtifactResource {
  private final GARResourceService gARResourceService;
  @GET
  @Path("getBuildDetails")
  @ApiOperation(
      value = "Gets google artifact registry build details", nickname = "getBuildDetailsForGoogleArtifactRegistry")
  public ResponseDTO<GARResponseDTO>
  getBuildDetails(@QueryParam("connectorRef") String GCPConnectorIdentifier, @QueryParam("region") String region,
      @QueryParam("repositoryName") String repositoryName, @QueryParam("project") String project,
      @QueryParam("package") String pkg, @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("version") String version,
      @QueryParam("versionRegex") String versionRegex, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(GCPConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    GARResponseDTO buildDetails = gARResourceService.getBuildDetails(
        connectorRef, region, repositoryName, project, pkg, version, versionRegex, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @GET
  @Path("getRegions")
  @ApiOperation(value = "Gets google artifact registry regions", nickname = "getRegionsForGoogleArtifactRegistry")
  public ResponseDTO<List<regionGar>> getRegions() {
    List<regionGar> regions = new ArrayList<>();
    regions.add(new regionGar("asia", "asia"));
    regions.add(new regionGar("asia-east1", "asia-east1"));
    regions.add(new regionGar("asia-east2", "asia-east2"));
    regions.add(new regionGar("asia-northeast1", "asia-northeast1"));
    regions.add(new regionGar("asia-northeast2", "asia-northeast2"));
    regions.add(new regionGar("asia-northeast3", "asia-northeast3"));
    regions.add(new regionGar("asia-south1", "asia-south1"));
    regions.add(new regionGar("asia-south2", "asia-south2"));
    regions.add(new regionGar("asia-southeast1", "asia-southeast1"));
    regions.add(new regionGar("asia-southeast2", "asia-southeast2"));
    regions.add(new regionGar("australia-southeast1", "australia-southeast1"));
    regions.add(new regionGar("australia-southeast2", "australia-southeast2"));
    regions.add(new regionGar("europe", "europe"));
    regions.add(new regionGar("europe-central2", "europe-central2"));
    regions.add(new regionGar("europe-north1", "europe-north1"));
    regions.add(new regionGar("europe-southwest1", "europe-southwest1"));
    regions.add(new regionGar("europe-west1", "europe-west1"));
    regions.add(new regionGar("europe-west2", "europe-west2"));
    regions.add(new regionGar("europe-west3", "europe-west3"));
    regions.add(new regionGar("europe-west4", "europe-west4"));
    regions.add(new regionGar("europe-west6", "europe-west6"));
    regions.add(new regionGar("europe-west8", "europe-west8"));
    regions.add(new regionGar("europe-west9", "europe-west9"));
    regions.add(new regionGar("northamerica-northeast1", "northamerica-northeast1"));
    regions.add(new regionGar("northamerica-northeast2", "northamerica-northeast2"));
    regions.add(new regionGar("southamerica-east1", "southamerica-east1"));
    regions.add(new regionGar("southamerica-west1", "southamerica-west1"));
    regions.add(new regionGar("us", "us"));
    regions.add(new regionGar("us-central1", "us-central1"));
    regions.add(new regionGar("us-east1", "us-east1"));
    regions.add(new regionGar("us-east4", "us-east4"));
    regions.add(new regionGar("us-east5", "us-east5"));
    regions.add(new regionGar("us-south1", "us-south1"));
    regions.add(new regionGar("us-west1", "us-west1"));
    regions.add(new regionGar("us-west2", "us-west2"));
    regions.add(new regionGar("us-west3", "us-west3"));
    regions.add(new regionGar("us-west4", "us-west4"));

    return ResponseDTO.newResponse(regions);
  }
}
