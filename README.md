# SENG4430_Project_Group5: Automative Industry Software Quality Auditing Tool

## Team Members

| Name | Student Number | GitHub Username |
| :--- | :---: | ---: |
| Joe McIntyre  | 3429578 | joe-mcintyre |
| Brandon Ballard | 3429564 | Brandon-Ballard25  |
| Brandon Cave | 3305287 | Bc0706 |



```
              ____----------- _____
\~~~~~~~~~~/~_--~~~------~~~~~     \
 `---`\  _-~      |                   \
   _-~  <_         |                     \[]
 / ___     ~~--[""] |      ________-------'_
> /~` \    |-.   `\~~.~~~~~                _ ~ - _
 ~|  ||\%  |       |    ~  ._                ~ _   ~ ._
   `_//|_%  \      |          ~  .              ~-_   /\
          `--__     |    _-____  /\               ~-_ \/.
               ~--_ /  ,/ -~-_ \ \/          _______---~/
                   ~~-/._<   \ \`~~~~~~~~~~~~~     ##--~/
                         \    ) |`------##---~~~~-~  ) )
                          ~-_/_/                  ~~ ~~
```

# Family

# Run Test Cases

To run the project test suite, run `mvn package`.
The list of 64 test cases will be run and displayed in the terminal.

# Build Tool

To generate the `.jar` use `mvn package` this can then be run with:

`java -jar target/quality-auditor-tool-1.0.0-jar-with-dependencies.jar --source "path/to/project"`

Note: if you don't want to install maven just use the VSCode Maven extension and use that instead.

https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html

# Expected Project structure
The project which will be audited must be a Java Maven project 

# Editing the severity thresholds 
The project contains a configuration file named `default_config.json` with reasonable defaults which define the severity of the thresholds. 
