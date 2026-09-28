# 🇮🇳 Indian Stock Trading Platform

A **Java-based web application** for simulated stock trading in the Indian stock market. Traders can view stocks, buy and sell shares, manage their portfolio, check balance, and view transaction history. An Admin module is also provided for managing users, stocks, and transactions.

> This is an academic project. Stock prices are simulated/demo values.

## 🛠️ Tech Stack

* **Java**
* **Java Servlets**
* **JDBC**
* **MySQL**
* **Apache Tomcat**
* **Maven**
* **HTML, CSS & JavaScript**

## ✨ Features

* Trader Registration & Login
* Admin Login
* Indian Stock Listing
* Stock Search
* Buy & Sell Stocks
* Portfolio Management
* Balance Management
* Transaction History
* User & Stock Management
* Session-based Authentication
* JDBC Database Integration

## 🏗️ Project Structure

```text
stock-trading-platform/
│
├── src/
│   └── main/
│       ├── java/
│       │   ├── model/
│       │   ├── dao/
│       │   ├── service/
│       │   ├── servlet/
│       │   └── exception/
│       │
│       ├── resources/
│       └── webapp/
│
├── database/
│   └── database.sql
│
├── docs/
│   └── PROJECT_WORKFLOW.md
│
├── pom.xml
├── README.md
└── .gitignore
```

## 🔄 Application Flow

```text
          User
           │
           ▼
    ┌──────────────┐
    │ Login/Register│
    └──────┬───────┘
           │
           ▼
      ┌─────────┐
      │Dashboard│
      └────┬────┘
           │
     ┌─────┼─────────┐
     ▼     ▼         ▼
   Stocks Buy/Sell Portfolio
     │     │         │
     └─────┼─────────┘
           ▼
      Java Servlet
           │
           ▼
       Service Layer
           │
           ▼
          DAO
           │
           ▼
         JDBC
           │
           ▼
         MySQL
```

## 🗄️ Database

Main tables:

```text
Users
Stocks
Portfolio
Transactions
```

## ☕ Core Java Concepts

The project demonstrates:

* OOP
* Inheritance
* Polymorphism
* Interfaces
* Exception Handling
* Collections & Generics
* JDBC
* Java Servlets
* Session Management

## 🚀 How to Run

1. Install **JDK 17+, Maven, MySQL and Apache Tomcat**.
2. Clone the repository.
3. Create the MySQL database using `database.sql`.
4. Configure the database connection.
5. Build the project:

```bash
mvn clean package
```

6. Deploy the generated WAR file on Apache Tomcat.
7. Open the application in a browser.

## 📊 GUVI Java Web-Based Rubric

| Area                                    | Implementation                          |
| --------------------------------------- | --------------------------------------- |
| Problem Understanding & Solution Design | Trading workflow & Admin/Trader modules |
| Core Java Concepts                      | OOP, Exceptions, Collections & Generics |
| Database Integration                    | JDBC + MySQL + DAO                      |
| Servlets & Web Integration              | Java Servlets + Sessions                |

## 👨‍💻 Author

**Prince Kumar**
B.Tech CSE – Artificial Intelligence & Machine Learning

GitHub: [@princekr75620](https://github.com/princekr75620)
