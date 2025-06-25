KITE
========

_KITE is an application aiming to detect any combination of microbe genome selected to be detected from metagenome data.  
[HPV-Kite](https://github.com/HPDCJ/HPV-KITE) in an independent application aiming to detect only HPV genomes in metagenome data._

# Introduction

KITE is a Java application that detects various pathogens within samples. It can be used for DNA and/or RNA FASTQ files.

KITE stands for _K-mer Index Tversky Estimator_, as the application leverages _k-mer_ analysis combined with the
_Tversky index_.

KITE not only expedites viral detection but also maintains comparable sensitivity to existing approaches.

The database can also be any FASTA file(s) with nucleotide sequences proceeded by descriptions of them.

# Flowchart

![KITE Schema](https://github.com/hpdcj/HPV-KITE/assets/567976/f96c66b9-1bbf-4a25-8fb8-3936b7450b04)

_**Schematic view of KITE execution.** (Image from [[1]](https://doi.org/10.1093/bib/bbaf155))_

# Usage

## Prerequisities

### Java $\geq$ 17

To run the application _Java Runtime Environment_, at least in version 17, is required.
You can check the available version of the Java on the system by invoking the command: `java -version` in the terminal
window.
It would produce output like:

> ```
> openjdk version "22.0.1" 2024-04-16
> OpenJDK Runtime Environment (build 22.0.1+8-16)
> OpenJDK 64-Bit Server VM (build 22.0.1+8-16, mixed mode, sharing)
> ```

That means that Java in version 22 is installed on the computer, so it meets the requirement.
If the `java` command is not found, that can mean that the command is not in the `PATH` or Java is not installed at all.
You can easily install the current ready-to-use version from https://jdk.java.net/ just by downloading the version that
is adequate for your operating system.

### PCJ library

The application uses [the PCJ library](https://pcj.icm.edu.pl) ([GitHub repository](https://github.com/hpdcj/PCJ))
to process files concurrently using the multinode environment. It uses PCJ in version 5.3.3.
The jar file with the PCJ library is attached
in a [release zip file](https://github.com/hpdcj/KITE/releases/latest).

### Gradle Build Tool (for compiling only)

<details>
<summary>Building the application from source code</summary>

The Gradle build system is used to manage dependencies and build software. It is possible to build the application from
the source code, by calling:
<code>./gradlew assemble</code> or <code>gradlew.bat assemble</code>.

Other useful Gradle tasks: `createDependenciesJar`, `createFatJar`.
</details>

## Release (binary version)

The current version of the application is available on the [Release](https://github.com/hpdcj/KITE/releases/latest)
page.
The release is packed as a ZIP file that has to be unpacked into a local directory.

## Executing

To run the application, just run the following command:

`java -jar kite-1.1.0.jar <list-of-file-paths.fq.gz>`

For example:

`java -jar kite-1.1.0.jar  *.fq.gz`

<details><summary>Click to see command output</summary>
It would produce output like:

> ```
> cze 25, 2025 9:56:21 AM org.pcj.internal.InternalPCJ start
> INFO: PCJ version 5.3.3-831a4fa (2023-10-10T14:35:07.064+0200)
> cze 25, 2025 9:56:22 AM org.pcj.internal.InternalPCJ start
> INFO: Starting pl.edu.icm.heap.kite.PcjMain with 1 thread (on 1 node)...
> [2025-06-25 09:56:22,070] shingleLength = 31
> [2025-06-25 09:56:22,073] gzipBuffer = 512
> [2025-06-25 09:56:22,074] readerBuffer = 512
> [2025-06-25 09:56:22,074] processingBuffer = 64
> [2025-06-25 09:56:22,074] threadPoolSize = 8
> [2025-06-25 09:56:22,074] outputVirusCount = 0
> [2025-06-25 09:56:22,074] databasePaths = hpv_222.fasta
> [2025-06-25 09:56:22,075] Files to process (3): [sample_00005.fq.gz, sample_00064.fq.gz, sample_08414_without.fq.gz]
> [2025-06-25 09:56:22,075] filesGroupPattern = <none>
> [2025-06-25 09:56:22,076] Reading virus database files by all threads
> [2025-06-25 09:56:22,077] Reading database file: hpv_222.fasta... takes 0.7487134
> [2025-06-25 09:56:22,825] Loaded 222 viruses in 0,748713: HPV69REF(7669), HPV82REF(7839), HPV71REF(8004), HPV126REF(7295), HPV160REF(7748), HPV85REF(7781), HPV83REF(8073), HPV84REF(7917), HPV86REF(7952), HPV91REF(7935), HPV89REF(8047), HPV74REF(7856), HPV92REF(7430), HPV87REF(7968), HPV43REF(7944), HPV81REF(8039), HPV95REF(7306), HPV94REF(7850), HPV90REF(8002), HPV93REF(7419), HPV96REF(7407), HPV62REF(8061), HPV67REF(7770), HPV58REF(7793), HPV103REF(7232), HPV68REF(7791), HPV97REF(7812), HPV101REF(7228), HPV106REF(8004), HPV102REF(8041), HPV107REF(7452), HPV88REF(7295), HPV110REF(7392), HPV111REF(7355), HPV109REF(7315), HPV112REF(7196), HPV116REF(7153), HPV115REF(7445), HPV108REF(7119), HPV98REF(7435), HPV99REF(7667), HPV100REF(7349), HPV104REF(7355), HPV105REF(7636), HPV113REF(7381), HPV125REF(7778), HPV150REF(7405), HPV151REF(7355), HPV114REF(8039), HPV117REF(7864), HPV118REF(7566), HPV119REF(7220), HPV120REF(7273), HPV121REF(7311), HPV122REF(7366), HPV123REF(7298), HPV124REF(7458), HPV149REF(7302), HPV130REF(7357), HPV131REF(7151), HPV132REF(7094), HPV133REF(7327), HPV134REF(7278), HPV148REF(7133), HPV128REF(7228), HPV129REF(7188), HPV159REF(7412), HPV174REF(7328), HPV179REF(7197), HPV184REF(7293), HPV127REF(7150), HPV135REF(7262), HPV136REF(7288), HPV137REF(7205), HPV138REF(7322), HPV139REF(7329), HPV140REF(7310), HPV141REF(7345), HPV142REF(7343), HPV143REF(7684), HPV144REF(7240), HPV145REF(7344), HPV146REF(7234), HPV147REF(7193), HPV31REF(7877), HPV152REF(7449), HPV155REF(7321), HPV153REF(7209), HPV154REF(7255), HPV166REF(7181), HPV169REF(7221), HPV164REF(7202), HPV163REF(7202), HPV162REF(7183), HPV161REF(7207), HPV170REF(7386), HPV156REF(7297), HPV165REF(7098), HPV16REF(7875), HPV175REF(7195), HPV180REF(7325), HPV78REF(7799), HPV168REF(7173), HPV167REF(7197), HPV171REF(7230), HPV172REF(7172), HPV173REF(7266), HPV178REF(7283), HPV199REF(7153), HPV197REF(7247), HPV200REF(7106), HPV201REF(7260), HPV202REF(7313), HPV204REF(7196), HPV176REF(7195), HPV177REF(7902), HPV181REF(7209), HPV182REF(7385), HPV183REF(7255), HPV185REF(7413), HPV186REF(7358), HPV187REF(7237), HPV188REF(7154), HPV189REF(7287), HPV190REF(7244), HPV191REF(7333), HPV192REF(7213), HPV193REF(7317), HPV194REF(7229), HPV195REF(7537), HPV196REF(7461), HPV157REF(7123), HPV205REF(7267), HPV158REF(7161), HPV209REF(7368), HPV33REF(7826), HPV8REF(7623), HPV11REF(7900), HPV5REF(7715), HPV47REF(7695), HPV39REF(7802), HPV51REF(7777), HPV42REF(7886), HPV224REF(7202), HPV211REF(7222), HPV212REF(7177), HPV213REF(7065), HPV214REF(7326), HPV215REF(7155), HPV216REF(7202), HPV223REF(7192), HPV225REF(7289), HPV226REF(7282), HPV203REF(7341), HPV219REF(7077), HPV220REF(7350), HPV221REF(7295), HPV222REF(7244), HPV210REF(7104), HPV227REF(7410), HPV207REF(7216), HPV208REF(7252), HPV229REF(7288), HPV228REF(7246), HPV70REF(7874), HPV20REF(7705), HPV21REF(7734), HPV22REF(7337), HPV23REF(7293), HPV24REF(7421), HPV28REF(7892), HPV29REF(7885), HPV36REF(7691), HPV37REF(7390), HPV38REF(7369), HPV44REF(7802), HPV48REF(7069), HPV50REF(7153), HPV60REF(7282), HPV61REF(7958), HPV66REF(7793), HPV54REF(7728), HPV206REF(7701), HPV1REF(7785), HPV6REF(7965), HPV18REF(7826), HPV2REF(7829), HPV57REF(7830), HPV41REF(7583), HPV13REF(7849), HPV4REF(7322), HPV63REF(7317), HPV65REF(7277), HPV3REF(7789), HPV7REF(7996), HPV9REF(7403), HPV10REF(7888), HPV12REF(7642), HPV14REF(7670), HPV15REF(7382), HPV17REF(7395), HPV19REF(7630), HPV25REF(7674), HPV26REF(7824), HPV27REF(7800), HPV30REF(7821), HPV32REF(7930), HPV34REF(7687), HPV35REF(7848), HPV40REF(7878), HPV45REF(7824), HPV49REF(7529), HPV52REF(7911), HPV53REF(7828), HPV56REF(7808), HPV59REF(7865), HPV72REF(7958), HPV73REF(7669), HPV75REF(7506), HPV76REF(7518), HPV77REF(7853), HPV80REF(7396)
> <... processing ...>
> [2025-06-24 09:56:26,699] Total time: 2,994301700
> cze 25, 2025 9:56:27 PM org.pcj.internal.InternalPCJ start
> INFO: Completed pl.edu.icm.heap.kite.PcjMain with 1 thread (on 1 node) after 0h 0m 3s 251ms.
> ```

</details>

## Parameters

KITE has multiple parameters that can be used for the run.

The following tables show the names of the parameters with their default values and meaning.

| parameter name    |    default value    | description                                                                                                                                                                                                                                                                                      |
|-------------------|:-------------------:|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| shingleLength     |         31          | list of comma separated values for K-mer length (e.g. `18` or `30,32`)                                                                                                                                                                                                                           |
| outputVirusCount  |          0          | maximum number of viruses that match index is returned; if non-positive - return results for all viruses from the database                                                                                                                                                                       |
| databasePaths     | "" (_empty string_) | path to the FASTA files with viruses (reference) database. Multiple files should be separated by system path separator (`':'` on UNIX systems, and `';'` on Microsoft Windows systems). The name of the virus is the first word from the description field (that starts with `>` in FASTA file). |
| filesGroupPattern | "" (_empty string_) | regular expression pattern to group results from multiple input files; _empty string_ means not to group results                                                                                                                                                                                 |
| nodesFile         |      nodes.txt      | file with names of the nodes which will be used to start multinode processing                                                                                                                                                                                                                    |
| deploy            |        false        | flag to tell that application should use _deploy_ mechanism of the PCJ library (SSH connection) to start computation in multinode processing; if set to _false_, it is necessary to start processing files in multinode environment using available mechanisms like `srun`, `aprun`, `mpiexec`.  |                             
| threadPoolSize    |  _available CPUs_   | number of threads that is processing data                                                                                                                                                                                                                                                        |
| processingBuffer  |         64          | minimal size of buffer for characters to start processing data concurrently (in KB)                                                                                                                                                                                                              |
| gzipBuffer        |         512         | internal buffer size for loading GZIP files (in KB)                                                                                                                                                                                                                                              |                 
| readerBuffer      |         512         | internal buffer size for reading FASTQ files (in KB)                                                                                                                                                                                                                                             |                    

To modify the parameter, just give its name with the `-D` prefix (e.g. `-DshingleLength=30`) at the beginning of the
command line just after `java`.
For example:

```bash
java \
  -DdatabasePaths=hpv_222.fasta \
  -DshingleLength=30 \
  -DoutputVirusCount=1 \
  -jar kite-1.1.0.jar \
  sample_00005.fq.gz sample_00064.fq.gz sample_08414_without.fq.gz
```

It will start processing 3 files (`sample_00005.fq.gz`, `sample_00064.fq.gz`, and `sample_08414_without.fq.gz`) using the database stored in `hpv_222.fasta` file (`-DdatabasePaths=hpv_222.fasta`) on the
local machine, returning _the index_ only for the most similar viruses (`-DoutputVirusCount=1`) using the 30-character
long shingles (_k-mers_; `-DshingleLength=30`).

### Advanced example

```bash
java \
  -DdatabasePaths=hpv_222.fasta \
  -DshingleLength=30 \
  -DnodesFile=all_nodes.txt \
  -Ddeploy=true \
  -DoutputVirusCount=1 \
  -DfilesGroupPattern='sample_0[0-9]'  \
  -jar kite-1.1.0.jar \
  sample_00005.fq.gz sample_00064.fq.gz sample_08414_without.fq.gz
```

The command will start processing 3 files (`sample_00005.fq.gz`, `sample_00064.fq.gz`, and `sample_08414_without.fq.gz`)
like in the example above, but on nodes described in `all_nodes.txt` file (`-DnodesFile=all_nodes.txt`).

<details><summary>all_nodes.txt file</summary>

The `all_nodes.txt` file should have the hostname of each node in a separate line, e.g.:

```
wn8001
wn8002
wn8003
```

</details>

It will start computation on these nodes using SSH connection (`-Ddeploy=true`).

> [!NOTE]
> Be sure that Kite _jar files_ (`kite-1.1.0.jar` and `pcj-5.3.3.jar`) are located in the same path as in the
> calling machine.
> This is normal in most of the _computer clusters_.
> However, then you are probably using `mpiexec`, `srun` or another command to run parallel jobs, so you do not have
> to use _deploy_ mechanism.

Shingles from multiple input files will be grouped (`-DfilesGroupPattern='sample_0[0-9]'`) to generate summary result
for these groups:

1. sample_00 (files: `sample_00005.fq.gz` and `sample_00064.fq.gz`),
2. sample_08 (file: `sample_08414_without.fq.gz`).

<details><summary>Click to see command output</summary>
The command would produce the following information in a header:

> ```
> maj 22, 2024 1:23:56 PM org.pcj.internal.InternalPCJ start
> INFO: PCJ version 5.3.3-831a4fa (2023-10-10T14:35:07.064+0200)
> maj 22, 2024 1:23:57 PM org.pcj.internal.InternalPCJ start
> INFO: Starting pl.edu.icm.heap.kite.PcjMain with 1 thread (on 1 node)...
> [2024-05-22 13:23:57,657] shingleLength = 30
> [2024-05-22 13:23:57,657] gzipBuffer = 512
> [2024-05-22 13:23:57,657] readerBuffer = 512
> [2024-05-22 13:23:57,658] processingBuffer = 64
> [2024-05-22 13:23:57,658] threadPoolSize = 8
> [2024-05-22 13:23:57,659] outputVirusCount = 1
> [2024-05-22 13:23:57,659] databasePaths = hpv_222.fasta
> [2024-05-22 13:23:57,661] Files to process (3): [sample_00005.fq.gz, sample_00064.fq.gz, sample_08414_without.fq.gz]
> [2024-05-22 13:23:57,662] filesGroupPattern = sample_0[0-9]
> [2024-05-22 13:23:57,668] File groups (2): [sample_00, sample_08]
> <... further processing ...>
> ```

</details>
  

# Please cite

When using the tool in published research, please cite:

1. Marek Nowicki, Magdalena Mroczek, Dhananjay Mukhedkar, Piotr Bała, Ville Nikolai Pimenoff, Laila Sara Arroyo Mühr,
   HPV-KITE: sequence analysis software for rapid HPV genotype detection, _Briefings in Bioinformatics_, Volume 26,
   Issue 2, March 2025, bbaf155, https://doi.org/10.1093/bib/bbaf155


<!---
### Conda

The KITE is also available as conda package: `conda-forge::kite`. The application can be executed by typing:
`kite sample.fq.gz`

To set up KITE parameters for conda application, the `KITE_PARAMS` environmental variable can be used, e.g.

```
KITE_PARAMS="-DdatabasePaths=hpv_222.fasta -DfilesGroupPattern=sample_0[0-9]"
```
---!>
