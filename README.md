## Antaeus

Antaeus (/ænˈtiːəs/), in Greek mythology, a giant of Libya, the son of the sea god Poseidon and the Earth goddess Gaia. He compelled all strangers who were passing through the country to wrestle with him. Whenever Antaeus touched the Earth (his mother), his strength was renewed, so that even if thrown to the ground, he was invincible. Heracles, in combat with him, discovered the source of his strength and, lifting him up from Earth, crushed him to death.

Welcome to our challenge.

## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will schedule payment of those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

## Instructions

Fork this repo with your solution. Ideally, we'd like to see your progression through commits, and don't forget to update the README.md to explain your thought process.

Please let us know how long the challenge takes you. We're not looking for how speedy or lengthy you are. It's just really to give us a clearer idea of what you've produced in the time you decided to take. Feel free to go as big or as small as you want.

## Developing

Requirements:
- \>= Java 11 environment

Open the project using your favorite text editor. If you are using IntelliJ, you can open the `build.gradle.kts` file and it is gonna setup the project in the IDE for you.

### Building

```
./gradlew build
```

### Running

There are 2 options for running Anteus. You either need libsqlite3 or docker. Docker is easier but requires some docker knowledge. We do recommend docker though.

*Running Natively*

Native java with sqlite (requires libsqlite3):

If you use homebrew on MacOS `brew install sqlite`.

```
./gradlew run
```

*Running through docker*

Install docker for your platform

```
docker build -t antaeus
docker run antaeus
```

### App Structure
The code given is structured as follows. Feel free however to modify the structure to fit your needs.
```
├── buildSrc
|  | gradle build scripts and project wide dependency declarations
|  └ src/main/kotlin/utils.kt 
|      Dependencies
|
├── pleo-antaeus-app
|       main() & initialization
|
├── pleo-antaeus-core
|       This is probably where you will introduce most of your new code.
|       Pay attention to the PaymentProvider and BillingService class.
|
├── pleo-antaeus-data
|       Module interfacing with the database. Contains the database 
|       models, mappings and access layer.
|
├── pleo-antaeus-models
|       Definition of the Internal and API models used throughout the
|       application.
|
└── pleo-antaeus-rest
        Entry point for HTTP REST API. This is where the routes are defined.
```

### Main Libraries and dependencies
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library
* [Sqlite3](https://sqlite.org/index.html) - Database storage engine

Happy hacking 😁!

## NOTES

### INITIAL DECISIONS:

- This project will be treated as a mix of a task and a real system
- The reason for statement above is to be pragmatic and to avoid the loop of over-engineering the task
- It will have **SOME** practices from real systems to show 

### ASSUMPTIONS

- The microservice is running on multiple servers
- System doesn't have constant high throughput, because invoices are handled periodically (transactions would be the opposite) 
- Pleo has established excellent SLA with payment provider regarding the rate-limit of requests (because of transactions microservice), so don't have to worry about it
- Don't know the logic behind the amount of the bill


### BUSINESS DECISIONS
1. **TIMEZONES**
- PROBLEM:
  - There are multiple currencies in the project which indicates customers are in different countries/continents. 
  - Is there a need to handle charging of customers depending on the timezone?
  - Does the pricing depend on the number of transactions of customer? If yes the timezone will matter.
- SOLUTION:
    - After looking at the PLEO [documentation](https://www.pleo.io/en/pricing), the charging of the PLEO services is based on the number of customer and administrative transactions 
    - PLEO Sales/Administrative team will prepare the bills before the every first of the month
2. **PENDING INVOICES**
- PROBLEM:
  - What are representing the PENDING INVOICES?
- SOLUTION:
  - As mentioned in the timezone problem the Sales/Administrative team will prepare the bills before and enter them into the system using admin tool which will in the background insert those bills in the Invoice table and mark them as PENDING.
  - The solution is to get those invoices from database on every 1. of the month and pass them to the payment provider

#### ARCHITECTURE DECISIONS

1. SCHEDULER 
- PROBLEM:
  - For scheduling payments there are two options:
    1. Java util ScheduledExecutorService
    2. [Quartz](http://www.quartz-scheduler.org/) a richly featured, open source job scheduling library
- SOLUTION
  - The ScheduledExecutorService will be used for this task to avoid overhead of importing the library and to keep it simple
  - However, I think Quartz is a great solution since it provides a lot of [configuration](https://github.com/quartz-scheduler/quartz/blob/master/docs/configuration.adoc) for managing e.g the threads
  - If the PLEO has a lot of scheduling in their microservices I think it would be great even to have a custom library such as quartz
