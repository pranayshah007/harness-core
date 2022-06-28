package io.harness.ng.instancesync.resources;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.gitops.DeleteInstancesDTO;
import io.harness.dtos.gitops.GitOpsInstanceRequestDTO;
import io.harness.helper.GitOpsRequestDTOMapper;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.service.instance.InstanceService;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("gitOpsInstanceSync")
@Path("gitOpsInstanceSync")
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Produces({"application/json"})
@Consumes({"application/json"})
@Hidden
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class GitOpsInstanceSyncResource {
  private final InstanceService instanceService;
  private final GitOpsRequestDTOMapper gitOpsRequestDTOMapper;
  @POST
  @ApiOperation(value = "Create instances and save in DB", nickname = "createGitOpsInstances")
  public ResponseDTO<PageResponse<InstanceDTO>> createGitOpsInstances(
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @Valid List<GitOpsInstanceRequestDTO> gitOpsInstanceRequestDTOList) {
    List<InstanceDTO> instanceDTOList =
        instanceService.saveAll(gitOpsRequestDTOMapper.instanceDTOList(gitOpsInstanceRequestDTOList, accountId));
    PageResponse<InstanceDTO> pageResponse =
        PageResponse.<InstanceDTO>builder().content(new ArrayList<>(instanceDTOList)).empty(false).build();
    return ResponseDTO.newResponse(pageResponse);
  }
  @DELETE
  @Path("/delete")
  @ApiOperation(value = "Delete instances", nickname = "deleteGitOpsInstances")
  public ResponseDTO<DeleteInstancesDTO> deleteGitOpsInstances(
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @Valid List<GitOpsInstanceRequestDTO> gitOpsInstanceRequestDTOList) {
    instanceService.deleteAll(gitOpsRequestDTOMapper.instanceDTOList(gitOpsInstanceRequestDTOList, accountId));
    return ResponseDTO.newResponse(
        DeleteInstancesDTO.builder().deletedCount(gitOpsInstanceRequestDTOList.size()).status(true).build());
  }
}
