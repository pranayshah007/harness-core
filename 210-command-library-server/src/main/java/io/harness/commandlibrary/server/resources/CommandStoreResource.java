/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.commandlibrary.server.resources;

import static io.harness.commandlibrary.server.utils.ArchiveUtils.getAllFilePaths;
import static io.harness.commandlibrary.server.utils.CommandVersionUtils.populateCommandVersionDTO;
import static io.harness.commandlibrary.server.utils.YamlUtils.fromYaml;
import static io.harness.data.structure.CollectionUtils.trimmedLowercaseSet;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import static java.lang.String.format;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static org.apache.commons.collections4.SetUtils.emptyIfNull;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.commandlibrary.api.dto.CommandEntityDTO;
import io.harness.commandlibrary.api.dto.CommandStoreDTO;
import io.harness.commandlibrary.api.dto.CommandVersionDTO;
import io.harness.commandlibrary.server.app.CommandLibraryServerConfig;
import io.harness.commandlibrary.server.beans.CommandArchiveContext;
import io.harness.commandlibrary.server.beans.CommandManifest;
import io.harness.commandlibrary.server.beans.TagConfig;
import io.harness.commandlibrary.server.beans.archive.ArchiveFile;
import io.harness.commandlibrary.server.service.intfc.CommandService;
import io.harness.commandlibrary.server.service.intfc.CommandStoreService;
import io.harness.commandlibrary.server.service.intfc.CommandVersionService;
import io.harness.commandlibrary.server.utils.ArchiveUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NoResultFoundException;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.PublicApi;

import software.wings.api.commandlibrary.EnrichedCommandVersionDTO;
import software.wings.beans.commandlibrary.CommandEntity;
import software.wings.beans.commandlibrary.CommandVersionEntity;
import software.wings.security.annotations.AuthRule;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.jetbrains.annotations.NotNull;

@Api("command-stores")
@Path("/command-stores")
@Produces("application/json")
@Slf4j
public class CommandStoreResource {
  public static final String COMMAND_YAML = "command.yaml";

  @Inject private CommandStoreService commandStoreService;
  @Inject private CommandService commandService;
  @Inject private CommandVersionService commandVersionService;
  @Inject private CommandLibraryServerConfig commandLibraryServerConfig;

  @GET
  @Path("test")
  @PublicApi
  public RestResponse<String> testApi() {
    return new RestResponse<>("hello world");
  }

  @GET
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<List<CommandStoreDTO>> getCommandStores(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(commandStoreService.getCommandStores());
  }

  @GET
  @Path("{commandStoreName}/commands/tags")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<Set<String>> getCommandTags(@QueryParam("accountId") String accountId,
      @PathParam("commandStoreName") String commandStoreName,
      @DefaultValue("true") @QueryParam("onlyImportant") boolean onlyImportant) {
    return new RestResponse<>(onlyImportant ? commandLibraryServerConfig.getTagConfig().getImportantTags()
                                            : commandLibraryServerConfig.getTagConfig().getAllowedTags());
  }

  @GET
  @Path("{commandStoreName}/commands")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<PageResponse<CommandEntityDTO>> listCommands(@QueryParam("accountId") String accountId,
      @PathParam("commandStoreName") String commandStoreName, @BeanParam PageRequest<CommandEntity> pageRequest,
      @QueryParam("cl_implementation_version") Integer clImplementationVersion, @QueryParam("tag") String tag) {
    return new RestResponse<>(commandStoreService.listCommandsForStore(commandStoreName, pageRequest, tag));
  }

  @GET
  @Path("{commandStoreName}/commands/{commandName}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<CommandEntityDTO> getCommandDetails(@QueryParam("accountId") String accountId,
      @PathParam("commandStoreName") String commandStoreName, @PathParam("commandName") String commandName) {
    return new RestResponse<>(
        commandService.getCommandDetails(commandStoreName, commandName)
            .orElseThrow(notFoundExceptionSupplier(
                format("Command Store Name = [%s], command name = [%s] not found", commandStoreName, commandName))));
  }

  @NotNull
  private Supplier<NoResultFoundException> notFoundExceptionSupplier(String message) {
    return () -> NoResultFoundException.newBuilder().code(ErrorCode.RESOURCE_NOT_FOUND).message(message).build();
  }

  @POST
  @Path("{commandStoreName}/commands")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<CommandEntity> saveCommand(
      @QueryParam("accountId") String accountId, CommandEntity commandEntity) {
    final String commandId = commandService.save(commandEntity);
    return new RestResponse<>(commandService.getEntityById(commandId).orElseThrow(
        notFoundExceptionSupplier("command not found with id =" + commandId)));
  }

  @GET
  @Path("{commandStoreName}/commands/{commandName}/versions/{version}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<EnrichedCommandVersionDTO> getVersionDetails(@QueryParam("accountId") String accountId,
      @PathParam("commandStoreName") String commandStoreName, @PathParam("commandName") String commandName,
      @PathParam("version") String version) {
    final EnrichedCommandVersionDTO enrichedCommandVersionDTO =
        commandVersionService.getCommandVersionEntity(commandStoreName, commandName, version)
            .map(commandVersionEntity
                -> populateCommandVersionDTO(EnrichedCommandVersionDTO.builder(), commandVersionEntity).build())
            .orElseThrow(notFoundExceptionSupplier(
                format("command version not found with store =[%s], command =[%s], version=[%s]", commandStoreName,
                    commandName, version)));
    return new RestResponse<>(enrichedCommandVersionDTO);
  }

  @POST
  @Path("{commandStoreName}/commands/{commandName}/versions")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<CommandVersionEntity> saveCommandVersion(
      @QueryParam("accountId") String accountId, CommandVersionEntity commandVersionEntity) {
    final String commandVersionId = commandVersionService.save(commandVersionEntity);
    return new RestResponse<>(
        commandVersionService.getEntityById(commandVersionId)
            .orElseThrow(() -> NoResultFoundException.newBuilder().message("command Version does not exists").build()));
  }

  @POST
  @Path("{commandStoreName}/commands")
  @Consumes(MULTIPART_FORM_DATA)
  @Produces(APPLICATION_JSON)
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<CommandVersionDTO> publishCommand(@QueryParam("accountId") final String accountId,
      @PathParam("commandStoreName") String commandStoreName,
      @FormDataParam("file") final InputStream uploadInputStream) {
    validateStore(commandStoreName);

    final ArchiveFile archiveFile = ArchiveUtils.createArchiveFile(uploadInputStream);
    log.info("Files read from the archive are [{}]", getAllFilePaths(archiveFile));

    final String versionId = commandVersionService.createNewVersionFromArchive(
        createCommandArchiveContext(commandStoreName, archiveFile, accountId));

    final EnrichedCommandVersionDTO commandVersionDTO =
        commandVersionService.getEntityById(versionId)
            .map(commandVersionEntity
                -> populateCommandVersionDTO(EnrichedCommandVersionDTO.builder(), commandVersionEntity).build())
            .orElse(null);
    return new RestResponse<>(commandVersionDTO);
  }

  private void validateStore(@PathParam("commandStoreName") String commandStoreName) {
    if (!commandStoreService.getStoreByName(commandStoreName).isPresent()) {
      throw new InvalidRequestException("Command Store not found");
    }
  }

  private CommandArchiveContext createCommandArchiveContext(
      String commandStoreName, ArchiveFile archiveFile, String accountId) {
    return CommandArchiveContext.builder()
        .commandStoreName(commandStoreName)
        .archiveFile(archiveFile)
        .commandManifest(createCommandManifest(archiveFile))
        .accountId(accountId)
        .build();
  }

  private CommandManifest createCommandManifest(ArchiveFile archiveFile) {
    final CommandManifest commandManifest =
        archiveFile.getContent(COMMAND_YAML)
            .map(archiveContent -> fromYaml(archiveContent.string(StandardCharsets.UTF_8), CommandManifest.class))
            .orElseThrow(() -> new InvalidRequestException(COMMAND_YAML + " file not found in archive"));
    commandManifest.setTags(
        validateAndGetCanonicalizedTags(commandManifest.getTags(), commandLibraryServerConfig.getTagConfig()));

    return commandManifest;
  }

  private Set<String> validateAndGetCanonicalizedTags(Set<String> tags, TagConfig tagConfig) {
    final Map<String, String> allowedTagsCanonicalMap = canonicalMapOfTags(tagConfig.getAllowedTags());
    validateAllowedTags(tags, allowedTagsCanonicalMap);
    return emptyIfNull(trimmedLowercaseSet(tags))
        .stream()
        .map(allowedTagsCanonicalMap::get)
        .filter(Objects::nonNull)
        .collect(toSet());
  }

  private void validateAllowedTags(Set<String> tags, Map<String, String> allowedTagsCanonicalMap) {
    final List<String> unknownTags = emptyIfNull(tags)
                                         .stream()
                                         .filter(Objects::nonNull)
                                         .map(StringUtils::trim)
                                         .filter(EmptyPredicate::isNotEmpty)
                                         .filter(tag -> !allowedTagsCanonicalMap.containsKey(tag.toLowerCase()))
                                         .collect(Collectors.toList());
    if (isNotEmpty(unknownTags)) {
      throw new InvalidRequestException(format("these tags are not allowed = %s", unknownTags));
    }
  }

  private Map<String, String> canonicalMapOfTags(Set<String> tags) {
    return tags.stream().collect(toMap(String::toLowerCase, Function.identity()));
  }
}
