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

package org.apache.hc.client5.http.impl.nio;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.apache.hc.client5.http.impl.ConnPoolSupport;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.nio.command.ShutdownCommand;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.TlsCapableIOSession;
import org.apache.hc.core5.reactor.ssl.SSLBufferManagement;
import org.apache.hc.core5.reactor.ssl.SSLSessionInitializer;
import org.apache.hc.core5.reactor.ssl.SSLSessionVerifier;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.reactor.ssl.TransportSecurityLayer;
import org.apache.hc.core5.util.Identifiable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class ManagedAsyncClientConnection implements Identifiable, HttpConnection, TransportSecurityLayer {

    private final Logger log = LogManager.getLogger(getClass());

    private final TlsCapableIOSession ioSession;
    private final AtomicBoolean closed;

    public ManagedAsyncClientConnection(final TlsCapableIOSession ioSession) {
        this.ioSession = ioSession;
        this.closed = new AtomicBoolean();
    }

    @Override
    public String getId() {
        return ConnPoolSupport.getId(ioSession);
    }

    @Override
    public void shutdown(final ShutdownType shutdownType) {
        if (this.closed.compareAndSet(false, true)) {
            if (log.isDebugEnabled()) {
                log.debug(getId() + ": Shutdown connection " + shutdownType);
            }
            ioSession.shutdown(shutdownType);
        }
    }

    @Override
    public void close() throws IOException {
        if (this.closed.compareAndSet(false, true)) {
            if (log.isDebugEnabled()) {
                log.debug(getId() + ": Close connection");
            }
            ioSession.addFirst(new ShutdownCommand(ShutdownType.GRACEFUL));
        }
    }

    @Override
    public boolean isOpen() {
        return !ioSession.isClosed();
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        ioSession.setSocketTimeout(timeout);
    }

    @Override
    public int getSocketTimeout() {
        return ioSession.getSocketTimeout();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return ioSession.getRemoteAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return ioSession.getLocalAddress();
    }

    @Override
    public EndpointDetails getEndpointDetails() {
        final IOEventHandler handler = ioSession.getHandler();
        if (handler instanceof HttpConnection) {
            return ((HttpConnection) handler).getEndpointDetails();
        } else {
            return null;
        }
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        final IOEventHandler handler = ioSession.getHandler();
        if (handler instanceof HttpConnection) {
            return ((HttpConnection) handler).getProtocolVersion();
        } else {
            return HttpVersion.DEFAULT;
        }
    }

    @Override
    public void startTls(
            final SSLContext sslContext,
            final SSLBufferManagement sslBufferManagement,
            final SSLSessionInitializer initializer,
            final SSLSessionVerifier verifier) throws UnsupportedOperationException {
        if (log.isDebugEnabled()) {
            log.debug(getId() + ": start TLS");
        }
        ioSession.startTls(sslContext, sslBufferManagement, initializer, verifier);
    }

    @Override
    public TlsDetails getTlsDetails() {
        return ioSession.getTlsDetails();
    }

    @Override
    public SSLSession getSSLSession() {
        final TlsDetails tlsDetails = ioSession.getTlsDetails();
        return tlsDetails != null ? tlsDetails.getSSLSession() : null;
    }

    public void submitPriorityCommand(final Command command) {
        if (log.isDebugEnabled()) {
            log.debug(getId() + ": priority command " + command);
        }
        ioSession.addFirst(command);
    }

    public void submitCommand(final Command command) {
        if (log.isDebugEnabled()) {
            log.debug(getId() + ": command " + command);
        }
        ioSession.addLast(command);
    }

}
