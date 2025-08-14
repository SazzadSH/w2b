# Wallet To Bank

Highly Scalable, High Performance &amp; Secured Event Driven Microservices for financial transaction to Bank from Digital Wallet application with very strong resiliency &amp; fallback mechanism.

## Features

* Idempotency to avoid duplicate transaction processing
* High Performance: Maximum throughput with submilisecond latency using virtual thread and asynchronous processing.
* ACID Compliance for strong consistency
* Write-through Caching: Ensures fast reads but strict data consistency
* High Scalability & Availability: Capable to scale for large number of users and handle millions of request at a time.
* Security: Public API security with HMAC
* Resiliency, Fault Tolerance, Failover &amp; Fallback mechanism with Exponential Backoff
* Logging & Monitoring

## Requirements

## Technology Stack

* Java 21, Spring Boot 3.5.4
* PostgreSQL, MongoDB, Redis
* RabbitMQ
* RESTful API, WebHook, Microservice
* Event Driven Arthitecture, Producer-Consumer Pattern
* Docker
* Java Virtual Thread, Asynchronous / Non-Blocking IO


## How to Run
### With Docker:
* Run in CLI: "docker-compose up" or "docker-compose up -d" (for detached mode)