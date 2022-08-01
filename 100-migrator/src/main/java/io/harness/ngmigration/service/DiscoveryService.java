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

import static java.util.stream.Collectors.groupingBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.network.Http;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.ngmigration.beans.BaseEntityInput;
import io.harness.ngmigration.beans.DiscoverEntityInput;
import io.harness.ngmigration.beans.DiscoveryInput;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.MigrationInputResult;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.utils.NGMigrationConstants;
import io.harness.remote.client.ServiceHttpClientConfig;

import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.ngmigration.NGMigrationStatus;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import guru.nidi.graphviz.attribute.Label;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.Factory;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.model.MutableNode;
import io.serializer.HObjectMapper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
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
import okhttp3.OkHttpClient;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class DiscoveryService {
  @Inject private NgMigrationFactory migrationFactory;
  @Inject private MigratorMappingService migratorMappingService;
  @Inject @Named("ngClientConfig") private ServiceHttpClientConfig ngClientConfig;
  @Inject @Named("pipelineServiceClientConfig") private ServiceHttpClientConfig pipelineServiceClientConfig;

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
        DiscoveryNode node = ngMigrationService.discover(accountId, appId, child.getId());
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
          throw new IllegalStateException(
              String.format("Entity not found! - Type: %s & ID: %s", child.getType(), entityId));
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

  public NGMigrationStatus getMigrationStatus(DiscoveryResult discoveryResult) {
    if (EmptyPredicate.isEmpty(discoveryResult.getEntities())) {
      return NGMigrationStatus.builder().status(true).build();
    }
    boolean possible = true;
    List<String> errors = new ArrayList<>();
    for (CgEntityNode node : discoveryResult.getEntities().values()) {
      NgMigrationService ngMigration = migrationFactory.getMethod(node.getType());
      NGMigrationStatus migrationStatus = ngMigration.canMigrate(node.getEntity());
      if (!migrationStatus.isStatus()) {
        possible = false;
        errors.addAll(migrationStatus.getReasons());
      }
    }
    return NGMigrationStatus.builder().status(possible).reasons(errors).build();
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

  public MigrationInputResult migrationInput(DiscoveryResult result) {
    Collection<CgEntityNode> cgEntityNodes = result.getEntities().values();
    Map<CgEntityId, BaseEntityInput> inputMap = new HashMap<>();
    for (CgEntityNode node : cgEntityNodes) {
      NgMigrationService ngMigration = migrationFactory.getMethod(node.getType());
      BaseEntityInput generatedInputs =
          ngMigration.generateInput(result.getEntities(), result.getLinks(), node.getEntityId());
      if (generatedInputs != null) {
        inputMap.put(node.getEntityId(), generatedInputs);
      }
    }
    return MigrationInputResult.builder().inputs(inputMap).build();
  }

  public StreamingOutput exportYamlFilesAsZip(MigrationInputDTO inputDTO, DiscoveryResult discoveryResult) {
    List<NGYamlFile> ngYamlFiles = migrateEntity(inputDTO, discoveryResult);
    String folder = "/tmp/" + UUIDGenerator.generateUuid();
    exportZip(ngYamlFiles, folder);
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

  private List<NGYamlFile> migrateEntity(MigrationInputDTO inputDTO, DiscoveryResult discoveryResult) {
    Map<CgEntityId, NgEntityDetail> migratedEntities = new HashMap<>();
    Map<CgEntityId, Set<CgEntityId>> leafTracker = discoveryResult.getLinks().entrySet().stream().collect(
        Collectors.toMap(Entry::getKey, e -> Sets.newHashSet(e.getValue())));
    return getAllYamlFiles(inputDTO, discoveryResult.getEntities(), discoveryResult.getLinks(),
        discoveryResult.getRoot(), migratedEntities, leafTracker);
  }

  public List<NGYamlFile> migrateEntity(
      String auth, MigrationInputDTO inputDTO, DiscoveryResult discoveryResult, boolean dryRun) {
    List<NGYamlFile> ngYamlFiles = migrateEntity(inputDTO, discoveryResult);
    exportZip(ngYamlFiles, NGMigrationConstants.DEFAULT_ZIP_DIRECTORY);
    if (!dryRun) {
      createEntities(auth, inputDTO, ngYamlFiles);
    }
    return ngYamlFiles;
  }

  private static <T> T getRestClient(ServiceHttpClientConfig ngClientConfig, Class<T> clazz) {
    OkHttpClient okHttpClient = Http.getOkHttpClient(ngClientConfig.getBaseUrl(), false);
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(ngClientConfig.getBaseUrl())
                            .addConverterFactory(JacksonConverterFactory.create(HObjectMapper.NG_DEFAULT_OBJECT_MAPPER))
                            .build();
    return retrofit.create(clazz);
  }

  private void createEntities(String auth, MigrationInputDTO inputDTO, List<NGYamlFile> ngYamlFiles) {
    NGClient ngClient = getRestClient(ngClientConfig, NGClient.class);
    PmsClient pmsClient = getRestClient(pipelineServiceClientConfig, PmsClient.class);
    // Sort such that we create secrets first then connectors and so on.
    MigratorUtility.sort(ngYamlFiles);
    for (NGYamlFile file : ngYamlFiles) {
      try {
        NgMigrationService ngMigration = migrationFactory.getMethod(file.getType());
        if (!file.isExists()) {
          ngMigration.migrate(auth, ngClient, pmsClient, inputDTO, file);
        } else {
          log.info("Skipping creation of entity with basic info {}", file.getCgBasicInfo());
        }
        migratorMappingService.mapCgNgEntity(file);
      } catch (IOException e) {
        log.error("Unable to migrate entity", e);
      }
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
        if (file.isExists()) {
          // TODO: @vaibhav.si Add the mapping to the response
          continue;
        }
        ZipEntry e = new ZipEntry(file.getFilename());
        out.putNextEntry(e);
        byte[] data = NGYamlUtils.getYamlString(file.getYaml()).getBytes();
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

  private List<NGYamlFile> getAllYamlFiles(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NgEntityDetail> migratedEntities,
      Map<CgEntityId, Set<CgEntityId>> leafTracker) {
    if (!leafTracker.containsKey(entityId) || isEmpty(leafTracker.get(entityId))) {
      return new ArrayList<>();
    }

    List<NGYamlFile> files = new ArrayList<>();
    while (isNotEmpty(leafTracker)) {
      List<CgEntityId> leafNodes = getLeafNodes(leafTracker);
      for (CgEntityId entry : leafNodes) {
        List<NGYamlFile> currentEntity =
            migrationFactory.getMethod(entry.getType()).getYaml(inputDTO, entities, graph, entry, migratedEntities);
        if (isNotEmpty(currentEntity)) {
          files.addAll(currentEntity);
        }
      }
      removeLeafNodes(leafTracker);
    }

    return files;
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
