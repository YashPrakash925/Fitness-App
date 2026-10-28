# Fitness Tracker

A full-stack workout tracker with a plain Java HTTP API, React client, and PostgreSQL database.

## Features

- Sign up and sign in with email and password.
- Search a built-in exercise catalog and add personal exercises.
- Start and finish workouts, log sets with reps and kilograms, and review workout history.
- See elapsed workout time and total volume (`reps × weight`) per set and workout.
- Each user's workout and exercise data is private to that account.

## Stack

- `backend/`: Java 21 built-in HTTP server, JDBC, PostgreSQL driver, and Jackson for JSON.
- `frontend/`: React, Vite, JavaScript.
- `docker-compose.yml`: local PostgreSQL for development.

## Local setup

Requirements: Java 21+, Node.js 20.19+ or 22.12+, npm, PostgreSQL 18 or Docker Desktop, and Git. This checkout has Maven 3.10.0 installed under `.tools/` (ignored by Git).

1. Start PostgreSQL. With Docker Desktop: `docker compose up -d db`. With an existing local PostgreSQL server, use pgAdmin's Query Tool connected to the `postgres` database to run `CREATE ROLE fitness LOGIN PASSWORD 'fitness_dev_only';`, then separately run `CREATE DATABASE fitness_tracker OWNER fitness;`.
2. Set a signing secret, package the API, and start it from the repository root. In PowerShell: `$env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))`; `& '.tools\apache-maven-3.10.0\bin\mvn.cmd' -f backend/pom.xml clean package`; `java -jar backend/target/fitness-tracker-api-0.2.0.jar`. In Bash, if Maven is installed on `PATH`: `export JWT_SECRET="$(openssl rand -base64 32)"`; `mvn -f backend/pom.xml clean package`; `java -jar backend/target/fitness-tracker-api-0.2.0.jar`.
3. In another terminal, start the client from `frontend/` with `npm install` and `npm run dev`.
4. Visit the Vite URL printed in the terminal and create an account.

The API listens on `127.0.0.1:8080` by default. On startup it creates its tables, indexes, and built-in exercise rows if they do not exist. PostgreSQL defaults are for local development only; set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET` in the environment for other environments. Set `HOST=0.0.0.0` on a hosting platform that requires the server to bind to all interfaces, set `CORS_ALLOWED_ORIGINS` to the deployed frontend's exact origin, and set the frontend build variable `VITE_API_URL` to the deployed API URL ending in `/api` (for example, `https://api.example.com/api`). Never commit production secrets.

## GitHub workflow

Work in small, reviewable commits on a feature branch, push the branch, and open a pull request before merging. Add deployment secrets in the hosting provider rather than in GitHub source files. Hosting and production database setup are intentionally left for the project owner.
