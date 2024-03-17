package dls.service;

import jakarta.persistence.AttributeConverter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
public class SecretConverterHelper implements AttributeConverter<String, String> {

 

    @Override
    public String convertToDatabaseColumn(String decryptedValue) {
        String encryptedValue = null;
        Base64.Encoder encrypter = Base64.getEncoder();
        if(decryptedValue != null)
            encryptedValue = new String(encrypter.encode(decryptedValue.getBytes(StandardCharsets.UTF_8)));
        return encryptedValue;
    }

 

    @Override
    public String convertToEntityAttribute(String encryptedValue) {
        String decryptedValue = null;
        Base64.Decoder decrypter = Base64.getDecoder();
        if(encryptedValue != null)
            decryptedValue = new String(decrypter.decode(encryptedValue), StandardCharsets.UTF_8);
        return decryptedValue;
    }
}
