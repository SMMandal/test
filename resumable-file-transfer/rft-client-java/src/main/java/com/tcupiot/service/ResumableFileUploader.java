package com.tcupiot.service;

import com.tcupiot.beans.FileInfo;
import com.tcupiot.beans.UploadConfig;
import com.tcupiot.exception.ServerException;
import com.tcupiot.exception.ValidationException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketConnector;
import io.rsocket.metadata.CompositeMetadataCodec;
import io.rsocket.metadata.TaggingMetadataCodec;
import io.rsocket.metadata.WellKnownMimeType;
import io.rsocket.transport.netty.client.WebsocketClientTransport;
import io.rsocket.util.DefaultPayload;
import reactor.netty.ConnectionObserver;
import reactor.netty.tcp.TcpClient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Level;

/**
 * Upload a large file in resumable method using RSocket transport
 *
 * @author Sumanta Ghosh
 * @since 1.0
 */
public class ResumableFileUploader {

    public String upload(String key, UploadConfig config, FileInfo fileInfo) throws IOException, ValidationException, ServerException {

        config.validate();
        fileInfo.validate();
        FileReader.init(fileInfo);
        return createConnection(key, config, fileInfo);

    }

    private static final String SERVER_URN = "upload.";
    private String createConnection(String key, UploadConfig config, FileInfo fileInfo) throws ConnectException, ServerException {

        var metadata = ByteBufAllocator.DEFAULT.compositeBuffer();
        var routingMetadata = TaggingMetadataCodec.createRoutingMetadata(ByteBufAllocator.DEFAULT,
                new ArrayList<>(Collections.singleton(SERVER_URN + key)));
        CompositeMetadataCodec.encodeAndAddMetadata(
                metadata,
                ByteBufAllocator.DEFAULT,
                WellKnownMimeType.MESSAGE_RSOCKET_ROUTING,
                routingMetadata.getContent()
        );

        RSocket rSocket;
        WebsocketClientTransport transport = WebsocketClientTransport.create(URI.create(config.getServerUrl()))
                .header("x-dls-key", key)
                .header("x-api-key", key);

//        transport.webSocketSpec(cfg -> cfg.handlePing(true));

        try {
            rSocket = RSocketConnector.create()
                    .acceptor(SocketAcceptor.forRequestStream(skipBlocks -> FileReader.read(config, fileInfo, skipBlocks)))
                    .metadataMimeType(WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA.getString())
                    .dataMimeType(WellKnownMimeType.APPLICATION_OCTET_STREAM.getString())
                    .connect(transport)
                    .log("", Level.ALL)
                    .block();
        } catch (Exception e) {
            throw new ConnectException(e.getLocalizedMessage());
        }
        try {
            assert rSocket != null;
            Payload payload = rSocket
                    .requestResponse(DefaultPayload.create(setupPayload(fileInfo, config), metadata))
                    .block();

            return payload.getDataUtf8() ;


        } catch (Exception e) {
            throw new ServerException(e.getLocalizedMessage());
        }


    }


    public static String setupPayloadString(FileInfo info, UploadConfig config) {
        return "fileName\n" + info.getFileName() + "\r\n" +
                "fileSize\n" + nullToEmpty(info.getFileSize()) + "\r\n" +
                "savepoint\n" + nullToEmpty(info.getSavepoint()) + "\r\n" +
                "directory\n" + nullToEmpty(info.getDirectory()) + "\r\n" +
                "metadata\n" + nullToEmpty(info.getMetadata()) + "\r\n" +
                "comment\n" + nullToEmpty(info.getComment()) + "\r\n" +
                "blockSize\n" + config.getBlockSize() + "\r\n" +
                "resume\n" + nullToEmpty(config.getResume()) + "\r\n" +
                "overwrite\n" + nullToEmpty(config.getOverwrite());
    }

    public static ByteBuf setupPayload(FileInfo info, UploadConfig config) {
        String payload = "fileName\n" + info.getFileName() + "\r\n" +
                "fileSize\n" + nullToEmpty(info.getFileSize()) + "\r\n" +
                "savepoint\n" + nullToEmpty(info.getSavepoint()) + "\r\n" +
                "directory\n" + nullToEmpty(info.getDirectory()) + "\r\n" +
                "metadata\n" + nullToEmpty(info.getMetadata()) + "\r\n" +
                "comment\n" + nullToEmpty(info.getComment()) + "\r\n" +
                "blockSize\n" + config.getBlockSize() + "\r\n" +
                "resume\n" + nullToEmpty(config.getResume()) + "\r\n" +
                "overwrite\n" + nullToEmpty(config.getOverwrite());
        return ByteBufAllocator.DEFAULT.buffer().writeBytes(payload.getBytes());
    }

    private static String nullToEmpty(Object val) {
        if (val == null) return "";
        else return val.toString();
    }
}


