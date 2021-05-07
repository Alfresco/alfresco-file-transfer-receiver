### Alfresco File Transfer Receiver
The File System Transfer Receiver transfers folders and content from an Alfresco Content Services core repository (the DM) to configured targets using the Transfer Service, for example, a remote file system.
The source code contains the FTR and it's packaging (zip archive).
For documentation please visit [the official site](http://docs.alfresco.com).

### Building and testing
The project can be built by running Maven command:
~~~
mvn clean install
~~~

### Artifacts
The artifacts can be obtained by:
* downloading from [Alfresco repository](https://artifacts.alfresco.com/nexus/content/groups/public)
* getting as Maven dependency by adding the dependency to your pom file:
~~~
<dependency>
  <groupId>org.alfresco</groupId>
  <artifactId>alfresco-file-transfer-receiver</artifactId>
  <version>version</version>
</dependency>
~~~
and Alfresco Maven repository:
~~~
<repository>
  <id>alfresco-maven-repo</id>
  <url>https://artifacts.alfresco.com/nexus/content/groups/public</url>
</repository>
~~~
The SNAPSHOT version of the artifact is **never** published.

## Build and release process for 6.2

For a complete walk-through check out the
[build-and-release-101.MD](documentation/build-and-release-101.md) under the `documentation` folder.

### Contributing guide
Please use [this guide](CONTRIBUTING.md) to make a contribution to the project.