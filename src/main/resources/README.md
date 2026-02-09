# Expenses Tracking API

This project is an expenses tracking application developed with the assistance of AI tools, leveraging AI-assisted coding for enhanced productivity and code quality.

## High-Level Overview

The **Expenses Tracking API** is a Spring Boot application designed to help users manage their financial expenses on a yearly and monthly basis. It provides a RESTful interface to:

*   **Manage Years:** Create new financial years, each initialized with 12 months.
*   **Track Income:** Update the total income for specific months.
*   **Handle Expenses:**
    *   Add and replicate **fixed expenses** across all months of a year.
    *   Add **variable expenses** to specific months (though the endpoint for this might need to be exposed in the controller).
    *   Delete expenses from specific months (though the endpoint for this might need to be exposed in the controller).
*   **Calculate Balances:** Automatically calculates the remaining balance for each month based on income and expenses.

The application uses **MongoDB** as its persistent data store, with Spring Data MongoDB simplifying data access.

## Technologies Used

*   **Spring Boot:** Framework for building robust, stand-alone, production-grade Spring applications.
*   **Spring Data MongoDB:** Provides an abstraction layer for interacting with MongoDB, simplifying data access.
*   **Lombok:** A library that helps reduce boilerplate code (like getters, setters, constructors) in Java classes.
*   **MongoDB:** A popular NoSQL document database used for flexible and scalable data storage.
*   **Maven:** A powerful build automation tool used for project management and dependency handling.
*   **Java:** The primary programming language used for the application.

## How to Execute

Follow these steps to get the Expenses Tracking API up and running on your local machine.

### Prerequisites

*   **Java Development Kit (JDK) 17 or higher:** Ensure Java is installed and configured on your system.
*   **Apache Maven:** Used for building the project. The project also includes a Maven Wrapper (`mvnw` / `mvnw.cmd`) so a global Maven installation is optional.
*   **MongoDB:** A running MongoDB instance. The application is configured to connect to `mongodb://localhost:27017/expenses_db` by default.

### Setup and Run

1.  **Clone the Repository:**
    ```bash
    git clone <your-repository-url>
    cd expensesApp
    ```
    (Assuming `expensesApp` is the root directory of the project where `pom.xml` is located)

2.  **Start MongoDB:**
    Ensure your MongoDB server is running, typically accessible on `mongodb://localhost:27017`.

3.  **Build the Project:**
    Navigate to the project root directory (`expensesApp`) and build the application using the Maven Wrapper:
    ```bash
    ./mvnw clean install
    ```
    (On Windows, use `mvnw.cmd clean install`)

4.  **Run the Application:**
    After a successful build, you can run the Spring Boot application:
    ```bash
    java -jar target/expenses-api-0.0.1-SNAPSHOT.jar
    ```
    (Replace `expenses-api-0.0.1-SNAPSHOT.jar` with the actual generated JAR file name found in your `target` directory. Alternatively, you can run it directly via Maven: `./mvnw spring-boot:run`)

    The application will start on port `8080` by default.

### API Endpoints (Examples)

Once the application is running, you can interact with it using HTTP requests.

*   **Create a New Year:** `POST http://localhost:8080/api/years/{yearNumber}`
    Example: `POST http://localhost:8080/api/years/2023`

*   **Get Year Data:** `GET http://localhost:8080/api/years/{yearNumber}`
    Example: `GET http://localhost:8080/api/years/2023`

*   **Add and Replicate Fixed Expense:** `POST http://localhost:8080/api/years/{yearNumber}/replicate-fixed` with a JSON `Expense` body.

*   **Update Monthly Income:** `PATCH http://localhost:8080/api/years/{yearNumber}/{monthName}/income?value={incomeValue}`
    Example: `PATCH http://localhost:8080/api/years/2023/January/income?value=5000.00`

### Configuration

The MongoDB connection details can be found and modified in `src/main/resources/application.properties`:

```properties
spring.data.mongodb.database=expenses_db
spring.application.name=expenses-api
spring.data.mongodb.uri=mongodb://localhost:27017/expenses_db
```

```