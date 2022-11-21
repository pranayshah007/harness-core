/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.yaml.directory;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.yaml.gitSync.beans.YamlGitConfig;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@OwnedBy(HarnessTeam.DX)
public class FolderNode extends DirectoryNode {
  private boolean defaultToClosed;
  private List<DirectoryNode> children = new ArrayList<>();
  private String appId;
  @Getter @Setter private transient YamlGitConfig yamlGitConfig;

  public FolderNode() {
    this.setType(NodeType.FOLDER);
    this.setRestName("folders");
  }

  public FolderNode(String accountId, String name, Class theClass) {
    super(accountId, name, theClass);
    this.setType(NodeType.FOLDER);
    this.setRestName("folders");
  }

  public FolderNode(String accountId, String name, Class theClass, DirectoryPath directoryPath) {
    super(accountId, name, theClass, directoryPath, NodeType.FOLDER);
    this.setRestName("folders");
  }

  public FolderNode(String accountId, String name, Class theClass, DirectoryPath directoryPath, String appId) {
    super(accountId, name, theClass, directoryPath, NodeType.FOLDER);
    this.appId = appId;
    this.setRestName("folders");
  }

  public boolean isDefaultToClosed() {
    return defaultToClosed;
  }

  public void setDefaultToClosed(boolean defaultToClosed) {
    this.defaultToClosed = defaultToClosed;
  }

  public List<DirectoryNode> getChildren() {
    return children;
  }

  public void setChildren(List<DirectoryNode> children) {
    this.children = children;
  }

  public void addChild(DirectoryNode child) {
    this.children.add(child);
  }

  public String getAppId() {
    return appId;
  }

  public void setAppId(String appId) {
    this.appId = appId;
  }
}
