package io.harness.mongo.metrics;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.event.ConnectionAddedEvent;
import com.mongodb.event.ConnectionCheckedInEvent;
import com.mongodb.event.ConnectionCheckedOutEvent;
import com.mongodb.event.ConnectionPoolListenerAdapter;
import com.mongodb.event.ConnectionPoolOpenedEvent;
import com.mongodb.event.ConnectionPoolWaitQueueEnteredEvent;
import com.mongodb.event.ConnectionPoolWaitQueueExitedEvent;
import com.mongodb.event.ConnectionRemovedEvent;
import com.mongodb.management.ConnectionPoolStatisticsMBean;
import java.util.concurrent.atomic.AtomicInteger;

@OwnedBy(HarnessTeam.PL)
public class HarnessConnectionPoolStatistics
    extends ConnectionPoolListenerAdapter implements ConnectionPoolStatisticsMBean {
  private final ServerAddress serverAddress;
  private final ConnectionPoolSettings settings;
  private final AtomicInteger size = new AtomicInteger();
  private final AtomicInteger checkedOutCount = new AtomicInteger();
  private final AtomicInteger waitQueueSize = new AtomicInteger();

  public HarnessConnectionPoolStatistics(final ConnectionPoolOpenedEvent event) {
    serverAddress = event.getServerId().getAddress();
    settings = event.getSettings();
  }

  @Override
  public String getHost() {
    return serverAddress.getHost();
  }

  @Override
  public int getPort() {
    return serverAddress.getPort();
  }

  @Override
  public int getMinSize() {
    return settings.getMinSize();
  }

  @Override
  public int getMaxSize() {
    return settings.getMaxSize();
  }

  @Override
  public int getSize() {
    return size.get();
  }

  @Override
  public int getCheckedOutCount() {
    return checkedOutCount.get();
  }

  @Override
  public int getWaitQueueSize() {
    return waitQueueSize.get();
  }

  @Override
  public void connectionCheckedOut(final ConnectionCheckedOutEvent event) {
    checkedOutCount.incrementAndGet();
  }

  @Override
  public void connectionCheckedIn(final ConnectionCheckedInEvent event) {
    checkedOutCount.decrementAndGet();
  }

  @Override
  public void connectionAdded(final ConnectionAddedEvent event) {
    size.incrementAndGet();
  }

  @Override
  public void connectionRemoved(final ConnectionRemovedEvent event) {
    size.decrementAndGet();
  }

  @Override
  public void waitQueueEntered(final ConnectionPoolWaitQueueEnteredEvent event) {
    waitQueueSize.incrementAndGet();
  }

  @Override
  public void waitQueueExited(final ConnectionPoolWaitQueueExitedEvent event) {
    waitQueueSize.decrementAndGet();
  }
}
