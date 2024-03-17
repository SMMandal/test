package dls;

import dls.service.DfsSyncService;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.security.UserGroupInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

@org.springframework.context.annotation.Configuration
public class HDFSConfig {


    @Value("${spring.hadoop.security.authMethod}") private String authMethod;
    @Value("${spring.hadoop.security.userPrincipal}") private String hadoopUser;
    @Value("${spring.hadoop.security.userKeytab}") private String keyPath;
    @Bean
    public Configuration getConfiguration (@Value("${spring.hadoop.fsUri}")
                                           String hdfsUri) throws IOException {

        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", hdfsUri);
        conf.setBoolean("dfs.support.append", true);
        conf.set("hadoop.security.authentication", authMethod);

        UserGroupInformation.setConfiguration(conf);
        UserGroupInformation.loginUserFromKeytab(hadoopUser,keyPath);

        return conf;
    }

    @Bean
    public FileSystem getFileSystem (@Autowired Configuration configuration) throws IOException {
        return FileSystem.get(configuration);
    }


    @Bean
    @ConditionalOnProperty(value = "dls.enable.hdfs", matchIfMissing = true, havingValue = "true")
    public DfsSyncService schedule() {
        return new DfsSyncService();
    }


}
