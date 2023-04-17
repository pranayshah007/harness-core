/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import software.wings.beans.configfile.ConfigFileDto;

import java.io.IOException;
import java.util.List;

/**
 * Created by peeyushaggarwal on 1/9/17.
 */
public interface DelegateConfigService {
  List<ConfigFileDto> getConfigFiles(String appId, String envId, String uuid, String hostId, String accountId)
      throws IOException;
}
