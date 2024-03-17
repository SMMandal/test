package com.tcupiot.rftserver

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*


@Component
class DlsClient ( @Value("\${dls.url}") val dlsUrl : String, ) {


    val log: Logger = LoggerFactory.getLogger(DlsClient::class.java)
    private val X_API_KEY = "x-api-key"
    private val X_DLS_KEY = "x-dls-key"
    private val DELETED = "deleted"
    private val FILENAME = "filename"
    private val SAVEPOINT = "savepoint"
    private val FILE = "file"
    private val METADATA = "metadata"
    private val DIRECTORY = "directory"
    private val CATALOG_URL = "/catalog"
    private val FILE_URL = "/file/resumable"
    private val FILE_STATUS_URL = "/file/status"
    private val STORAGE_URL = "/admin/storage/type"
    private val PARAMETERS = "parameters"
    private val AND_STATUS_IS = "&status="
    private val COMMENT = "comment"

    val restTemplate: RestTemplate = RestTemplate()

    @Value("\${date.pattern}")
    private val datePattern = "dd MMM yyyy HH.mm.ss z"


    @Value("\${saveTo}")
    private val nfsPath: String? = null


    @Throws(Exception::class)
    fun updateFileStatusDls(key : String, fileUri : String, status : String) {
        val headers = HttpHeaders()
        headers[X_API_KEY] = key
        headers[X_DLS_KEY] = key
        val map: LinkedMultiValueMap<String, Any> = LinkedMultiValueMap()
        try {
            val requestEntity: HttpEntity<LinkedMultiValueMap<String, Any>> = HttpEntity(map, headers)
            val url: String = "$dlsUrl$FILE_STATUS_URL?file-uri=$fileUri$AND_STATUS_IS$status"
            log.debug("Calling DLS [{}]", url)
            val responseEntity = restTemplate.exchange(
                url, HttpMethod.PUT, requestEntity,
                String::class.java
            )
        } catch (ex: HttpClientErrorException) {
            throw ex
        } catch (ex: ResourceAccessException) {
            throw Exception("DLS is not running")
        }
    }

    @Throws(Exception::class)
    fun checkDlsFileStatus(key : String, filename : String, savepoint : String?, directory : String?, resume : Boolean): String {
        val headers = HttpHeaders()
        headers.accept = listOf(MediaType.TEXT_PLAIN)
        headers[X_API_KEY] = key
        headers[X_DLS_KEY] = key
        val entity = HttpEntity(PARAMETERS, headers)
        val builder = UriComponentsBuilder.fromHttpUrl("$dlsUrl$FILE_STATUS_URL/$filename${if(resume)"..resume" else ""}")
        if (!savepoint.isNullOrEmpty()) {
            builder.queryParam(SAVEPOINT, savepoint)
        }
        if (!directory.isNullOrEmpty()) {
            builder.queryParam(DIRECTORY, directory)
        }
        val url = builder.build(false).toUriString()
        log.debug("Calling DLS [{}]", url)
        return try {
            val responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String::class.java)
            val fsPath = responseEntity.body as String
            if(resume) fsPath.removeSuffix("..resume")
            else fsPath
        } catch (ex: HttpClientErrorException) {
            //            ex.printStackTrace();
            throw ex
        } catch (ex: ResourceAccessException) {
            throw Exception("DLS is not running")
        }
    }


    @Value("\${adminKey}")
    private val apiKey: String? = null

    @Throws(Exception::class)
    fun checkDlsStorage() {
        val headers = HttpHeaders()
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        headers[X_API_KEY] = apiKey
        val entity = HttpEntity(PARAMETERS, headers)
        val builder = UriComponentsBuilder.fromHttpUrl(dlsUrl + STORAGE_URL)
        val url = builder.build(false).toUriString()
        log.debug("Calling DLS [{}]", url)
        try {
            val responseEntity: ResponseEntity<Map<*, *>> =
                restTemplate.exchange(url, HttpMethod.GET, entity, Map::class.java)
            val map: Map<String,String> = responseEntity.body as Map<String, String>
            if (!map["type"].equals("NFS")) throw Exception("DLS is not running in NFS storage mode")
            if (!map["value"].equals(nfsPath)) throw Exception("NFS mount point is different in DLS")
        } catch (ex: HttpClientErrorException) {
//            ex.printStackTrace();
            throw ex
        } catch (ex: ResourceAccessException) {
            throw Exception("DLS is not running")
        }
    }

    @Throws(Exception::class)
    fun callDlsFileUpload(key : String, filename: String, savepoint : String?, directory : String?,
                          metadata : String?, comment : String?, fileSize : String?): String? {
        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        headers[X_API_KEY] = key
        headers[X_DLS_KEY] = key
        val map: LinkedMultiValueMap<String, Any> = LinkedMultiValueMap()
//        fileInfo.setMetadata(fileInfo.getMetadata())
        return try {
            var url = "$FILE_URL?filename=$filename"
            if (!savepoint.isNullOrEmpty()) {
                url = "$url&savepoint=$savepoint"
            }
            if (!directory.isNullOrEmpty()) {
                url = "$url&directory=$directory"
            }
            if (null != fileSize) {
                url = "$url&sizeInBytes=$fileSize"
            }
            log.debug(comment)
            log.debug(metadata)
            if (!comment.isNullOrEmpty()) {
                map.add(COMMENT, comment )
            }
            log.info(metadata)
            if (metadata != null) {
                map.add(METADATA, metadata)
            }
            val requestEntity: HttpEntity<LinkedMultiValueMap<String, Any>> = HttpEntity(map, headers)
            url = dlsUrl + url
            log.debug("Calling DLS $url")
            val responseEntity = restTemplate.exchange(
                url, HttpMethod.POST, requestEntity,
                String::class.java
            )
            responseEntity.body
        } catch (ex: HttpClientErrorException) {
            throw Exception(ex.responseBodyAsString + " ( status:" + ex.message + ")")
        } catch (ex: ResourceAccessException) {
            throw Exception("DLS is not running")
        }
    }




    @Throws(Exception::class)
    fun callDlsGetCatalog(key : String, filename: String, savepoint : String?, directory : String?): String {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers[X_API_KEY] = key
        headers[X_DLS_KEY] = key
        val map: LinkedMultiValueMap<String, Any> = LinkedMultiValueMap()
//        fileInfo.setMetadata(fileInfo.getMetadata())
        return try {
            var url = "$CATALOG_URL?filename=$filename"
            if (!savepoint.isNullOrEmpty()) {
                url = "$url&savepoint=$savepoint"
            }
            if (!directory.isNullOrEmpty()) {
                url = "$url&directory=$directory"
            }

            val requestEntity: HttpEntity<LinkedMultiValueMap<String, Any>> = HttpEntity(map, headers)
            url = dlsUrl + url
            log.debug("Calling DLS [{}]", url)

            val responseEntity = restTemplate.exchange(
                url, HttpMethod.GET, requestEntity,
                String::class.java
            )
            responseEntity.body ?: ""
        } catch (ex: HttpClientErrorException) {
            throw Exception(ex.responseBodyAsString + " ( status:" + ex.message + ")")
        } catch (ex: ResourceAccessException) {
            throw Exception("DLS is not running")
        }
    }

//    java.nio.file.Path createEmptyLocalFile(String fileName) {
//        try {
//            java.nio.file.Path path = FileSystems.getDefault().getPath(localDir, fileName);
//            return Files.createFile(path);
//        } catch (IOException ex) {
////            ex.printStackTrace();
//            log.error("Could not create local empty file to upload to DLS");
//            return null;
//        }
//    }

    //    java.nio.file.Path createEmptyLocalFile(String fileName) {
    //        try {
    //            java.nio.file.Path path = FileSystems.getDefault().getPath(localDir, fileName);
    //            return Files.createFile(path);
    //        } catch (IOException ex) {
    ////            ex.printStackTrace();
    //            log.error("Could not create local empty file to upload to DLS");
    //            return null;
    //        }
    //    }
    private fun getDate(): String? {
        val sdf = SimpleDateFormat(datePattern)
        return sdf.format(Date.from(Instant.now()))
    }
}