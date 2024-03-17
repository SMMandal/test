package dls.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class DlsSecurityException extends Exception {

	private static final long serialVersionUID = 1L;

}
