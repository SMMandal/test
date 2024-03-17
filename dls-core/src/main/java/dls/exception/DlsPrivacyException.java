package dls.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class DlsPrivacyException extends Exception {

	private static final long serialVersionUID = 1L;

}
