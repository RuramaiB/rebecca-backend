package com.example.taxbackend.auth;

import com.example.taxbackend.user.Gender;
import com.example.taxbackend.user.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

  private String firstname;
  private String lastname;
  private String email;
  private String dateOfBirth;
  private Gender gender;
  private String physicalAddress;
  private String password;
  private Role role;
  private boolean enabled;
}
