### Maven Goals Run
1. `./mvnw clean install`
2. `./mvnw spring-boot:run`

### Jar Run
1. `./mvnw clean package`
2. `java -jar target/your-application-name.jar`

#### Check dependency tree 
`./mvnw dependency:tree`

### Deploy
pm2 start ecosystem.config.js

```javascript
module.exports = {
    apps: [
        {
            name: 'pdm-sync-server',
            script: 'java',
            args: '-jar target/pdm-sync-server-0.0.1-SNAPSHOT.jar',
            exec_interpreter: 'none',
            exec_mode: 'fork',
            watch: false,
            autorestart: false,
            max_memory_restart: '2G',
            env: {
                NODE_ENV: 'development',
            },
            env_production: {
                NODE_ENV: 'production',
            },
        },
    ],
};
```