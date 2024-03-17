package dls.util;

public interface Constants {
	interface UserMsg {
		public static final String INTERNAL_SERVER_ERROR = "An error occurred";
		
		public static final String CONTAINER_CREATED = "Container created successfully";
		public static final String CONTAINER_EXISTS = "Container already exists";
		public static final String CONTAINER_NOT_EXIST = "Container does not exist";
		
		public static final String BLOB_UPLOADED = "Blob uploaded successfully";
		public static final String BLOB_NOT_EXIST = "Blob does not exist";
		public static final String BLOB_DELETED = "Blob deleted successfully";
		public static final String BLOB_APPENDED = "Blob successfully appended";
	}
}
