.DEFAULT_GOAL := help

.PHONY: help build test run up down

help:
	@printf '%s\n' \
	  'make build  Compile, run tests, and package the JAR' \
	  'make test   Run the tests' \
	  'make run    Start Spring Boot (press Ctrl+C to stop)' \
	  'make up     Start the Docker services' \
	  'make down   Stop and remove Docker containers and networks while retaining volumes'

build:
	./mvnw clean package

test:
	./mvnw test

run:
	./mvnw spring-boot:run

up:
	docker compose up -d

down:
	docker compose down
