package dls;

import dls.service.AzureSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureBlobConfig {
    @Bean
    @ConditionalOnProperty(value = "dls.enable.azureblob", matchIfMissing = true, havingValue = "true")
    public AzureSyncService schedule() {
        return new AzureSyncService();
    }
}
