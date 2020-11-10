# R2RML-mapping
[![License](http://img.shields.io/badge/license-LGPL_2.1-blue.svg?style=flat)](https://github.com/danielabutano/intermine-R2RML-mapping/blob/master/LICENSE)

Clone the project from github and execute:
```
cd intermine-R2RML-mapping
./gradlew install
./gradlew -q run > mapping.ttl # Silence gradle or it will corrupt the mapping file
```

This mapping.ttl file can then be used by R2RML implementations such as [ontop](https://ontop-vkg.org/guide/cli.html).

If one wants to use ontop
```
mkdir ontop-cli
cd ontop-cli

wget "https://github.com/ontop/ontop/releases/download/ontop-4.0.3/ontop-cli-4.0.3.zip"
unzip ontop-cli-4.0.3.zip

# intermine uses postgresql so get the JDBC driver
cd jdbc
wget "https://jdbc.postgresql.org/download/postgresql-42.2.18.jar"

#make a file db.properties
jdbc.url=jdbc:postgresql://localhost:5432/biotestmine #Change to your settinggs
jdbc.user=${YOUR_PGSQL_USERNAME}
jdbc.password=${YOUR_PGSQL_PASSWORD}


#Then one can use 
./ontop query -m ../mapping.ttl -p db.properties -q ../queries/proteins.rq
```



