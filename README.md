# Otis

The `Otis` project is a REST API service designed to store various data about players in a database.  
It is built on the Micronaut framework, which facilitates the development of such services.

This approach minimizes the need for requests to third-party services and enables internal services to work with players
who are offline.

> **⚠ CAUTION**  
> If a player is not registered on the server, a call to a third-party service is necessary.  
> Currently, this project does not provide functionality to handle such cases.

---

## Interaction

Plugins, extensions, or services from our ecosystem communicate with the `Otis` service via web requests.  
While this may seem unconventional in a plugin, REST API calls offer greater flexibility because they eliminate the need
for an active connection to a database server.

It is the responsibility of the use case to handle scenarios where the service is unreachable.

### Available Controllers

#### `/otis` Controller

This controller handles operations related to player profiles:

| Endpoint          | Method | Parameters      |  
|-------------------|--------|-----------------|  
| `/`               | POST   | `body: Profile` |  
| `/byId/{owner}`   | GET    | `id: UUID`      |  
| `/byName/{name}`  | GET    | `name: String`  |  
| `/delete/{owner}` | DELETE | `id: UUID`      |  
| `/all`            | GET    | `<none>`        |  

---

#### `/search` Controller

This controller is used to check if a given player or UUID has a profile:

| Endpoint         | Method | Parameters     |  
|------------------|--------|----------------|  
| `/byId/{id}`     | GET    | `id: UUID`     |  
| `/byName/{name}` | GET    | `name: String` |  

---
