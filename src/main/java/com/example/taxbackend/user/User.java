package com.example.taxbackend.user;


import com.example.taxbackend.token.Token;
import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document
@TypeAlias("User")
public class User implements UserDetails {

  @Id
  private String id;
  private String firstname;
  private String lastname;
  @Indexed(unique = true)
  private String email;
  private String dateOfBirth;
  private Gender gender;
  @Indexed(unique = true)
  private String physicalAddress;
  private String password;
  private Role role;
  private Boolean enabled = true;
//  private Boolean isFirstTime = true;
//  private Boolean isVerified = true;
//  private Boolean hasTwoFactor = true;
  @JsonBackReference
  private List<Token> tokens;

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return role.getAuthorities();
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }
}
