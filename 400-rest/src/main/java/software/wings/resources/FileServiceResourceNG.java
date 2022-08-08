/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.FileBucket;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import software.wings.service.intfc.FileService;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api(value = "file-service", hidden = true)
@Path("/ng/file-service")
@Produces("application/json")
@Consumes("application/json")
// TODO: check with Vikas on Auth
@NextGenManagerAuth
@OwnedBy(CDP)
public class FileServiceResourceNG {
  @Inject private FileService fileService;

  @GET
  @Path("latestFileId")
  public RestResponse<String> getLatestFileId(
      @QueryParam("entityId") @NotNull String entityId, @QueryParam("fileBucket") @NotNull FileBucket fileBucket) {
    String latestFileId = fileService.getLatestFileId(entityId, fileBucket);
    return new RestResponse<>(latestFileId);
  }

  @POST
  @Path("parentEntityIdAndVersion")
  public RestResponse<Boolean> updateParentEntityIdAndVersion(@QueryParam("entityId") @NotNull String entityId,
      @QueryParam("stateFileId") @NotNull String stateFileId, @QueryParam("fileBucket") @NotNull FileBucket fileBucket)
      throws UnsupportedEncodingException {
    boolean updated = fileService.updateParentEntityIdAndVersion(
        null, URLDecoder.decode(entityId, "UTF-8"), null, stateFileId, null, fileBucket);
    return new RestResponse<>(updated);
  }

  @DELETE
  @Path("{fileId}")
  public RestResponse<Void> deleteFile(
      @PathParam("fileId") String fileId, @QueryParam("bucket") FileBucket fileBucket) {
    fileService.deleteFile(fileId, fileBucket);
    return new RestResponse<>(null);
  }
}
