/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngmigration.utils.NGMigrationConstants.VIZ_FILE_NAME;
import static io.harness.ngmigration.utils.NGMigrationConstants.VIZ_TEMP_DIR_PREFIX;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;
import static software.wings.ngmigration.NGMigrationEntityType.DUMMY_HEAD;
import static software.wings.ngmigration.NGMigrationEntityType.ENVIRONMENT;
import static software.wings.ngmigration.NGMigrationEntityType.MANIFEST;
import static software.wings.ngmigration.NGMigrationEntityType.SECRET;
import static software.wings.ngmigration.NGMigrationEntityType.SECRET_MANAGER;
import static software.wings.ngmigration.NGMigrationEntityType.SECRET_MANAGER_TEMPLATE;

import static java.util.stream.Collectors.groupingBy;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.ngmigration.beans.DiscoverEntityInput;
import io.harness.ngmigration.beans.DiscoveryInput;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGSkipDetail;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.SkippedExpressionDetail;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.EntityMigratedStats;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigratedDetails;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.dto.SaveSummaryDTO;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngmigration.utils.NGMigrationConstants;
import io.harness.remote.client.ServiceHttpClientConfig;

import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import guru.nidi.graphviz.attribute.Label;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.Factory;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.model.MutableNode;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.ws.rs.core.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class DiscoveryService {
  @Inject private NgMigrationFactory migrationFactory;
  @Inject private MigratorMappingService migratorMappingService;
  @Inject @Named("ngClientConfig") private ServiceHttpClientConfig ngClientConfig;
  @Inject @Named("pipelineServiceClientConfig") private ServiceHttpClientConfig pipelineServiceClientConfig;

  @Inject @Named("templateServiceClientConfig") private ServiceHttpClientConfig templateServiceClientConfig;

  private void travel(String accountId, String appId, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId parent, DiscoveryNode discoveryNode) {
    if (discoveryNode == null) {
      return;
    }
    CgEntityNode currentNode = discoveryNode.getEntityNode();
    Set<CgEntityId> chilldren = discoveryNode.getChildren();

    if (graph.containsKey(currentNode.getEntityId()) && parent != null) {
      // We have already discovered and traversed the children. We do not need to process the children again
      graph.get(parent).add(currentNode.getEntityId());
      return;
    }

    // To ensure that appId is present in case of account level discovery
    if (NGMigrationEntityType.APPLICATION.equals(currentNode.getType())) {
      appId = currentNode.getId();
    }

    // Add the discovered node to the graph
    entities.putIfAbsent(currentNode.getEntityId(), currentNode);
    graph.putIfAbsent(currentNode.getEntityId(), new HashSet<>());

    // Link the discovered node to the parent
    if (parent != null) {
      // Note: parent will be null only the first time
      graph.get(parent).add(currentNode.getEntityId());
    }

    // Discover the child nodes and add to graph
    if (isNotEmpty(chilldren)) {
      // TODO: check if child already discovered, if yes, no need to rediscover. Just create a link in parent.
      for (CgEntityId child : chilldren) {
        NgMigrationService ngMigrationService = migrationFactory.getMethod(child.getType());
        DiscoveryNode node = null;
        try {
          node = ngMigrationService.discover(accountId, appId, child.getId());
        } catch (Exception e) {
          log.error("error when fetching entity", e);
        }
        travel(accountId, appId, entities, graph, currentNode.getEntityId(), node);
      }
    }
  }

  public Map<NGMigrationEntityType, BaseSummary> getSummary(
      String accountId, String appId, String entityId, NGMigrationEntityType entityType) {
    DiscoveryResult result = discover(accountId, appId, entityId, entityType, null);
    Map<NGMigrationEntityType, List<CgEntityNode>> entitiesByType =
        result.getEntities().values().stream().collect(groupingBy(CgEntityNode::getType));

    Map<NGMigrationEntityType, BaseSummary> summaries = new HashMap<>();

    entitiesByType.forEach((key, value) -> {
      NgMigrationService ngMigrationService = migrationFactory.getMethod(key);
      BaseSummary summary = ngMigrationService.getSummary(value);
      summaries.put(key, summary);
    });

    return summaries;
  }

  /*
   * We individually discover provided entities & finally merge them to a dummy head
   * */
  public DiscoveryResult discoverMulti(String accountId, DiscoveryInput discoveryInput) {
    Map<CgEntityId, CgEntityNode> entities = new HashMap<>();
    Map<CgEntityId, Set<CgEntityId>> graph = new HashMap<>();
    NgMigrationService ngMigrationService = migrationFactory.getMethod(NGMigrationEntityType.DUMMY_HEAD);
    DiscoveryNode head = ngMigrationService.discover(accountId, null, null);
    // Add the dummy head to the graph
    entities.putIfAbsent(head.getEntityNode().getEntityId(), head.getEntityNode());
    graph.putIfAbsent(head.getEntityNode().getEntityId(), new HashSet<>());
    if (isNotEmpty(discoveryInput.getEntities())) {
      for (DiscoverEntityInput child : discoveryInput.getEntities()) {
        String appId = child.getAppId();
        String entityId = child.getEntityId();
        NGMigrationEntityType entityType = child.getType();
        if (NGMigrationEntityType.APPLICATION.equals(entityType)) {
          // ensure that appId & entityId are same if we are tying to migrate an app.
          appId = entityId;
        }
        ngMigrationService = migrationFactory.getMethod(entityType);
        DiscoveryNode node = ngMigrationService.discover(accountId, appId, entityId);
        if (node == null) {
          log.warn(String.format("Entity not found! - Type: %s & ID: %s", child.getType(), entityId));
          continue;
        }
        // We add the node the dummy head's children & to the graph
        head.getChildren().add(node.getEntityNode().getEntityId());
        graph.get(head.getEntityNode().getEntityId()).add(node.getEntityNode().getEntityId());
        // Individually discover the child of every input
        travel(accountId, appId, entities, graph, null, node);
      }
    }
    if (discoveryInput.isExportImage()) {
      exportImg(entities, graph);
    }
    return DiscoveryResult.builder().entities(entities).links(graph).root(head.getEntityNode().getEntityId()).build();
  }

  public StreamingOutput discoverImg(String accountId, String appId, String entityId, NGMigrationEntityType entityType)
      throws IOException {
    Path path = Files.createTempDirectory(VIZ_TEMP_DIR_PREFIX);
    String imgPath = path.toFile().getAbsolutePath() + VIZ_FILE_NAME;
    discover(accountId, appId, entityId, entityType, imgPath);
    return output -> {
      try {
        byte[] data = Files.readAllBytes(Paths.get(imgPath));
        output.write(data);
        output.flush();
      } catch (Exception e) {
        throw new IllegalStateException("Could not export viz output file");
      }
    };
  }

  public DiscoveryResult discover(
      String accountId, String appId, String entityId, NGMigrationEntityType entityType, String filePath) {
    if (NGMigrationEntityType.APPLICATION.equals(entityType)) {
      // ensure that appId & entityId are same if we are tying to migrate an app.
      appId = entityId;
    }
    Map<CgEntityId, CgEntityNode> entities = new HashMap<>();
    Map<CgEntityId, Set<CgEntityId>> graph = new HashMap<>();

    NgMigrationService ngMigrationService = migrationFactory.getMethod(entityType);
    DiscoveryNode node = ngMigrationService.discover(accountId, appId, entityId);
    if (node == null) {
      throw new IllegalStateException("Root cannot be found!");
    }
    travel(accountId, appId, entities, graph, null, node);
    if (StringUtils.isNotBlank(filePath)) {
      exportImg(entities, graph, filePath);
    }
    return DiscoveryResult.builder().entities(entities).links(graph).root(node.getEntityNode().getEntityId()).build();
  }

  private void exportImg(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph, String filePath) {
    MutableGraph vizGraph = getGraphViz(entities, graph);
    try {
      Graphviz.fromGraph(vizGraph).render(Format.PNG).toFile(new File(filePath));
    } catch (IOException e) {
      log.warn("Unable to write visualization to file");
    }
  }

  private void exportImg(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph) {
    exportImg(entities, graph, NGMigrationConstants.DISCOVERY_IMAGE_PATH);
  }

  public StreamingOutput exportYamlFilesAsZip(MigrationInputDTO inputDTO, DiscoveryResult discoveryResult) {
    YamlGenerationDetails generationDetails = migrateEntity(inputDTO, discoveryResult);
    return createZip(generationDetails.getYamlFileList());
  }

  public StreamingOutput createZip(List<NGYamlFile> yamlFiles) {
    String folder = "/tmp/" + UUIDGenerator.generateUuid();
    exportZip(yamlFiles, folder);
    return output -> {
      try {
        byte[] data = Files.readAllBytes(Paths.get(folder + NGMigrationConstants.ZIP_FILE_PATH));
        output.write(data);
        output.flush();
      } catch (Exception e) {
        throw new IllegalStateException("Could not export zip file");
      }
    };
  }

  public SaveSummaryDTO migrateEntitySequentially(MigrationInputDTO inputDTO, DiscoveryResult discoveryResult) {
    Map<CgEntityId, NGYamlFile> migratedEntities = new HashMap<>();
    Map<CgEntityId, Set<CgEntityId>> leafTracker = discoveryResult.getLinks().entrySet().stream().collect(
        Collectors.toMap(Entry::getKey, e -> Sets.newHashSet(e.getValue())));

    MigrationContext context = MigrationContext.builder()
                                   .accountId(inputDTO.getAccountIdentifier())
                                   .migratedEntities(migratedEntities)
                                   .entities(discoveryResult.getEntities())
                                   .graph(discoveryResult.getLinks())
                                   .inputDTO(inputDTO)
                                   .root(inputDTO.getRoot())
                                   .build();

    Map<CgEntityId, CgEntityNode> entities = context.getEntities();
    CgEntityId entityId = discoveryResult.getRoot();
    if (!leafTracker.containsKey(entityId)) {
      return null;
    }
    List<NGYamlFile> files = new ArrayList<>();
    List<NGSkipDetail> skipDetails = new ArrayList<>();
    List<SkippedExpressionDetail> skippedExpressionDetails = new ArrayList<>();

    SaveSummaryDTO summaryDTO = SaveSummaryDTO.builder()
                                    .skippedExpressions(skippedExpressionDetails)
                                    .errors(new ArrayList<>())
                                    .stats(new HashMap<>())
                                    .skipDetails(skipDetails)
                                    .alreadyMigratedDetails(new ArrayList<>())
                                    .successfullyMigratedDetails(new ArrayList<>())
                                    .build();

    // Load all migrated entities for the CG entities before actual migration
    // We'll first load the environment because infra depends on Environment
    entities.keySet()
        .stream()
        .filter(id -> !DUMMY_HEAD.equals(id.getType()))
        .sorted(Comparator.comparing(id -> !ENVIRONMENT.equals(id.getType())))
        .forEach(cgEntityId -> {
          NGYamlFile yamlFile = migrationFactory.getMethod(cgEntityId.getType()).getExistingYaml(context, cgEntityId);
          if (yamlFile != null) {
            context.getMigratedEntities().put(cgEntityId, yamlFile);
            files.add(yamlFile);
            summaryDTO.getStats().putIfAbsent(yamlFile.getType(), new EntityMigratedStats());
            summaryDTO.getStats().get(yamlFile.getType()).incrementAlreadyMigrated();
            summaryDTO.getAlreadyMigratedDetails().add(MigratedDetails.builder()
                                                           .cgEntityDetail(yamlFile.getCgBasicInfo())
                                                           .ngEntityDetail(yamlFile.getNgEntityDetail())
                                                           .build());
          }
        });

    MigratorUtility.sort(files);

    NGClient ngClient = MigratorUtility.getRestClient(inputDTO, ngClientConfig, NGClient.class);
    PmsClient pmsClient = MigratorUtility.getRestClient(inputDTO, pipelineServiceClientConfig, PmsClient.class);
    TemplateClient templateClient =
        MigratorUtility.getRestClient(inputDTO, templateServiceClientConfig, TemplateClient.class);

    migrateForSpecificType(summaryDTO, inputDTO, ngClient, pmsClient, templateClient, context, entityId,
        SECRET_MANAGER_TEMPLATE, files, skipDetails);
    migrateForSpecificType(summaryDTO, inputDTO, ngClient, pmsClient, templateClient, context, entityId, SECRET_MANAGER,
        files, skipDetails);
    migrateForSpecificType(
        summaryDTO, inputDTO, ngClient, pmsClient, templateClient, context, entityId, SECRET, files, skipDetails);
    migrateForSpecificType(
        summaryDTO, inputDTO, ngClient, pmsClient, templateClient, context, entityId, CONNECTOR, files, skipDetails);
    migrateForSpecificType(
        summaryDTO, inputDTO, ngClient, pmsClient, templateClient, context, entityId, ENVIRONMENT, files, skipDetails);

    while (isNotEmpty(leafTracker)) {
      List<CgEntityId> leafNodes = getLeafNodes(leafTracker);
      for (CgEntityId entry : leafNodes) {
        if (Sets.newHashSet(SECRET_MANAGER_TEMPLATE, SECRET_MANAGER, SECRET, CONNECTOR, ENVIRONMENT)
                .contains(entry.getType())) {
          continue;
        }
        List<NGYamlFile> yamlFiles = generateYaml(context, entityId, files, skipDetails, entry);
        for (NGYamlFile file : yamlFiles) {
          try {
            NgMigrationService ngMigration = migrationFactory.getMethod(file.getType());
            MigrationImportSummaryDTO importSummaryDTO =
                ngMigration.migrate(ngClient, pmsClient, templateClient, inputDTO, file);
            addToSummary(summaryDTO, file, importSummaryDTO);
            migratorMappingService.mapCgNgEntity(file);
          } catch (IOException e) {
            log.error("Unable to migrate entity", e);
            summaryDTO.getErrors().add(
                ImportError.builder().message(e.getMessage()).entity(file.getCgBasicInfo()).build());
          }
        }
      }
      removeLeafNodes(leafTracker);
    }

    for (NGYamlFile yamlFile : files) {
      Set<String> skippedExpressions = SetUtils.emptyIfNull(MigratorExpressionUtils.getExpressions(yamlFile))
                                           .stream()
                                           .filter(exp -> exp.contains("."))
                                           .collect(Collectors.toSet());
      if (EmptyPredicate.isNotEmpty(skippedExpressions)) {
        skippedExpressionDetails.add(SkippedExpressionDetail.builder()
                                         .expressions(skippedExpressions)
                                         .entityType(yamlFile.getNgEntityDetail().getEntityType())
                                         .orgIdentifier(yamlFile.getNgEntityDetail().getOrgIdentifier())
                                         .projectIdentifier(yamlFile.getNgEntityDetail().getProjectIdentifier())
                                         .identifier(yamlFile.getNgEntityDetail().getIdentifier())
                                         .build());
      }
    }
    return summaryDTO;
  }

  private YamlGenerationDetails migrateEntity(MigrationInputDTO inputDTO, DiscoveryResult discoveryResult) {
    Map<CgEntityId, NGYamlFile> migratedEntities = new HashMap<>();
    Map<CgEntityId, Set<CgEntityId>> leafTracker = discoveryResult.getLinks().entrySet().stream().collect(
        Collectors.toMap(Entry::getKey, e -> Sets.newHashSet(e.getValue())));

    MigrationContext migrationContext = MigrationContext.builder()
                                            .accountId(inputDTO.getAccountIdentifier())
                                            .migratedEntities(migratedEntities)
                                            .entities(discoveryResult.getEntities())
                                            .graph(discoveryResult.getLinks())
                                            .inputDTO(inputDTO)
                                            .root(inputDTO.getRoot())
                                            .build();

    return getAllYamlFiles(migrationContext, discoveryResult.getRoot(), leafTracker);
  }

  public SaveSummaryDTO migrateEntities(MigrationInputDTO inputDTO, DiscoveryResult discoveryResult) {
    YamlGenerationDetails generationDetails = migrateEntity(inputDTO, discoveryResult);
    return createEntities(inputDTO, generationDetails);
  }

  private SaveSummaryDTO createEntities(MigrationInputDTO inputDTO, YamlGenerationDetails generationDetails) {
    List<NGYamlFile> ngYamlFiles = generationDetails.getYamlFileList();
    List<NGSkipDetail> skipDetails = generationDetails.getSkipDetails();
    NGClient ngClient = MigratorUtility.getRestClient(inputDTO, ngClientConfig, NGClient.class);
    PmsClient pmsClient = MigratorUtility.getRestClient(inputDTO, pipelineServiceClientConfig, PmsClient.class);
    TemplateClient templateClient =
        MigratorUtility.getRestClient(inputDTO, templateServiceClientConfig, TemplateClient.class);
    // Sort such that we create secrets first then connectors and so on.
    MigratorUtility.sort(ngYamlFiles);
    SaveSummaryDTO summaryDTO = SaveSummaryDTO.builder()
                                    .skippedExpressions(generationDetails.getSkippedExpressions())
                                    .errors(new ArrayList<>())
                                    .stats(new HashMap<>())
                                    .skipDetails(skipDetails)
                                    .alreadyMigratedDetails(new ArrayList<>())
                                    .successfullyMigratedDetails(new ArrayList<>())
                                    .build();

    for (NGYamlFile file : ngYamlFiles) {
      try {
        summaryDTO.getStats().putIfAbsent(file.getType(), new EntityMigratedStats());
        NgMigrationService ngMigration = migrationFactory.getMethod(file.getType());
        if (!file.isExists()) {
          MigrationImportSummaryDTO importSummaryDTO =
              ngMigration.migrate(ngClient, pmsClient, templateClient, inputDTO, file);
          addToSummary(summaryDTO, file, importSummaryDTO);
        } else {
          summaryDTO.getStats().get(file.getType()).incrementAlreadyMigrated();
          summaryDTO.getAlreadyMigratedDetails().add(MigratedDetails.builder()
                                                         .cgEntityDetail(file.getCgBasicInfo())
                                                         .ngEntityDetail(file.getNgEntityDetail())
                                                         .build());
          log.info("Skipping creation of entity with basic info {}", file.getCgBasicInfo());
        }
        migratorMappingService.mapCgNgEntity(file);
      } catch (IOException e) {
        log.error("Unable to migrate entity", e);
        summaryDTO.getErrors().add(ImportError.builder().message(e.getMessage()).entity(file.getCgBasicInfo()).build());
      }
    }
    summaryDTO.setNgYamlFiles(ngYamlFiles);
    return summaryDTO;
  }

  public static void addToSummary(
      SaveSummaryDTO summaryDTO, NGYamlFile file, MigrationImportSummaryDTO importSummaryDTO) {
    summaryDTO.getStats().putIfAbsent(file.getType(), new EntityMigratedStats());
    if (importSummaryDTO != null && importSummaryDTO.isSuccess()) {
      summaryDTO.getStats().get(file.getType()).incrementSuccessfullyMigrated();
      summaryDTO.getSuccessfullyMigratedDetails().add(MigratedDetails.builder()
                                                          .cgEntityDetail(file.getCgBasicInfo())
                                                          .ngEntityDetail(file.getNgEntityDetail())
                                                          .build());
    }
    if (importSummaryDTO != null && EmptyPredicate.isNotEmpty(importSummaryDTO.getErrors())) {
      summaryDTO.getErrors().addAll(importSummaryDTO.getErrors());
    }
  }

  private void exportZip(List<NGYamlFile> ngYamlFiles, String dirName) {
    // Write the files to ZIP folder
    try {
      File directory = new File(dirName);
      if (directory.exists()) {
        FileUtils.cleanDirectory(directory);
      }
    } catch (IOException e) {
      log.warn("Failed to clean output directory");
    }
    File zipFile = new File(dirName + NGMigrationConstants.ZIP_FILE_PATH);
    zipFile.getParentFile().mkdirs();
    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile))) {
      for (NGYamlFile file : ngYamlFiles) {
        if (MANIFEST.equals(file.getType()) || file.isExists()) {
          // TODO: @deepak.puthraya Add the mapping to the response
          continue;
        }
        ZipEntry e = new ZipEntry(file.getFilename());
        out.putNextEntry(e);
        byte[] data = migrationFactory.getMethod(file.getType()).getYamlString(file).getBytes();
        out.write(data, 0, data.length);
        out.closeEntry();
      }
    } catch (IOException e) {
      log.warn("Unable to save zip file");
    }
  }

  private List<CgEntityId> getLeafNodes(Map<CgEntityId, Set<CgEntityId>> graph) {
    if (isEmpty(graph)) {
      return new ArrayList<>();
    }
    return graph.entrySet()
        .stream()
        .filter(entry -> isEmpty(entry.getValue()))
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
  }

  private void removeLeafNodes(Map<CgEntityId, Set<CgEntityId>> graph) {
    List<CgEntityId> leafNodes = getLeafNodes(graph);
    if (isEmpty(leafNodes)) {
      return;
    }
    leafNodes.forEach(graph::remove);
    if (isEmpty(graph)) {
      return;
    }
    for (Map.Entry<CgEntityId, Set<CgEntityId>> entry : graph.entrySet()) {
      if (isNotEmpty(entry.getValue())) {
        graph.get(entry.getKey()).removeAll(leafNodes);
      }
    }
  }

  private YamlGenerationDetails getAllYamlFiles(
      MigrationContext context, CgEntityId entityId, Map<CgEntityId, Set<CgEntityId>> leafTracker) {
    Map<CgEntityId, CgEntityNode> entities = context.getEntities();
    if (!leafTracker.containsKey(entityId)) {
      return null;
    }
    List<NGYamlFile> files = new ArrayList<>();
    List<NGSkipDetail> skipDetails = new ArrayList<>();

    // Load all migrated entities for the CG entities before actual migration
    // We'll first load the environment because infra depends on Environment
    entities.keySet()
        .stream()
        .filter(id -> !DUMMY_HEAD.equals(id.getType()))
        .sorted(Comparator.comparing(id -> !ENVIRONMENT.equals(id.getType())))
        .forEach(cgEntityId -> {
          NGYamlFile yamlFile = migrationFactory.getMethod(cgEntityId.getType()).getExistingYaml(context, cgEntityId);
          if (yamlFile != null) {
            context.getMigratedEntities().put(cgEntityId, yamlFile);
            files.add(yamlFile);
          }
        });

    // Note: Special case: Migrate environments
    // We are doing this because when we migrate infra we need to reference environment
    // & environment is parent of infra. Environment also has no business logic.
    generateYamlsForSpecificType(context, entityId, SECRET_MANAGER_TEMPLATE, files, skipDetails);
    generateYamlsForSpecificType(context, entityId, SECRET_MANAGER, files, skipDetails);
    generateYamlsForSpecificType(context, entityId, SECRET, files, skipDetails);
    generateYamlsForSpecificType(context, entityId, CONNECTOR, files, skipDetails);
    generateYamlsForSpecificType(context, entityId, ENVIRONMENT, files, skipDetails);

    while (isNotEmpty(leafTracker)) {
      List<CgEntityId> leafNodes = getLeafNodes(leafTracker);
      for (CgEntityId entry : leafNodes) {
        if (Sets.newHashSet(SECRET_MANAGER_TEMPLATE, SECRET_MANAGER, SECRET, CONNECTOR, ENVIRONMENT)
                .contains(entry.getType())) {
          continue;
        }
        generateYaml(context, entityId, files, skipDetails, entry);
      }
      removeLeafNodes(leafTracker);
    }

    List<SkippedExpressionDetail> skippedExpressionDetails = new ArrayList<>();
    for (NGYamlFile yamlFile : files) {
      Set<String> skippedExpressions = SetUtils.emptyIfNull(MigratorExpressionUtils.getExpressions(yamlFile))
                                           .stream()
                                           .filter(exp -> exp.contains("."))
                                           .collect(Collectors.toSet());
      if (EmptyPredicate.isNotEmpty(skippedExpressions)) {
        skippedExpressionDetails.add(SkippedExpressionDetail.builder()
                                         .expressions(skippedExpressions)
                                         .entityType(yamlFile.getNgEntityDetail().getEntityType())
                                         .orgIdentifier(yamlFile.getNgEntityDetail().getOrgIdentifier())
                                         .projectIdentifier(yamlFile.getNgEntityDetail().getProjectIdentifier())
                                         .identifier(yamlFile.getNgEntityDetail().getIdentifier())
                                         .build());
      }
    }
    return YamlGenerationDetails.builder()
        .yamlFileList(files)
        .skippedExpressions(skippedExpressionDetails)
        .skipDetails(skipDetails)
        .build();
  }

  private List<NGYamlFile> generateYaml(MigrationContext context, CgEntityId entityId, List<NGYamlFile> files,
      List<NGSkipDetail> skipDetails, CgEntityId entry) {
    YamlGenerationDetails details = migrationFactory.getMethod(entry.getType()).getYamls(context, entityId, entry);
    if (details != null) {
      if (isNotEmpty(details.getSkipDetails())) {
        skipDetails.addAll(details.getSkipDetails());
      }
      if (isNotEmpty(details.getYamlFileList())) {
        files.addAll(details.getYamlFileList());
        return details.getYamlFileList();
      }
    }
    return Collections.emptyList();
  }

  private void generateYamlsForSpecificType(MigrationContext context, CgEntityId entityId,
      NGMigrationEntityType entityType, List<NGYamlFile> files, List<NGSkipDetail> skipDetails) {
    List<CgEntityId> specificEntities =
        context.getGraph()
            .entrySet()
            .stream()
            .filter(entry -> entityType.equals(entry.getKey().getType()))
            .sorted((e1, e2) -> {
              boolean hasChildrenOfSameType = e2.getValue().stream().anyMatch(
                  childrenCgEntityId -> childrenCgEntityId.getType().equals(e2.getKey().getType()));
              if (hasChildrenOfSameType) {
                return -1;
              }
              return 1;
            })
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    for (CgEntityId entry : specificEntities) {
      generateYaml(context, entityId, files, skipDetails, entry);
    }
  }

  private void migrateForSpecificType(SaveSummaryDTO saveSummaryDTO, MigrationInputDTO inputDTO, NGClient ngClient,
      PmsClient pmsClient, TemplateClient templateClient, MigrationContext context, CgEntityId entityId,
      NGMigrationEntityType entityType, List<NGYamlFile> files, List<NGSkipDetail> skipDetails) {
    List<CgEntityId> specificEntities =
        context.getGraph()
            .entrySet()
            .stream()
            .filter(entry -> entityType.equals(entry.getKey().getType()))
            .sorted((e1, e2) -> {
              boolean hasChildrenOfSameType = e2.getValue().stream().anyMatch(
                  childrenCgEntityId -> childrenCgEntityId.getType().equals(e2.getKey().getType()));
              if (hasChildrenOfSameType) {
                return -1;
              }
              return 1;
            })
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    for (CgEntityId entry : specificEntities) {
      List<NGYamlFile> generatedYamls = generateYaml(context, entityId, files, skipDetails, entry);
      for (NGYamlFile file : generatedYamls) {
        try {
          NgMigrationService ngMigration = migrationFactory.getMethod(file.getType());
          MigrationImportSummaryDTO importSummaryDTO =
              ngMigration.migrate(ngClient, pmsClient, templateClient, inputDTO, file);
          addToSummary(saveSummaryDTO, file, importSummaryDTO);
        } catch (IOException e) {
          log.error("Unable to migrate entity", e);
          saveSummaryDTO.getErrors().add(
              ImportError.builder().message(e.getMessage()).entity(file.getCgBasicInfo()).build());
        }
      }
    }
  }

  private MutableGraph getGraphViz(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph) {
    MutableGraph vizGraph = Factory.mutGraph().setDirected(true);
    Map<CgEntityId, MutableNode> nodes = new HashMap<>();

    vizGraph.use((gr, ctx) -> {
      for (CgEntityId node : graph.keySet()) {
        CgEntityNode cgEntityNode = entities.get(node);
        NGMigrationEntity entityNode = cgEntityNode.getEntity();
        MutableNode vizNode = Factory.mutNode(node.toString());
        vizNode.setName(Label.htmlLines(entityNode.getMigrationEntityName(), cgEntityNode.getType().name()));
        nodes.put(node, vizNode);
      }
      for (Map.Entry<CgEntityId, Set<CgEntityId>> entry : graph.entrySet()) {
        Set<CgEntityId> children = entry.getValue();
        MutableNode parentVizNode = nodes.get(entry.getKey());
        parentVizNode.addLink(children.stream().map(nodes::get).toArray(MutableNode[] ::new));
      }
    });

    vizGraph.add(nodes.values().toArray(new MutableNode[0]));
    return vizGraph;
  }
}
