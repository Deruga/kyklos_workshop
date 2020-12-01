# How one can access ISO 10303 Repository
# Part II

EDMtruePLM web application has some REST web services exposed.
This is Java application that demonstrates basic usage of the services.

The project consists of the following two parts.
1. Client stub generated by [swagger-codegen](https://github.com/swagger-api/swagger-codegen)
2. Small client application.

## How to build

1. Clone source files from the GitHub repository
```javascript
git clone https://github.com/Deruga/kyklos_workshop.git .
```

2. Download swagger-codegen
This can be done via PowerShell command in Windows. Run PowerShell and execute the following command.
Please, get appropriate version

```javascript
Invoke-WebRequest -Uri https://repo1.maven.org/maven2/io/swagger/swagger-codegen-cli/2.4.17/swagger-codegen-cli-2.4.17.jar -OutFile swagger-codegen-cli.jar
```

3. Assume ***swagger-codegen-cli.jar*** is already downloaded into root folder of your working copy.
Run java-generate.bat script to generate client stub (***demolib*** library project)

```javascript
java-generate.bat
```

Note, generated code may have some bugs and be not compilable.
Therefore some source files is stored in the repository (ApiClient.java and DataControllerApi.java).
After code generation something shall be reverted.

```javascript
git checkout demolib/src/main/java/com/jotne/demo/ApiClient.java
git checkout demolib/src/main/java/com/jotne/demo/api/DataControllerApi.java
```

4. Go to ***demolib*** folder and build the library project by using ***Maven*** builder
Note, [Java](https://www.oracle.com/ru/java/technologies/javase-downloads.html) and [Maven](https://maven.apache.org/) must be installed on your system

```javascript
cd demolib
mvn package
```

5. Project ***democli*** can be imported to appropriate IDE that supports Java development: Eclipse, NetBeans, IntelliJ IDEA, etc.
IDE allows you to debug the client app.

Note, ***democli*** project has configuration file with dummy password stored: [config.properties](democli/src/resources/config.properties).
Actual user credentials can be provided by request.

