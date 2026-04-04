# SENG4430_Project_Group5

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

# Build Tool

To generate the `.jar` use `mvn package` this can then be run with:

`java -jar target/quality-auditor-tool-1.0.0-jar-with-dependencies.jar --source "path/to/project"`

Note: if you don't want to install maven just use the VSCode Maven extension and use that instead.

https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html

# Expected Project structure
The project which will be audited must be a Java Maven project 

# Editing the severity thresholds 
The project contains a configuration file named `default_config.json` with reasonable defaults which define the severity of the thresholds. 
