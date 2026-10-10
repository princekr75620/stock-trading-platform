package model.dto;

import java.io.Serializable;

/**
 * Data Transfer Object for authentication and registration requests.
 */
public class UserCredentialsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String usernameOrEmail;
    private String password;
    private String name;
    private String email;
    private String role;
    private double initialDeposit;

    public UserCredentialsDTO() {}

    public UserCredentialsDTO(String usernameOrEmail, String password) {
        this.usernameOrEmail = usernameOrEmail;
        this.password = password;
    }

    public String getUsernameOrEmail() { return usernameOrEmail; }
    public void setUsernameOrEmail(String usernameOrEmail) { this.usernameOrEmail = usernameOrEmail; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public double getInitialDeposit() { return initialDeposit; }
    public void setInitialDeposit(double initialDeposit) { this.initialDeposit = initialDeposit; }
}
