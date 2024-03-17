package dls.exception;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.TypeMismatchException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public @Data class DlsValidationException extends RuntimeException {

	public DlsValidationException(List<ObjectError> allErrors) {

		Map<String, String> messages = Maps.newHashMap();

		if(null != allErrors)
			allErrors.forEach(er -> {
//				messages.add(er.getDefaultMessage());
				String fieldName = Optional.ofNullable (((FieldError) er).getRejectedValue()).orElse("").toString() ;
				String errorMessage =  er.getDefaultMessage();
				if(er.contains(TypeMismatchException.class)) {
					errorMessage =  " Invalid '"  + ((FieldError) er).getField() + "' value";
				}

				messages.put(fieldName, errorMessage);
			});

		message = Joiner.on("; ").withKeyValueSeparator(":").join(messages);
	}



	private String message;
	private static final long serialVersionUID = 1L;

}
