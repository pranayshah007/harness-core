/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.message;

public interface MessageConstants {
  // Watcher data name
  String WATCHER_DATA = "watcher-data";

  // Watcher data fields
  String WATCHER_VERSION = "version";
  String WATCHER_PROCESS = "process";
  String WATCHER_HEARTBEAT = "heartbeat";
  String RUNNING_DELEGATES = "running-delegates";
  String NEXT_WATCHER = "next-watcher";
  String EXTRA_WATCHER = "extra-watcher";

  // Messages sent between watcher processes on upgrade
  String WATCHER_STARTED = "watcher-started";
  String NEW_WATCHER = "new-watcher";
  String WATCHER_GO_AHEAD = "go-ahead";

  // Prefix for delegate data names
  String DELEGATE_DASH = "delegate-";

  // Delegate data fields
  String DELEGATE_VERSION = "version";
  String DELEGATE_ID = "delegateId";
  String DELEGATE_TOKEN_NAME = "delegateTokenName";
  String DELEGATE_HEARTBEAT = "heartbeat";
  String DELEGATE_IS_NEW = "newDelegate";
  String DELEGATE_RESTART_NEEDED = "restartNeeded";
  String DELEGATE_UPGRADE_NEEDED = "upgradeNeeded";
  String DELEGATE_UPGRADE_PENDING = "upgradePending";
  String DELEGATE_UPGRADE_STARTED = "upgradeStarted";
  String DELEGATE_SHUTDOWN_PENDING = "shutdownPending";
  String DELEGATE_SHUTDOWN_STARTED = "shutdownStarted";
  String DELEGATE_SELF_DESTRUCT = "selfDestruct";
  String DELEGATE_MIGRATE = "migrate";
  String DELEGATE_SWITCH_STORAGE = "switchStorage";
  String DELEGATE_JRE_VERSION = "delegateJreVersion";
  String MIGRATE_TO_JRE_VERSION = "migrateToJreVersion";

  // Messages sent from watcher to delegate processes
  String UPGRADING_DELEGATE = "upgrading";
  String DELEGATE_STOP_ACQUIRING = "stop-acquiring";
  String DELEGATE_GO_AHEAD = "go-ahead";
  String DELEGATE_RESUME = "resume";
  String DELEGATE_STOP_GRPC = "stop-grpc";
  String DELEGATE_START_GRPC = "start-grpc";

  // Messages received by watcher from delegate processes
  String NEW_DELEGATE = "new-delegate";
  String DELEGATE_STARTED = "delegate-started";

  // below msg will be sent to watcher if delegateId is not present
  String UNREGISTERED = "Unregistered";
}
