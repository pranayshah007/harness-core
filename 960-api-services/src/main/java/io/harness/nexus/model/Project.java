/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.nexus.model;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.io.Serializable;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
/**
 * Created by srinivas on 4/6/17.
 */

@XmlRootElement(name = "project")
@XmlAccessorType(XmlAccessType.FIELD)
@lombok.Data
@OwnedBy(HarnessTeam.CDC)
public class Project implements Serializable {
  private String modelVersion;
  private String groupId;
  private String artifactId;
  private String version;
  private String packaging;
  private String description;

  private Parent parent;
}
