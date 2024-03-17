package com.tcupiot.rftserver

import io.netty.util.ReferenceCountUtil
import io.rsocket.util.DefaultPayload
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.messaging.handler.annotation.*
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.stereotype.Controller
import reactor.kotlin.core.publisher.doOnError
import java.io.File
import java.io.IOException
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

@Controller
class UploadController ( @Value("\${saveTo}") val stageDirectory : String, val dlsClient: DlsClient) {

    private val UPLOADED: String = "UPLOADED"
    private val PAUSED: String = "PAUSED"
    private val RESUMED: String = "RESUMED"
    val log: Logger = LoggerFactory.getLogger("UploadController")

//    @MessageMapping("filesize.{apikey}")
//    fun getFileSize(@DestinationVariable apikey: String, @Payload data: String) : Flux <io.rsocket.Payload?> {
//        println("inside getFileSize")
//        val map = processFileInfo(apikey, data)
//        val fsPath = dlsClient.checkDlsFileStatus(apikey,
//            map["fileName"] ?: throw RuntimeException("File name is missing"),
////            map["metadata"],
//            map["savepoint"],
//            map["directory"],
//            false
//        )
//
//        val p: Path = Paths.get(fsPath)
//        val channel = Files.newByteChannel(p, StandardOpenOption.READ)
//        val partNo: Int = 0
//        val splitId = AtomicLong(partNo.toLong())
//        val blockSize = 1024
//        return Flux.generate { sink: SynchronousSink<io.rsocket.Payload?> ->
//            try {
//                if (splitId.get() > 0) {
//
//                    channel.position(splitId.get() as Long * blockSize)
//                    //                            splitId.set(splitId.get());
//                }
//                //                        System.out.println("new split id. " + splitId);
//                val dataBuffer = ByteBuffer.allocate(blockSize)
//                val longVal: ByteArray = (splitId.incrementAndGet().toString() + "").toByteArray()
//                val metaBuffer = ByteBuffer.wrap(longVal)
//                val read = channel.read(dataBuffer)
//                dataBuffer.flip()
//                sink.next(DefaultPayload.create(dataBuffer, metaBuffer))
//                //                        console.printf("%s", "\r" + config.getBlockSize() * splitId.get() + " bytes transferred");
//
//                if (read == -1 || read == 0) {
//                    channel.close()
//                    sink.complete()
//                }
//            } catch (t: Throwable) {
//                sink.error(t)
//            }
//        }
//
//    }



    @MessageMapping("upload.{apikey}")
//    @MessageMapping("upload")
    fun uploadFile(@DestinationVariable apikey : String,
                   @Headers allHeaders : Map<String, Any>, @Payload data : String, requester: RSocketRequester) : String {

        val fsPath : String

//        requester.rsocket().
//        println("dls key : $allHeaders")
        try {
            val map = processFileInfo(apikey, data)
            fsPath = dlsClient.checkDlsFileStatus(apikey,
                map["fileName"] ?: throw RuntimeException("File name is missing"),
//            map["metadata"],
                map["savepoint"],
                map["directory"],
                map["resume"]?.let { it.toBoolean() } ?: false
            )

            val partNo = checkFile(
                apikey, fsPath, map["resume"].toBoolean(), map["overwrite"].toBoolean(),
                map["blockSize"]?.toInt() ?: 1024,
                map["fileName"] ?: throw RuntimeException("File name is missing"),
                map["savepoint"],
                map["directory"],
                map["metadata"],
                map["comment"],
                map["fileSize"]
            )

            val flux = requester.rsocket()?.requestStream(DefaultPayload.create("$partNo"))?.limitRate(2)
//        flux?.let {  writeFile(apikey, fsPath, it) }

//        log.info("file writing started...")
            val f = File("$stageDirectory/$fsPath.part")
            val channel: SeekableByteChannel =
                Files.newByteChannel(f.toPath(), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            flux
//            ?.doOnNext { println("write file called") }
                ?.doOnError {
                    log.info("{} [Interrupted]", fsPath)
                    dlsClient.updateFileStatusDls(apikey, fsPath, PAUSED);
                }
                ?.doOnCancel { log.info("{} [Cancelled]", fsPath) }
                ?.doOnComplete {
                    try {
                        channel.close()
                        dlsClient.updateFileStatusDls(apikey, fsPath, UPLOADED)
                        f.renameTo(File("$stageDirectory/$fsPath"))

                        log.info("{} [Transferred]", fsPath)
                    } catch (e: IOException) {
                        log.error(e.message)
                    }
                }
                ?.subscribe { payload ->

                    try {
                        channel.write(payload.data)
//                    log.info("written block");
                        ReferenceCountUtil.safeRelease(payload)
                    } catch (e: IOException) {
//                    return DefaultPayload.create("err " + e.message)
                        log.error(e.localizedMessage)
                    } catch (e: UnsupportedOperationException) {
                        log.error(e.localizedMessage)
//                    return DefaultPayload.create("err " + e.message)
                    }
                }
        } catch (e : Throwable) {
            log.error(e.message)
            throw e
        }
//        while(Files.notExists(Path.of(stageDirectory, fsPath)))
//            sleep(100)
        return fsPath

    }

    fun processFileInfo(apikey : String, str : String) : MutableMap<String,String?> {
        val map = mutableMapOf<String, String?>()
        log.debug("received payload : $str")
        try {
            str.split("\r\n")
                .map {
                    val s = it.split('\n')
                    var v: String? = s[1]
                    if (v != null) {
                        if (v.isEmpty()) v = null
                    }
                    map.put(s[0], v)
                }
        } catch (e : Exception) {
            log.error("Error parsing the file info payload : ${e.message}")
            throw Exception("Error parsing the file info payload")
        }
        return map

    }



    private fun checkFile(key : String, fsPath : String, resume: Boolean, overwrite : Boolean, blockSize : Int,
                          filename : String, savepoint : String?, directory : String?, metadata : String?, comment : String?, fileSize : String?) : Long {

        val fileCheck = File("$stageDirectory/$fsPath")
        if (fileCheck.exists() && !overwrite) {
            throw RuntimeException("File already uploaded")
        }
        val f = File("$stageDirectory/$fsPath.part")
        var partNo: Long = 0
        if (f.exists() && resume) {
            partNo = f.length() / blockSize
//            fileInfo.setPartNo(partNo)
            dlsClient.updateFileStatusDls(key, fsPath, RESUMED)
            log.info("{} [Resumed]", fsPath)
        } else {
            if (f.exists()) {
                f.delete()
            }
            Files.createDirectories(Paths.get(f.parent));
            val created: Boolean = f.createNewFile()
            val fsUri = dlsClient.callDlsFileUpload(key, filename, savepoint, directory, metadata, comment, fileSize)
            log.info("{} [Initiated]", fsUri)
        }
        return partNo

    }

//    private fun writeFile(key : String, fsPath : String, flux: Flux<io.rsocket.Payload>) {
//
//
//    }




}