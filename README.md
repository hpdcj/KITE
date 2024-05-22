HPV-Kite
========

# Introduction

HPV-Kite is acronym for the _HPV K-mer Index Tversky Estimator_.

This is a Java application for searching for the genes of HPV viruses in the samples in form of .fq.gz files.

Database of HPV viruses is generated from data from
[The PapillomaVirus Episteme (PaVE) database](https://pave.niaid.nih.gov/).
The database is embedded into the output JAR file.

## Usage

### Prerequisities

To be able to run the application _Java Runtime Environment_ at least in version 17 is required.
You can check the available version of the Java on the system by invoking command:`java -version` in the terminal
window.
It would produce output like:

```
openjdk version "22.0.1" 2024-04-16
OpenJDK Runtime Environment (build 22.0.1+8-16)
OpenJDK 64-Bit Server VM (build 22.0.1+8-16, mixed mode, sharing)
```

That means that the Java in version 22 is installed on the computer, so it meets the requirement.
If the `java` command is not found, that can mean that the command is not in the `PATH` or Java is not installed at all.
You can easily install current, ready to use, version from https://jdk.java.net/ just by downloading the version
adequate
to your operating system.

#### PCJ library

The application uses [the PCJ library](https://pcj.icm.edu.pl) ([GitHub](https://github.com/hpdcj/PCJ)) for process
files concurrently using the multinode environment.
The jar file with the PCJ library is attached in release zip file.

#### Gradle

<details>
<summary>Building the application from source code</summary>
The Gradle build system is used for managing  dependencies and for building software.
It is possible to build the application from source code, by invoking `./gradlew assemble` or `gradlew.bat assemble`.

Other useful tasks Gradle: `copyDependencies`, `createDependenciesJar`, `createFatJar`, `release`.
</details>

### Release

The current version of the application is available in [Release](https://github.com/hpdcj/HPV-KITE/releases/latest)
page.
The release is packed as ZIP file that has to be unpacked into local directory.

### Executing

To run the application just run the following command:

`java -jar hpv-kite-1.0.jar <list-of-file-paths.fq.gz>`

For example:

`java -jar hpv-kite-1.0.jar  *.fq.gz`

<details><summary>Click to see command output</summary>
It would produce output like:

```
maj 22, 2024 12:33:40 PM org.pcj.internal.InternalPCJ start
INFO: PCJ version 5.3.3-831a4fa (2023-10-10T14:35:07.064+0200)
maj 22, 2024 12:33:41 PM org.pcj.internal.InternalPCJ start
INFO: Starting pl.edu.icm.heap.kite.PcjMain with 1 thread (on 1 node)...
[2024-05-22 12:33:41,806] shingleLength = 18
[2024-05-22 12:33:41,807] gzipBuffer = 512
[2024-05-22 12:33:41,807] readerBuffer = 512
[2024-05-22 12:33:41,808] processingBuffer = 64
[2024-05-22 12:33:41,809] threadPoolSize = 8
[2024-05-22 12:33:41,809] outputHpvCount = 3
[2024-05-22 12:33:41,810] hpvVirusesPath = <bundled>
[2024-05-22 12:33:41,813] Files to process (3): [sample_00005.fq.gz, sample_00064.fq.gz, sample_08414_without.fq.gz]
[2024-05-22 12:33:41,813] filesGroupPattern = <none>
[2024-05-22 12:33:41,814] Reading HPV viruses file by all threads... takes 0,894718
[2024-05-22 12:33:42,697] Loaded 222 HPV viruses: [HPV69REF, HPV82REF, HPV71REF, HPV126REF, HPV160REF, HPV85REF, HPV83REF, HPV84REF, HPV86REF, HPV91REF, HPV89REF, HPV74REF, HPV92REF, HPV87REF, HPV43REF, HPV81REF, HPV95REF, HPV94REF, HPV90REF, HPV93REF, HPV96REF, HPV62REF, HPV67REF, HPV58REF, HPV103REF, HPV68REF, HPV97REF, HPV101REF, HPV106REF, HPV102REF, HPV107REF, HPV88REF, HPV110REF, HPV111REF, HPV109REF, HPV112REF, HPV116REF, HPV115REF, HPV108REF, HPV98REF, HPV99REF, HPV100REF, HPV104REF, HPV105REF, HPV113REF, HPV125REF, HPV150REF, HPV151REF, HPV114REF, HPV117REF, HPV118REF, HPV119REF, HPV120REF, HPV121REF, HPV122REF, HPV123REF, HPV124REF, HPV149REF, HPV130REF, HPV131REF, HPV132REF, HPV133REF, HPV134REF, HPV148REF, HPV128REF, HPV129REF, HPV159REF, HPV174REF, HPV179REF, HPV184REF, HPV127REF, HPV135REF, HPV136REF, HPV137REF, HPV138REF, HPV139REF, HPV140REF, HPV141REF, HPV142REF, HPV143REF, HPV144REF, HPV145REF, HPV146REF, HPV147REF, HPV31REF, HPV152REF, HPV155REF, HPV153REF, HPV154REF, HPV166REF, HPV169REF, HPV164REF, HPV163REF, HPV162REF, HPV161REF, HPV170REF, HPV156REF, HPV165REF, HPV16REF, HPV175REF, HPV180REF, HPV78REF, HPV168REF, HPV167REF, HPV171REF, HPV172REF, HPV173REF, HPV178REF, HPV199REF, HPV197REF, HPV200REF, HPV201REF, HPV202REF, HPV204REF, HPV176REF, HPV177REF, HPV181REF, HPV182REF, HPV183REF, HPV185REF, HPV186REF, HPV187REF, HPV188REF, HPV189REF, HPV190REF, HPV191REF, HPV192REF, HPV193REF, HPV194REF, HPV195REF, HPV196REF, HPV157REF, HPV205REF, HPV158REF, HPV209REF, HPV33REF, HPV8REF, HPV11REF, HPV5REF, HPV47REF, HPV39REF, HPV51REF, HPV42REF, HPV224REF, HPV211REF, HPV212REF, HPV213REF, HPV214REF, HPV215REF, HPV216REF, HPV223REF, HPV225REF, HPV226REF, HPV203REF, HPV219REF, HPV220REF, HPV221REF, HPV222REF, HPV210REF, HPV227REF, HPV207REF, HPV208REF, HPV229REF, HPV228REF, HPV70REF, HPV20REF, HPV21REF, HPV22REF, HPV23REF, HPV24REF, HPV28REF, HPV29REF, HPV36REF, HPV37REF, HPV38REF, HPV44REF, HPV48REF, HPV50REF, HPV60REF, HPV61REF, HPV66REF, HPV54REF, HPV206REF, HPV1REF, HPV6REF, HPV18REF, HPV2REF, HPV57REF, HPV41REF, HPV13REF, HPV4REF, HPV63REF, HPV65REF, HPV3REF, HPV7REF, HPV9REF, HPV10REF, HPV12REF, HPV14REF, HPV15REF, HPV17REF, HPV19REF, HPV25REF, HPV26REF, HPV27REF, HPV30REF, HPV32REF, HPV34REF, HPV35REF, HPV40REF, HPV45REF, HPV49REF, HPV52REF, HPV53REF, HPV56REF, HPV59REF, HPV72REF, HPV73REF, HPV75REF, HPV76REF, HPV77REF, HPV80REF]
<... processing ...>
[2024-05-22 12:33:44,699] Total time: 2,897480500
maj 22, 2024 12:33:44 PM org.pcj.internal.InternalPCJ start
INFO: Completed pl.edu.icm.heap.kite.PcjMain with 1 thread (on 1 node) after 0h 0m 2s 959ms.
```

</details>

### Parameters

HPV-KITE as multiple parameters that can be used for the run.

The following tables shows the names of the parameters with their default values and their meaning.

| parameter name    |    default value    | description                                                                                                                                                                                                                                                                                     |
|-------------------|:-------------------:|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| shingleLength     |         18          | list of comma separated values for K-mer length (e.g. `18` or `30,32`)                                                                                                                                                                                                                          |
| outputHpvCount    |          3          | maximum number of HPV viruses that match index is returned; if non-positive - return results for all HPV viruses from the database                                                                                                                                                              |
| hpvVirusesPath    |      _bundled_      | path to the FASTA files with HPV viruses database; if not provided, the application will use embedded database                                                                                                                                                                                  |
| filesGroupPattern | "" (_empty string_) | regular expression pattern to group results from multiple input files                                                                                                                                                                                                                           |
| nodesFile         |      nodes.txt      | file with names of the nodes which will be used to start multinode processing                                                                                                                                                                                                                   |
| deploy            |        false        | flag to tell that application should use _deploy_ mechanism of the PCJ library (SSH connection) to start computation in multinode processing; if set to _false_, it is necessary to start processing files in multinode environment using available mechanisms like `srun`, `aprun`, `mpiexec`. |                             
| threadPoolSize    |  _available CPUs_   | number of threads that is processing data                                                                                                                                                                                                                                                       |
| processingBuffer  |         64          | minimal size of buffer for characters to start processing data concurrently (in KB)                                                                                                                                                                                                             |
| gzipBuffer        |         512         | internal buffer size for loading GZIP files (in KB)                                                                                                                                                                                                                                             |                 
| readerBuffer      |         512         | internal buffer size for reading FASTQ files (in KB)                                                                                                                                                                                                                                            |                    

To modify parameter, just give its name with the `-D` prefix (eg. `-DshingleLength=30`) at the beginning of the command
line just after `java`.
For example:

```bash
java \
  -DshingleLength=30 \
  -DnodesFile=all_nodes.txt \
  -Ddeploy=true \
  -DoutputHpvCount=1 \
  -DfilesGroupPattern='sample_0[0-9]'  \
  -jar hpv-kite-1.0.jar \
  sample_00005.fq.gz sample_00064.fq.gz sample_08414_without.fq.gz
```

<details><summary>Click to see command output</summary>
That command would produce following information in a header:

```
maj 22, 2024 1:23:56 PM org.pcj.internal.InternalPCJ start
INFO: PCJ version 5.3.3-831a4fa (2023-10-10T14:35:07.064+0200)
maj 22, 2024 1:23:57 PM org.pcj.internal.InternalPCJ start
INFO: Starting pl.edu.icm.heap.kite.PcjMain with 1 thread (on 1 node)...
[2024-05-22 13:23:57,657] shingleLength = 30
[2024-05-22 13:23:57,657] gzipBuffer = 512
[2024-05-22 13:23:57,657] readerBuffer = 512
[2024-05-22 13:23:57,658] processingBuffer = 64
[2024-05-22 13:23:57,658] threadPoolSize = 8
[2024-05-22 13:23:57,659] outputHpvCount = 1
[2024-05-22 13:23:57,659] hpvVirusesPath = <bundled>
[2024-05-22 13:23:57,661] Files to process (3): [sample_00005.fq.gz, sample_00064.fq.gz, sample_08414_without.fq.gz]
[2024-05-22 13:23:57,662] filesGroupPattern = sample_0[0-9]
[2024-05-22 13:23:57,668] File groups (2): [sample_00, sample_08]
<... further processing ...>
```

</details>
