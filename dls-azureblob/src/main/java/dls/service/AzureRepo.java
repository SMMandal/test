package dls.service;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.multipart.MultipartFile;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.blob.specialized.AppendBlobClient;

import dls.util.Constants;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class AzureRepo {
	 @Autowired
	 private Environment env;
	 
	 static String connectStr;
	 static BlobServiceClient blobServiceClient;
	 String containerName;
	 
	 @Autowired
	 public void setBlobServiceClient() {
		 try {
		 // Retrieve the connection string from properties file
		 connectStr = env.getProperty("dls.azure.Storage.ConnectStr");
 		
		 // Create a BlobServiceClient object using a connection string
		 blobServiceClient = new BlobServiceClientBuilder().connectionString(connectStr).buildClient();
		 
		 // Create default DLS blob container
		 containerName = env.getProperty("dls.azure.Storage.DefaultContainer");
		 blobServiceClient.createBlobContainer(containerName);
			} catch (BlobStorageException ex) {
				log.error(Constants.UserMsg.CONTAINER_EXISTS);
			} catch (Exception ex) {
				log.error(Constants.UserMsg.INTERNAL_SERVER_ERROR + " : " + ex.toString());
			}
	}
	 
	public boolean uploadBlob(String dirName, MultipartFile file) {
		try {
			// Get container client object by container name
			BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(containerName);
			if (!blobContainerClient.exists()) {
				log.error(Constants.UserMsg.CONTAINER_NOT_EXIST);
				return false;
			}

			// Get a reference to a blob
			BlobClient blobClient;
			if (dirName == null) {
				blobClient = blobContainerClient.getBlobClient(file.getOriginalFilename());
			} else {
				blobClient = blobContainerClient.getBlobClient(dirName + '/' + file.getOriginalFilename());
			}

			// Upload the blob
			blobClient.upload(file.getInputStream(), file.getSize(), true);
			log.info(Constants.UserMsg.BLOB_UPLOADED);
			return true;
		} catch (Exception ex) {
			log.error(Constants.UserMsg.INTERNAL_SERVER_ERROR + " : " + ex.toString());
			return false;
		}
	}
	
	public String downloadBlobByURL(String blobPath) {
		try {
			//Get container client object by container name
			BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(containerName);
			if(!blobContainerClient.exists()) {
				log.error(Constants.UserMsg.CONTAINER_NOT_EXIST);
				return null;
			}
				
			// Get a reference to a blob
	    	BlobClient blobClient = blobContainerClient.getBlobClient(blobPath);
	    		
	    	if(!blobClient.exists()) {
	    		log.error(Constants.UserMsg.BLOB_NOT_EXIST);
				return null;
			}
			
	    	 BlobSasPermission blobSasPermission = new BlobSasPermission().setReadPermission(true);                                                                                           
	         OffsetDateTime expiryTime = OffsetDateTime.now().plusDays(1);   
	         BlobServiceSasSignatureValues values = new BlobServiceSasSignatureValues(expiryTime, blobSasPermission)  
	                         .setStartTime(OffsetDateTime.now()); 
	         String sasBlobToken = blobClient.generateSas(values);

	    	return blobClient.getBlobUrl() + "?" + sasBlobToken;
		} catch (Exception ex) {
			log.error(Constants.UserMsg.INTERNAL_SERVER_ERROR + " : " + ex.toString());
			return null;
		}
	}
	
	public boolean deleteBlob(String blobPath) {
		try {
			//Get container client object by container name
			BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(containerName);
			if(!blobContainerClient.exists()) {
				log.error(Constants.UserMsg.CONTAINER_NOT_EXIST);
				return false;
			}
			
			// Get a reference to a blob
	    	BlobClient blobClient = blobContainerClient.getBlobClient(blobPath);
			Boolean blobExist = blobClient.deleteIfExists();
		    
			if(blobExist) {
				log.info(Constants.UserMsg.BLOB_DELETED);
				return true;
			} else {
				log.error(Constants.UserMsg.BLOB_NOT_EXIST);
			}
		} catch (Exception ex) {
			log.error(ex.toString());
		}
		return false;
	}
	
	public boolean appendBlob(String fsPath, MultipartFile file) {
		try {
			//Get container client object by container name
			BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(containerName);
			if(!blobContainerClient.exists()) {
				log.error(Constants.UserMsg.CONTAINER_NOT_EXIST);
				return false;
			}
			
			//1. Create a temporary AppendBlob
			BlobClient newBlobClient = blobContainerClient.getBlobClient("tempappendblob");
			AppendBlobClient appendBlobClient = newBlobClient.getAppendBlobClient();
			appendBlobClient.deleteIfExists();
			appendBlobClient.createIfNotExists();
			
			//2. Copy old contents of BlockBlob into AppendBlob
			BlobClient blockBlobClient = blobContainerClient.getBlobClient(fsPath);
	    	if(!blockBlobClient.exists()) {
	    		log.error(Constants.UserMsg.BLOB_NOT_EXIST);
				return false;
			}
	    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    	blockBlobClient.download(baos);
	    	byte[] con = baos.toByteArray();
	    	InputStream targetStream = new ByteArrayInputStream(con);
	    	long len = con.length;
	    	appendBlobClient.appendBlock(targetStream, len);
	    	
			//3. Copy append contents of BlockBlob into AppendBlob
	        byte[] content = file.getBytes();
	    	InputStream inputStream =  new BufferedInputStream(file.getInputStream());
	    	int contentlen = content.length;
	    	appendBlobClient.appendBlock(inputStream, contentlen);
	    	
			//4. Delete BlockBlob
	    	blockBlobClient.deleteIfExists();
			
			//5. Copy contents of Appendblob into new Blockblob
	     	//Generate Blob SAS token
	    	BlobSasPermission blobSasPermission = new BlobSasPermission().setReadPermission(true);                                                                                           
	        OffsetDateTime expiryTime = OffsetDateTime.now().plusDays(1);   
	        BlobServiceSasSignatureValues values = new BlobServiceSasSignatureValues(expiryTime, blobSasPermission).setStartTime(OffsetDateTime.now()); 
	        String sasBlobToken = blockBlobClient.generateSas(values);
	        //Copy blob from source to destination
	        BlobClient newBlockBlobClient = blobContainerClient.getBlobClient(fsPath);
	        newBlockBlobClient.copyFromUrl(appendBlobClient.getBlobUrl() + "?" + sasBlobToken);
	        
	        //6. Delete temporary AppendBlob
	        appendBlobClient.deleteIfExists();
	 
	    	log.info(Constants.UserMsg.BLOB_APPENDED);
			return true;
		} catch (Exception ex) {
			log.error(ex.toString());
			return false;
		}
	}
	
	public boolean copyBlob(String srcPath, String destPath) {
		try {
			//Get container client object by container name
			BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(containerName);
			if(!blobContainerClient.exists()) {
				log.error(Constants.UserMsg.CONTAINER_NOT_EXIST);
				return false;
			}
			
			//Get Blob reference
	    	BlobClient srcBlob = blobContainerClient.getBlobClient(srcPath);
	    	BlobClient destBlob = blobContainerClient.getBlobClient(destPath);
	    	
	    	//Generate Blob SAS token
	    	BlobSasPermission blobSasPermission = new BlobSasPermission().setReadPermission(true);                                                                                           
	        OffsetDateTime expiryTime = OffsetDateTime.now().plusDays(1);   
	        BlobServiceSasSignatureValues values = new BlobServiceSasSignatureValues(expiryTime, blobSasPermission).setStartTime(OffsetDateTime.now()); 
	        String sasBlobToken = srcBlob.generateSas(values);

	        //Copy blob from source to destination
	    	destBlob.copyFromUrl(srcBlob.getBlobUrl() + "?" + sasBlobToken);
	    	return true;
		} catch (Exception ex) {
			log.error(ex.toString());
		}
		return false;
	}
}
