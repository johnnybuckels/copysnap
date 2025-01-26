# copysnap

CopySnap is a lightweight command line tool for creating incremental backup snapshots of a target directory similar to macOS 'timemachine'.

Snapshots are computed based on last modified date and actual file contents. Currently, copysnap only supports copying files to a locally available drive.

## Build

Copysnap is built using [mvn](https://maven.apache.org/index.html). Building is only tested on linux (ubuntu) systems. Others might work as well.

Build, test and package the project with the `package` goal.
```shell
mvn clean package
```

This also runs unit tests creating temporary files in your tmp folder.


## Usage

Currently, there are no pre-built releases available. You need to [build](#build) the project first.

Copysnap is released as a java jar compiled with `java 21`. 

You may run copysnap like this:

```shell
java -jar /path/to/copysnap/copysnap-X.X.X.jar
```