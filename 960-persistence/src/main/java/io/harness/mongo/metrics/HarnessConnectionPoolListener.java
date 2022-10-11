package io.harness.mongo.metrics;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.mongodb.connection.ConnectionId;
import com.mongodb.connection.ServerId;
import com.mongodb.event.ConnectionAddedEvent;
import com.mongodb.event.ConnectionCheckedInEvent;
import com.mongodb.event.ConnectionCheckedOutEvent;
import com.mongodb.event.ConnectionPoolClosedEvent;
import com.mongodb.event.ConnectionPoolListener;
import com.mongodb.event.ConnectionPoolOpenedEvent;
import com.mongodb.event.ConnectionPoolWaitQueueEnteredEvent;
import com.mongodb.event.ConnectionPoolWaitQueueExitedEvent;
import com.mongodb.event.ConnectionRemovedEvent;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@OwnedBy(HarnessTeam.PL)
public class HarnessConnectionPoolListener implements ConnectionPoolListener {
  private final ConcurrentMap<ServerId, HarnessConnectionPoolStatistics> map = new ConcurrentHashMap<>();

  @Override
  public void connectionPoolOpened(final ConnectionPoolOpenedEvent event) {
    HarnessConnectionPoolStatistics statistics = new HarnessConnectionPoolStatistics(event);
    map.put(event.getServerId(), statistics);
  }

  @Override
  public void connectionPoolClosed(final ConnectionPoolClosedEvent event) {
    map.remove(event.getServerId());
  }

  @Override
  public void connectionCheckedOut(final ConnectionCheckedOutEvent event) {
    HarnessConnectionPoolStatistics statistics = getStatistics(event.getConnectionId());
    if (statistics != null) {
      statistics.connectionCheckedOut(event);
    }
  }

  @Override
  public void connectionCheckedIn(final ConnectionCheckedInEvent event) {
    HarnessConnectionPoolStatistics statistics = getStatistics(event.getConnectionId());
    if (statistics != null) {
      statistics.connectionCheckedIn(event);
    }
  }

  @Override
  public void waitQueueEntered(final ConnectionPoolWaitQueueEnteredEvent event) {
    HarnessConnectionPoolStatistics statistics = getStatistics(event.getServerId());
    if (statistics != null) {
      statistics.waitQueueEntered(event);
    }
  }

  @Override
  public void waitQueueExited(final ConnectionPoolWaitQueueExitedEvent event) {
    HarnessConnectionPoolStatistics statistics = getStatistics(event.getServerId());
    if (statistics != null) {
      statistics.waitQueueExited(event);
    }
  }

  @Override
  public void connectionAdded(final ConnectionAddedEvent event) {
    HarnessConnectionPoolStatistics statistics = getStatistics(event.getConnectionId());
    if (statistics != null) {
      statistics.connectionAdded(event);
    }
  }

  @Override
  public void connectionRemoved(final ConnectionRemovedEvent event) {
    HarnessConnectionPoolStatistics statistics = getStatistics(event.getConnectionId());
    if (statistics != null) {
      statistics.connectionRemoved(event);
    }
  }

  private HarnessConnectionPoolStatistics getStatistics(final ConnectionId connectionId) {
    return getStatistics(connectionId.getServerId());
  }

  private HarnessConnectionPoolStatistics getStatistics(final ServerId serverId) {
    return map.get(serverId);
  }

  public ConcurrentMap<ServerId, HarnessConnectionPoolStatistics> getStatistics() {
    return map;
  }
}
