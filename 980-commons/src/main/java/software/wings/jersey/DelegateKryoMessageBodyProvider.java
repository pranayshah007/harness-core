/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.jersey;

import io.harness.serializer.KryoSerializer;

import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Provider
@Consumes("application/x-kryo-v2")
@Produces("application/x-kryo-v2")
@Singleton
public class DelegateKryoMessageBodyProvider implements MessageBodyWriter<Object>, MessageBodyReader<Object> {
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer kryoSerializer;

  @Override
  public long getSize(final Object object, final Class<?> type, final Type genericType, final Annotation[] annotations,
      final MediaType mediaType) {
    return -1;
  }

  @Override
  public boolean isWriteable(
      final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType) {
    return true;
  }

  @Override
  public void writeTo(final Object object, final Class<?> type, final Type genericType, final Annotation[] annotations,
      final MediaType mediaType, final MultivaluedMap<String, Object> httpHeaders, final OutputStream entityStream)
      throws IOException, WebApplicationException {
    byte[] bytes = null;
    try {
      // We are seeing occasional failures writing delegate tasks where it fails with broken pipe.
      // Separating the kryo serialization from writing to the stream to identify the real cause of the issue.
      bytes = kryoSerializer.asBytes(object);
      entityStream.write(bytes);
      entityStream.flush();
    } catch (Exception e) {
      log.error("Failed to write {} to stream. {} bytes deserialized.", object.getClass().getCanonicalName(),
          bytes == null ? 0 : bytes.length, e);
    }
  }

  //
  // MessageBodyReader
  //

  @Override
  public boolean isReadable(
      final Class<?> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType) {
    return true;
  }

  @Override
  public Object readFrom(final Class<Object> type, final Type genericType, final Annotation[] annotations,
      final MediaType mediaType, final MultivaluedMap<String, String> httpHeaders, final InputStream entityStream)
      throws IOException, WebApplicationException {
    byte[] bytes = ByteStreams.toByteArray(entityStream);
    return kryoSerializer.asObject(bytes);
  }
}
