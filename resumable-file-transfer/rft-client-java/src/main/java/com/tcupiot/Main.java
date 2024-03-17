package com.tcupiot;

import com.tcupiot.beans.FileInfo;
import com.tcupiot.beans.UploadConfig;
import com.tcupiot.exception.ServerException;
import com.tcupiot.exception.ValidationException;
import com.tcupiot.service.ResumableFileUploader;
import org.apache.commons.cli.*;

import java.io.*;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.Map;
import java.util.Properties;

public class Main {
    private static final Console console = System.console();
    public static final String RFTC_CONFIG = "rftc.config";
    public static final String MESSAGE_SEPARATOR = "\r\t\t\t\t\t\t";

    public static void main(String[] args) {

        Options options = new Options();

        options.addOption(
                Option.builder("i").longOpt("verbose")
                        .argName("verbose")
                        .hasArg(false)
                        .required(false)
                        .desc("Print payload").build()
        );

        options.addOption(
                Option.builder("d").longOpt("download")
                        .argName("download")
                        .hasArg(false)
                        .required(false)
                        .desc("Download a file").build()
        );

        options.addOption(
                Option.builder("u").longOpt("upload")
                        .argName("upload")
                        .hasArg(false)
                        .required(false)
                        .desc("Upload a file").build()
        );


        options.addOption(
                Option.builder("uf").longOpt("upload-file")
                        .argName("upload-file")
                        .hasArg(true)
                        .required(false)
                        .desc("File to upload").build()
        );

        options.addOption(
                Option.builder("ufn").longOpt("upload-filename")
                        .argName("upload-filename")
                        .hasArg(true)
                        .required(false)
                        .desc("User provided filename to be used (rename)").build()
        );

        options.addOption(
                Option.builder("dl").longOpt("download-location")
                        .argName("download-location")
                        .hasArg(true)
                        .required(false)
                        .desc("Downloaded file saving directory in client system").build()
        );

        options.addOption(
                Option.builder("k").longOpt("key")
                        .argName("key")
                        .hasArg(true)
                        .required(false)
                        .desc("API Key").build()
        );

        options.addOption(
                Option.builder("s").longOpt("server")
                        .argName("server")
                        .hasArg(true)
                        .required(false)
                        .desc("Server URL, e.g - https://in.tcupiot.tcsapps.com/rft").build()
        );


        options.addOption(
                Option.builder("b").longOpt("blockSize")
                        .argName("blockSize")
                        .hasArg(true)
                        .type(Integer.class)
            .required(false)
                .desc("Block size of the file (defaults to 1024 B)").build()
    );

        options.addOption(
                Option.builder("r").longOpt("resume")
                        .argName("resume")
                        .hasArg(false)
                        .required(false)
                        .desc("Resumes from last uploaded part").build()
        );

        options.addOption(
                Option.builder("o").longOpt("overwrite")
                        .argName("overwrite")
                        .hasArg(false)
                        .required(false)
                        .desc("Overwrites if any part is already sent to server").build()
        );

        options.addOption(
                Option.builder("um").longOpt("upload-metadata")
                        .argName("upload-metadata")
                        .hasArg(true)
                        .required(false)
                        .desc("Metadata of the uploaded file in \"key=value\" format").build()
        );

        options.addOption(
                Option.builder("mf").longOpt("metadata-file")
                        .argName("metadata-file")
                        .hasArg(false)
                        .required(false)
                        .desc("Fetch metadata from rftc_file_metadata file. Ignored with -m flag.").build()
        );

        options.addOption(
                Option.builder("ud").longOpt("upload-directory")
                        .argName("upload-directory")
                        .hasArg(true)
                        .required(false)
                        .desc("Directory of the uploaded file").build()
        );

        options.addOption(
                Option.builder("v").longOpt("savepoint")
                        .argName("savepoint")
                        .hasArg(true)
                        .required(false)
                        .desc("Savepoint of the uploaded file").build()
        );


        options.addOption(
                Option.builder("uc").longOpt("upload-comment")
                        .argName("upload-comment")
                        .hasArg(true)
                        .required(false)
                        .desc("Comment of the uploaded file").build()
        );

        CommandLineParser parser  = new DefaultParser();
        HelpFormatter helper = new HelpFormatter();

        Properties prop = new Properties();
        try {
            prop.loadFromXML(new FileInputStream(RFTC_CONFIG));
            console.printf("%s", MESSAGE_SEPARATOR + "-> Loading config");
        } catch (IOException e) {
//            console.printf("%s", "\r\t\t\t\t\t\t-> Configuring ! ");
        }



        FileInfo fileInfo = new FileInfo();
        UploadConfig config = new UploadConfig();
        String key = "";
        String filePath = "";
        String fileName = "";
        try {
            CommandLine cmd = parser.parse(options, args);
            if(cmd.hasOption("r")) {
                config.setResume(true);
            }

            if(cmd.hasOption("o")) {
                config.setOverwrite(true);
            }

            if (cmd.hasOption("b")) {
                Integer blocksize = Integer.parseInt(cmd.getOptionValue("blockSize"));
                prop.put("blockSize", blocksize.toString());
                config.setBlockSize(blocksize);
            } else if(null != prop.get("blockSize")){
                config.setBlockSize(Integer.parseInt(prop.get("blockSize").toString()));
            }

            if (cmd.hasOption("s")) {
                String url = cmd.getOptionValue("server");
                prop.put("server", url);
                config.setServerUrl(url);
            } else if(null != prop.getProperty("server")){
                config.setServerUrl(prop.getProperty("server"));
            } else {
                System.out.println(MESSAGE_SEPARATOR + "Usage : rft <file> -s <server_url> -k <apikey> [OPTIONS]");
                System.exit(0);
            }

            if (cmd.hasOption("um")) {
                fileInfo.setMetadata(cmd.getOptionValue("upload-metadata"));
            }

            else if (cmd.hasOption("mf")) {
                try {
                    Properties metaP = new Properties();
                    metaP.load(new FileInputStream("rftc_file_metadata"));
                    StringBuilder kvString = new StringBuilder();
                    for(Map.Entry<?, ?> e : metaP.entrySet()) {
                        if(kvString.length() > 0) kvString.append(",");
                        kvString.append(e.getKey()).append("=").append(e.getValue());
                    };
                    fileInfo.setMetadata(kvString.toString());
                } catch (FileNotFoundException e) {
                    System.err.println(MESSAGE_SEPARATOR + "'rftc_file_metadata' file not found! Remove '-mf' flag.");
                    System.exit(0);
                } catch (IOException e) {
                    System.err.println(MESSAGE_SEPARATOR + "Content of 'rftc_file_metadata' file should be in 'key=value' format! Otherwise, remove '-mf' flag.");
                    System.exit(0);
                }

            }

            if (cmd.hasOption("d")) {
                System.err.println(MESSAGE_SEPARATOR + "File download feature is not yet implemented");
                System.exit(0);
            }


            if (cmd.hasOption("v")) {
                fileInfo.setSavepoint(cmd.getOptionValue("savepoint"));
            }


            if (cmd.hasOption("uf")) {
                filePath = cmd.getOptionValue("upload-file");
            }

            if (cmd.hasOption("ufn")) {
                fileName = cmd.getOptionValue("upload-filename");
            }

            if (cmd.hasOption("uc")) {
                fileInfo.setComment(cmd.getOptionValue("upload-comment"));
            }

            if (cmd.hasOption("ud")) {
                fileInfo.setDirectory(cmd.getOptionValue("upload-directory"));
//                prop.put("upload-directory", cmd.getOptionValue("upload-directory"));
            }
            /*else {
                fileInfo.setDirectory(prop.getProperty("upload-directory"));
            }*/

            if (cmd.hasOption("k")) {
                key = cmd.getOptionValue("key");
                prop.put("key", cmd.getOptionValue("key"));
            } else if(null != prop.getProperty("key")){
                key = prop.getProperty("key");
            } else {
                System.out.println(MESSAGE_SEPARATOR + "Usage : rft <file> -s <server_url> -k <apikey> [OPTIONS]");
                System.exit(0);
            }

            if (cmd.hasOption("i")) {
                fileInfo.setFilePath(filePath);
                fileInfo.setFileName(fileName);
                String payload = ResumableFileUploader.setupPayloadString(fileInfo,config);
                System.out.println();
                System.out.println("*** PAYLOAD ***");
                System.out.println(payload.replace("\r\n",",")
                        .replace("\n", ":\t")
                        .replace(',', '\n'));
                System.out.println("***************");
            }


        } catch (ParseException e) {
            System.out.println(e.getMessage());
            helper.printHelp("Usage:", options);
            System.exit(0);
        }



        try {
            fileInfo.setFilePath(filePath);
            fileInfo.setFileName(fileName);
            ResumableFileUploader client = new ResumableFileUploader();
            String size = client.upload(key, config, fileInfo);

            console.printf("%s", MESSAGE_SEPARATOR + "-> File URI : " + size );
            // important to take user input for main thread blocking
            System.in.read();
            try {

                FileOutputStream fos = new FileOutputStream(RFTC_CONFIG);
                prop.storeToXML(fos, "RFT client properties");
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        } catch (ValidationException | ServerException e) {
            console.printf("%s", MESSAGE_SEPARATOR + "ERROR: " + e.getMessage());
        } catch (ConnectException e) {
            console.printf("%s", MESSAGE_SEPARATOR + "ERROR: Can not connect server");
        } catch (IOException e) {
            console.printf("%s", MESSAGE_SEPARATOR + "ERROR: File can not be read");
        }
        console.printf("%s", "\r\n");
    }
}