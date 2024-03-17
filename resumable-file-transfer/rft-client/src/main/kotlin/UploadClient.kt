import io.netty.buffer.ByteBufAllocator
import io.rsocket.Payload
import io.rsocket.RSocket
import io.rsocket.core.RSocketConnector
import io.rsocket.metadata.CompositeMetadataCodec
import io.rsocket.metadata.TaggingMetadataCodec
import io.rsocket.metadata.WellKnownMimeType
import io.rsocket.transport.netty.client.TcpClientTransport
import io.rsocket.transport.netty.client.WebsocketClientTransport
import io.rsocket.util.DefaultPayload
import reactor.core.publisher.Flux
import java.io.Console
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

data class FileInfo (
    var apiKey: String? = null,
    var dlsKey: String? = null,
    var fileName: String? = null,
    var directory: String? = null,
    var savepoint: String? = null,
    var metadata: String? = null,
    var comment: String? = null,
    var partNo: Long = 0,
    var blockSize : Int = 0,
    var resume : Boolean = true,
    var overwrite : Boolean = false,
    var fileSize: Long? = null) {
}

class UploadClient {

    @Throws(IOException::class)
    private fun readFile(filePath: String, fileInfo: FileInfo): Flux<Payload> {
        val splitId = AtomicLong(0)
        val p: Path = Paths.get(filePath)
        val channel: SeekableByteChannel = Files.newByteChannel(p, StandardOpenOption.READ)
        fileInfo.fileName = p.fileName.toString()
        println("file read ${fileInfo.fileName}")

        val metadata = ByteBufAllocator.DEFAULT.compositeBuffer()
        val routingMetadata = TaggingMetadataCodec.createRoutingMetadata(ByteBufAllocator.DEFAULT, listOf("upload"))
        CompositeMetadataCodec.encodeAndAddMetadata(
            metadata,
            ByteBufAllocator.DEFAULT,
            WellKnownMimeType.MESSAGE_RSOCKET_ROUTING,
            routingMetadata.content
        )



        try {
            fileInfo.fileSize = Files.size(p)
        } catch (e: IOException) {
            console.printf("%s", "\rError calculating size")
            throw e
        }
        var fileInfoJson = ""
//        try {
//            val objectMapper = ObjectMapper()
//            fileInfoJson = objectMapper.writeValueAsString(fileInfo)
//        } catch (ex: JsonProcessingException) {
//            ex.printStackTrace()
//        }
        console.printf("%s", "\rChecking file status..")
        return Flux.just(
            DefaultPayload.create(fileInfoJson, FILEINFO)
        )
            .concatWith(
                Flux. generate { sink ->
                    try {
                        if (responseInfo != null && responseInfo!!.partNo > 0) {
                            channel.position(responseInfo!!.partNo * responseInfo!!.blockSize)
                            splitId.set(responseInfo!!.partNo)
                            responseInfo = null
                        }
                        val dataBuffer: ByteBuffer = ByteBuffer.allocate(fileInfo.blockSize)
                        val longVal: ByteArray = (splitId.incrementAndGet().toString()).toByteArray()
                        val metaBuffer: ByteBuffer = ByteBuffer.wrap(longVal)
                        val read: Int = channel.read(dataBuffer)
                        dataBuffer.flip()
                        sink.next(DefaultPayload.create(dataBuffer))
//                        sink.next(DefaultPayload.create(dataBuffer, metaBuffer))
                        console.printf(
                            "%s",
                            ("\r" + fileInfo.blockSize * splitId.get()) + " bytes transferred"
                        )
                        if (read == -1) {
                            channel.close()
                            sink.complete()
                        }
                    } catch (t: Throwable) {
                        sink.error(t)
                    }
                }
                    .delaySubscription(Duration.ofSeconds(2))
            )
    }

    private val FILEINFO = "FILEINFO"

    private var responseInfo: FileInfo? = null
    private val console: Console = System.console()

    public fun start(serverUri: String, filePath: String, fileInfo: FileInfo) {
        try {

            console.printf("%s", "Initializing transfer")


            val metadata = ByteBufAllocator.DEFAULT.compositeBuffer()
            val routingMetadata = TaggingMetadataCodec.createRoutingMetadata(ByteBufAllocator.DEFAULT, listOf("upload"))
            CompositeMetadataCodec.encodeAndAddMetadata(
                metadata,
                ByteBufAllocator.DEFAULT,
                WellKnownMimeType.MESSAGE_RSOCKET_ROUTING,
                routingMetadata.content
            )

//            val rSocket = RSocketConnector.create()
//                .setupPayload(DefaultPayload.create( PayloadHelper().buildData( "t1"), metadata))
//                .acceptor(TMSCallAcceptor(helper, generator, delay))
//                .metadataMimeType(WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA.string)
//                .dataMimeType(WellKnownMimeType.APPLICATION_CBOR.string)
//                .connect(TcpClientTransport.create(host, port))
//                .block()


            val transport = TcpClientTransport.create("localhost", 8765)
//            val transport = WebsocketClientTransport.create(URI.create(serverUri))
            val client = RSocketConnector.create() //                            .resume(resume)
//                .setupPayload(DefaultPayload.create( PayloadHelper().buildData( "t1"), metadata))
                .metadataMimeType(WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA.string)
                .dataMimeType(WellKnownMimeType.APPLICATION_OCTET_STREAM.string)
                .connect(transport)
                .block()
            client
                ?.requestChannel(readFile(filePath, fileInfo))
                ?.switchOnFirst { signal, flux ->
                if (signal.hasValue()) {
                    try {
                        val json: String = signal.get()?.dataUtf8 ?: ""
                        //                            responseInfo = ObjectMapper().readValue(json, FileInfo::class.java)
                    } catch (e: IOException) {
                        System.err.println(e.message)
                    }
                }
                flux
            }?.map { obj: Payload -> obj.dataUtf8 }?.then()?.block()
            println("\rFile transfer is successful.")
        } catch (e: Exception) {
            e.printStackTrace()
            println("\rFile transfer failed - " + e.message)
        }
    }
}