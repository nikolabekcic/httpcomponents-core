/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.core5.http.impl.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.NotImplementedException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class TestDefaultBHttpServerConnection {

    @Mock
    private Socket socket;

    private DefaultBHttpServerConnection conn;

    @BeforeEach
    public void prepareMocks() {
        MockitoAnnotations.openMocks(this);
        conn = new DefaultBHttpServerConnection("http", Http1Config.DEFAULT,
                null, null,
                DefaultContentLengthStrategy.INSTANCE,
                DefaultContentLengthStrategy.INSTANCE,
                DefaultHttpRequestParserFactory.INSTANCE,
                DefaultHttpResponseWriterFactory.INSTANCE);
    }

    @Test
    public void testBasics() throws Exception {
        Assertions.assertFalse(conn.isOpen());
        Assertions.assertEquals("[Not bound]", conn.toString());
    }

    @Test
    public void testReadRequestHead() throws Exception {
        final String s = "GET / HTTP/1.1\r\nUser-Agent: test\r\n\r\n";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = conn.receiveRequestHeader();
        Assertions.assertNotNull(request);
        Assertions.assertEquals("/", request.getPath());
        Assertions.assertEquals(Method.GET.name(), request.getMethod());
        Assertions.assertTrue(request.containsHeader("User-Agent"));
        Assertions.assertEquals(1, conn.getEndpointDetails().getRequestCount());
    }

    @Test
    public void testReadRequestEntityWithContentLength() throws Exception {
        final String s = "POST / HTTP/1.1\r\nUser-Agent: test\r\nContent-Length: 3\r\n\r\n123";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = conn.receiveRequestHeader();

        Assertions.assertNotNull(request);
        Assertions.assertEquals("/", request.getPath());
        Assertions.assertEquals(Method.POST.name(), request.getMethod());
        Assertions.assertTrue(request.containsHeader("User-Agent"));
        Assertions.assertNull(request.getEntity());
        Assertions.assertEquals(1, conn.getEndpointDetails().getRequestCount());

        conn.receiveRequestEntity(request);

        final HttpEntity entity = request.getEntity();
        Assertions.assertNotNull(entity);
        Assertions.assertEquals(3, entity.getContentLength());
        Assertions.assertEquals(1, conn.getEndpointDetails().getRequestCount());
        final InputStream content = entity.getContent();
        Assertions.assertNotNull(content);
        Assertions.assertTrue(content instanceof ContentLengthInputStream);
    }

    @Test
    public void testReadRequestEntityChunckCoded() throws Exception {
        final String s = "POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nTransfer-Encoding: " +
                "chunked\r\n\r\n3\r\n123\r\n0\r\n\r\n";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = conn.receiveRequestHeader();

        Assertions.assertNotNull(request);
        Assertions.assertEquals("/stuff", request.getPath());
        Assertions.assertEquals(Method.POST.name(), request.getMethod());
        Assertions.assertTrue(request.containsHeader("User-Agent"));
        Assertions.assertNull(request.getEntity());
        Assertions.assertEquals(1, conn.getEndpointDetails().getRequestCount());

        conn.receiveRequestEntity(request);

        final HttpEntity entity = request.getEntity();
        Assertions.assertNotNull(entity);
        Assertions.assertEquals(-1, entity.getContentLength());
        Assertions.assertTrue(entity.isChunked());
        Assertions.assertEquals(1, conn.getEndpointDetails().getRequestCount());
        final InputStream content = entity.getContent();
        Assertions.assertNotNull(content);
        Assertions.assertTrue(content instanceof ChunkedInputStream);
    }

    @Test
    public void testReadRequestEntityIdentity() throws Exception {
        final String s = "POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nTransfer-Encoding: " +
                "identity\r\n\r\n123";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = conn.receiveRequestHeader();

        Assertions.assertNotNull(request);
        Assertions.assertEquals("/stuff", request.getPath());
        Assertions.assertEquals(Method.POST.name(), request.getMethod());
        Assertions.assertTrue(request.containsHeader("User-Agent"));
        Assertions.assertNull(request.getEntity());
        Assertions.assertEquals(1, conn.getEndpointDetails().getRequestCount());

        Assertions.assertThrows(ProtocolException.class, () ->
                conn.receiveRequestEntity(request));
    }

    @Test
    public void testReadRequestNoEntity() throws Exception {
        final String s = "POST /stuff HTTP/1.1\r\nUser-Agent: test\r\n\r\n";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = conn.receiveRequestHeader();

        Assertions.assertNotNull(request);
        Assertions.assertEquals("/stuff", request.getPath());
        Assertions.assertEquals(Method.POST.name(), request.getMethod());
        Assertions.assertTrue(request.containsHeader("User-Agent"));
        Assertions.assertNull(request.getEntity());
        Assertions.assertEquals(1, conn.getEndpointDetails().getRequestCount());

        conn.receiveRequestEntity(request);

        final HttpEntity entity = request.getEntity();
        Assertions.assertNull(entity);
    }

    @Test
    public void testWriteResponseHead() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getResponseCount());

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.addHeader("User-Agent", "test");

        conn.sendResponseHeader(response);
        conn.flush();

        Assertions.assertEquals(1, conn.getEndpointDetails().getResponseCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("HTTP/1.1 200 OK\r\nUser-Agent: test\r\n\r\n", s);
    }

    @Test
    public void testWriteResponse100Head() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getResponseCount());

        final ClassicHttpResponse response = new BasicClassicHttpResponse(100, "Go on");

        conn.sendResponseHeader(response);
        conn.flush();

        Assertions.assertEquals(0, conn.getEndpointDetails().getResponseCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("HTTP/1.1 100 Go on\r\n\r\n", s);
    }

    @Test
    public void testWriteResponseEntityWithContentLength() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getResponseCount());

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.addHeader("Server", "test");
        response.addHeader("Content-Length", "3");
        response.setEntity(new StringEntity("123", ContentType.TEXT_PLAIN));

        conn.sendResponseHeader(response);
        conn.sendResponseEntity(response);
        conn.flush();

        Assertions.assertEquals(1, conn.getEndpointDetails().getResponseCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("HTTP/1.1 200 OK\r\nServer: test\r\nContent-Length: 3\r\n\r\n123", s);
    }

    @Test
    public void testWriteResponseEntityChunkCoded() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getResponseCount());

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.addHeader("Server", "test");
        response.addHeader("Transfer-Encoding", "chunked");
        response.setEntity(new StringEntity("123", ContentType.TEXT_PLAIN));

        conn.sendResponseHeader(response);
        conn.sendResponseEntity(response);
        conn.flush();

        Assertions.assertEquals(1, conn.getEndpointDetails().getResponseCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("HTTP/1.1 200 OK\r\nServer: test\r\nTransfer-Encoding: " +
                "chunked\r\n\r\n3\r\n123\r\n0\r\n\r\n", s);
    }

    @Test
    public void testWriteResponseEntityIdentity() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getResponseCount());

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.addHeader("Server", "test");
        response.addHeader("Transfer-Encoding", "identity");
        response.setEntity(new StringEntity("123", ContentType.TEXT_PLAIN));

        conn.sendResponseHeader(response);
        Assertions.assertThrows(NotImplementedException.class, () ->
                conn.sendResponseEntity(response));
        conn.flush();
    }

    @Test
    public void testWriteResponseNoEntity() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getResponseCount());

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.addHeader("Server", "test");

        conn.sendResponseHeader(response);
        conn.sendResponseEntity(response);
        conn.flush();

        Assertions.assertEquals(1, conn.getEndpointDetails().getResponseCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("HTTP/1.1 200 OK\r\nServer: test\r\n\r\n", s);
    }

}
