# CLI Guide for Resumable File Transfer Java Client

### Pre-requisite
JRE 17

### Run Command
`java -jar rftc-1.0.jar -uf AnyLargeFilePath -s https://in.tcupiot.tcsapps.com/rft -k APIKey`

Here `rftc-1.0.jar` is the client library to run.

### Command Line Arguments
```
 -b,--blockSize                        Block size of the file (defaults to 1024 B)
 -d,--download                         Download a file
 -dl,--download-location               Downloaded file saving directory in client system
 -k,--key <key>                        API Key
 -mf,--metadata-file                   Fetch metadata from rftc_file_metadata file. Ignored with -m flag.
 -o,--overwrite                        Overwrites if any part is already sent to server
 -r,--resume                           Resumes from last uploaded part
 -s,--server                           Server URL, e.g - https://in.tcupiot.tcsapps.com/rft
 -u,--upload                           Upload a file
 -uc,--upload-comment                  Comment of the uploaded file
 -ud,--upload-directory                Directory of the uploaded file
 -uf,--upload-file                     File to upload
 -ufn,--upload-filename                User provided filename to be used (rename)
 -um,--upload-metadata                 Metadata of the uploaded file in "key=value" format
 -v,--savepoint                        Savepoint of the uploaded file
```

> **Note** : Download feature is currently not implemented. [09-Feb-22]

### Few notes about the client :
At the client-side an auto-generated configuration file is maintained, which stores the
previously successful configurations like server URL, apikey, blockSize etc. Due to which
user may omit these parameters from second call onward. If these parameters are given in CLI, 
that will get higher precedence to the parameter values present in the config file.
The config file is named `rftc.config` and content is in XML format.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties>
    <comment>RFT client properties</comment>
    <entry key="server">https://in.tcupiot.tcsapps.com/rft</entry>
    <entry key="blockSize">10240</entry>
    <entry key="key">apikey</entry>
</properties>

```

If a client wants to upload a large number of metadata, he/she can chose to create a file
named `rftc_file_metadata` and keep on the same directory from where the client CLI is called.
The format of the content of this file is in `key=value` format in multiple lines.

### Notes regarding Resume and Overload
Due to any failure if a large file upload is terminated, there are two options while restarting the upload next time.

If `--resume` is used, the file which was partially uploaded to server will be continued. For example, if 1 GB out of 10GB
was earlier uploaded, then rftc will try to upload only rest of 9GB.

If `--overwrite` is used, then rftc will ignore any part uploaded previously. It will try to upload entire file again.

### Examples :
1. Upload a large file to DLS
```text
java -jar rftc-1.0.jar --upload -uf AnyLargeFilePath -s https://in.tcupiot.tcsapps.com/rft -k APIKey
```
Here, `--upload` or `-u` is optional as the default behavior is to uplaod.

2. Upload a file with metadata
```text
java -jar rftc-1.0.jar -uf AnyLargeFilePath -um "key1=value1,key2=value2"
```
Note that we have removed `-s` and `-k` flags, as those will be picked from previous successful call.

3. Upload a file with metadata from a metadata file
```text
java -jar rftc-1.0.jar -uf AnyLargeFilePath -mf
```
A file should be available at the client calling directory with name `rftc_file_metadata`.
The content should be like -
```properties
key1 = value 1
key2 = value 2
key3 = value 3
key4 = value 4
```

4. Upload a file to an existing directory of DLS
```text
java -jar rftc-1.0.jar -uf AnyLargeFilePath -ud /D1
```

5. Upload a file to different savepoint
```text
java -jar rftc-1.0.jar -uf payslip.pdf -v January
```

6. Resume a failed upload 
```text
java -jar rftc-1.0.jar -uf AnyLargeFilePath --resume
```

### Console Output
The file transfer progress, along with the generated file URI is shown as below -
```
6.653 GB of 6.653 GB transferred successfully      File URI : tenant1/user1/Data1.zip
```

### Error Handling
All client side errors like issues with user input, client side file reading issue, server connection issue etc are reported.

All issues which are due to DLS operations (e.g - Duplicate file upload, Directory permission etc) are reported along with HTTP status code.

### Client Side Resource Requirement
Client system may not require more than 1-core CPU or 200 MB of heap-space to transfer large files.
