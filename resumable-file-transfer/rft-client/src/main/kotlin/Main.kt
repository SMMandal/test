import org.apache.commons.cli.*;
import kotlin.system.exitProcess

fun main(args: Array<String>) {

    val options = Options()
    val filePath : String

    if(args.isNotEmpty()) {
        filePath = args[0];

    } else {
        println("Usage : rft <file> --key=<apikey> [OPTIONS]");
        exitProcess(0);
    }

    options.addOption(
        Option.builder("k").longOpt("key")
            .argName("key")
            .hasArg(true)
            .required(true)
            .desc("API Key").build()
    )

    options.addOption(
        Option.builder("u").longOpt("url")
            .argName("url")
            .hasArg(true)
            .required(true)
            .desc("Server URL").build()
    )


    options.addOption(
        Option.builder("b").longOpt("blockSize")
            .argName("blockSize")
            .hasArg(true)
            .type(Int::class.java)
            .required(false)
            .desc("Block size of the file (defaults to 1024 B)").build()
    )

    options.addOption(
        Option.builder("r").longOpt("resume")
            .argName("resume")
            .hasArg(false)
            .required(false)
            .desc("Resumes from last uploaded part").build()
    )

    options.addOption(
        Option.builder("o").longOpt("overwrite")
            .argName("overwrite")
            .hasArg(false)
            .required(false)
            .desc("Overwrites if any part is already sent to server").build()
    )

    options.addOption(
        Option.builder("m").longOpt("metadata")
            .argName("metadata")
            .hasArg(true)
            .required(false)
            .desc("Metadata of the uploaded file in \"key=value\" format").build()
    )

    options.addOption(
        Option.builder("d").longOpt("directory")
            .argName("directory")
            .hasArg(true)
            .required(false)
            .desc("Directory of the uploaded file").build()
    )

    options.addOption(
        Option.builder("s").longOpt("savepoint")
            .argName("savepoint")
            .hasArg(true)
            .required(false)
            .desc("Savepoint of the uploaded file").build()
    )


    options.addOption(
        Option.builder("c").longOpt("comment")
            .argName("comment")
            .hasArg(true)
            .required(false)
            .desc("Comment of the uploaded file").build()
    )

    // define parser
    val cmd : CommandLine
    val parser : CommandLineParser = BasicParser();
    val helper = HelpFormatter();

    val fileInfo = FileInfo()
    var serverUrl : String = ""
    try {
        cmd = parser.parse(options, args);
        if(cmd.hasOption("r")) {
            fileInfo.resume = true
        }

        if(cmd.hasOption("o")) {
            fileInfo.overwrite = true
        }

        if (cmd.hasOption("b")) {
            val blockSize = cmd.getOptionValue("blockSize")
            fileInfo.blockSize = blockSize.toInt()
        }

        if (cmd.hasOption("b")) {
            serverUrl = cmd.getOptionValue("url")
        }

        if (cmd.hasOption("m")) {
            fileInfo.metadata = cmd.getOptionValue("metadata")
        }

        if (cmd.hasOption("s")) {
            fileInfo.savepoint = cmd.getOptionValue("savepoint")
        }

        if (cmd.hasOption("c")) {
            fileInfo.comment = cmd.getOptionValue("comment")
        }

        if (cmd.hasOption("d")) {
            fileInfo.directory = cmd.getOptionValue("directory")
        }
    } catch ( e : ParseException) {
        println(e.message);
        helper.printHelp("Usage:", options);
        exitProcess(0);
    }
    println(fileInfo)
    val client = UploadClient()


    client.start(serverUrl, filePath, fileInfo)
}

    // Try adding program arguments via Run/Debug configuration.
    // Learn more about running applications: https://www.jetbrains.com/help/idea/running-applications.html.
//    UploadClient().start(serverUri, filePath, FileInfo.builder()
//        .apiKey(apiKey)
//        .dlsKey(dlsKey)
//        .metadata(metadata)
//        .savepoint(savepoint)
//        .overwrite(overwrite)
//        .resume(resume)
//        .directory(directory)
//        .blockSize(blockSize)
//        .comment(comment)
//        .build()
//    );
//}