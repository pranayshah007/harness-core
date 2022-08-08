/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.dto.node.FolderNodeDTO;
import io.harness.ng.core.filestore.NGFileType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDP)
@UtilityClass
public class FileStoreNodeUtils {
  public static List<String> getFSNodesIdentifiers(List<FileStoreNodeDTO> nodes) {
    if (isEmpty(nodes)) {
      return Collections.emptyList();
    }

    return nodes.stream().map(FileStoreNodeDTO::getIdentifier).collect(Collectors.toList());
  }

  public static Optional<FolderNodeDTO> getSubFolder(FolderNodeDTO folder, final String subFolderName) {
    if (isEmpty(subFolderName)) {
      return Optional.empty();
    }

    return folder.getChildren()
        .stream()
        .filter(fsNode
            -> NGFileType.FOLDER == fsNode.getType() && isNotEmpty(fsNode.getName())
                && fsNode.getName().equals(subFolderName))
        .map(FolderNodeDTO.class ::cast)
        .findFirst();
  }

  public static String joinFolderNames(List<FolderNodeDTO> folders, final String delimiter) {
    if (isEmpty(folders)) {
      return StringUtils.EMPTY;
    }

    return folders.stream().map(FileStoreNodeDTO::getName).collect(Collectors.joining(delimiter));
  }

  public static Optional<String> getFileContent(FileNodeDTO fileNode) {
    if (fileNode == null) {
      return Optional.empty();
    }

    return Optional.of(fileNode.getContent());
  }

  /**
   *  List folder files content on the first level, not walking through the whole folder tree structure.
   *
   * @param folderNode folder
   * @return folder files content
   */
  public static List<String> getFolderFilesContent(FolderNodeDTO folderNode) {
    if (folderNode == null) {
      return Collections.emptyList();
    }

    return folderNode.getChildren()
        .stream()
        .filter(Objects::nonNull)
        .filter(storeNode -> storeNode.getType() == NGFileType.FILE)
        .map(FileNodeDTO.class ::cast)
        .map(FileNodeDTO::getContent)
        .collect(Collectors.toList());
  }

  /**
   *  Map the file store node to type R and add to list.
   *  If node is folder then iterates over all sub-folders, map file nodes to type R and add to list.
   *
   * @param node file store node
   * @param fileMapper file mapper
   * @param <R> type to map file to
   * @return list of type R
   */
  public static <R> List<R> mapFileNodes(FileStoreNodeDTO node, Function<FileNodeDTO, R> fileMapper) {
    if (node == null) {
      return Collections.emptyList();
    }

    if (fileMapper == null) {
      throw new InvalidArgumentsException("File mapper can't be null");
    }

    List<R> result = new ArrayList<>();
    walkThroughNodes(Collections.singletonList(node), result, fileMapper);

    return result;
  }

  private static <R> void walkThroughNodes(
      List<FileStoreNodeDTO> nodes, List<R> result, Function<FileNodeDTO, R> fileMapper) {
    if (isEmpty(nodes)) {
      return;
    }

    List<FileStoreNodeDTO> folders = new ArrayList<>();
    for (FileStoreNodeDTO node : nodes) {
      NGFileType nodeType = node.getType();
      if (NGFileType.FOLDER.equals(nodeType)) {
        FolderNodeDTO folder = (FolderNodeDTO) node;
        folders.addAll(folder.getChildren());
        continue;
      }

      if (NGFileType.FILE.equals(nodeType)) {
        mapFileAndAddToResult(result, (FileNodeDTO) node, fileMapper);
      }
    }

    walkThroughNodes(folders, result, fileMapper);
  }

  private static <R> void mapFileAndAddToResult(List<R> result, FileNodeDTO file, Function<FileNodeDTO, R> fileMapper) {
    result.add(fileMapper.apply(file));
  }
}
