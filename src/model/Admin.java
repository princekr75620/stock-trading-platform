package model;

import java.time.LocalDateTime;

/**
 * Admin user with system management, financial data security, and reporting capabilities.
 * Demonstrates Inheritance and Polymorphism.
 */
public class Admin extends User {
    private static final long serialVersionUID = 1L;

    private String department;

    public Admin(String userId, String name, String email, String password) {
        super(userId, name, email, password, UserRole.ADMIN);
        this.department = "System Administration";
    }

    public Admin(String userId, String name, String email, String password, String department) {
        super(userId, name, email, password, UserRole.ADMIN);
        this.department = department;
    }

    public Admin(String userId, String name, String email, String password, String department, LocalDateTime createdAt) {
        super(userId, name, email, password, UserRole.ADMIN, createdAt);
        this.department = department;
    }

    @Override
    public String getRoleDescription() {
        return "Administrator: Manages users, financial security policies, system configuration, and trading reports.";
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }
}
