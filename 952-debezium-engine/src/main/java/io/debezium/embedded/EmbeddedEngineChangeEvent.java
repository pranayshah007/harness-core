/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.debezium.embedded;

import io.debezium.engine.ChangeEvent;
import io.debezium.engine.Header;
import io.debezium.engine.RecordChangeEvent;
import java.util.List;
import lombok.ToString;
import org.apache.kafka.connect.source.SourceRecord;

/**
 * This class is a copy of EmbeddedEngineChangeEvent from debezium-embedded library.
 * Weirdly, they have not made the class public, and we have to get sourceRecord from the event to get '__op' field
 * Hence, we have made a copy of the class and made it public.
 * Please change this class if you change the version of Debezium in the future, right now it is 2.0.0.Final
 */

@ToString
public class EmbeddedEngineChangeEvent<K, V, H> implements ChangeEvent<K, V>, RecordChangeEvent<V> {
  private final K key;
  private final V value;
  private final List<Header<H>> headers;
  private final SourceRecord sourceRecord;

  public EmbeddedEngineChangeEvent(K key, V value, List<Header<H>> headers, SourceRecord sourceRecord) {
    this.key = key;
    this.value = value;
    this.headers = headers;
    this.sourceRecord = sourceRecord;
  }

  public K key() {
    return this.key;
  }

  public V value() {
    return this.value;
  }

  @Override
  public List<Header<H>> headers() {
    return headers;
  }

  public V record() {
    return this.value;
  }

  public String destination() {
    return this.sourceRecord.topic();
  }

  public SourceRecord sourceRecord() {
    return this.sourceRecord;
  }
}
