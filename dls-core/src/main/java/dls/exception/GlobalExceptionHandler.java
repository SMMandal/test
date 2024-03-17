package dls.exception;

import com.diffplug.common.base.Errors;
import com.google.common.base.CaseFormat;
import com.google.common.collect.Sets;
import dls.bean.DlsResponse;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.internal.engine.path.PathImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import reactor.core.publisher.Flux;

import jakarta.validation.ConstraintViolationException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {


	public static final String DELETED = "deleted";
	public static final String LINKED = "linked";
	public static final String CREATED = "created";
	public static final String SHARED = "shared";
	public static final String UPDATED = "updated";
	public static final String ALREADY_EXISTS = "already exists";
	public static final String LOCKED = "locked";
	public static final String UNLOCKED = "unlocked";

    //private static final String log = null;
	@Autowired private Environment ev;
	@Value("${spring.servlet.multipart.max-file-size}") private String maxFileSize;
	
	@ExceptionHandler(DlsSecurityException.class)
    public ResponseEntity <String> handle(DlsSecurityException ex){		
		
		log.warn("Access denied for invalid API key");
		return ResponseEntity.status(UNAUTHORIZED).contentType(TEXT_PLAIN).body(ev.getProperty("not.authenticated")); 		
    }
	
	@ExceptionHandler(DlsPrivacyException.class)
    public ResponseEntity <String> handle(DlsPrivacyException ex){		
		
		log.warn("Access denied for invalid DLS key");
		return ResponseEntity.status(FORBIDDEN).contentType(TEXT_PLAIN).body(ev.getProperty("not.authorized")); 		
    }
	
	@ExceptionHandler(DlsNotFoundException.class)
    public ResponseEntity <String> handle(DlsNotFoundException ex){		
		
		return ResponseEntity.status(NOT_FOUND).contentType(TEXT_PLAIN).body(ev.getProperty("not.found")); 		 		
    }
    
	@ExceptionHandler(DlsBlobException.class)
    public ResponseEntity <String> handle(DlsBlobException ex){		
		return ResponseEntity.status(BAD_REQUEST).contentType(TEXT_PLAIN).body("An error occured while fetching Blob from Azure"); 		 		
    }
	
	@ExceptionHandler(DlsValidationException.class)
    public ResponseEntity <List<DlsResponse>> handle(DlsValidationException ex){

		log.error(ex.getMessage());
		String message = Optional.ofNullable(ev.getProperty(ex.getMessage())).orElse(ex.getMessage());

		if(message.contains("IllegalArgumentException")) {
			message = ev.getProperty("invalid.argument");
		}

		return ResponseEntity.status(BAD_REQUEST).contentType(APPLICATION_JSON)
				.body(List.of(DlsResponse.builder().messages(Set.of(message)).build()));
			
    }
	
	
	@ExceptionHandler(MultipartException.class)
    public ResponseEntity <String> handle(MultipartException ex){
		
		log.error(ex.getMessage());
		String message = MessageFormat.format(Objects.requireNonNull(ev.getProperty("upload.filesize.exceeds")), maxFileSize);
		return ResponseEntity.status(BAD_REQUEST).contentType(TEXT_PLAIN).body(message); 
    }
		
	@ExceptionHandler(ConstraintViolationException.class) 
	public ResponseEntity<List <DlsResponse>> handle(ConstraintViolationException ex) {
		log.error(ex.getMessage());
//		Multimap<String, String> messageMap = ArrayListMultimap.create();
		List <DlsResponse> errors = Flux.fromIterable(ex.getConstraintViolations())
//				.stream()
				.map(cv -> DlsResponse.builder()
						.key(CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN,  ((PathImpl)cv.getPropertyPath()).getLeafNode().asString()))
						.value(Errors.suppress().getWithDefault(() -> cv.getInvalidValue().toString(), ""))
						.messages(Set.of(cv.getMessage()))
				.build())
				.groupBy(r -> r.getKey().concat(r.getValue()))
				.flatMap(f ->
					f.reduce((r1,r2) -> {
						Set <String> messages = Sets.newHashSet(r1.getMessages());
						messages.addAll(r2.getMessages());
						r1.setMessages(messages);
						return DlsResponse.builder()
							.value(r1.getValue())
							.key(r1.getKey())
							.code(r1.getCode())
							.messages(r1.getMessages())
							.build();})

				)
				.doOnError(e -> log.error(e.getMessage()))
				.collectList().block()
				;

		return ResponseEntity.badRequest().contentType(APPLICATION_JSON).body(errors);
	}
	
	
	@ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity <String> handle(DataIntegrityViolationException ex){

//		ex.printStackTrace();
		String message = Optional.ofNullable(ev.getProperty(Objects.requireNonNull(ex.getMessage())))
				.orElse(Optional.ofNullable(ex.getMessage()).orElse(ev.getProperty("already.exists")));
		if(message.contains("ConstraintViolationException")) {
			message = ev.getProperty("already.exists");
		}
		log.error(message);
		return ResponseEntity.status(CONFLICT).contentType(TEXT_PLAIN).body(message);
			
    }


	
	
	@ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity <String> handle(IllegalArgumentException ex){
		log.error(ex.getMessage());
		//ex.printStackTrace();
		String message = ev.getProperty("invalid.argument");
		return ResponseEntity.status(BAD_REQUEST).contentType(TEXT_PLAIN).body(message);
			
    }
	
	@ExceptionHandler(Exception.class)
    public ResponseEntity <String> handle(Exception ex){
		log.error("{} at {}",ex.getMessage(), ex.getStackTrace()[0].toString());
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(TEXT_PLAIN).body(ev.getProperty("internal.error"));
			
    }

//	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
																  HttpHeaders headers, HttpStatus status,
																  WebRequest request) {

		return ResponseEntity.status(BAD_REQUEST).contentType(TEXT_PLAIN).body(ev.getProperty("invalid.input"));
	}
}
