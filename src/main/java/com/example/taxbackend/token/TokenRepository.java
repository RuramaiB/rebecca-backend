package com.example.taxbackend.token;


import com.example.taxbackend.user.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;


import java.util.List;
import java.util.Optional;

public interface TokenRepository extends MongoRepository<Token, String> {

  @Query(value = """
      select t from Token t inner join User u\s
      on t.user.id = u.id\s
      where u.id = :id and (t.expired = false or t.revoked = false)\s
      """)
  List<Token> findAllValidTokenByUser(String id);

  Optional<Token> findByToken(String token);
  Optional<Token> findByUserAndTokenState(User user, TokenState tokenState);

  List<Token> findByUser(User user);
}
