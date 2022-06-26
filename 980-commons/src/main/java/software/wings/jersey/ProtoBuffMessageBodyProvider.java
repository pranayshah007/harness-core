package software.wings.jersey;

import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;
import com.google.protobuf.util.JsonFormat;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

/**
 * A Jersey provider which enables using Protocol Buffers to parse request entities into objects and
 * generate response entities from objects.
 */
@Provider
@Produces("application/protobuf")
@Consumes("application/protobuf")
@Slf4j
@Singleton
public class ProtoBuffMessageBodyProvider implements MessageBodyReader<Message>, MessageBodyWriter<Message> {
  private final Map<Class<Message>, Method> methodCache = new ConcurrentHashMap<>();

  @Override
  public boolean isReadable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
    return Message.class.isAssignableFrom(aClass);
  }

  @Override
  public Message readFrom(Class<Message> aClass, Type type, Annotation[] annotations, MediaType mediaType,
      MultivaluedMap<String, String> multivaluedMap, InputStream inputStream)
      throws IOException, WebApplicationException {
    final Method newBuilder = methodCache.computeIfAbsent(aClass, t -> {
      try {
        return t.getMethod("newBuilder");
      } catch (Exception e) {
        return null;
      }
    });

    final Message.Builder builder;
    try {
      builder = (Message.Builder) newBuilder.invoke(type);
    } catch (Exception e) {
      throw new WebApplicationException(e);
    }

    if (mediaType.getSubtype().contains("text-format")) {
      TextFormat.merge(new InputStreamReader(inputStream, StandardCharsets.UTF_8), builder);
      return builder.build();
    } else if (mediaType.getSubtype().contains("json-format")) {
      JsonFormat.parser().ignoringUnknownFields().merge(
          new InputStreamReader(inputStream, StandardCharsets.UTF_8), builder);
      return builder.build();
    } else {
      return builder.mergeFrom(inputStream).build();
    }
  }

  @Override
  public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
    return Message.class.isAssignableFrom(aClass);
  }

  @Override
  public long getSize(Message message, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
    if (mediaType.getSubtype().contains("text-format")) {
      final String formatted = message.toString();
      return formatted.getBytes(StandardCharsets.UTF_8).length;
    } else if (mediaType.getSubtype().contains("json-format")) {
      try {
        final String formatted = JsonFormat.printer().omittingInsignificantWhitespace().print(message);
        return formatted.getBytes(StandardCharsets.UTF_8).length;
      } catch (InvalidProtocolBufferException e) {
        // invalid protocol message
        return -1L;
      }
    }

    return message.getSerializedSize();
  }

  @Override
  public void writeTo(Message message, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType,
      MultivaluedMap<String, Object> multivaluedMap, OutputStream outputStream)
      throws IOException, WebApplicationException {
    if (mediaType.getSubtype().contains("text-format")) {
      outputStream.write(message.toString().getBytes(StandardCharsets.UTF_8));
    } else if (mediaType.getSubtype().contains("json-format")) {
      final String formatted = JsonFormat.printer().omittingInsignificantWhitespace().print(message);
      outputStream.write(formatted.getBytes(StandardCharsets.UTF_8));
    } else {
      message.writeTo(outputStream);
    }
  }
}
