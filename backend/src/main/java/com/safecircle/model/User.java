package com.safecircle.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

@Document(collection = "users")
public class User {
    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String email;

    private String password;

    private String role;

    public User() {}

    public User(String id, String name, String email, String password, String role) {
        this.id = id; this.name = name; this.email = email;
        this.password = password; this.role = role;
    }

    public String getId()           { return id; }
    public void   setId(String id)  { this.id = id; }

    public String getName()             { return name; }
    public void   setName(String name)  { this.name = name; }

    public String getEmail()              { return email; }
    public void   setEmail(String email)  { this.email = email; }

    public String getPassword()                 { return password; }
    public void   setPassword(String password)  { this.password = password; }

    public String getRole()             { return role; }
    public void   setRole(String role)  { this.role = role; }

    // Builder-style factory
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id, name, email, password, role;
        public Builder id(String v)       { id = v;       return this; }
        public Builder name(String v)     { name = v;     return this; }
        public Builder email(String v)    { email = v;    return this; }
        public Builder password(String v) { password = v; return this; }
        public Builder role(String v)     { role = v;     return this; }
        public User build() { return new User(id, name, email, password, role); }
    }
}
